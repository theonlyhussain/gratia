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
}
