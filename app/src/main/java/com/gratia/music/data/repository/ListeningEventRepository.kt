package com.gratia.music.data.repository

import com.gratia.music.data.dao.ListeningEventDao
import com.gratia.music.data.model.DailyListeningSummary
import com.gratia.music.data.model.ListeningEventEntity
import com.gratia.music.data.model.SongEntity
import com.gratia.music.data.model.musicSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

class ListeningEventRepository(private val dao: ListeningEventDao) {

    /**
     * Records one listening event from the track itself.
     *
     * The whole identity snapshot travels with the event — title, artist, album,
     * artwork and source — so the aggregate never needs the `songs` row and a
     * remote track that was streamed but never persisted still counts and still
     * has artwork (spec 12/13). [song] is the track that was actually heard, so
     * the recorded identity is the played one, not a later lookup that might
     * have been edited or removed.
     */
    suspend fun logEvent(
        song: SongEntity,
        eventType: String,
        listenedSeconds: Long = 0L,
        completed: Boolean = false,
        skipped: Boolean = false
    ) {
        withContext(Dispatchers.IO) {
            val event = ListeningEventEntity(
                songId = song.id,
                eventType = eventType,
                listenedSeconds = listenedSeconds,
                completed = completed,
                skipped = skipped,
                source = song.musicSource.name,
                title = song.title,
                artist = song.artist,
                artistId = song.providerArtistId,
                album = song.album,
                albumId = song.providerAlbumId,
                artworkUrl = song.artworkUrl ?: song.coverArtPath,
                durationMs = song.durationMs,
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
            com.gratia.music.GratiaApp.instance.database.songDao().clearAllStats()
        }
    }
}
