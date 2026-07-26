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

/**
 * Background worker that syncs missing cover art and genre data from Deezer.
 * Runs after every media scan to fill in gaps.
 */
class CoverArtSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.d("CoverArtSync", "Starting CoverArtSyncWorker")
            val db = GratiaDatabase.getInstance(applicationContext)
            val songRepo = SongRepository(db.songDao())

            // Fetch all songs to check for missing cover art AND genre
            val allSongs = songRepo.getAllSongsOnce()
            val songsNeedingWork = allSongs.filter { song ->
                song.coverArtPath.isNullOrBlank() || song.genre.isNullOrBlank()
            }

            if (songsNeedingWork.isEmpty()) {
                Log.d("CoverArtSync", "No songs need cover art or genre sync.")
                return@withContext Result.success()
            }

            var coverCount = 0
            var genreCount = 0

            for (song in songsNeedingWork) {
                if (isStopped) break

                val result = CoverArtFetcher.searchDeezer(song.artist, song.title)

                if (result != null) {
                    // Save cover art if missing
                    if (song.coverArtPath.isNullOrBlank() && result.coverUrl != null) {
                        val bitmap = downloadBitmap(result.coverUrl)
                        if (bitmap != null) {
                            val path = CoverArtManager.saveCoverToInternal(applicationContext, song.id, bitmap)
                            songRepo.updateCoverArt(song.id, path, "deezer")
                            coverCount++
                        }
                    }

                    // Save genre if missing
                    if (song.genre.isNullOrBlank() && !result.genre.isNullOrBlank()) {
                        songRepo.updateGenre(song.id, result.genre)
                        genreCount++
                    }
                }

                // Throttle to avoid rate limits
                delay(400)
            }

            Log.d("CoverArtSync", "Finished syncing. Covers: $coverCount, Genres: $genreCount")
            Result.success()
        } catch (e: Exception) {
            Log.e("CoverArtSync", "Error syncing", e)
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
