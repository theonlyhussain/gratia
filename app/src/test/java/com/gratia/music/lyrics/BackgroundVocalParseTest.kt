package com.gratia.music.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The backing vocal must survive parsing as its own timed thing (spec 4/13).
 *
 * The parser side of the transient overlay: whatever a provider called the
 * answering voice, it comes out of [LyricsParser] as [LyricLine.background]
 * with its own word timings intact — never flattened into the lead's text, and
 * never re-timed from the line's stamp.
 *
 * Apple's TTML is the one format that marks it structurally
 * (`ttm:role="x-bg"`); the bracket-based providers are handled by
 * [withBackgroundVocals] and covered in `EmbeddedLyrics`/round-trip tests.
 * Everything asserted here is asserted against the provider's own numbers.
 */
class BackgroundVocalParseTest {

    private fun linesFrom(ttml: String): List<LyricLine> =
        when (val doc = LyricsParser.parse(ttml)) {
            is LyricsDocument.WordSynced -> doc.lines
            is LyricsDocument.LineSynced -> doc.lines
            is LyricsDocument.Plain -> emptyList()
        }

    @Test
    fun `ttm x-bg becomes the line's background with its own timing`() {
        val ttml = """
            <tt xmlns:ttm="http://www.w3.org/ns/ttml#metadata"><body><div>
                <p begin="42.000" end="43.000">
                    <span begin="42.000" end="42.200">I</span>
                    <span begin="42.200" end="42.300"> have</span>
                    <span begin="42.300" end="42.650" ttm:role="x-bg">(a)</span>
                </p>
            </div></body></tt>
        """.trimIndent()

        val line = linesFrom(ttml).first { it.text.startsWith("I") }

        // Primary words are exactly what the primary spans said.
        assertEquals("I have", line.text)
        assertEquals(listOf("I", "have"), line.words.map { it.text })
        assertEquals(42_000L, line.words[0].startMs)
        assertEquals(42_300L, line.words[1].endMs)

        // The answering voice is carried apart, with the timestamp it was
        // actually sung at — not folded into the lead and not the line's stamp.
        val backing = requireNotNull(line.background) { "x-bg was dropped during parsing" }
        assertEquals("(a)", backing.text)
        assertEquals(42_300L, backing.startMs)
        assertEquals(42_650L, backing.computedEndMs)
        assertTrue(backing.words.isNotEmpty())
        assertEquals(42_300L, backing.words.first().startMs)
        assertEquals(42_650L, backing.words.first().endMs)
    }

    @Test
    fun `splitting a background does not change primary word timing`() {
        val withBacking = """
            <tt xmlns:ttm="http://www.w3.org/ns/ttml#metadata"><body><div>
                <p begin="1.000" end="3.000">
                    <span begin="1.000" end="1.400">Hold</span>
                    <span begin="1.400" end="2.000"> on</span>
                    <span begin="2.000" end="2.500" ttm:role="x-bg">(hold on)</span>
                </p>
            </div></body></tt>
        """.trimIndent()

        val line = linesFrom(withBacking).first { it.text.startsWith("Hold") }

        // The lead is untouched by the presence of the background.
        assertEquals("Hold on", line.text)
        assertEquals(listOf(1_000L, 1_400L), line.words.map { it.startMs })
        assertEquals(listOf(1_400L, 2_000L), line.words.map { it.endMs })
        assertEquals(2_000L, line.words.last().endMs)
    }

    @Test
    fun `multiple timed background words keep individual timings`() {
        val ttml = """
            <tt xmlns:ttm="http://www.w3.org/ns/ttml#metadata"><body><div>
                <p begin="5.000" end="7.000">
                    <span begin="5.000" end="5.500">Sing</span>
                    <span ttm:role="x-bg">
                        <span begin="5.600" end="6.100">(oh</span>
                        <span begin="6.100" end="6.900"> baby)</span>
                    </span>
                </p>
            </div></body></tt>
        """.trimIndent()

        val line = linesFrom(ttml).first { it.text.startsWith("Sing") }
        val backing = requireNotNull(line.background) { "nested x-bg was dropped during parsing" }

        assertEquals("(oh baby)", backing.text)
        assertEquals(listOf("(oh", "baby)"), backing.words.map { it.text })
        assertEquals(listOf(5_600L, 6_100L), backing.words.map { it.startMs })
        assertEquals(listOf(6_100L, 6_900L), backing.words.map { it.endMs })
    }

    @Test
    fun `translations are still skipped and never become a background`() {
        val ttml = """
            <tt xmlns:ttm="http://www.w3.org/ns/ttml#metadata"><body><div>
                <p begin="1.000" end="2.000">
                    <span begin="1.000" end="2.000">Hello</span>
                    <span begin="1.000" end="2.000" ttm:role="x-translation">Bonjour</span>
                </p>
            </div></body></tt>
        """.trimIndent()

        val line = linesFrom(ttml).first { it.text.startsWith("Hello") }
        assertEquals("Hello", line.text)
        assertNull(line.background)
    }

    @Test
    fun `a line with no backing metadata has no background`() {
        val ttml = """
            <tt><body><div>
                <p begin="1.000" end="2.000">
                    <span begin="1.000" end="1.500">Just</span>
                    <span begin="1.500" end="2.000"> words</span>
                </p>
            </div></body></tt>
        """.trimIndent()

        val line = linesFrom(ttml).first { it.text.startsWith("Just") }
        assertNull(line.background)
    }
}
