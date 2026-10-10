package com.gratia.music.download

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.gratia.music.GratiaApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import com.gratia.music.data.repository.LyricsRepository
import com.gratia.music.data.repository.SongRepository

/**
 * Downloads one remote recording to app storage.
 *
 * The invariants this worker owns:
 *
 *  - **Temp file first.** Bytes land in `<downloads>/<key>.part` and are
 *    promoted to their final name only when the response has been drained in
 *    full. A killed process, a dropped connection or a failed probe therefore
 *    cannot leave something on disk that looks finished but is not — the
 *    partial is deleted on the way out, and a half-written file can never be
 *    mistaken for a song.
 *
 *  - **Real progress or no progress.** The percentage is derived from the
 *    response's content length when it has one, and reported as null when it
 *    does not. An invented percentage is a lie the downloads row would repeat
 *    every frame.
 *
 *  - **The download path, not the playing stream.** The rendition is resolved
 *    through [com.gratia.music.provider.ProviderManager.resolveForDownload] —
 *    the same explicit download resolution the player's temporary stream URL
 *    must never be trusted for.
 *
 *  - **Identity preserved.** The song row keeps its remote ids, artwork and
 *    source; the download only adds `isDownloaded`, `downloadPath`, format and
 *    file size. A downloaded track stays a remote recording that happens to
 *    have local bytes — never re-registered as local music.
 */
class DownloadWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "DownloadWorker"
        const val KEY_DOWNLOAD_KEY = "download_key"

        /**
         * Deletes the stored file for [songId] and clears its download state.
         * Runs on IO; safe to call from the UI layer via [DownloadManager].
         */
        suspend fun scheduleRemoval(context: Context, songId: String) {
            withContext(Dispatchers.IO) {
                val repo = SongRepository(GratiaApp.instance.database.songDao())
                val song = repo.getSongById(songId) ?: return@withContext
                song.downloadPath?.let { path ->
                    try {
                        File(path).delete()
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not delete ${path}: ${e.message}")
                    }
                }
                val artwork = song.coverArtPath
                if (song.storageProvider != "local" && artwork?.startsWith(context.filesDir.path) == true) {
                    try {
                        File(artwork).delete()
                    } catch (e: Exception) { /* artwork cleanup is best-effort */ }
                }
                repo.updateDownloadState(songId, false, null)
            }
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val key = inputData.getString(KEY_DOWNLOAD_KEY) ?: run {
            Log.e(TAG, "No download key in input data")
            return@withContext Result.failure()
        }
        val holder = GratiaApp.instance.downloadStateHolder
        val notifier = GratiaApp.instance.downloadManager

        try {
            val songRepo = SongRepository(GratiaApp.instance.database.songDao())
            val song = findSong(songRepo, key) ?: run {
                Log.e(TAG, "No song for download key $key")
                holder.setFailed(key, "Track not found")
                return@withContext Result.failure()
            }

            Log.d(TAG, "DOWNLOAD_REQUEST key=$key title='${song.title}' artist='${song.artist}'")

            val existing = song.downloadPath?.let(::File)
            if (song.isDownloaded && existing != null && existing.exists() && existing.length() > 0) {
                Log.d(TAG, "Already downloaded: $key")
                holder.setDownloaded(key)
                return@withContext Result.success()
            }

            // Resolution and transfer are different states with different
            // failures; the menu shows "Preparing…" for this one (spec 4/5).
            holder.setResolving(key)

            val settings = com.gratia.music.data.SettingsDataStore(context)
            val qualitySetting = settings.audioQualityFlow.first()
            val maxKbps = when (qualitySetting.uppercase()) {
                "LOW" -> 96
                "HIGH" -> 320
                else -> 160
            }
            Log.d(TAG, "STREAM_RESOLUTION_STARTED key=$key quality=${qualitySetting.uppercase()} ceiling=${maxKbps}kbps")

            // Explicit download resolution — the playing stream URL is not a
            // download source; see the class doc.
            val source = GratiaApp.instance.providerManager.resolveForDownload(song, maxKbps)
            if (source == null || source.streamUrl.isBlank()) {
                Log.e(TAG, "STREAM_RESOLUTION_FAILED key=$key reason=resolve_null")
                holder.setFailed(key, "Couldn't prepare the download")
                notifier.notifyFailed(key, song.title, song.artist, "Couldn't prepare the download")
                return@withContext Result.retry()
            }
            Log.d(
                TAG,
                "STREAM_RESOLUTION_SUCCESS key=$key mime=${source.mimeType} " +
                    "container=${source.format} bitrate=${source.bitrate ?: "?"}kbps",
            )

            // Validate the URL names audio before a byte is written (spec 6).
            // A resolver that answered with a manifest, an HTML page or a JSON
            // endpoint would otherwise be drained into a file that can never
            // play.
            val probe = OkHttpClient().newCall(
                Request.Builder().url(source.streamUrl).head().build()
            ).execute().use { head ->
                if (!head.isSuccessful) {
                    Log.e(TAG, "STREAM_PROBE_FAILED key=$key http=${head.code}")
                    null
                } else {
                    val mime = head.header("Content-Type")?.substringBefore(';')?.trim()
                    val length = head.header("Content-Length")?.toLongOrNull()
                    Log.d(TAG, "STREAM_PROBE key=$key mime=$mime length=$length")
                    // A media URL serves audio; HTML, XML manifests and JSON
                    // do not. Unknown MIME types still pass — some CDNs omit
                    // the header — but a known-wrong one refuses now rather
                    // than after 4MB of markup is on disk.
                    if (mime != null && !mime.startsWith("audio/") && !mime.startsWith("video/") && mime != "application/octet-stream") {
                        Log.e(TAG, "STREAM_PROBE_FAILED key=$key reason=not_media mime=$mime")
                        null
                    } else {
                        length
                    }
                }
            }
            if (probe == null) {
                holder.setFailed(key, "The audio source refused the download")
                notifier.notifyFailed(key, song.title, song.artist, "The audio source refused the download")
                return@withContext Result.retry()
            }

            val downloadsDir = File(context.filesDir, "downloads").apply { mkdirs() }
            val finalFile = File(downloadsDir, "$key.${extFor(source)}")
            val tempFile = File(downloadsDir, "$key.part")
            Log.d(TAG, "DOWNLOAD_FILE_CREATED key=$key path=${finalFile.absolutePath}")
            // A leftover partial from a previous killed run has no claim on
            // this one; it starts over.
            tempFile.delete()

            val client = OkHttpClient()
            val request = Request.Builder().url(source.streamUrl).build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "DOWNLOAD_HTTP_ERROR key=$key code=${response.code}")
                    holder.setFailed(key, "Server returned ${response.code}")
                    notifier.notifyFailed(key, song.title, song.artist, "Server returned ${response.code}")
                    return@withContext Result.retry()
                }
                val body = response.body
                    ?: run {
                        holder.setFailed(key, "Empty response")
                        return@withContext Result.failure()
                    }

                val contentLength = body.contentLength()
                var downloadedBytes = 0L
                var lastNotifiedPercent = -1

                body.byteStream().use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            downloadedBytes += read
                            val progress: Float? = if (contentLength > 0) downloadedBytes.toFloat() / contentLength else null
                            holder.setDownloading(key, progress)
                            // One notification update per whole percent, not
                            // per 64kB chunk — the notification is truthful
                            // without being a notification flood.
                            if (progress != null) {
                                val percent = (progress * 100).toInt()
                                if (percent != lastNotifiedPercent) {
                                    lastNotifiedPercent = percent
                                    notifier.notifyProgress(key, song.title, song.artist, progress)
                                    Log.d(TAG, "DOWNLOAD_PROGRESS key=$key percent=$percent")
                                }
                            }
                        }
                        output.flush()
                    }
                }

                // Drain completed: promote. Anything short of here deletes the
                // partial on the way out.
                if (!tempFile.renameTo(finalFile)) {
                    // Cross-device or transient FS stubbornness: copy over.
                    tempFile.copyTo(finalFile, overwrite = true)
                    tempFile.delete()
                }
            }

            // No fake success (spec 8): a connection that died mid-transfer
            // still "completes" the loop above — the loop just ends early. When
            // the response declared a length, the file must match it; a short
            // file is a broken song, not a downloaded one.
            if (probe > 0 && finalFile.length() != probe) {
                Log.e(
                    TAG,
                    "DOWNLOAD_TRUNCATED key=$key expected=$probe actual=${finalFile.length()}",
                )
                finalFile.delete()
                holder.setFailed(key, "The download was interrupted")
                notifier.notifyFailed(key, song.title, song.artist, "The download was interrupted")
                return@withContext Result.retry()
            }
            if (finalFile.length() == 0L) {
                Log.e(TAG, "DOWNLOAD_EMPTY key=$key")
                finalFile.delete()
                holder.setFailed(key, "The download was empty")
                notifier.notifyFailed(key, song.title, song.artist, "The download was empty")
                return@withContext Result.retry()
            }

            val downloadedSong = song.copy(
                isDownloaded = true,
                downloadPath = finalFile.absolutePath,
                fileSizeBytes = finalFile.length(),
                format = source.format ?: source.mimeType?.substringAfter("audio/")?.substringBefore(';'),
                bitrate = source.bitrate,
                updatedAt = System.currentTimeMillis(),
            )
            songRepo.insertSong(downloadedSong)

            // Keep the artwork for offline use: the library must not lose the
            // cover the moment the network is gone.
            if (!song.artworkUrl.isNullOrBlank()) {
                try {
                    val artResponse = client.newCall(Request.Builder().url(song.artworkUrl).build()).execute()
                    artResponse.use { art ->
                        if (art.isSuccessful) {
                            val artBody = art.body ?: return@use
                            val artExt = if (artBody.contentType()?.toString()?.contains("png") == true) "png" else "jpg"
                            val artFile = File(downloadsDir, "${key}_cover.$artExt")
                            artBody.byteStream().use { input ->
                                FileOutputStream(artFile).use { output ->
                                    input.copyTo(output)
                                }
                            }
                            songRepo.updateCoverArt(song.id, artFile.absolutePath, "downloaded_artwork")
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Artwork download failed for $key: ${e.message}")
                }
            }

            // Pre-cache lyrics so offline playback keeps its words.
            try {
                LyricsRepository(GratiaApp.instance.database.lyricsDao())
                    .getLyrics(downloadedSong, forceRefresh = false)
            } catch (e: Exception) {
                Log.w(TAG, "Lyrics prefetch failed for $key: ${e.message}")
            }

            holder.setDownloaded(key)
            notifier.notifyComplete(key, song.title, song.artist)
            Log.d(TAG, "DOWNLOAD_COMPLETE key=$key path=${finalFile.absolutePath}")
            Result.success()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "DOWNLOAD_FAIL key=$key", e)
            holder.setFailed(key, e.message ?: "Download failed")
            // The old call passed the KEY as the notification title — a failure
            // notification reading "dQw4w9WgXcQ — Download failed". The song
            // row is in scope here; use its name.
            val songTitle = runCatching {
                SongRepository(GratiaApp.instance.database.songDao())
                    .getSongById("ytm_$key")?.title ?: key
            }.getOrDefault(key)
            notifier.notifyFailed(key, songTitle, "", e.message ?: "Download failed")
            cleanupPartial(key, context)
            Result.retry()
        }
    }

    private fun cleanupPartial(key: String, context: Context) {
        try {
            File(File(context.filesDir, "downloads"), "$key.part").delete()
        } catch (e: Exception) { /* nothing else to do */ }
    }

    private suspend fun findSong(repo: SongRepository, key: String) =
        repo.getSongById("ytm_$key") ?: repo.getAllSongsOnce().firstOrNull {
            it.providerTrackId == key || it.downloadKey() == key
        }

    private fun extFor(source: com.gratia.music.provider.PlaybackSource): String {
        val mime = source.mimeType ?: source.format ?: ""
        return when {
            mime.contains("mp4") || mime.contains("m4a") || mime.contains("aac") -> "m4a"
            mime.contains("flac") -> "flac"
            mime.contains("mpeg") || mime.contains("mp3") -> "mp3"
            else -> "m4a"
        }
    }
}
