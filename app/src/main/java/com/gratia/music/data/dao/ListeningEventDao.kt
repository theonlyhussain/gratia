package com.gratia.music.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.gratia.music.data.model.ListeningEventEntity
import com.gratia.music.data.model.DailyListeningSummary

@Dao
interface ListeningEventDao {
    @Insert
    suspend fun insertEvent(event: ListeningEventEntity)

    @Query("""
        SELECT 
            date(timestamp / 1000, 'unixepoch', 'localtime') AS dateString,
            COUNT(DISTINCT songId) AS songsPlayed,
            SUM(listenedSeconds) AS listeningSeconds,
            SUM(CASE WHEN completed = 1 THEN 1 ELSE 0 END) AS completedSongs,
            SUM(CASE WHEN skipped = 1 THEN 1 ELSE 0 END) AS skips,
            SUM(CASE WHEN eventType = 'lyrics_opened' THEN 1 ELSE 0 END) AS lyricsOpenedCount,
            SUM(CASE WHEN eventType = 'song_added' THEN 1 ELSE 0 END) AS songsAdded
        FROM listening_events
        WHERE timestamp >= :startTimestamp
        GROUP BY dateString
        ORDER BY dateString DESC
    """)
    suspend fun getDailySummariesSince(startTimestamp: Long): List<DailyListeningSummary>

    @Query("DELETE FROM listening_events")
    suspend fun clearAllHistory()

    /**
     * Top artists over a period, aggregated from the events themselves.
     *
     * The `songs` join is a *fallback* for rows written before the identity
     * snapshot existed (v16); new events carry the artist directly, so a remote
     * track streamed but never persisted is counted like any other. The
     * correlated subquery picks the best-known cover among the artist's
     * listened tracks, so a remote artist resolves real artwork (spec 12/58/59).
     */
    @Query("""
        SELECT resolved.artist AS artist,
               SUM(resolved.listenedSeconds) AS totalSeconds,
               (SELECT e2.artworkUrl FROM listening_events e2
                  WHERE e2.artist = resolved.artist
                    AND e2.artworkUrl IS NOT NULL AND e2.artworkUrl != ''
                  ORDER BY e2.listenedSeconds DESC LIMIT 1) AS artworkUrl
        FROM (
            SELECT COALESCE(NULLIF(e.artist, ''), NULLIF(s.artist, ''), '') AS artist,
                   e.listenedSeconds AS listenedSeconds
            FROM listening_events e
            LEFT JOIN songs s ON e.songId = s.id
            WHERE e.timestamp >= :startTimestamp AND e.timestamp <= :endTimestamp
              AND e.listenedSeconds > 0
        ) AS resolved
        WHERE resolved.artist != ''
        GROUP BY resolved.artist
        ORDER BY totalSeconds DESC
        LIMIT :limit
    """)
    suspend fun getTopArtists(startTimestamp: Long, endTimestamp: Long, limit: Int = 5): List<com.gratia.music.data.model.ArtistListenSummary>

    /**
     * Top tracks over a period, aggregated from the events themselves — a
     * streamed track counts without a `songs` row, and carries its own cover
     * (spec 12/58/60).
     */
    @Query("""
        SELECT resolved.songId AS songId,
               MAX(resolved.title) AS title,
               MAX(resolved.artist) AS artist,
               SUM(resolved.listenedSeconds) AS totalSeconds,
               (SELECT e2.artworkUrl FROM listening_events e2
                  WHERE e2.songId = resolved.songId
                    AND e2.artworkUrl IS NOT NULL AND e2.artworkUrl != ''
                  ORDER BY e2.listenedSeconds DESC LIMIT 1) AS artworkUrl
        FROM (
            SELECT e.songId AS songId,
                   COALESCE(NULLIF(e.title, ''), NULLIF(s.title, ''), '') AS title,
                   COALESCE(NULLIF(e.artist, ''), NULLIF(s.artist, ''), '') AS artist,
                   e.listenedSeconds AS listenedSeconds
            FROM listening_events e
            LEFT JOIN songs s ON e.songId = s.id
            WHERE e.timestamp >= :startTimestamp AND e.timestamp <= :endTimestamp
              AND e.listenedSeconds > 0
        ) AS resolved
        GROUP BY resolved.songId
        ORDER BY totalSeconds DESC
        LIMIT :limit
    """)
    suspend fun getTopTracks(startTimestamp: Long, endTimestamp: Long, limit: Int = 5): List<com.gratia.music.data.model.TrackListenSummary>

    /**
     * Top albums over a period. Albums are not addressable in `songs` for a
     * streamed track, so the name is taken off the event's own snapshot; an
     * event with no album contributes nothing rather than a fabricated label
     * (spec 58/60).
     */
    @Query("""
        SELECT resolved.name AS name,
               MAX(resolved.artist) AS artist,
               SUM(resolved.listenedSeconds) AS totalSeconds,
               (SELECT e2.artworkUrl FROM listening_events e2
                  WHERE e2.album = resolved.name
                    AND e2.artworkUrl IS NOT NULL AND e2.artworkUrl != ''
                  ORDER BY e2.listenedSeconds DESC LIMIT 1) AS artworkUrl
        FROM (
            SELECT COALESCE(NULLIF(e.album, ''), NULLIF(s.album, ''), '') AS name,
                   COALESCE(NULLIF(e.artist, ''), NULLIF(s.artist, ''), '') AS artist,
                   e.listenedSeconds AS listenedSeconds
            FROM listening_events e
            LEFT JOIN songs s ON e.songId = s.id
            WHERE e.timestamp >= :startTimestamp AND e.timestamp <= :endTimestamp
              AND e.listenedSeconds > 0
        ) AS resolved
        WHERE resolved.name != ''
        GROUP BY resolved.name
        ORDER BY totalSeconds DESC
        LIMIT :limit
    """)
    suspend fun getTopAlbums(startTimestamp: Long, endTimestamp: Long, limit: Int = 5): List<com.gratia.music.data.model.AlbumListenSummary>

    @Query("""
        SELECT SUM(listenedSeconds)
        FROM listening_events
        WHERE timestamp >= :startTimestamp AND timestamp <= :endTimestamp AND listenedSeconds > 0
    """)
    suspend fun getTotalListeningSeconds(startTimestamp: Long, endTimestamp: Long): Long?

    /** Completed plays inside a period — the count the Replay hero shows. */
    @Query("""
        SELECT COUNT(*) FROM listening_events
        WHERE timestamp >= :startTimestamp AND timestamp <= :endTimestamp AND completed = 1
    """)
    suspend fun getCompletedPlayCount(startTimestamp: Long, endTimestamp: Long): Long

    /** How many different recordings were actually listened to in a period. */
    @Query("""
        SELECT COUNT(DISTINCT songId) FROM listening_events
        WHERE timestamp >= :startTimestamp AND timestamp <= :endTimestamp AND listenedSeconds > 0
    """)
    suspend fun getDistinctSongCount(startTimestamp: Long, endTimestamp: Long): Long

    /**
     * The hour of day (0–23, device local time) with the most listened
     * seconds in the period — computed in SQLite from the recorded
     * timestamps, never guessed (spec 81). Null when nothing was listened to.
     */
    @Query("""
        SELECT CAST(strftime('%H', timestamp / 1000, 'unixepoch', 'localtime') AS INTEGER) AS hour
        FROM listening_events
        WHERE timestamp >= :startTimestamp AND timestamp <= :endTimestamp AND listenedSeconds > 0
        GROUP BY hour
        ORDER BY SUM(listenedSeconds) DESC
        LIMIT 1
    """)
    suspend fun peakListeningHour(startTimestamp: Long, endTimestamp: Long): Int?
}
