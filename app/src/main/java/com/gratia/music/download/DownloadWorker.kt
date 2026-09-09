package com.gratia.music.download

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.gratia.music.GratiaApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import com.gratia.music.data.repository.LyricsRepository
import com.gratia.music.provider.ProviderManager


class DownloadWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "DownloadWorker"
        const val KEY_SONG_ID = "song_id"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val songId = inputData.getString(KEY_SONG_ID) ?: return@withContext Result.failure()
        Log.d(TAG, "Starting download for song: $songId")

        try {
            val songRepo = com.gratia.music.data.repository.SongRepository(GratiaApp.instance.database.songDao())
            val song = songRepo.getSongById(songId) ?: return@withContext Result.failure()

            if (song.isDownloaded && !song.downloadPath.isNullOrBlank()) {
                val existingFile = File(song.downloadPath)
                if (existingFile.exists()) {
                    Log.d(TAG, "Song already downloaded: $songId")
                    return@withContext Result.success()
                }
            }

            val settings = com.gratia.music.data.SettingsDataStore(context)
            val qualitySetting = settings.audioQualityFlow.first()
            val maxKbps = when (qualitySetting) {
                "LOW" -> 64
                "HIGH" -> 320
                else -> 128
            }

            // Resolve streaming URL for download
            val source = GratiaApp.instance.providerManager.resolveForDownload(song, maxKbps)
            if (source == null || source.streamUrl.isBlank()) {
                Log.e(TAG, "Failed to resolve stream for download: $songId")
                return@withContext Result.retry()
            }

            // Perform download using OkHttp
            val client = OkHttpClient.Builder().build()
            val request = Request.Builder().url(source.streamUrl).build()
            
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e(TAG, "Failed to download stream: HTTP ${response.code}")
                return@withContext Result.retry()
            }

            val body = response.body
            if (body == null) {
                Log.e(TAG, "Empty response body")
                return@withContext Result.failure()
            }

            // Determine extension from content type if possible
            val contentType = body.contentType()?.toString() ?: ""
            val ext = when {
                contentType.contains("mp4") || contentType.contains("m4a") -> "m4a"
                contentType.contains("webm") -> "webm"
                else -> "m4a" // Fallback
            }

            val downloadsDir = File(context.filesDir, "downloads")
            if (!downloadsDir.exists()) downloadsDir.mkdirs()

            val outputFile = File(downloadsDir, "${song.id}.$ext")
            
            body.byteStream().use { inputStream ->
                FileOutputStream(outputFile).use { outputStream ->
                    val buffer = ByteArray(8 * 1024)
                    var bytesRead: Int
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                    }
                    outputStream.flush()
                }
            }

            // Update database
            songRepo.updateDownloadState(songId, true, outputFile.absolutePath)
            
            // 2. Download Artwork
            if (!song.artworkUrl.isNullOrBlank() && song.coverArtPath.isNullOrBlank()) {
                try {
                    val artRequest = Request.Builder().url(song.artworkUrl).build()
                    val artResponse = client.newCall(artRequest).execute()
                    if (artResponse.isSuccessful) {
                        val artBody = artResponse.body
                        if (artBody != null) {
                            val artExt = if (artBody.contentType()?.toString()?.contains("png") == true) "png" else "jpg"
                            val artFile = File(downloadsDir, "${song.id}_cover.$artExt")
                            artBody.byteStream().use { inputStream ->
                                FileOutputStream(artFile).use { outputStream ->
                                    val buffer = ByteArray(8 * 1024)
                                    var bytesRead: Int
                                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                                        outputStream.write(buffer, 0, bytesRead)
                                    }
                                    outputStream.flush()
                                }
                            }
                            // Update song with local artwork path
                            val updatedSong = song.copy(
                                coverArtPath = artFile.absolutePath, 
                                isDownloaded = true, 
                                downloadPath = outputFile.absolutePath
                            )
                            songRepo.insertSong(updatedSong)
                            Log.d(TAG, "Artwork downloaded successfully: ${artFile.absolutePath}")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to download artwork for song $songId", e)
                }
            }
            
            // 3. Fetch Lyrics
            try {
                val lyricsRepo = LyricsRepository(GratiaApp.instance.database.lyricsDao())
                val lyrics = lyricsRepo.getLyrics(song, forceRefresh = false)
                if (lyrics != null) {
                    Log.d(TAG, "Lyrics downloaded/cached successfully for song $songId")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to pre-fetch lyrics for download: $songId", e)
            }
            
            Log.d(TAG, "Download completed successfully: ${outputFile.absolutePath}")
            Result.success()

        } catch (e: Exception) {
            Log.e(TAG, "Download failed for song $songId", e)
            Result.retry()
        }
    }
}
