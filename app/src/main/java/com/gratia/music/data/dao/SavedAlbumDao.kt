package com.gratia.music.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.gratia.music.data.model.SavedAlbumEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedAlbumDao {
    @Query("SELECT * FROM saved_albums ORDER BY savedAt DESC")
    fun getAllSavedAlbums(): Flow<List<SavedAlbumEntity>>

    @Query("SELECT * FROM saved_albums WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getSavedAlbum(remoteId: String): SavedAlbumEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM saved_albums WHERE remoteId = :remoteId)")
    fun isSavedFlow(remoteId: String): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM saved_albums WHERE remoteId = :remoteId)")
    suspend fun isSaved(remoteId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSavedAlbum(album: SavedAlbumEntity)

    @Query("DELETE FROM saved_albums WHERE remoteId = :remoteId")
    suspend fun deleteSavedAlbum(remoteId: String)
}
