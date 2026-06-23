package com.gratia.music.lyrics

/**
 * Canonical documents representing parsed lyrics.
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
 * Represents a single line in synced lyrics with timing information.
 */
data class LyricLine(
    val text: String,
    val startMs: Long,
    val endMs: Long? = null,
    val words: List<LyricWord> = emptyList()
)

/**
 * Represents a single word in a word-synced line.
 */
data class LyricWord(
    val text: String,
    val startMs: Long,
    val endMs: Long
)
