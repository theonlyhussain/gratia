package com.gratia.music.data.repository

import com.gratia.music.GratiaApp
import com.gratia.music.data.dao.PlaybackHistoryDao
import com.gratia.music.data.model.PlaybackHistoryEntity
import com.gratia.music.data.model.SongEntity
import com.gratia.music.data.model.musicSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * The one authoritative record of what actually played (spec 17–19, 93).
 *
 * Every path that starts a track funnels through [record]; the snapshot comes
 * from the [SongEntity] itself, so a remote track that was never persisted to
 * the `songs` table still appears in the timeline with its artwork. History is
 * written by the player's own truth — a track that only appeared on screen is
 * not a play, and nothing in the UI layer writes here.
 */
class PlaybackHistoryRepository(private val dao: PlaybackHistoryDao) {

    /**
     * One playback, at the moment it actually starts making sound.
     *
     * Returns the row id so the player can keep this session's listened
     * seconds current as they accumulate — the timeline carries real
     * engagement, not just a timestamp.
     */
    suspend fun record(song: SongEntity): String? = withContext(Dispatchers.IO) {
        // A track that is not playing and has no identity cannot be history.
        if (song.title.isBlank()) return@withContext null
        val entry = PlaybackHistoryEntity(
            songId = song.id,
            videoId = song.providerTrackId?.takeIf { it.isNotBlank() },
            title = song.title,
            artist = song.artist.ifBlank { "Unknown artist" },
            album = song.album?.takeIf { it.isNotBlank() },
            artworkUrl = song.artworkUrl ?: song.coverArtPath,
            source = song.musicSource.name,
        )
        dao.insert(entry)
        entry.id
    }

    /** Keeps the current play's listened seconds current. */
    suspend fun updateListening(historyId: String, seconds: Long, completed: Boolean) =
        withContext(Dispatchers.IO) {
            dao.updateListening(historyId, seconds, completed)
        }

    fun recent(limit: Int = 20): Flow<List<PlaybackHistoryEntity>> = dao.recent(limit)

    fun all(limit: Int = 2000): Flow<List<PlaybackHistoryEntity>> = dao.all(limit)

    suspend fun count(): Int = dao.count()

    /** Remove one play — Home shelf, history screen and stats all follow. */
    suspend fun delete(id: String) = withContext(Dispatchers.IO) { dao.delete(id) }

    /** Every play of one recording leaves the timeline. */
    suspend fun deleteForSong(songId: String) =
        withContext(Dispatchers.IO) { dao.deleteForSong(songId) }

    /** Clear the timeline. Listening aggregates in `listening_events` are untouched —
     *  that policy lives with the screens that offer the action (spec 123). */
    suspend fun clear() = withContext(Dispatchers.IO) { dao.clear() }
}
