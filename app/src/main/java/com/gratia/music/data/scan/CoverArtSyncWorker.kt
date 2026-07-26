package com.gratia.music.data.scan

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.gratia.music.data.CoverArtManager
import com.gratia.music.data.db.GratiaDatabase
import com.gratia.music.data.repository.SongRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class CoverArtSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.d("CoverArtSync", "Starting CoverArtSyncWorker")
            val db = GratiaDatabase.getInstance(applicationContext)
            val songRepo = SongRepository(db.songDao())

            val missingCoverSongs = songRepo.getSongsWithoutCover()
            if (missingCoverSongs.isEmpty()) {
                Log.d("CoverArtSync", "No songs missing cover art.")
                return@withContext Result.success()
            }

            var successCount = 0
            for (song in missingCoverSongs) {
                if (isStopped) break

                val coverUrl = CoverArtFetcher.searchDeezerArt(song.artist, song.title)
                if (coverUrl != null) {
                    val bitmap = downloadBitmap(coverUrl)
                    if (bitmap != null) {
                        val path = CoverArtManager.saveCoverToInternal(applicationContext, song.id, bitmap)
                        songRepo.updateCoverArt(song.id, path, "deezer")
                        successCount++
                    }
                }
                
                // Throttle to avoid rate limits
                delay(300)
            }

            Log.d("CoverArtSync", "Finished syncing cover art. Found $successCount new covers.")
            Result.success()
        } catch (e: Exception) {
            Log.e("CoverArtSync", "Error syncing covers", e)
            Result.failure()
        }
    }

    private fun downloadBitmap(urlString: String): android.graphics.Bitmap? {
        return try {
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.doInput = true
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.connect()
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val input = connection.inputStream
                BitmapFactory.decodeStream(input)
            } else null
        } catch (e: Exception) {
            Log.e("CoverArtSync", "Failed to download image", e)
            null
        }
    }
}
