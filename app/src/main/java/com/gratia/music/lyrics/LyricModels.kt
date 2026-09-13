package com.gratia.music.lyrics

/**
 * The wire format a raw lyrics payload was written in.
 *
 * Detected by [LyricsFormatDetector] from the content itself — never from
 * provider metadata, database flags, or user selection.
 */
enum class LyricsFormat(val label: String) {
    PLAIN("Plain text"),
    LRC("LRC"),
    ENHANCED_LRC("Enhanced LRC"),
    TTML("TTML"),
    JSON_WORD("JSON"),
    UNKNOWN("Unknown");

    /** Human-readable sync description for the UI status line. */
    fun syncDescription(quality: LyricsQuality): String = when (quality) {
        LyricsQuality.SYLLABLE_SYNCED -> "Syllable synced"
        LyricsQuality.WORD_SYNCED     -> "Word synced"
        LyricsQuality.LINE_SYNCED     -> "Line synced"
        LyricsQuality.PLAIN           -> "No sync"
    }
}

/**
 * How precisely the parsed lyrics are timed.
 *
 * Determined from the *parsed output*, not from provider claims.
 * Used internally to rank providers and to drive UI status labels.
 */
enum class LyricsQuality(val priority: Int) {
    PLAIN(0),
    LINE_SYNCED(1),
    WORD_SYNCED(2),
    SYLLABLE_SYNCED(3)
}

/**
 * Canonical models for parsed lyrics in Gratia.
 *
 * Every raw lyrics payload — LRC, ELRC, TTML, JSON, plain text — is
 * normalised into one of these three shapes. The player and the editor
 * consume [LyricsDocument] exclusively; they never see raw strings.
 *
 * [format] and [quality] are informational: they record what the
 * detector and parser found so the editor can show a status line like
 * "TTML · Syllable synced", but they never gate rendering logic.
 */
sealed class LyricsDocument {

    /** The wire format the raw input was detected as. */
    abstract val format: LyricsFormat

    /** How precisely the parsed result is timed. */
    abstract val quality: LyricsQuality

    data class Plain(
        val text: String,
        override val format: LyricsFormat = LyricsFormat.PLAIN
    ) : LyricsDocument() {
        override val quality = LyricsQuality.PLAIN
    }

    data class LineSynced(
        val lines: List<LyricLine>,
        override val format: LyricsFormat = LyricsFormat.LRC
    ) : LyricsDocument() {
        override val quality = LyricsQuality.LINE_SYNCED
    }

    data class WordSynced(
        val lines: List<LyricLine>,
        override val format: LyricsFormat = LyricsFormat.ENHANCED_LRC
    ) : LyricsDocument() {
        override val quality: LyricsQuality
            get() {
                // Syllable-level providers (Apple Music TTML) split words into
                // sub-300ms spans. If any line has such fine timing, call it
                // syllable-synced; otherwise it is word-synced.
                val hasSyllables = lines.any { line ->
                    line.words.size > 1 && line.words.any { w ->
                        (w.endMs - w.startMs) in 1..299
                    }
                }
                return if (hasSyllables) LyricsQuality.SYLLABLE_SYNCED
                       else LyricsQuality.WORD_SYNCED
            }
    }
}
