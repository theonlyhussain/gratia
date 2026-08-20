package com.gratia.music.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity representing local lyrics storage.
 * Separated from SongEntity to allow independent updates, manual overrides,
 * and different providers (e.g. LRCLIB, manual edit).
 */
@Entity(tableName = "lyrics", primaryKeys = ["songId", "provider"])
data class LyricsEntity(
    val songId: String,
    val text: String,
    val isSynced: Boolean,
    val provider: String, // e.g. "LRCLIB", "manual"
    val offsetMs: Long = 0L,
    val isManuallyEdited: Boolean = false,
    val downloadDate: Long = System.currentTimeMillis(),
    val hash: String = "", // Optional hash for future validation
    val isWordLevel: Boolean = false,
    val isActiveOverride: Boolean = false
)
