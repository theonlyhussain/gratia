package com.gratia.music.lyrics

import org.json.JSONObject

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

        var textToParse = input.trim()

        // Read the format off the WRAPPER, before unwrapping it. Once the
        // envelope is gone there is nothing left to say the payload arrived as
        // JSON, and a provider that ships TTML inside `content` is worth
        // reporting as exactly that rather than as a bare document.
        val wrappedTtml = LyricsFormatDetector.detect(textToParse) == LyricsFormat.JSON_WRAPPED_TTML

        // Globally unwrap PaxSenix/Lyrically JSON payloads if present in DB
        if (textToParse.startsWith("{") && textToParse.endsWith("}")) {
            try {
                val obj = JSONObject(textToParse)
                if (obj.has("content")) {
                    textToParse = obj.optString("content", textToParse).trim()
                }
            } catch (_: Exception) {}
        }

        if (textToParse.isBlank()) {
            return LyricsDocument.Plain(input)
        }

        val format = LyricsFormatDetector.detect(textToParse)

        // Try the detected format first
        var document: LyricsDocument? = tryParse(format, textToParse, enableEstimatedTimings)

        // Sequential fallback — try every format in quality order
        if (document == null) {
            for (fallback in listOf(
                LyricsFormat.TTML,
                LyricsFormat.JSON_WORD,
                LyricsFormat.ENHANCED_LRC,
                LyricsFormat.LRC
            )) {
                if (fallback == format) continue // already tried
                document = tryParse(fallback, textToParse, enableEstimatedTimings)
                if (document != null) break
            }
        }

        // Nothing worked — return as plain text
        val parsed = document ?: LyricsDocument.Plain(textToParse)
        return if (wrappedTtml) parsed.asJsonWrappedTtml() else parsed
    }

    /**
     * Re-labels a document that came out of an envelope as TTML.
     *
     * Only where the document really is TTML: if the envelope's content turned
     * out to be LRC, or the TTML parser fell through to another format, the
     * format that actually produced the lines is the honest one to report.
     */
    private fun LyricsDocument.asJsonWrappedTtml(): LyricsDocument {
        if (format != LyricsFormat.TTML) return this
        return when (this) {
            is LyricsDocument.WordSynced -> copy(format = LyricsFormat.JSON_WRAPPED_TTML)
            is LyricsDocument.LineSynced -> copy(format = LyricsFormat.JSON_WRAPPED_TTML)
            is LyricsDocument.Plain -> this
        }
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



            LyricsFormat.JSON_WRAPPED_TTML -> {
                // The envelope is stripped by [parse] before a format is chosen,
                // so this is only reached if a caller dispatches the wrapper
                // straight at the format table. Treat the payload as the TTML
                // it is.
                tryParse(LyricsFormat.TTML, input, enableEstimatedTimings)
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
