package com.gratia.music.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.gratia.music.data.model.LyricsEntity

@Dao
interface LyricsDao {

    @Query("SELECT * FROM lyrics WHERE songId = :songId")
    suspend fun getLyricsForSong(songId: String): LyricsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLyrics(lyrics: LyricsEntity)

    @Update
    suspend fun updateLyrics(lyrics: LyricsEntity)

    @Query("DELETE FROM lyrics WHERE songId = :songId")
    suspend fun deleteLyricsForSong(songId: String)
}
