package com.gratia.music.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "collections")
data class CollectionEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String? = null,
    val coverArtUri: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "collection_songs",
    primaryKeys = ["collectionId", "songId"]
)
data class CollectionSongCrossRef(
    val collectionId: String,
    val songId: String,
    val addedAt: Long = System.currentTimeMillis(),
    val sortOrder: Int = 0,
)
