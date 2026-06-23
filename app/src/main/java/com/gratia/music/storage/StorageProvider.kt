package com.gratia.music.storage

import android.net.Uri

/**
 * Gratia Storage Provider Interface.
 * All storage backends implement this interface.
 */
interface StorageProvider {
    val type: StorageProviderType
    val displayName: String

    suspend fun connect(): Result<Unit>
    suspend fun testConnection(): Result<Boolean>
    suspend fun getStreamUri(fileId: String): Result<Uri>
    suspend fun listMusicFiles(): Result<List<StoredMusicFile>>
    suspend fun deleteMusic(fileId: String): Result<Unit>
}

enum class StorageProviderType(val displayName: String) {
    LOCAL("Local Device"),
    GOOGLE_DRIVE("Google Drive"),
    NEXTCLOUD("Nextcloud / WebDAV")
}

data class StoredMusicFile(
    val fileId: String,
    val fileName: String,
    val mimeType: String?,
    val sizeBytes: Long?,
    val path: String?,
)

data class StorageAccount(
    val id: String,
    val providerType: StorageProviderType,
    val displayName: String,
    val isConnected: Boolean = false,
    val lastSyncAt: Long? = null,
)
