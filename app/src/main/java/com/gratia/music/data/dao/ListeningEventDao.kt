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

    @Query("""
        SELECT s.artist, SUM(e.listenedSeconds) as totalSeconds
        FROM listening_events e
        INNER JOIN songs s ON e.songId = s.id
        WHERE e.timestamp >= :startTimestamp AND e.timestamp <= :endTimestamp AND (e.eventType = 'play' OR e.eventType = 'complete')
        GROUP BY s.artist
        ORDER BY totalSeconds DESC
        LIMIT :limit
    """)
    suspend fun getTopArtists(startTimestamp: Long, endTimestamp: Long, limit: Int = 5): List<com.gratia.music.data.model.ArtistListenSummary>

    @Query("""
        SELECT s.id as songId, s.title, s.artist, SUM(e.listenedSeconds) as totalSeconds
        FROM listening_events e
        INNER JOIN songs s ON e.songId = s.id
        WHERE e.timestamp >= :startTimestamp AND e.timestamp <= :endTimestamp AND (e.eventType = 'play' OR e.eventType = 'complete')
        GROUP BY s.id
        ORDER BY totalSeconds DESC
        LIMIT :limit
    """)
    suspend fun getTopTracks(startTimestamp: Long, endTimestamp: Long, limit: Int = 5): List<com.gratia.music.data.model.TrackListenSummary>

    @Query("""
        SELECT SUM(listenedSeconds)
        FROM listening_events
        WHERE timestamp >= :startTimestamp AND timestamp <= :endTimestamp AND (eventType = 'play' OR eventType = 'complete')
    """)
    suspend fun getTotalListeningSeconds(startTimestamp: Long, endTimestamp: Long): Long?
}
