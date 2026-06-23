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
}
