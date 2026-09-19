package com.gratia.music.lyrics

/**
 * Content-based lyrics format detector.
 *
 * Examines the raw string to determine the wire format. The detection is
 * purely structural — it never consults provider names, database flags,
 * or user preferences.
 *
 * Priority order mirrors the parsing fallback: TTML and JSON are checked
 * first (they have unambiguous markers), then ELRC vs LRC (both start
 * with `[mm:ss.xx]` but ELRC also has `<mm:ss.xx>` word tags).
 */
object LyricsFormatDetector {

    private val LRC_TIMESTAMP_REGEX = Regex("""\[\d{1,3}:\d{2}(?:\.\d{1,3})?\]""")
    private val ELRC_TIMESTAMP_REGEX = Regex("""<\d{1,3}:\d{2}(?:\.\d{1,3})?>""")

    /**
     * Detects the [LyricsFormat] of a raw lyrics payload.
     */
    fun detect(input: String?): LyricsFormat {
        if (input.isNullOrBlank()) return LyricsFormat.PLAIN

        val trimmed = input.trim()

        // 1. A JSON envelope wrapping the real document. Checked before the
        //    generic JSON branch below, because a wrapper carries `content`
        //    rather than any of the keys a word-timing payload would.
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            val wrapped = detectWrappedTtml(trimmed)
            if (wrapped) return LyricsFormat.JSON_WRAPPED_TTML
        }

        // 2. TTML — XML containing timing elements
        if (trimmed.startsWith("<") &&
            (trimmed.contains("ttm:") || trimmed.contains("<tt") || trimmed.contains("<body>"))) {
            return LyricsFormat.TTML
        }

        // 3. JSON — structured word/syllable timing
        if ((trimmed.startsWith("[") && trimmed.endsWith("]")) ||
            (trimmed.startsWith("{") && trimmed.endsWith("}"))) {
            if (trimmed.contains("\"words\"") || trimmed.contains("'words'") ||
                trimmed.contains("\"syllabus\"") || trimmed.contains("\"lyrics\"") ||
                trimmed.contains("\"text\"")) {
                return LyricsFormat.JSON_WORD
            }
        }

        // 4. LRC / ELRC — timestamp tags in brackets
        val hasLrcTimestamp = trimmed.contains(LRC_TIMESTAMP_REGEX)
        if (hasLrcTimestamp) {
            val hasWordTimestamp = trimmed.contains(ELRC_TIMESTAMP_REGEX)
            return if (hasWordTimestamp) LyricsFormat.ENHANCED_LRC else LyricsFormat.LRC
        }

        // 5. Plain text fallback
        return LyricsFormat.PLAIN
    }

    /**
     * Whether a JSON object is an envelope around an XML document.
     *
     * Recognised either by an explicit type — `{"type": "TTML", …}` — or, when
     * the envelope says nothing useful, by its content simply being markup. The
     * second test is what catches the providers that wrap without labelling.
     */
    private fun detectWrappedTtml(json: String): Boolean {
        return try {
            val obj = org.json.JSONObject(json)
            val type = obj.optString("type", "")
            val content = obj.optString("content", "")
            type.contains("ttml", ignoreCase = true) || content.trimStart().startsWith("<")
        } catch (_: Exception) {
            false
        }
    }
}
