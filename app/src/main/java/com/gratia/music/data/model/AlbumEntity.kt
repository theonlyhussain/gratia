package com.gratia.music.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "albums")
data class AlbumEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val artistId: String? = null,
    val releaseDate: String? = null,
    val coverUrl: String? = null,
    val localCoverPath: String? = null,
    val coverHash: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
