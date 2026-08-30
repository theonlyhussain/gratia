package com.gratia.music.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class CacheManager(private val context: Context) {

    suspend fun getArtworkCacheSize(): Long = withContext(Dispatchers.IO) {
        getFolderSize(File(context.filesDir, "artwork_cache"))
    }

    suspend fun getCoilCacheSize(): Long = withContext(Dispatchers.IO) {
        getFolderSize(File(context.cacheDir, "image_cache"))
    }

    suspend fun clearArtworkCache() = withContext(Dispatchers.IO) {
        deleteFolder(File(context.filesDir, "artwork_cache"))
        // Delete database entries too?
        com.gratia.music.GratiaApp.instance.database.artworkDao().clearCache()
    }

    suspend fun clearCoilCache() = withContext(Dispatchers.IO) {
        deleteFolder(File(context.cacheDir, "image_cache"))
    }

    suspend fun clearAllCaches() {
        clearArtworkCache()
        clearCoilCache()
    }

    private fun getFolderSize(folder: File): Long {
        var size: Long = 0
        if (folder.exists() && folder.isDirectory) {
            folder.listFiles()?.forEach { file ->
                size += if (file.isDirectory) getFolderSize(file) else file.length()
            }
        }
        return size
    }

    private fun deleteFolder(folder: File) {
        if (folder.exists() && folder.isDirectory) {
            folder.listFiles()?.forEach { file ->
                if (file.isDirectory) deleteFolder(file) else file.delete()
            }
        }
    }
}
