package com.gratia.music.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "artists")
data class ArtistEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val pictureUrl: String? = null,
    val localPicturePath: String? = null,
    val pictureHash: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
