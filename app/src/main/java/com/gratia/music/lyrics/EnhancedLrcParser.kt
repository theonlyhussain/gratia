package com.gratia.music.lyrics

/**
 * Parser for Enhanced LRC (ELRC) format.
 */
object EnhancedLrcParser {
    // Matches one or more [mm:ss.xx] tags
    private val TIME_TAG_PATTERN = Regex("""\[(\d{1,2}):(\d{2})(?:\.(\d{1,3}))?\]""")
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

            val timeTags = TIME_TAG_PATTERN.findAll(line).toList()
            if (timeTags.isNotEmpty()) {
                val lastTagEnd = timeTags.last().range.last + 1
                val content = line.substring(lastTagEnd).trim()

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

                val finalText = if (words.isEmpty()) content.trim() else lineTextBuilder.toString()

                for (tag in timeTags) {
                    val minutes = tag.groupValues[1].toIntOrNull() ?: 0
                    val seconds = tag.groupValues[2].toIntOrNull() ?: 0
                    val millisStr = tag.groupValues[3]
                    val millis = if (millisStr.isEmpty()) 0 else millisStr.padEnd(3, '0').take(3).toIntOrNull() ?: 0
                    val lineStartMs = (minutes * 60_000L) + (seconds * 1000L) + millis

                    // Copy the words list (deep copy not strictly necessary since words are read-only data classes, but we do need new instances of the list)
                    lines.add(LyricLine(text = finalText, startMs = lineStartMs, words = words.toList()))
                }
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
