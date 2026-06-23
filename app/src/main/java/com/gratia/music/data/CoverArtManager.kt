package com.gratia.music.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min

/**
 * Manages cover art extraction, storage, and retrieval.
 * Covers are stored as square JPEG images in internal storage at filesDir/covers/{songId}.jpg.
 */
object CoverArtManager {

    private const val COVERS_DIR = "covers"
    private const val MAX_SIZE = 1024
    private const val JPEG_QUALITY = 85

    /**
     * Extract embedded album artwork from an audio file.
     * Returns null if no embedded art exists or extraction fails.
     */
    fun extractEmbeddedCover(context: Context, audioUri: Uri): Bitmap? {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, audioUri)
            val artBytes = retriever.embeddedPicture
            retriever.release()
            if (artBytes != null) {
                BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Save a bitmap as cover art for a song.
     * Resizes to max 1024x1024, center-cropped square, saved as JPEG.
     * Returns the absolute file path of the saved cover.
     */
    fun saveCoverToInternal(context: Context, songId: String, bitmap: Bitmap): String {
        val coversDir = File(context.filesDir, COVERS_DIR)
        if (!coversDir.exists()) coversDir.mkdirs()

        val coverFile = File(coversDir, "$songId.jpg")
        val processed = cropAndResize(bitmap)

        FileOutputStream(coverFile).use { fos ->
            processed.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, fos)
        }

        if (processed !== bitmap) {
            processed.recycle()
        }

        return coverFile.absolutePath
    }

    /**
     * Copy a user-selected image from a content URI and save as cover art.
     * Returns the absolute file path of the saved cover, or null on failure.
     */
    fun copyCoverFromUri(context: Context, songId: String, imageUri: Uri): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(imageUri) ?: return null
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            if (bitmap != null) {
                val path = saveCoverToInternal(context, songId, bitmap)
                bitmap.recycle()
                path
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Get the cover art file for a song.
     */
    fun getCoverFile(context: Context, songId: String): File {
        return File(File(context.filesDir, COVERS_DIR), "$songId.jpg")
    }

    /**
     * Delete the cover art file for a song.
     */
    fun deleteCover(context: Context, songId: String) {
        try {
            getCoverFile(context, songId).delete()
        } catch (_: Exception) { }
    }

    /**
     * Center-crop to square and resize to MAX_SIZE.
     */
    private fun cropAndResize(bitmap: Bitmap): Bitmap {
        val size = min(bitmap.width, bitmap.height)
        val x = (bitmap.width - size) / 2
        val y = (bitmap.height - size) / 2

        val cropped = Bitmap.createBitmap(bitmap, x, y, size, size)

        return if (size > MAX_SIZE) {
            val scaled = Bitmap.createScaledBitmap(cropped, MAX_SIZE, MAX_SIZE, true)
            if (cropped !== bitmap) cropped.recycle()
            scaled
        } else {
            cropped
        }
    }
}
