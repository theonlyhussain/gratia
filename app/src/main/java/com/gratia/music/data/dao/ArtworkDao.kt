package com.gratia.music.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.gratia.music.data.model.ArtworkEntity

@Dao
interface ArtworkDao {
    @Query("SELECT * FROM artwork_cache WHERE id = :id")
    suspend fun getArtworkById(id: String): ArtworkEntity?
    
    @Query("SELECT * FROM artwork_cache WHERE url = :url LIMIT 1")
    suspend fun getArtworkByUrl(url: String): ArtworkEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArtwork(artwork: ArtworkEntity)

    @Update
    suspend fun updateArtwork(artwork: ArtworkEntity)
}
