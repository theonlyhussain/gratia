package com.gratia.music.lyrics

/**
 * @deprecated Use [LyricsFormatDetector] instead. This shim exists only
 * for backwards compatibility while callers are migrated.
 */
enum class LyricsMode {
    PLAIN, LRC, ELRC, JSON, TTML
}

/**
 * @deprecated Use [LyricsFormatDetector.detect] instead.
 */
object LyricsModeDetector {
    fun detectMode(input: String?): LyricsMode {
        return when (LyricsFormatDetector.detect(input)) {
            LyricsFormat.PLAIN -> LyricsMode.PLAIN
            LyricsFormat.LRC -> LyricsMode.LRC
            LyricsFormat.ENHANCED_LRC -> LyricsMode.ELRC
            LyricsFormat.JSON_WORD -> LyricsMode.JSON
            LyricsFormat.TTML -> LyricsMode.TTML
            // Deprecated shim with no TTML-wrapped-in-JSON mode of its own;
            // the payload is TTML, so that is what it reports.
            LyricsFormat.JSON_WRAPPED_TTML -> LyricsMode.TTML
            LyricsFormat.UNKNOWN -> LyricsMode.PLAIN
        }
    }
}
