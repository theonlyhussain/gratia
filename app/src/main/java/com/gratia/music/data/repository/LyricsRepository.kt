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
        
        // Concurrently fetch from all providers
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
        
        // Await all with a hard timeout of 8 seconds to prevent indefinite hangs
        val results: List<com.gratia.music.lyrics.LyricsResult> = withTimeoutOrNull(8000L) {
            deferredResults.awaitAll()
        }?.filterNotNull() ?: emptyList()

        if (results.isEmpty()) return@withContext activeLyrics

        // Filter and sort results
        val bestResult = results
            .filter { it.durationDifferenceMs <= 15000L } // Reject wildly wrong durations
            .sortedWith(
                compareByDescending<com.gratia.music.lyrics.LyricsResult> { it.syncLevel.priority }
                    .thenByDescending { it.matchConfidence }
                    .thenBy { it.durationDifferenceMs }
                    .thenByDescending { providerPreferenceOrder[it.providerName] ?: 0 }
            ).firstOrNull()
            
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
