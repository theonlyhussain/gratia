package com.gratia.music.lyrics

/**
 * Unified entry point for parsing raw lyrics into a canonical [LyricsDocument].
 *
 * This is the single central path for all lyrics interpretation in Gratia.
 * The UI, the repository, and the editor all call [parse] — no other code
 * should invoke individual format parsers directly.
 *
 * Detection → Parser selection → Canonical document, with a sequential
 * fallback when the detected format's parser fails.
 */
object LyricsParser {

    /**
     * Parses raw lyrics text into a [LyricsDocument].
     *
     * @param input           Raw lyrics string (LRC, ELRC, TTML, JSON, or plain text).
     * @param enableEstimatedTimings  When true and the input is line-synced LRC,
     *                        generate estimated per-word timings for the sweep animation.
     */
    fun parse(input: String?, enableEstimatedTimings: Boolean = false): LyricsDocument {
        if (input.isNullOrBlank()) {
            return LyricsDocument.Plain("")
        }

        val format = LyricsFormatDetector.detect(input)

        // Try the detected format first
        val primary = tryParse(format, input, enableEstimatedTimings)
        if (primary != null) return primary

        // Sequential fallback — try every format in quality order
        for (fallback in listOf(
            LyricsFormat.TTML,
            LyricsFormat.JSON_WORD,
            LyricsFormat.ENHANCED_LRC,
            LyricsFormat.LRC
        )) {
            if (fallback == format) continue // already tried
            val result = tryParse(fallback, input, enableEstimatedTimings)
            if (result != null) return result
        }

        // Nothing worked — return as plain text
        return LyricsDocument.Plain(input)
    }

    /**
     * Attempts to parse [input] as the given [format].
     * Returns null if the parser fails or produces empty output.
     */
    private fun tryParse(
        format: LyricsFormat,
        input: String,
        enableEstimatedTimings: Boolean
    ): LyricsDocument? {
        return when (format) {
            LyricsFormat.TTML -> {
                try {
                    val lines = TtmlLyrics.parse(input)
                    if (lines.isNotEmpty()) {
                        val hasWords = lines.any { it.words.isNotEmpty() }
                        if (hasWords) LyricsDocument.WordSynced(lines, LyricsFormat.TTML)
                        else LyricsDocument.LineSynced(lines, LyricsFormat.TTML)
                    } else null
                } catch (_: Exception) { null }
            }

            LyricsFormat.JSON_WORD -> {
                try {
                    val lines = JsonWordLyricsParser.parse(input)
                    if (lines.isNotEmpty()) {
                        val hasWords = lines.any { it.words.isNotEmpty() }
                        if (hasWords) LyricsDocument.WordSynced(lines, LyricsFormat.JSON_WORD)
                        else LyricsDocument.LineSynced(lines, LyricsFormat.JSON_WORD)
                    } else null
                } catch (_: Exception) { null }
            }

            LyricsFormat.ENHANCED_LRC -> {
                try {
                    val lines = EnhancedLrcParser.parse(input)
                    if (lines.isNotEmpty()) {
                        val hasWords = lines.any { it.words.isNotEmpty() }
                        if (hasWords) LyricsDocument.WordSynced(lines, LyricsFormat.ENHANCED_LRC)
                        else LyricsDocument.LineSynced(lines, LyricsFormat.ENHANCED_LRC)
                    } else null
                } catch (_: Exception) { null }
            }

            LyricsFormat.LRC -> {
                try {
                    val lines = LrcParser.parse(input, enableEstimatedTimings)
                    if (lines.isNotEmpty()) {
                        // LrcParser can produce word timings when enableEstimatedTimings is on,
                        // or when the input contains <mm:ss.xx> tags that slipped through detection.
                        val hasWords = lines.any { it.words.isNotEmpty() }
                        if (hasWords) LyricsDocument.WordSynced(lines, LyricsFormat.LRC)
                        else LyricsDocument.LineSynced(lines, LyricsFormat.LRC)
                    } else null
                } catch (_: Exception) { null }
            }

            LyricsFormat.PLAIN -> {
                LyricsDocument.Plain(input)
            }

            LyricsFormat.UNKNOWN -> null
        }
    }
}
