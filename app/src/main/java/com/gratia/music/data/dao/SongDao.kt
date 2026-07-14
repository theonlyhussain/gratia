package com.gratia.music.data.dao

import androidx.room.*
import com.gratia.music.data.model.SongEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {

    @Query("SELECT * FROM songs ORDER BY createdAt DESC")
    fun getAllSongs(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs ORDER BY createdAt DESC")
    suspend fun getAllSongsOnce(): List<SongEntity>

    @Query("SELECT * FROM songs WHERE id = :id")
    suspend fun getSongById(id: String): SongEntity?

    @Query("SELECT * FROM songs WHERE id = :id")
    fun getSongByIdFlow(id: String): Flow<SongEntity?>

    @Query("SELECT * FROM songs WHERE isFavorite = 1 ORDER BY updatedAt DESC")
    fun getFavorites(): Flow<List<SongEntity>>

    @Query("SELECT COUNT(*) FROM songs WHERE isFavorite = 1")
    fun getFavoritesCount(): Flow<Int>

    @Query("SELECT * FROM songs ORDER BY lastPlayedAt DESC LIMIT :limit")
    fun getRecentlyPlayed(limit: Int = 20): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE playCount > 0 ORDER BY playCount DESC LIMIT :limit")
    fun getMostPlayed(limit: Int = 20): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs ORDER BY createdAt DESC LIMIT :limit")
    fun getLastAdded(limit: Int = 20): Flow<List<SongEntity>>

    @Query("SELECT DISTINCT artist FROM songs WHERE lastPlayedAt IS NOT NULL ORDER BY lastPlayedAt DESC LIMIT :limit")
    fun getRecentArtists(limit: Int = 10): Flow<List<String>>

    @Query("""
        SELECT * FROM songs WHERE 
        title LIKE '%' || :query || '%' OR 
        artist LIKE '%' || :query || '%' OR 
        album LIKE '%' || :query || '%' OR 
        mood LIKE '%' || :query || '%' OR 
        language LIKE '%' || :query || '%' OR 
        tags LIKE '%' || :query || '%' OR 
        aliases LIKE '%' || :query || '%' OR 
        id IN (SELECT songId FROM lyrics WHERE text LIKE '%' || :query || '%')
        ORDER BY 
            CASE WHEN title LIKE '%' || :query || '%' THEN 0
                 WHEN artist LIKE '%' || :query || '%' THEN 1
                 WHEN album LIKE '%' || :query || '%' THEN 2
                 WHEN id IN (SELECT songId FROM lyrics WHERE text LIKE '%' || :query || '%') THEN 3
                 ELSE 4
            END,
            playCount DESC
    """)
    fun search(query: String): Flow<List<SongEntity>>

    @Query("""
        SELECT * FROM songs WHERE 
        id IN (SELECT songId FROM lyrics WHERE text LIKE '%' || :query || '%')
    """)
    suspend fun searchByLyrics(query: String): List<SongEntity>

    /** Get songs that have no cover art and have a local URI for backfill */
    @Query("SELECT * FROM songs WHERE coverArtPath IS NULL AND localUri IS NOT NULL")
    suspend fun getSongsWithoutCover(): List<SongEntity>

    @Query("SELECT * FROM songs WHERE localUri = :localUri LIMIT 1")
    suspend fun getSongByLocalUri(localUri: String): SongEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSong(song: SongEntity)

    @Update
    suspend fun updateSong(song: SongEntity)

    @Delete
    suspend fun deleteSong(song: SongEntity)

    @Query("UPDATE songs SET isFavorite = :isFavorite, updatedAt = :now WHERE id = :id")
    suspend fun setFavorite(id: String, isFavorite: Boolean, now: Long = System.currentTimeMillis())

    @Query("UPDATE songs SET playCount = playCount + 1, lastPlayedAt = :now WHERE id = :id")
    suspend fun incrementPlayCount(id: String, now: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM songs")
    fun getSongCount(): Flow<Int>

    @Query("UPDATE songs SET coverArtPath = :path, coverSource = :source, updatedAt = :now WHERE id = :id")
    suspend fun updateCoverArt(id: String, path: String, source: String, now: Long = System.currentTimeMillis())
}
