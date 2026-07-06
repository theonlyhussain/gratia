package com.gratia.music.data.dao

import androidx.room.*
import com.gratia.music.data.model.CollectionEntity
import com.gratia.music.data.model.CollectionSongCrossRef
import com.gratia.music.data.model.SongEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CollectionDao {
    @Query("SELECT * FROM collections ORDER BY updatedAt DESC")
    fun getAllCollections(): Flow<List<CollectionEntity>>

    @Query("SELECT * FROM collections WHERE id = :collectionId")
    fun getCollection(collectionId: String): Flow<CollectionEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCollection(collection: CollectionEntity)

    @Delete
    suspend fun deleteCollection(collection: CollectionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addSongToCollection(crossRef: CollectionSongCrossRef)

    @Delete
    suspend fun removeSongFromCollection(crossRef: CollectionSongCrossRef)

    @Query("""
        SELECT songs.* FROM songs 
        INNER JOIN collection_songs ON songs.id = collection_songs.songId 
        WHERE collection_songs.collectionId = :collectionId 
        ORDER BY collection_songs.sortOrder ASC, collection_songs.addedAt ASC
    """)
    fun getSongsForCollection(collectionId: String): Flow<List<SongEntity>>
}
