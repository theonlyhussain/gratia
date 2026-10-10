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
    val provider: String, // slot: "automatic" (provider-chosen) or "manual"
    /**
     * The provider that actually produced the chosen result — "BetterLyrics",
     * "BiniLyrics", "LRCLIB", … Kept apart from [provider] because the slot
     * answers "is this the auto pick or the user's edit?" while this answers
     * "who gets the credit?", and the credit used to be lost when the result
     * was written into the fixed "automatic" slot.
     */
    val providerName: String? = null,
    val offsetMs: Long = 0L,
    val isManuallyEdited: Boolean = false,
    val downloadDate: Long = System.currentTimeMillis(),
    val hash: String = "", // Optional hash for future validation
    val isWordLevel: Boolean = false,
    val isActiveOverride: Boolean = false
)
