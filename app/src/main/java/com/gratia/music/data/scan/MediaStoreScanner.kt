package com.gratia.music.data.scan

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import com.gratia.music.data.CoverArtManager
import com.gratia.music.data.model.SongEntity
import com.gratia.music.data.repository.SongRepository
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object MediaStoreScanner {

    private const val TAG = "MediaStoreScanner"

    suspend fun scanLocalMusic(context: Context, songRepository: SongRepository): Int = withContext(Dispatchers.IO) {
        var importedCount = 0
        val contentResolver = context.contentResolver

        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_MODIFIED
        )

        // Filter: is_music = 1 and duration >= 15 seconds (filtering short files similar to Retro/general player preferences)
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= 15000"

        val cursor = try {
            contentResolver.query(
                uri,
                projection,
                selection,
                null,
                "${MediaStore.Audio.Media.DATE_MODIFIED} DESC"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to query MediaStore", e)
            null
        }

        cursor?.use { cur ->
            val idCol = cur.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cur.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cur.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cur.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationCol = cur.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dataCol = cur.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val mimeCol = cur.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
            val sizeCol = cur.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val modifiedCol = cur.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)

            while (cur.moveToNext()) {
                val mediaId = cur.getLong(idCol)
                val filePath = cur.getString(dataCol) ?: continue

                // Check if file physically exists
                val file = File(filePath)
                if (!file.exists()) continue

                // Stably map file path to UUID
                val songId = UUID.nameUUIDFromBytes(filePath.toByteArray()).toString()
                
                // Construct standard media content URI
                val songLocalUri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    mediaId
                ).toString()

                // Check if song already exists in repository
                val existing = songRepository.getSongById(songId)
                if (existing != null) {
                    // Update dynamic fields like localUri/duration/size if changed, but retain custom user edits like favorites, lyrics
                    if (existing.localUri != songLocalUri || existing.durationMs != cur.getLong(durationCol)) {
                        val updated = existing.copy(
                            localUri = songLocalUri,
                            durationMs = cur.getLong(durationCol),
                            fileSizeBytes = cur.getLong(sizeCol),
                            updatedAt = System.currentTimeMillis()
                        )
                        songRepository.updateSong(updated)
                    }
                    continue
                }

                val title = cur.getString(titleCol) ?: "Unknown Title"
                val artist = cur.getString(artistCol) ?: "Unknown Artist"
                val album = cur.getString(albumCol)
                val duration = cur.getLong(durationCol)
                val mimeType = cur.getString(mimeCol)
                val size = cur.getLong(sizeCol)
                val modifiedTime = cur.getLong(modifiedCol) * 1000L

                // Extract cover art if present
                var coverArtPath: String? = null
                var coverSource: String? = null
                try {
                    val embedded = CoverArtManager.extractEmbeddedCover(context, Uri.parse(songLocalUri))
                    if (embedded != null) {
                        coverArtPath = CoverArtManager.saveCoverToInternal(context, songId, embedded)
                        coverSource = "embedded"
                        embedded.recycle()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to extract cover art for $title", e)
                }

                val newSong = SongEntity(
                    id = songId,
                    title = title,
                    artist = artist,
                    album = album,
                    durationMs = duration,
                    localUri = songLocalUri,
                    storagePath = filePath,
                    mimeType = mimeType,
                    fileSizeBytes = size,
                    fileName = file.name,
                    createdAt = modifiedTime,
                    updatedAt = System.currentTimeMillis(),
                    coverArtPath = coverArtPath,
                    coverSource = coverSource
                )

                songRepository.insertSong(newSong)
                importedCount++
            }
        }

        importedCount
    }
}
