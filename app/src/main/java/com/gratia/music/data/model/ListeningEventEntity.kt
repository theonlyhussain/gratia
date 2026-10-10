package com.gratia.music.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * One recorded listening event — the canonical, self-contained record of a play.
 *
 * The row deliberately carries the track's own identity snapshot (title, artist,
 * album, artwork, source) alongside [songId]. The aggregates on the Stats/Replay
 * screens used to `INNER JOIN songs`, which silently dropped every remote track
 * that was streamed but never persisted to the `songs` table: a YouTube Music
 * play contributed to total time but vanished from Top Songs / Top Artists /
 * Top Albums, and had no artwork (spec 12/13/58–60). Keeping the snapshot on the
 * event makes the aggregate independent of whether a `songs` row happens to
 * exist, and lets the timeline render offline.
 *
 * [source] is the [MusicSource] the track played from (LOCAL,
 * YOUTUBE_MUSIC, DOWNLOADED_REMOTE) — the same three-way classification the rest
 * of the app uses, so "does this count?" is never a second, drifting rule.
 */
@Entity(tableName = "listening_events")
data class ListeningEventEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val songId: String,
    val eventType: String, // "play", "pause", "complete", "skip", "lyrics_opened", "song_added"
    val timestamp: Long = System.currentTimeMillis(),
    val listenedSeconds: Long = 0L,
    val source: String = MusicSource.LOCAL.name,
    val completed: Boolean = false,
    val skipped: Boolean = false,

    // ── Canonical identity snapshot (spec 13) ─────────────────────────────
    /** Track title at the moment of the play. Empty only on pre-v16 rows. */
    val title: String = "",
    /** Primary artist credit at the moment of the play. Empty on pre-v16 rows. */
    val artist: String = "",
    /** Remote artist id, when the track came from the catalogue. */
    val artistId: String? = null,
    /** Album name, when the track stated one. */
    val album: String? = null,
    /** Remote album id, when the track came from the catalogue. */
    val albumId: String? = null,
    /** Best-known artwork URL — remote thumbnail or local cover path. */
    val artworkUrl: String? = null,
    /** Track length, so display and aggregation never need the songs row. */
    val durationMs: Long = 0L,
)
