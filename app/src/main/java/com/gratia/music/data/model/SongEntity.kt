package com.gratia.music.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Core song entity stored in Room.
 * Supports local device storage and future cloud providers.
 */
@Entity(tableName = "songs")
data class SongEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val artist: String,
    val album: String? = null,
    val mood: String? = null,
    val language: String? = null,
    val tags: String? = null,
    val aliases: String? = null,
    val lyrics: String? = null,
    val durationMs: Long = 0L,
    val coverArtUri: String? = null,
    val storageProvider: String = "local",
    val storageAccountId: String? = null,
    val storageFileId: String? = null,
    val storagePath: String? = null,
    val localUri: String? = null,
    val mimeType: String? = null,
    val fileName: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false,
    val playCount: Int = 0,
    val skipCount: Int = 0,
    val completedCount: Int = 0,
    val totalListenTime: Long = 0L,
    val lastPlayedAt: Long? = null,

    // Cover art — added in v2
    val coverArtPath: String? = null,
    val coverSource: String? = null, // "embedded", "user_selected", "generated"

    // Lyrics modes — added in v2
    val lyricsPlain: String? = null,
    val lyricsSynced: String? = null,
    val lyricsMode: String = "plain", // "plain" or "synced"

    // Audio quality metadata — added in v2
    val format: String? = null,
    val bitrate: Int? = null,
    val sampleRate: Int? = null,
    val fileSizeBytes: Long? = null,
)
