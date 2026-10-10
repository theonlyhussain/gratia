package com.gratia.music.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.gratia.music.data.model.SavedArtistEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedArtistDao {
    @Query("SELECT * FROM saved_artists ORDER BY savedAt DESC")
    fun getAllSavedArtists(): Flow<List<SavedArtistEntity>>

    @Query("SELECT * FROM saved_artists WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getSavedArtist(remoteId: String): SavedArtistEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM saved_artists WHERE remoteId = :remoteId)")
    fun isSavedFlow(remoteId: String): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM saved_artists WHERE remoteId = :remoteId)")
    suspend fun isSaved(remoteId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSavedArtist(artist: SavedArtistEntity)

    @Query("DELETE FROM saved_artists WHERE remoteId = :remoteId")
    suspend fun deleteSavedArtist(remoteId: String)
}
