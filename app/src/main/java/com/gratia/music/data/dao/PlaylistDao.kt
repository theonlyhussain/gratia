package com.gratia.music.data.dao

import androidx.room.*
import com.gratia.music.data.model.PlaylistEntity
import com.gratia.music.data.model.PlaylistSongCrossRef
import com.gratia.music.data.model.SongEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY updatedAt DESC")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT COUNT(*) FROM playlists")
    fun getPlaylistCount(): Flow<Int>

    @Query("SELECT * FROM playlists WHERE id = :playlistId")
    fun getPlaylist(playlistId: String): Flow<PlaylistEntity?>

    /** One-shot existence check used before adding a remote track to a playlist. */
    @Query("SELECT * FROM songs WHERE id = :songId LIMIT 1")
    suspend fun getSongByIdOnce(songId: String): SongEntity?

    /**
     * Ensures a song row exists before a playlist cross-ref is written.
     * Remote tracks have no row until something persists them; without this,
     * the cross-ref insert is a silent no-op and "Add to playlist" does
     * nothing for streamed songs.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPlaylistSong(song: SongEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity)

    @Delete
    suspend fun deletePlaylist(playlist: PlaylistEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addSongToPlaylist(crossRef: PlaylistSongCrossRef)

    @Delete
    suspend fun removeSongFromPlaylist(crossRef: PlaylistSongCrossRef)

    @Query("""
        SELECT songs.* FROM songs 
        INNER JOIN playlist_songs ON songs.id = playlist_songs.songId 
        WHERE playlist_songs.playlistId = :playlistId 
        ORDER BY playlist_songs.sortOrder ASC, playlist_songs.addedAt ASC
    """)
    fun getSongsForPlaylist(playlistId: String): Flow<List<SongEntity>>
}
