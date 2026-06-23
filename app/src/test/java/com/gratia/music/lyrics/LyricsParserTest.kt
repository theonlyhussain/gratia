package com.gratia.music.lyrics

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests verifying parser behaviors and edge case fallbacks.
 */
class LyricsParserTest {

    @Test
    fun testPlainLyrics() {
        val input = """
            First line of lyrics
            Second line of lyrics
            Third line of lyrics
        """.trimIndent()

        val doc = LyricsParser.parse(input)
        assertTrue("Expected Plain lyrics mode", doc is LyricsDocument.Plain)
        assertEquals(input, (doc as LyricsDocument.Plain).text)
    }

    @Test
    fun testStandardLrc() {
        val input = """
            [00:05.00] First line starts here
            [00:10.50] Second line starts here
            [01:02.00] Another line starts here
        """.trimIndent()

        val doc = LyricsParser.parse(input)
        assertTrue("Expected LineSynced lyrics mode", doc is LyricsDocument.LineSynced)
        val lines = (doc as LyricsDocument.LineSynced).lines
        assertEquals(3, lines.size)

        assertEquals("First line starts here", lines[0].text)
        assertEquals(5000L, lines[0].startMs)
        assertEquals(10500L, lines[0].endMs)

        assertEquals("Second line starts here", lines[1].text)
        assertEquals(10500L, lines[1].startMs)
        assertEquals(62000L, lines[1].endMs)

        assertEquals("Another line starts here", lines[2].text)
        assertEquals(62000L, lines[2].startMs)
        assertNull(lines[2].endMs)
    }

    @Test
    fun testStandardLrcWithMetadata() {
        val input = """
            [ti: Song Title]
            [ar: Artist Name]
            [al: Album Name]
            [00:02.00] Line 1
            [00:05.00] Line 2
        """.trimIndent()

        val doc = LyricsParser.parse(input)
        assertTrue("Expected LineSynced lyrics mode", doc is LyricsDocument.LineSynced)
        val lines = (doc as LyricsDocument.LineSynced).lines
        assertEquals(2, lines.size)
        assertEquals("Line 1", lines[0].text)
        assertEquals(2000L, lines[0].startMs)
        assertEquals(5000L, lines[0].endMs)
    }

    @Test
    fun testEnhancedLrc() {
        val input = """
            [00:14.20] <00:14.20> I <00:14.50> want <00:14.80> to <00:15.10> break <00:15.60> free
            [00:18.10] <00:18.10> I <00:18.40> want <00:18.70> to <00:19.00> break <00:19.50> free
        """.trimIndent()

        val doc = LyricsParser.parse(input)
        assertTrue("Expected WordSynced lyrics mode", doc is LyricsDocument.WordSynced)
        val lines = (doc as LyricsDocument.WordSynced).lines
        assertEquals(2, lines.size)

        val firstLine = lines[0]
        assertEquals("I want to break free", firstLine.text)
        assertEquals(14200L, firstLine.startMs)
        assertEquals(18100L, firstLine.endMs)
        assertEquals(5, firstLine.words.size)

        assertEquals("I", firstLine.words[0].text)
        assertEquals(14200L, firstLine.words[0].startMs)
        assertEquals(14500L, firstLine.words[0].endMs)

        assertEquals("free", firstLine.words[4].text)
        assertEquals(15600L, firstLine.words[4].startMs)
        assertEquals(18100L, firstLine.words[4].endMs)
    }

    @Test
    fun testEnhancedLrcWithInstrumental() {
        val input = """
            [00:02.00] <00:02.00> Intro
            [00:05.00]
            [00:08.00] <00:08.00> Verse <00:09.00> One
        """.trimIndent()

        val doc = LyricsParser.parse(input)
        assertTrue("Expected WordSynced lyrics mode due to tag presence", doc is LyricsDocument.WordSynced)
        val lines = (doc as LyricsDocument.WordSynced).lines
        assertEquals(3, lines.size)
        assertEquals("", lines[1].text)
        assertEquals(5000L, lines[1].startMs)
        assertEquals(8000L, lines[1].endMs)
    }

    @Test
    fun testJsonWordLyrics() {
        val input = """
            [
              {
                "line": "I want to break free",
                "start_time": 14.20,
                "end_time": 16.50,
                "words": [
                  {"word": "I", "start": 14.20, "end": 14.45},
                  {"word": "want", "start": 14.50, "end": 14.75},
                  {"word": "to", "start": 14.80, "end": 15.05},
                  {"word": "break", "start": 15.10, "end": 15.55},
                  {"word": "free", "start": 15.60, "end": 16.50}
                ]
              }
            ]
        """.trimIndent()

        val doc = LyricsParser.parse(input)
        assertTrue("Expected WordSynced lyrics mode from JSON", doc is LyricsDocument.WordSynced)
        val lines = (doc as LyricsDocument.WordSynced).lines
        assertEquals(1, lines.size)
        val firstLine = lines[0]
        assertEquals("I want to break free", firstLine.text)
        assertEquals(14200L, firstLine.startMs)
        assertEquals(16500L, firstLine.endMs)

        assertEquals(5, firstLine.words.size)
        assertEquals("I", firstLine.words[0].text)
        assertEquals(14200L, firstLine.words[0].startMs)
        assertEquals(14450L, firstLine.words[0].endMs)
    }

    @Test
    fun testMalformedJsonFallback() {
        val input = """
            [
              {
                "line": "I want to break free",
                "start_time": 14.20,
                "end_time": 16.50,
                "words": [
                  {"word": "I", "start": 14.20, "end": 14.45}
                  {"word": "want", "start": 14.50, "end": 14.75}
                ]
              }
            ]
        """.trimIndent()

        val doc = LyricsParser.parse(input)
        assertTrue("Expected Plain fallback due to malformed JSON structure", doc is LyricsDocument.Plain)
        assertTrue((doc as LyricsDocument.Plain).text.startsWith("["))
    }

    @Test
    fun testMalformedTimestampsSkippedSafely() {
        val input = """
            [00:05.xx] Malformed seconds decimals
            [00:10.50] Valid line
            [00:abc] Malformed digits
        """.trimIndent()

        val doc = LyricsParser.parse(input)
        assertTrue("Expected LineSynced mode from single valid LRC row", doc is LyricsDocument.LineSynced)
        val lines = (doc as LyricsDocument.LineSynced).lines
        assertEquals(1, lines.size)
        assertEquals("Valid line", lines[0].text)
        assertEquals(10500L, lines[0].startMs)
    }
}
