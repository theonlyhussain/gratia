package com.gratia.music.data.repository

import com.gratia.music.data.dao.ListeningEventDao
import com.gratia.music.data.model.DailyListeningSummary
import com.gratia.music.data.model.ListeningEventEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

class ListeningEventRepository(private val dao: ListeningEventDao) {

    suspend fun logEvent(
        songId: String,
        eventType: String,
        listenedSeconds: Long = 0L,
        completed: Boolean = false,
        skipped: Boolean = false
    ) {
        withContext(Dispatchers.IO) {
            val event = ListeningEventEntity(
                songId = songId,
                eventType = eventType,
                listenedSeconds = listenedSeconds,
                completed = completed,
                skipped = skipped
            )
            dao.insertEvent(event)
        }
    }

    suspend fun getDailySummaries(days: Int): List<DailyListeningSummary> {
        return withContext(Dispatchers.IO) {
            val startTimestamp = LocalDate.now()
                .minusDays(days.toLong() - 1) // include today
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
            val offsetSeconds = ZoneId.systemDefault().rules.getOffset(java.time.Instant.now()).totalSeconds.toLong()
            dao.getDailySummariesSince(startTimestamp, offsetSeconds)
        }
    }

    suspend fun clearHistory() {
        withContext(Dispatchers.IO) {
            dao.clearAllHistory()
            com.gratia.music.GratiaApp.instance.database.songDao().clearAllStats()
        }
    }

    suspend fun getTopArtists(startTimestamp: Long, endTimestamp: Long, limit: Int = 5): List<com.gratia.music.data.model.ArtistListenSummary> {
        return withContext(Dispatchers.IO) {
            val events = dao.getTopArtists(startTimestamp, endTimestamp, limit)
            if (events.isEmpty() && startTimestamp == 0L) {
                val allSongs = com.gratia.music.GratiaApp.instance.database.songDao().getAllSongsOnce()
                allSongs.filter { it.playCount > 0 }
                    .groupBy { it.artist }
                    .map { (artist, songs) ->
                        val totalSec = songs.sumOf { 
                            if (it.totalListenTime > 0) it.totalListenTime / 1000 
                            else (it.playCount * (it.durationMs / 1000.0)).toLong() 
                        }
                        com.gratia.music.data.model.ArtistListenSummary(artist, totalSec)
                    }
                    .sortedByDescending { it.totalSeconds }
                    .take(limit)
            } else {
                events
            }
        }
    }

    suspend fun getTopTracks(startTimestamp: Long, endTimestamp: Long, limit: Int = 5): List<com.gratia.music.data.model.TrackListenSummary> {
        return withContext(Dispatchers.IO) {
            val events = dao.getTopTracks(startTimestamp, endTimestamp, limit)
            if (events.isEmpty() && startTimestamp == 0L) {
                val allSongs = com.gratia.music.GratiaApp.instance.database.songDao().getAllSongsOnce()
                allSongs.filter { it.playCount > 0 }
                    .map { song ->
                        val totalSec = if (song.totalListenTime > 0) song.totalListenTime / 1000 else (song.playCount * (song.durationMs / 1000.0)).toLong()
                        com.gratia.music.data.model.TrackListenSummary(song.id, song.title, song.artist, totalSec)
                    }
                    .sortedByDescending { it.totalSeconds }
                    .take(limit)
            } else {
                events
            }
        }
    }

    suspend fun getTotalListeningSeconds(startTimestamp: Long, endTimestamp: Long): Long {
        return withContext(Dispatchers.IO) {
            val total = dao.getTotalListeningSeconds(startTimestamp, endTimestamp) ?: 0L
            if (total == 0L && startTimestamp == 0L) {
                val allSongs = com.gratia.music.GratiaApp.instance.database.songDao().getAllSongsOnce()
                allSongs.sumOf { 
                    if (it.totalListenTime > 0) it.totalListenTime / 1000 
                    else (it.playCount * (it.durationMs / 1000.0)).toLong() 
                }
            } else {
                total
            }
        }
    }
}
