package com.gratia.music.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "artwork_cache")
data class ArtworkEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val url: String,
    val localPath: String,
    val hash: String? = null,
    val lastUpdated: Long = System.currentTimeMillis()
)
