package com.gratia.music.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * In-memory cache for dominant colors extracted from cover art.
 * Extracts colors on IO thread, returns cached results on subsequent calls.
 */
object CoverColorCache {

    private val cache = LinkedHashMap<String, CoverColors>(32, 0.75f, true)
    private const val MAX_CACHE_SIZE = 50

    data class CoverColors(
        val dominant: Color,
        val darkMuted: Color,
        val vibrant: Color,
        val lightMuted: Color
    )

    // Gratia fallback colors
    val FALLBACK = CoverColors(
        dominant = Color(0xFF1B1716),   // Noir Black
        darkMuted = Color(0xFF630102),  // Maroon
        vibrant = Color(0xFF810100),    // Cherry Red
        lightMuted = Color(0xFF2D1E1D)
    )

    /**
     * Get cached colors for a song, or extract them from cover art.
     * Returns fallback colors if extraction fails or no cover exists.
     */
    suspend fun getColors(songId: String, coverArtPath: String?): CoverColors {
        // Check cache first
        cache[songId]?.let { return it }

        if (coverArtPath.isNullOrBlank()) return FALLBACK

        return withContext(Dispatchers.IO) {
            try {
                // Decode a small version of the cover for color extraction
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeFile(coverArtPath, options)

                // Calculate sample size for ~100px image (fast extraction)
                val maxDim = maxOf(options.outWidth, options.outHeight)
                options.inSampleSize = maxOf(1, maxDim / 100)
                options.inJustDecodeBounds = false

                val bitmap = BitmapFactory.decodeFile(coverArtPath, options)
                    ?: return@withContext FALLBACK

                val palette = Palette.from(bitmap).maximumColorCount(8).generate()
                bitmap.recycle()

                val colors = CoverColors(
                    dominant = palette.getDominantColor(0xFF1B1716.toInt()).toComposeColor(),
                    darkMuted = palette.getDarkMutedColor(0xFF630102.toInt()).toComposeColor(),
                    vibrant = palette.getVibrantColor(0xFF810100.toInt()).toComposeColor(),
                    lightMuted = palette.getLightMutedColor(0xFF2D1E1D.toInt()).toComposeColor()
                )

                // Cache result
                synchronized(cache) {
                    cache[songId] = colors
                    if (cache.size > MAX_CACHE_SIZE) {
                        cache.remove(cache.keys.first())
                    }
                }

                colors
            } catch (e: Exception) {
                FALLBACK
            }
        }
    }

    fun clearCache() {
        synchronized(cache) { cache.clear() }
    }

    private fun Int.toComposeColor(): Color = Color(this)
}
