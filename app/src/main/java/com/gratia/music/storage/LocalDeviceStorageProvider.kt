package com.gratia.music.storage

import android.net.Uri

/**
 * Local Device storage provider — fully working.
 * Uses content:// URIs from SAF file picker.
 */
class LocalDeviceStorageProvider : StorageProvider {
    override val type = StorageProviderType.LOCAL
    override val displayName = "Local Device"

    override suspend fun connect(): Result<Unit> = Result.success(Unit)

    override suspend fun testConnection(): Result<Boolean> = Result.success(true)

    override suspend fun getStreamUri(fileId: String): Result<Uri> {
        return try {
            Result.success(Uri.parse(fileId))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun listMusicFiles(): Result<List<StoredMusicFile>> {
        // For local device, songs are added via file picker and stored in Room
        // This method isn't used for local device mode
        return Result.success(emptyList())
    }

    override suspend fun deleteMusic(fileId: String): Result<Unit> {
        // Local files are not deleted by Gratia, only removed from the library
        return Result.success(Unit)
    }
}
