package com.gratia.music.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.gratia.music.data.model.PlaybackHistoryEntity
import kotlinx.coroutines.flow.Flow

/**
 * The unified playback timeline.
 *
 * Written once per actual playback; read by Home's Recently Played shelf and
 * the Listening History screen. Deleting a row removes that play from both —
 * one timeline, one source of truth.
 */
@Dao
interface PlaybackHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: PlaybackHistoryEntity)

    /** The newest [limit] plays, newest first — the Home shelf. */
    @Query("SELECT * FROM playback_history ORDER BY playedAt DESC LIMIT :limit")
    fun recent(limit: Int = 20): Flow<List<PlaybackHistoryEntity>>

    /** The newest [limit] plays, one-shot for callers that must not observe. */
    @Query("SELECT * FROM playback_history ORDER BY playedAt DESC LIMIT :limit")
    suspend fun recentOnce(limit: Int = 20): List<PlaybackHistoryEntity>

    /** Everything recorded — the See-all screen's list. */
    @Query("SELECT * FROM playback_history ORDER BY playedAt DESC LIMIT :limit")
    fun all(limit: Int = 2000): Flow<List<PlaybackHistoryEntity>>

    @Query("SELECT COUNT(*) FROM playback_history")
    suspend fun count(): Int

    /** Keep a single session's listened time current as the player reports it. */
    @Query("UPDATE playback_history SET listenedSeconds = :seconds, completed = :completed WHERE id = :id")
    suspend fun updateListening(id: String, seconds: Long, completed: Boolean)

    /** Remove one play from the timeline. */
    @Query("DELETE FROM playback_history WHERE id = :id")
    suspend fun delete(id: String)

    /** Remove one recording's plays from the timeline (used when a download is removed). */
    @Query("DELETE FROM playback_history WHERE songId = :songId")
    suspend fun deleteForSong(songId: String)

    /** Clear the whole timeline — the "Clear all" action on the history screen. */
    @Query("DELETE FROM playback_history")
    suspend fun clear()
}
