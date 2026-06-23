package com.gratia.music.lyrics

/**
 * Timing engine helper for mapping playback coordinates to lines and words indexes/progress.
 */
object LyricsTimingEngine {

    /**
     * Resolves the index of the currently active line.
     * Returns the index of the last line whose start time is before or equal to currentPositionMs.
     */
    fun findActiveLineIndex(lines: List<LyricLine>, currentPositionMs: Long): Int {
        if (lines.isEmpty()) return -1
        return lines.indexOfLast { it.startMs <= currentPositionMs }
    }

    /**
     * Resolves the index of the active word in the active line.
     */
    fun findActiveWordIndex(words: List<LyricWord>, currentPositionMs: Long): Int {
        if (words.isEmpty()) return -1
        return words.indexOfLast { it.startMs <= currentPositionMs }
    }

    /**
     * Calculates the normalized progress (0.0 to 1.0) of a word.
     */
    fun getWordProgress(word: LyricWord, currentPositionMs: Long): Float {
        if (currentPositionMs <= word.startMs) return 0f
        if (currentPositionMs >= word.endMs) return 1f
        val duration = word.endMs - word.startMs
        if (duration <= 0) return 1f
        return ((currentPositionMs - word.startMs).toFloat() / duration).coerceIn(0f, 1f)
    }
}
