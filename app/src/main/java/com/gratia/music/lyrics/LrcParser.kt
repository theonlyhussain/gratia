package com.gratia.music.lyrics

import java.util.regex.Pattern

/**
 * Robust LRC parser inspired by Rhythm's parsing architecture.
 * Handles standard LRC, multi-timestamp lines, metadata tags,
 * translations, romanizations, and voice tags.
 *
 * Attribution: Parsing logic adapted from Rhythm (ChromaHub), GPL-3.0.
 */
object LrcParser {

    // Matches [mm:ss.xx], [mm:ss:xx], [mm:ss.xxx], [mm:ss]
    private val TIMESTAMP_PATTERN =
        Pattern.compile("\\[\\s*(\\d{1,3})\\s*:\\s*(\\d{2})(?:\\s*[.:]?\\s*(\\d{0,3}))?\\s*]")

    // Metadata tags to skip: ar, ti, al, by, offset, re, ve, length, v1, v2, etc.
    private val METADATA_PATTERN =
        Pattern.compile("\\[(ar|ti|al|by|offset|re|ve|length|v\\d+|version|editor|tool):[^]]*]", Pattern.CASE_INSENSITIVE)

    // Enhanced LRC word-level timestamps: <mm:ss.xx>
    private val WORD_TIMESTAMP_PATTERN =
        Pattern.compile("<(\\d{1,3}):(\\d{2})(?:\\.(\\d{2,3}))?>")

    // Voice tag at start of line text: "v1: some text"
    private val VOICE_TAG_PATTERN =
        Pattern.compile("^(v\\d+):\\s*(.*)$", Pattern.CASE_INSENSITIVE)

    /**
     * Parse LRC content into a list of [LyricLine].
     * Supports multi-line lyrics where untimestamped lines following a timestamped
     * line are treated as translations/romanizations.
     */
    fun parse(input: String): List<LyricLine> {
        if (input.isBlank()) return emptyList()

        val rawLines = input.lines()
        val hasWordTimestamps = WORD_TIMESTAMP_PATTERN.matcher(input).find()

        // Phase 1: Group raw lines into blocks (timestamped line + trailing plain lines)
        data class RawBlock(
            val timestamps: List<Long>,
            val content: String,
            val supplemental: MutableList<String> = mutableListOf()
        )

        val blocks = mutableListOf<RawBlock>()

        for (rawLine in rawLines) {
            val trimmed = rawLine.trim()
            if (trimmed.isBlank()) continue

            // Skip pure metadata lines
            val metaMatcher = METADATA_PATTERN.matcher(trimmed)
            val strippedOfMeta = metaMatcher.replaceAll("").trim()
            if (strippedOfMeta.isEmpty() && metaMatcher.find()) continue
            // Reset after check
            metaMatcher.reset()

            val tsMatcher = TIMESTAMP_PATTERN.matcher(trimmed)
            val timestamps = mutableListOf<Long>()
            var lastTagEnd = 0

            while (tsMatcher.find()) {
                val minutes = tsMatcher.group(1)!!.toLong()
                val seconds = tsMatcher.group(2)!!.toLong()
                val millisStr = tsMatcher.group(3) ?: ""
                val millis = if (millisStr.isEmpty()) 0L
                else millisStr.padEnd(3, '0').take(3).toLong()
                timestamps.add(minutes * 60_000L + seconds * 1000L + millis)
                lastTagEnd = tsMatcher.end()
            }

            if (timestamps.isNotEmpty()) {
                val content = trimmed.substring(lastTagEnd).trim()
                blocks.add(RawBlock(timestamps, content))
            } else if (blocks.isNotEmpty()) {
                // Untimestamped line → supplemental to the previous block
                blocks.last().supplemental.add(trimmed)
            }
        }

        // Phase 2: Convert blocks to LyricLines with translation/romanization detection
        val lines = mutableListOf<LyricLine>()

        for (block in blocks) {
            val allTextLines = mutableListOf(block.content)
            allTextLines.addAll(block.supplemental)

            val (mainText, translation, romanization) = separateTranslation(allTextLines)
            val (voiceTag, cleanedText) = extractVoiceTag(mainText)

            // Parse word-level timestamps if present
            val words = if (hasWordTimestamps) {
                parseWordTimestamps(block.content, block.timestamps.first())
            } else {
                emptyList()
            }

            for (ts in block.timestamps) {
                lines.add(
                    LyricLine(
                        text = cleanedText,
                        startMs = ts,
                        words = words,
                        translation = translation,
                        romanization = romanization,
                        voiceTag = voiceTag
                    )
                )
            }
        }

        // Phase 3: Sort and infer endMs
        val sorted = lines.sortedBy { it.startMs }
        return sorted.mapIndexed { i, line ->
            val nextStart = if (i < sorted.size - 1) sorted[i + 1].startMs else null
            val resolvedWords = if (line.words.isNotEmpty()) {
                resolveWordEndTimes(line.words, nextStart)
            } else {
                emptyList()
            }
            line.copy(endMs = nextStart, words = resolvedWords)
        }
    }

    /**
     * Parse <mm:ss.xx> word-level timestamps within a line.
     */
    private fun parseWordTimestamps(content: String, lineStartMs: Long): List<LyricWord> {
        val matcher = WORD_TIMESTAMP_PATTERN.matcher(content)
        val wordMatches = mutableListOf<Pair<Long, Int>>() // (timestamp, endIndex)

        while (matcher.find()) {
            val minutes = matcher.group(1)!!.toLong()
            val seconds = matcher.group(2)!!.toLong()
            val millisStr = matcher.group(3) ?: ""
            val millis = if (millisStr.isEmpty()) 0L
            else millisStr.padEnd(3, '0').take(3).toLong()
            wordMatches.add(Pair(minutes * 60_000L + seconds * 1000L + millis, matcher.end()))
        }

        if (wordMatches.isEmpty()) return emptyList()

        val words = mutableListOf<LyricWord>()
        for (i in wordMatches.indices) {
            val (ts, textStart) = wordMatches[i]
            val textEnd = if (i < wordMatches.size - 1) {
                // Find the start of the next <timestamp> tag
                val nextTagStart = content.indexOf('<', textStart)
                if (nextTagStart >= 0) nextTagStart else content.length
            } else {
                content.length
            }
            val wordText = content.substring(textStart, textEnd).trim()
            if (wordText.isNotEmpty()) {
                words.add(LyricWord(text = wordText, startMs = ts, endMs = 0L))
            }
        }

        return words
    }

    /**
     * Fill in word endMs from the next word's startMs.
     */
    private fun resolveWordEndTimes(words: List<LyricWord>, lineEndMs: Long?): List<LyricWord> {
        return words.mapIndexed { i, word ->
            val nextStart = if (i < words.size - 1) words[i + 1].startMs
            else lineEndMs ?: (word.startMs + 2000L)
            word.copy(endMs = nextStart)
        }
    }

    /**
     * Separate main text from translation and romanization lines.
     * Inspired by Rhythm's separateTranslation logic.
     */
    private fun separateTranslation(textLines: List<String>): Triple<String, String?, String?> {
        val normalized = textLines.map { it.trim() }.filter { it.isNotEmpty() }
        if (normalized.isEmpty()) return Triple("", null, null)
        if (normalized.size == 1) return Triple(normalized[0], null, null)

        val mainText = normalized[0]
        var translation: String? = null
        var romanization: String? = null

        for (i in 1 until normalized.size) {
            val line = normalized[i]
            when {
                line.startsWith("(") && line.endsWith(")") -> {
                    val inner = line.substring(1, line.length - 1).trim()
                    translation = appendUnique(translation, inner)
                }
                line.startsWith("[") && line.endsWith("]") -> {
                    val inner = line.substring(1, line.length - 1).trim()
                    romanization = appendUnique(romanization, inner)
                }
                // Main text has non-ASCII, this line is ASCII → romanization
                mainText.any { it.code > 127 } && line.all { it.code <= 127 || it.isWhitespace() } -> {
                    romanization = appendUnique(romanization, line)
                }
                // Main text is ASCII, this line has non-ASCII → translation
                mainText.all { it.code <= 127 || it.isWhitespace() } && line.any { it.code > 127 } -> {
                    translation = appendUnique(translation, line)
                }
                else -> {
                    translation = appendUnique(translation, line)
                }
            }
        }

        return Triple(mainText, translation?.ifBlank { null }, romanization?.ifBlank { null })
    }

    private fun appendUnique(existing: String?, incoming: String): String {
        val trimmed = incoming.trim()
        if (trimmed.isEmpty()) return existing.orEmpty()
        return if (existing.isNullOrBlank()) trimmed else "$existing\n$trimmed"
    }

    private fun extractVoiceTag(text: String): Pair<String?, String> {
        val matcher = VOICE_TAG_PATTERN.matcher(text.trim())
        return if (matcher.matches()) {
            Pair(matcher.group(1)!!.lowercase(), (matcher.group(2) ?: text).trim())
        } else {
            Pair(null, text)
        }
    }
}
