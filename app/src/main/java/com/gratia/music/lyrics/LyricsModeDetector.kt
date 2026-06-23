package com.gratia.music.lyrics

enum class LyricsMode {
    PLAIN, LRC, ELRC, JSON
}

object LyricsModeDetector {
    private val LRC_TIMESTAMP_REGEX = Regex("""\[\d{1,2}:\d{2}(?:\.\d{1,3})?\]""")
    private val ELRC_TIMESTAMP_REGEX = Regex("""<\d{1,2}:\d{2}(?:\.\d{1,3})?>""")

    /**
     * Detects the lyric format type from a raw string.
     */
    fun detectMode(input: String?): LyricsMode {
        if (input.isNullOrBlank()) return LyricsMode.PLAIN

        val trimmed = input.trim()

        // 1. JSON Mode Detection
        if ((trimmed.startsWith("[") && trimmed.endsWith("]")) ||
            (trimmed.startsWith("{") && trimmed.endsWith("}"))) {
            if (trimmed.contains("\"words\"") || trimmed.contains("'words'")) {
                return LyricsMode.JSON
            }
        }

        // 2 & 3. LRC / ELRC Mode Detection
        val hasLrcTimestamp = trimmed.contains(LRC_TIMESTAMP_REGEX)
        if (hasLrcTimestamp) {
            val hasWordTimestamp = trimmed.contains(ELRC_TIMESTAMP_REGEX)
            return if (hasWordTimestamp) {
                LyricsMode.ELRC
            } else {
                LyricsMode.LRC
            }
        }

        // 4. Default plain lyrics fallback
        return LyricsMode.PLAIN
    }
}
