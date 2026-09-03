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


