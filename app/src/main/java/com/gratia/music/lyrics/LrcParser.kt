package com.gratia.music.lyrics

/**
 * Parser for Standard LRC format.
 */
object LrcParser {
    // Matches one or more [mm:ss.xx] tags
    private val TIME_TAG_PATTERN = Regex("""\[(\d{1,2}):(\d{2})(?:\.(\d{1,3}))?\]""")
    // Matches metadata tags like [ti:Title], [ar:Artist], etc.
    private val METADATA_PATTERN = Regex("""^\[([a-zA-Z]+):(.*)\]$""")

    /**
     * Parses LRC text into a list of LyricLine objects.
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
                val text = line.substring(lastTagEnd).trim()

                for (tag in timeTags) {
                    val minutes = tag.groupValues[1].toIntOrNull() ?: 0
                    val seconds = tag.groupValues[2].toIntOrNull() ?: 0
                    val millisStr = tag.groupValues[3]
                    val millis = if (millisStr.isEmpty()) {
                        0
                    } else {
                        millisStr.padEnd(3, '0').take(3).toIntOrNull() ?: 0
                    }
                    val startMs = (minutes * 60_000L) + (seconds * 1000L) + millis

                    lines.add(LyricLine(text = text, startMs = startMs))
                }
            }
        }

        // Sort lines by startMs
        val sortedLines = lines.sortedBy { it.startMs }

        // Infer endMs from the next line's startMs
        val result = mutableListOf<LyricLine>()
        for (i in sortedLines.indices) {
            val current = sortedLines[i]
            val nextStart = if (i < sortedLines.size - 1) sortedLines[i + 1].startMs else null
            result.add(current.copy(endMs = nextStart))
        }

        return result
    }
}
