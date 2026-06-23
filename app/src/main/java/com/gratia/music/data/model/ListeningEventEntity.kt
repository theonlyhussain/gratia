package com.gratia.music.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "listening_events")
data class ListeningEventEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val songId: String,
    val eventType: String, // "play", "pause", "complete", "skip", "lyrics_opened", "song_added"
    val timestamp: Long = System.currentTimeMillis(),
    val listenedSeconds: Long = 0L,
    val source: String = "local",
    val completed: Boolean = false,
    val skipped: Boolean = false
)
