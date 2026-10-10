package com.gratia.music.data.repository

import android.util.Log
import com.gratia.music.data.dao.LyricsDao
import com.gratia.music.data.model.LyricsEntity
import com.gratia.music.data.model.SongEntity
import com.gratia.music.lyrics.LRCLIBProvider
import com.gratia.music.lyrics.LyricallyProvider
import com.gratia.music.lyrics.LyricsProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withTimeoutOrNull
import com.gratia.music.GratiaApp
import com.gratia.music.data.SettingsDataStore

class LyricsRepository(
    private val lyricsDao: LyricsDao
) {
    private val ytmLyricsProvider = com.gratia.music.lyrics.YouTubeMusicLyricsProvider()
    private val providers: List<LyricsProvider> = listOf(
        // BiniLyrics leads: it resolves the recording (and reports its ISRC)
        // before fetching, so it is the least likely of the sources to return
        // the wrong edit under the right title.
        com.gratia.music.lyrics.BiniLyrics,
        com.gratia.music.lyrics.BetterLyrics,
        com.gratia.music.lyrics.LyricsPlus,
        com.gratia.music.lyrics.SimpMusicLyrics,
        com.gratia.music.lyrics.KuGou,
        LRCLIBProvider(),
        LyricallyProvider(),
        com.gratia.music.lyrics.LyricsifyProvider(),
        ytmLyricsProvider
    )

    // Used as a tie-breaker if SyncLevel, matchConfidence, and duration difference are all equal
    private val providerPreferenceOrder = mapOf(
        "BiniLyrics" to 100,
        "BetterLyrics" to 70,
        "Lyricsify" to 65,
        "LyricsPlus" to 60,
        "SimpMusic" to 50,
        "YouTube Music" to 40,
        "KuGou" to 30,
        "Lyrically" to 20,
        "LRCLIB" to 10
    )

    suspend fun getLyrics(song: SongEntity, forceRefresh: Boolean = false): LyricsEntity? = withContext(Dispatchers.IO) {
        val allLyrics = lyricsDao.getLyricsForSong(song.id)
        val manualLyrics = allLyrics.find { it.provider == "manual" }
        val autoLyrics = allLyrics.find { it.provider == "automatic" }
        
        val activeLyrics = if (manualLyrics?.isActiveOverride == true) manualLyrics else autoLyrics

        if (activeLyrics != null && !forceRefresh) {
            return@withContext activeLyrics
        }

        // If force refresh, don't overwrite if manually edited and selected
        if (forceRefresh && activeLyrics?.provider == "manual") {
            return@withContext activeLyrics
        }

        val settings = SettingsDataStore(GratiaApp.instance.applicationContext)
        val onlineEnabled = settings.onlineDataEnabledFlow.first()
        
        if (!onlineEnabled) {
            return@withContext activeLyrics
        }

        val videoId = song.providerTrackId ?: (if (song.storageProvider == "youtube_music") song.id.removePrefix("ytm_") else null)

        // Every provider is asked at once, but the answer is returned as soon
        // as one is *good enough* rather than when the slowest has spoken. The
        // full race used to gate every return on all nine providers finishing
        // (or an 8s ceiling) — which is the difference between lyrics appearing
        // in under a second and the panel sitting on its skeleton for the
        // ceiling. Grading is [quality] — word-synced beats line-synced beats
        // unsynced — so an early word-synced result is taken over a line-synced
        // one still in flight, but an early plain-text hit does not stop the
        // search for something better. Remaining providers keep running in the
        // background: their best answer is still graded, still written to the
        // cache, and still shown on the next visit — the race is only exited
        // early, never narrowed.
        val deferredResults = providers.map { provider ->
            async {
                try {
                    provider.fetchLyrics(song.title, song.artist, song.album, song.durationMs, videoId)
                } catch (e: Exception) {
                    Log.e("LyricsRepository", "Error fetching from ${provider.name}", e)
                    null
                }
            }
        }

        fun grade(result: com.gratia.music.lyrics.LyricsResult): Int =
            when {
                result.durationDifferenceMs > 15000L -> -1 // Reject wildly wrong durations
                else -> result.syncLevel.priority
            }

        suspend fun bestOf(collected: List<com.gratia.music.lyrics.LyricsResult>): com.gratia.music.lyrics.LyricsResult? =
            collected.filter { grade(it) >= 0 }
                .sortedWith(
                    compareByDescending<com.gratia.music.lyrics.LyricsResult> { it.syncLevel.priority }
                        .thenByDescending { it.matchConfidence }
                        .thenBy { it.durationDifferenceMs }
                        .thenByDescending { providerPreferenceOrder[it.providerName] ?: 0 }
                ).firstOrNull()

        val collected = mutableListOf<com.gratia.music.lyrics.LyricsResult>()
        var bestResult: com.gratia.music.lyrics.LyricsResult? = null
        withTimeoutOrNull(PROVIDER_RACE_TIMEOUT_MS) {
            for (deferred in deferredResults) {
                val result = deferred.await()
                if (result != null) collected.add(result)
                val best = bestOf(collected)
                if (best != null) {
                    bestResult = best
                    // Word-synced from any provider is the ceiling: no line
                    // worth waiting for behind it.
                    if (best.syncLevel.priority >= WORD_SYNC_PRIORITY) return@withTimeoutOrNull
                }
            }
        }
        // Whatever the exit — early, ceiling, or every provider answered — the
        // best answer seen so far stands.
        if (bestResult == null) bestResult = bestOf(collected)

        if (bestResult != null) {
            // Re-parse the content to determine actual sync level from content
            // rather than trusting provider metadata
            val doc = com.gratia.music.lyrics.LyricsParser.parse(bestResult.text)
            val existingOffset = autoLyrics?.offsetMs ?: 0L
            val newLyrics = LyricsEntity(
                songId = song.id,
                text = bestResult.text,
                isSynced = doc !is com.gratia.music.lyrics.LyricsDocument.Plain,
                provider = "automatic",
                // The provider that actually answered — the UI credits this,
                // never the "automatic" slot it is stored under.
                providerName = bestResult.providerName,
                offsetMs = existingOffset,
                isManuallyEdited = false,
                isWordLevel = doc is com.gratia.music.lyrics.LyricsDocument.WordSynced,
                isActiveOverride = false
            )
            lyricsDao.insertLyrics(newLyrics)
            return@withContext if (manualLyrics?.isActiveOverride == true) manualLyrics else newLyrics
        }

        return@withContext activeLyrics
    }

    /**
     * How long the whole provider race may run. Generous enough for the slow
     * catalogue-backed provider to answer, short enough that a dead network
     * fails inside a beat rather than a hang.
     */
    private companion object {
        const val PROVIDER_RACE_TIMEOUT_MS = 8000L

        /** [com.gratia.music.lyrics.SyncLevel.WORD]'s priority — the ceiling grade. */
        const val WORD_SYNC_PRIORITY = 3
    }

    /**
     * Saves manual lyrics with auto-detected format from content.
     * This is the preferred entry point — the UI should not specify isSynced/isWordLevel.
     */
    suspend fun saveManualLyrics(songId: String, text: String, isActive: Boolean = true) {
        val doc = com.gratia.music.lyrics.LyricsParser.parse(text)
        val allLyrics = lyricsDao.getLyricsForSong(songId)
        val manualLyrics = allLyrics.find { it.provider == "manual" }
        
        val newLyrics = LyricsEntity(
            songId = songId,
            text = text,
            isSynced = doc !is com.gratia.music.lyrics.LyricsDocument.Plain,
            provider = "manual",
            offsetMs = manualLyrics?.offsetMs ?: 0L,
            isManuallyEdited = true,
            isWordLevel = doc is com.gratia.music.lyrics.LyricsDocument.WordSynced,
            isActiveOverride = isActive
        )
        lyricsDao.insertLyrics(newLyrics)
    }

    /**
     * Legacy overload for callers that still pass explicit format flags (e.g. UploadScreen).
     */
    suspend fun saveManualLyrics(songId: String, text: String, isSynced: Boolean, isWordLevel: Boolean = false, isActive: Boolean = true) {
        val allLyrics = lyricsDao.getLyricsForSong(songId)
        val manualLyrics = allLyrics.find { it.provider == "manual" }
        
        val newLyrics = LyricsEntity(
            songId = songId,
            text = text,
            isSynced = isSynced,
            provider = "manual",
            offsetMs = manualLyrics?.offsetMs ?: 0L,
            isManuallyEdited = true,
            isWordLevel = isWordLevel,
            isActiveOverride = isActive
        )
        lyricsDao.insertLyrics(newLyrics)
    }
    
    suspend fun updateOffset(songId: String, newOffsetMs: Long) {
        val allLyrics = lyricsDao.getLyricsForSong(songId)
        val activeLyrics = allLyrics.find { it.provider == "manual" && it.isActiveOverride } ?: allLyrics.find { it.provider == "automatic" }
        if (activeLyrics != null) {
            lyricsDao.updateLyrics(activeLyrics.copy(offsetMs = newOffsetMs))
        }
    }

    suspend fun getLyricsOnce(songId: String): LyricsEntity? = withContext(Dispatchers.IO) {
        val allLyrics = lyricsDao.getLyricsForSong(songId)
        return@withContext allLyrics.find { it.provider == "manual" && it.isActiveOverride } ?: allLyrics.find { it.provider == "automatic" }
    }
    
    suspend fun getAllLyricsForSong(songId: String): List<LyricsEntity> = withContext(Dispatchers.IO) {
        return@withContext lyricsDao.getLyricsForSong(songId)
    }

    suspend fun deleteLyrics(songId: String, provider: String? = null) {
        if (provider != null) {
            val allLyrics = lyricsDao.getLyricsForSong(songId)
            val toDelete = allLyrics.find { it.provider == provider }
            if (toDelete != null) {
                lyricsDao.delete(toDelete)
            }
        } else {
            lyricsDao.deleteLyricsForSong(songId)
        }
    }

    suspend fun setActiveLyrics(songId: String, provider: String) {
        val allLyrics = lyricsDao.getLyricsForSong(songId)
        // Set all to inactive first, then the requested one to active.
        allLyrics.forEach { lyrics ->
            val updated = lyrics.copy(isActiveOverride = (lyrics.provider == provider && provider == "manual"))
            lyricsDao.updateLyrics(updated)
        }
    }
}
