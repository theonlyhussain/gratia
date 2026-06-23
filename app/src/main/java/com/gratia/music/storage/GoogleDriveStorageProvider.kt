package com.gratia.music.storage

import android.net.Uri

/**
 * Google Drive storage provider — scaffolded, not yet functional.
 * Shows honest "not configured" message.
 */
class GoogleDriveStorageProvider : StorageProvider {
    override val type = StorageProviderType.GOOGLE_DRIVE
    override val displayName = "Google Drive"

    override suspend fun connect(): Result<Unit> =
        Result.failure(NotConfiguredException("Google Drive connection is not configured yet."))

    override suspend fun testConnection(): Result<Boolean> =
        Result.failure(NotConfiguredException("Google Drive connection is not configured yet."))

    override suspend fun getStreamUri(fileId: String): Result<Uri> =
        Result.failure(NotConfiguredException("Google Drive streaming is not configured yet."))

    override suspend fun listMusicFiles(): Result<List<StoredMusicFile>> =
        Result.failure(NotConfiguredException("Google Drive is not configured yet."))

    override suspend fun deleteMusic(fileId: String): Result<Unit> =
        Result.failure(NotConfiguredException("Google Drive is not configured yet."))
}

class NotConfiguredException(message: String) : Exception(message)
