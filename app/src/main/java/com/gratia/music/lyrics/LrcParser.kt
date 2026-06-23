package com.gratia.music.lyrics

/**
 * Parser for Standard LRC format.
 */
object LrcParser {
    // Matches [mm:ss.xx] or [m:ss.xx] or [mm:ss] followed by text
    private val LRC_LINE_PATTERN = Regex("""^\[(\d{1,2}):(\d{2})(?:\.(\d{1,3}))?\]\s*(.*)$""")
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

            val match = LRC_LINE_PATTERN.matchEntire(line)
            if (match != null) {
                val minutes = match.groupValues[1].toIntOrNull() ?: 0
                val seconds = match.groupValues[2].toIntOrNull() ?: 0
                val millisStr = match.groupValues[3]
                val millis = if (millisStr.isEmpty()) {
                    0
                } else {
                    millisStr.padEnd(3, '0').take(3).toIntOrNull() ?: 0
                }
                val startMs = (minutes * 60_000L) + (seconds * 1000L) + millis
                val text = match.groupValues[4].trim()

                lines.add(LyricLine(text = text, startMs = startMs))
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
