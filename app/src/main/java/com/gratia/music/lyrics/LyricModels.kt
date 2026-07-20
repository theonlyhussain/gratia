package com.gratia.music.lyrics

/**
 * Canonical models for parsed lyrics in Gratia.
 */
sealed class LyricsDocument {
    data class Plain(
        val text: String
    ) : LyricsDocument()

    data class LineSynced(
        val lines: List<LyricLine>
    ) : LyricsDocument()

    data class WordSynced(
        val lines: List<LyricLine>
    ) : LyricsDocument()
}

/**
 * A single timed lyrics line.
 */
data class LyricLine(
    val text: String,
    val startMs: Long,
    val endMs: Long? = null,
    val words: List<LyricWord> = emptyList(),
    val translation: String? = null,
    val romanization: String? = null,
    val voiceTag: String? = null
)

/**
 * A single timed word within a synced line.
 */
data class LyricWord(
    val text: String,
    val startMs: Long,
    val endMs: Long
)
