package com.gratia.music.lyrics

/**
 * Parser for Enhanced LRC (ELRC) format.
 */
object EnhancedLrcParser {
    // Matches [mm:ss.xx] or [m:ss.xx] or [mm:ss] followed by text
    private val LRC_LINE_PATTERN = Regex("""^\[(\d{1,2}):(\d{2})(?:\.(\d{1,3}))?\]\s*(.*)$""")
    // Matches word timestamp tags like <00:14.20>
    private val ELRC_WORD_PATTERN = Regex("""<(\d{1,2}):(\d{2})(?:\.(\d{1,3}))?>""")
    // Matches metadata tags like [ti:Title], [ar:Artist], etc.
    private val METADATA_PATTERN = Regex("""^\[([a-zA-Z]+):(.*)\]$""")

    /**
     * Parses ELRC text into a list of LyricLine objects with words timing data.
     */
    fun parse(input: String): List<LyricLine> {
        val lines = mutableListOf<LyricLine>()
        val rawLines = input.lines().map { it.trim() }

        for (line in rawLines) {
            if (line.isBlank()) continue

            // Skip metadata tags
            if (METADATA_PATTERN.matches(line)) {
                continue
            }

            val match = LRC_LINE_PATTERN.matchEntire(line)
            if (match != null) {
                val minutes = match.groupValues[1].toIntOrNull() ?: 0
                val seconds = match.groupValues[2].toIntOrNull() ?: 0
                val millisStr = match.groupValues[3]
                val millis = if (millisStr.isEmpty()) 0 else millisStr.padEnd(3, '0').take(3).toIntOrNull() ?: 0
                val lineStartMs = (minutes * 60_000L) + (seconds * 1000L) + millis
                val content = match.groupValues[4]

                // Parse word tokens in the content
                val wordMatches = ELRC_WORD_PATTERN.findAll(content).toList()
                val words = mutableListOf<LyricWord>()
                val lineTextBuilder = StringBuilder()

                for (i in wordMatches.indices) {
                    val currentMatch = wordMatches[i]
                    val nextMatch = if (i < wordMatches.size - 1) wordMatches[i + 1] else null

                    val wordMin = currentMatch.groupValues[1].toIntOrNull() ?: 0
                    val wordSec = currentMatch.groupValues[2].toIntOrNull() ?: 0
                    val wordMillisStr = currentMatch.groupValues[3]
                    val wordMillis = if (wordMillisStr.isEmpty()) 0 else wordMillisStr.padEnd(3, '0').take(3).toIntOrNull() ?: 0
                    val wordStartMs = (wordMin * 60_000L) + (wordSec * 1000L) + wordMillis

                    val wordTextStart = currentMatch.range.last + 1
                    val wordTextEnd = nextMatch?.range?.first ?: content.length
                    val rawWordText = content.substring(wordTextStart, wordTextEnd)
                    val trimmedWordText = rawWordText.trim()

                    if (trimmedWordText.isNotEmpty()) {
                        words.add(LyricWord(text = trimmedWordText, startMs = wordStartMs, endMs = 0L))
                        if (lineTextBuilder.isNotEmpty()) lineTextBuilder.append(" ")
                        lineTextBuilder.append(trimmedWordText)
                    }
                }

                // Fall back to line level text if no word matches exist
                val finalText = if (words.isEmpty()) content.trim() else lineTextBuilder.toString()
                lines.add(LyricLine(text = finalText, startMs = lineStartMs, words = words))
            }
        }

        val sortedLines = lines.sortedBy { it.startMs }

        // Infer line endMs and resolve word endMs
        val result = mutableListOf<LyricLine>()
        for (i in sortedLines.indices) {
            val currentLine = sortedLines[i]
            val nextLineStart = if (i < sortedLines.size - 1) sortedLines[i + 1].startMs else null
            val lineEndMs = nextLineStart

            val updatedWords = mutableListOf<LyricWord>()
            val words = currentLine.words
            for (j in words.indices) {
                val currentWord = words[j]
                val nextWordStart = if (j < words.size - 1) words[j + 1].startMs else null
                val wordEndMs = nextWordStart ?: lineEndMs ?: (currentWord.startMs + 2000L)
                updatedWords.add(currentWord.copy(endMs = wordEndMs))
            }

            result.add(currentLine.copy(endMs = lineEndMs, words = updatedWords))
        }

        return result
    }
}
