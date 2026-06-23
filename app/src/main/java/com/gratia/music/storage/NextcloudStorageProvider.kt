package com.gratia.music.storage

import android.net.Uri

/**
 * Nextcloud / WebDAV storage provider — scaffolded, not yet functional.
 * Shows honest "not configured" message.
 */
class NextcloudStorageProvider : StorageProvider {
    override val type = StorageProviderType.NEXTCLOUD
    override val displayName = "Nextcloud / WebDAV"

    override suspend fun connect(): Result<Unit> =
        Result.failure(NotConfiguredException("Nextcloud connection is not configured yet."))

    override suspend fun testConnection(): Result<Boolean> =
        Result.failure(NotConfiguredException("Nextcloud connection is not configured yet."))

    override suspend fun getStreamUri(fileId: String): Result<Uri> =
        Result.failure(NotConfiguredException("Nextcloud streaming is not configured yet."))

    override suspend fun listMusicFiles(): Result<List<StoredMusicFile>> =
        Result.failure(NotConfiguredException("Nextcloud is not configured yet."))

    override suspend fun deleteMusic(fileId: String): Result<Unit> =
        Result.failure(NotConfiguredException("Nextcloud is not configured yet."))
}
