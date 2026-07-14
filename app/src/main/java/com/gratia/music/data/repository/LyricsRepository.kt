package com.gratia.music.data.repository

import com.gratia.music.data.dao.LyricsDao
import com.gratia.music.data.model.LyricsEntity
import com.gratia.music.data.model.SongEntity
import com.gratia.music.lyrics.LRCLIBProvider
import com.gratia.music.lyrics.LyricsProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LyricsRepository(
    private val lyricsDao: LyricsDao
) {
    private val providers: List<LyricsProvider> = listOf(LRCLIBProvider())

    suspend fun getLyrics(song: SongEntity, forceRefresh: Boolean = false): LyricsEntity? = withContext(Dispatchers.IO) {
        // 1. Check local DB first
        val localLyrics = lyricsDao.getLyricsForSong(song.id)

        if (localLyrics != null && !forceRefresh) {
            return@withContext localLyrics
        }

        // If force refresh, don't overwrite if manually edited
        if (forceRefresh && localLyrics?.isManuallyEdited == true) {
            return@withContext localLyrics
        }

        // 2. Fetch from providers
        for (provider in providers) {
            val result = provider.fetchLyrics(song.title, song.artist, song.album)
            if (result != null) {
                // We want to preserve existing offset if there was one, else 0
                val existingOffset = localLyrics?.offsetMs ?: 0L
                val newLyrics = LyricsEntity(
                    songId = song.id,
                    text = result.text,
                    isSynced = result.isSynced,
                    provider = result.providerName,
                    offsetMs = existingOffset,
                    isManuallyEdited = false
                )
                lyricsDao.insertLyrics(newLyrics)
                return@withContext newLyrics
            }
        }

        return@withContext localLyrics // return whatever we had (or null) if all fails
    }

    suspend fun saveManualLyrics(songId: String, text: String, isSynced: Boolean) {
        val existing = lyricsDao.getLyricsForSong(songId)
        val newLyrics = LyricsEntity(
            songId = songId,
            text = text,
            isSynced = isSynced,
            provider = "manual",
            offsetMs = existing?.offsetMs ?: 0L,
            isManuallyEdited = true
        )
        lyricsDao.insertLyrics(newLyrics)
    }
    
    suspend fun updateOffset(songId: String, newOffsetMs: Long) {
        val existing = lyricsDao.getLyricsForSong(songId)
        if (existing != null) {
            lyricsDao.updateLyrics(existing.copy(offsetMs = newOffsetMs))
        }
    }

    suspend fun getLyricsOnce(songId: String): LyricsEntity? = withContext(Dispatchers.IO) {
        return@withContext lyricsDao.getLyricsForSong(songId)
    }

    suspend fun deleteLyrics(songId: String) {
        lyricsDao.deleteLyricsForSong(songId)
    }
}
