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
            dao.getDailySummariesSince(startTimestamp)
        }
    }

    suspend fun clearHistory() {
        withContext(Dispatchers.IO) {
            dao.clearAllHistory()
        }
    }

    suspend fun getTopArtists(startTimestamp: Long, endTimestamp: Long, limit: Int = 5): List<com.gratia.music.data.model.ArtistListenSummary> {
        return withContext(Dispatchers.IO) {
            dao.getTopArtists(startTimestamp, endTimestamp, limit)
        }
    }

    suspend fun getTopTracks(startTimestamp: Long, endTimestamp: Long, limit: Int = 5): List<com.gratia.music.data.model.TrackListenSummary> {
        return withContext(Dispatchers.IO) {
            dao.getTopTracks(startTimestamp, endTimestamp, limit)
        }
    }

    suspend fun getTotalListeningSeconds(startTimestamp: Long, endTimestamp: Long): Long {
        return withContext(Dispatchers.IO) {
            dao.getTotalListeningSeconds(startTimestamp, endTimestamp) ?: 0L
        }
    }
}
