package com.gratia.music.data.metadata

import android.content.Context
import android.util.Log
import com.gratia.music.data.dao.ArtworkDao
import com.gratia.music.data.model.ArtworkEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.security.MessageDigest
import java.util.UUID

class ArtworkManager(
    private val context: Context,
    private val artworkDao: ArtworkDao
) {
    private val TAG = "ArtworkManager"

    /**
     * Retrieves local artwork if cached, otherwise downloads, caches, and returns the local path.
     */
    suspend fun getOrDownloadArtwork(url: String): String? = withContext(Dispatchers.IO) {
        if (url.isBlank()) return@withContext null

        val existing = artworkDao.getArtworkByUrl(url)
        if (existing != null && File(existing.localPath).exists()) {
            return@withContext existing.localPath
        }

        // Download
        val localPath = downloadImage(url) ?: return@withContext null
        val hash = calculateHash(localPath)

        val entity = ArtworkEntity(
            url = url,
            localPath = localPath,
            hash = hash
        )
        artworkDao.insertArtwork(entity)
        return@withContext localPath
    }

    private fun downloadImage(urlString: String): String? {
        try {
            val url = URL(urlString)
            val connection = url.openConnection()
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            
            val inputStream = connection.getInputStream()
            val fileName = UUID.randomUUID().toString() + ".jpg"
            val file = File(context.cacheDir, fileName)
            
            val outputStream = FileOutputStream(file)
            inputStream.copyTo(outputStream)
            outputStream.close()
            inputStream.close()
            
            return file.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download artwork: $urlString", e)
            return null
        }
    }

    private fun calculateHash(filePath: String): String {
        val file = File(filePath)
        if (!file.exists()) return ""
        val bytes = file.readBytes()
        val md = MessageDigest.getInstance("MD5")
        val hashBytes = md.digest(bytes)
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}
