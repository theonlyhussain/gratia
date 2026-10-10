package com.gratia.music.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * One row per actual playback — the unified, source-aware listening timeline.
 *
 * [ListeningEventEntity] answers "how much was listened to" for the stats
 * aggregates; this table answers "what was played, when, from where" for the
 * Recently Played shelf and the Listening History screen. The two are written
 * by the same moment in the player but are deliberately separate facts.
 *
 * A remote YouTube Music track and a local file with the same title and artist
 * are different rows here, exactly as they are different facts: the remote one
 * carries its `videoId` and `ytm_` song id, the local one its own id, and the
 * [source] column says which is which in human terms. A metadata snapshot
 * (title, artist, album, artwork) is stored with the row so a track that was
 * streamed once and never persisted to `songs` still shows its face in the
 * timeline instead of a blank.
 */
@Entity(tableName = "playback_history")
data class PlaybackHistoryEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    /** The [SongEntity.id] that played — `ytm_<videoId>` for remote tracks. */
    val songId: String,
    /** The stable remote recording id, when the track has one. */
    val videoId: String? = null,
    val title: String,
    val artist: String,
    val album: String? = null,
    val artworkUrl: String? = null,
    /** [MusicSource] name — LOCAL, YOUTUBE_MUSIC or DOWNLOADED_REMOTE. */
    val source: String,
    val playedAt: Long = System.currentTimeMillis(),
    /** Listened seconds, updated as the player reports them. */
    val listenedSeconds: Long = 0L,
    val completed: Boolean = false,
)
