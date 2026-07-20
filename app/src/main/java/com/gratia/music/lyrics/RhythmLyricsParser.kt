package com.gratia.music.lyrics

import android.util.Log
import com.gratia.music.lyrics.RhythmLyricsLine
import com.gratia.music.lyrics.RhythmLyricsWord


import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import kotlin.math.abs

object RhythmLyricsParser {
    private const val TAG = "RhythmLyricsParser"
    
    // Pattern to detect voice tags in lyrics text (e.g., "v1: text" or "v2: text")
    private val voiceTagPattern = java.util.regex.Pattern.compile("^(v\\d+):\\s*(.*)$", java.util.regex.Pattern.CASE_INSENSITIVE)

    private enum class SupplementalLineKind {
        TRANSLATION,
        ROMANIZATION
    }

    /**
    * Parses Rhythm word-by-word lyrics JSON into structured format
    * @param jsonContent JSON string containing word-by-word lyrics data
     * @return List of parsed word-level lyrics, or empty if parsing fails
     */
    fun parseWordByWordLyrics(jsonContent: String): List<WordByWordLyricLine> = emptyList()

    fun toWordByWordJson(lines: List<WordByWordLyricLine>): String = ""

    /**
     * Parse TTML (Timed Text Markup Language) formatted synchronized lyrics.
     * Extracts lines (<p>) and word-by-word timestamps (<span>).
     */
    fun parseTtmlLyrics(ttmlContent: String): List<RhythmLyricsLine> {
        val parsed = parseTtml(audioMimeType = null, lyricText = ttmlContent)
        if (parsed is SemanticLyrics.SyncedLyrics) {
            return parsed.text.map { semanticLine ->
                val slWords = semanticLine.words
                val rhythmLyricsWords = slWords?.mapIndexed { idx, word ->
                    val rawText = semanticLine.text.substring(word.charRange)
                    val trimmedText = rawText.trim()
                    
                    val leadingSpaces = rawText.takeWhile { it.isWhitespace() }.length
                    val trailingSpaces = rawText.takeLastWhile { it.isWhitespace() }.length
                    val trimmedStart = word.charRange.first + leadingSpaces
                    val trimmedEnd = word.charRange.last - trailingSpaces

                    val isPart = if (idx > 0 && trimmedText.isNotEmpty()) {
                        val prevWord = slWords[idx - 1]
                        val prevRawText = semanticLine.text.substring(prevWord.charRange)
                        val prevTrimmedText = prevRawText.trim()
                        if (prevTrimmedText.isNotEmpty()) {
                            val prevTrailingSpaces = prevRawText.takeLastWhile { it.isWhitespace() }.length
                            val prevTrimmedEnd = prevWord.charRange.last - prevTrailingSpaces
                            
                            val gap = semanticLine.text.substring(prevTrimmedEnd + 1, trimmedStart)
                            gap.isEmpty()
                        } else {
                            false
                        }
                    } else {
                        false
                    }
                    RhythmLyricsWord(
                        text = trimmedText,
                        part = isPart,
                        timestamp = word.begin.toLong(),
                        endtime = (word.endInclusive ?: word.begin).toLong()
                    )
                } ?: listOf(
                    RhythmLyricsWord(
                        text = semanticLine.text,
                        part = false,
                        timestamp = semanticLine.start.toLong(),
                        endtime = semanticLine.end.toLong()
                    )
                )
                
                RhythmLyricsLine(
                    text = rhythmLyricsWords,
                    background = semanticLine.speaker?.isBackground ?: false,
                    backgroundText = if (semanticLine.isTranslated) listOf(semanticLine.text) else null,
                    oppositeTurn = semanticLine.speaker?.isVoice2,
                    timestamp = semanticLine.start.toLong(),
                    endtime = semanticLine.end.toLong(),
                    endIsImplicit = semanticLine.endIsImplicit
                )
            }
        }
        return emptyList()
    }
}

/**
 * Represents a line of lyrics with word-level timing
 */
data class WordByWordLyricLine(
    val words: List<WordByWordWord>,
    val lineTimestamp: Long,
    val lineEndtime: Long,
    val background: Boolean = false,
    val voiceTag: String? = null, // Voice tag (v1, v2, v3, etc.) for multi-voice lyrics
    val translation: String? = null,
    val romanization: String? = null,
    val endIsImplicit: Boolean = false
)

/**
 * Represents a single word with precise timing
 */
data class WordByWordWord(
    val text: String,
    val isPart: Boolean, // true if this is a syllable/part of a split word
    val timestamp: Long, // start time in milliseconds
    val endtime: Long // end time in milliseconds
)

