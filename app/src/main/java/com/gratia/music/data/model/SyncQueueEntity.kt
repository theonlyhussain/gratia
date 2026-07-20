package com.gratia.music.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "sync_queue")
data class SyncQueueEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val songId: String,
    val status: String = "QUEUED", // "QUEUED", "PROCESSING", "FAILED", "COMPLETED"
    val retryCount: Int = 0,
    val queuedAt: Long = System.currentTimeMillis()
)
