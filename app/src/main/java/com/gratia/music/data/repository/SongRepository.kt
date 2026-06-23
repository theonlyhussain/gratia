package com.gratia.music.data.repository

import com.gratia.music.data.dao.SongDao
import com.gratia.music.data.model.SongEntity
import kotlinx.coroutines.flow.Flow

class SongRepository(private val songDao: SongDao) {

    fun getAllSongs(): Flow<List<SongEntity>> = songDao.getAllSongs()

    suspend fun getAllSongsOnce(): List<SongEntity> = songDao.getAllSongsOnce()

    suspend fun getSongById(id: String): SongEntity? = songDao.getSongById(id)

    suspend fun getSongByLocalUri(localUri: String): SongEntity? = songDao.getSongByLocalUri(localUri)

    fun getSongByIdFlow(id: String): Flow<SongEntity?> = songDao.getSongByIdFlow(id)

    fun getFavorites(): Flow<List<SongEntity>> = songDao.getFavorites()

    fun getFavoritesCount(): Flow<Int> = songDao.getFavoritesCount()

    fun getRecentlyPlayed(limit: Int = 20): Flow<List<SongEntity>> =
        songDao.getRecentlyPlayed(limit)

    fun getMostPlayed(limit: Int = 20): Flow<List<SongEntity>> =
        songDao.getMostPlayed(limit)

    fun getLastAdded(limit: Int = 20): Flow<List<SongEntity>> =
        songDao.getLastAdded(limit)

    fun getRecentArtists(limit: Int = 10): Flow<List<String>> =
        songDao.getRecentArtists(limit)

    fun search(query: String): Flow<List<SongEntity>> = songDao.search(query)

    suspend fun searchByLyrics(query: String): List<SongEntity> = songDao.searchByLyrics(query)

    suspend fun getSongsWithoutCover(): List<SongEntity> = songDao.getSongsWithoutCover()

    suspend fun insertSong(song: SongEntity) = songDao.insertSong(song)

    suspend fun updateSong(song: SongEntity) = songDao.updateSong(song)

    suspend fun deleteSong(song: SongEntity) = songDao.deleteSong(song)

    suspend fun toggleFavorite(id: String, isFavorite: Boolean) =
        songDao.setFavorite(id, isFavorite)

    suspend fun incrementPlayCount(id: String) = songDao.incrementPlayCount(id)

    fun getSongCount(): Flow<Int> = songDao.getSongCount()

    suspend fun updateCoverArt(id: String, path: String, source: String) =
        songDao.updateCoverArt(id, path, source)
}
