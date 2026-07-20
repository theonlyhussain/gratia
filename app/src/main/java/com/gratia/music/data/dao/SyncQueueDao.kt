package com.gratia.music.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.gratia.music.data.model.SyncQueueEntity

@Dao
interface SyncQueueDao {
    @Query("SELECT * FROM sync_queue WHERE status = 'QUEUED' ORDER BY queuedAt ASC")
    suspend fun getPendingSyncs(): List<SyncQueueEntity>

    @Query("SELECT * FROM sync_queue WHERE songId = :songId LIMIT 1")
    suspend fun getSyncBySongId(songId: String): SyncQueueEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSync(sync: SyncQueueEntity)

    @Update
    suspend fun updateSync(sync: SyncQueueEntity)
    
    @Query("DELETE FROM sync_queue WHERE songId = :songId")
    suspend fun deleteSyncForSong(songId: String)
}
