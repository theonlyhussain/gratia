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
    val albumArtistId: String? = null,
    val trackNumber: Int? = null,
    val discNumber: Int? = null,
    val genre: String? = null,
    val releaseDate: String? = null,
    val isrc: String? = null,
    val bpm: Float? = null,
    val composer: String? = null,
    val explicit: Boolean = false,
    val popularity: Int = 0,
    val label: String? = null,
    val copyright: String? = null,
    
    // Legacy / Extra Metadata
    val mood: String? = null,
    val language: String? = null,
    val tags: String? = null,
    val aliases: String? = null,
    
    // Metadata Sync Tracking
    val lastMetadataSync: Long = 0L,
    val metadataSource: String = "local", // local, deezer, lrclib, etc
    
    // Storage Details
    val durationMs: Long = 0L,
    val storageProvider: String = "local",
    val storageAccountId: String? = null,
    val storageFileId: String? = null,
    val storagePath: String? = null,
    val localUri: String? = null,
    val mimeType: String? = null,
    val fileName: String? = null,
    
    // Audio quality metadata — added in v2
    val format: String? = null,
    val bitrate: Int? = null,
    val sampleRate: Int? = null,
    val fileSizeBytes: Long? = null,

    // Legacy fields preserved for backward compatibility until UI rewrite
    val coverArtPath: String? = null,
    val coverSource: String? = null, 
    
    // Stats
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false,
    val playCount: Int = 0,
    val skipCount: Int = 0,
    val completedCount: Int = 0,
    val totalListenTime: Long = 0L,
    val lastPlayedAt: Long? = null
)
