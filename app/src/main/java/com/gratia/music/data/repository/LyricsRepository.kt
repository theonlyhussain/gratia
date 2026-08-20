package com.gratia.music.data.repository

import com.gratia.music.data.dao.LyricsDao
import com.gratia.music.data.model.LyricsEntity
import com.gratia.music.data.model.SongEntity
import com.gratia.music.lyrics.LRCLIBProvider
import com.gratia.music.lyrics.LyricallyProvider
import com.gratia.music.lyrics.LyricsProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LyricsRepository(
    private val lyricsDao: LyricsDao
) {
    private val providers: List<LyricsProvider> = listOf(LyricallyProvider(), LRCLIBProvider())

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

        // Fetch from providers
        for (provider in providers) {
            val result = if (provider is LRCLIBProvider) {
                provider.fetchLyricsWithDuration(song.title, song.artist, song.album, song.durationMs)
            } else {
                provider.fetchLyrics(song.title, song.artist, song.album)
            }
            
            if (result != null) {
                // Determine format
                // Priority: Enhanced -> Synced -> Plain
                // We trust the provider's result to be the highest priority it found.
                // We save it as "automatic" to ensure only ONE automatic version exists.
                val existingOffset = autoLyrics?.offsetMs ?: 0L
                val newLyrics = LyricsEntity(
                    songId = song.id,
                    text = result.text,
                    isSynced = result.isSynced,
                    provider = "automatic",
                    offsetMs = existingOffset,
                    isManuallyEdited = false,
                    isWordLevel = result.isWordLevel,
                    isActiveOverride = false
                )
                lyricsDao.insertLyrics(newLyrics)
                
                // If user doesn't have an active manual override, we return the newly fetched one.
                return@withContext if (manualLyrics?.isActiveOverride == true) manualLyrics else newLyrics
            }
        }

        return@withContext activeLyrics
    }

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
