package com.gratia.music.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity representing local lyrics storage.
 * Separated from SongEntity to allow independent updates, manual overrides,
 * and different providers (e.g. LRCLIB, manual edit).
 */
@Entity(tableName = "lyrics")
data class LyricsEntity(
    @PrimaryKey val songId: String,
    val text: String,
    val isSynced: Boolean,
    val provider: String, // e.g. "LRCLIB", "manual"
    val offsetMs: Long = 0L,
    val isManuallyEdited: Boolean = false,
    val downloadDate: Long = System.currentTimeMillis()
)
