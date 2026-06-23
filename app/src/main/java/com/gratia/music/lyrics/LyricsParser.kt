package com.gratia.music.lyrics

/**
 * Unified entry point for parsing lyrics into a canonical [LyricsDocument].
 * Incorporates a sequential fallback mechanism on parsing failures.
 */
object LyricsParser {

    /**
     * Parses the lyrics raw text.
     */
    fun parse(input: String?): LyricsDocument {
        if (input.isNullOrBlank()) {
            return LyricsDocument.Plain("")
        }

        val mode = LyricsModeDetector.detectMode(input)

        when (mode) {
            LyricsMode.JSON -> {
                try {
                    val lines = JsonWordLyricsParser.parse(input)
                    if (lines.isNotEmpty()) {
                        return LyricsDocument.WordSynced(lines)
                    }
                } catch (_: Exception) {}
            }
            LyricsMode.ELRC -> {
                try {
                    val lines = EnhancedLrcParser.parse(input)
                    if (lines.isNotEmpty()) {
                        val hasWords = lines.any { it.words.isNotEmpty() }
                        return if (hasWords) LyricsDocument.WordSynced(lines) else LyricsDocument.LineSynced(lines)
                    }
                } catch (_: Exception) {}
            }
            LyricsMode.LRC -> {
                try {
                    val lines = LrcParser.parse(input)
                    if (lines.isNotEmpty()) {
                        return LyricsDocument.LineSynced(lines)
                    }
                } catch (_: Exception) {}
            }
            LyricsMode.PLAIN -> {
                return LyricsDocument.Plain(input)
            }
        }

        // Sequential Fallback sequence:
        // Try JSON
        try {
            val lines = JsonWordLyricsParser.parse(input)
            if (lines.isNotEmpty()) return LyricsDocument.WordSynced(lines)
        } catch (_: Exception) {}

        // Try ELRC
        try {
            val lines = EnhancedLrcParser.parse(input)
            if (lines.isNotEmpty()) {
                val hasWords = lines.any { it.words.isNotEmpty() }
                return if (hasWords) LyricsDocument.WordSynced(lines) else LyricsDocument.LineSynced(lines)
            }
        } catch (_: Exception) {}

        // Try LRC
        try {
            val lines = LrcParser.parse(input)
            if (lines.isNotEmpty()) return LyricsDocument.LineSynced(lines)
        } catch (_: Exception) {}

        // Return plain
        return LyricsDocument.Plain(input)
    }
}
