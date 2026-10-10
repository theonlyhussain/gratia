package com.gratia.music.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The backing vocal is a question asked of the clock, never a one-way flag.
 *
 * These tests pin [TransientVocal] — the resolver the transient overlay draws
 * its visibility from — to playback position alone, which is what makes seeking
 * backwards re-arm a vocal (spec 16) and pausing inside its window leave it
 * exactly where it was (spec 17). Every case below is a bare position, because
 * that is all the overlay is allowed to remember: nothing.
 */
class TransientVocalTest {

    /** `(startMs, endMs, text)` for one timed word. */
    private fun word(startMs: Long, endMs: Long, text: String) = Triple(startMs, endMs, text)

    /** A word-timed backing vocal, the shape Apple's `ttm:role="x-bg"` produces. */
    private fun backing(vararg words: Triple<Long, Long, String>): LyricLine = LyricLine(
        startMs = words.first().first,
        text = words.joinToString(" ") { it.third },
        words = words.map { LyricWord(it.first, it.second, it.third) },
    )

    // ── Timing window (spec 8) ───────────────────────────────────────────────

    @Test
    fun `not active before its window opens`() {
        val a = backing(word(42_300L, 42_650L, "(a)"))
        assertFalse(TransientVocal.isActive(a, 42_000L))
        assertFalse(TransientVocal.isActive(a, 42_299L))
    }

    @Test
    fun `active inside its window`() {
        val a = backing(word(42_300L, 42_650L, "(a)"))
        assertTrue(TransientVocal.isActive(a, 42_300L))
        assertTrue(TransientVocal.isActive(a, 42_350L))
        assertTrue(TransientVocal.isActive(a, 42_500L))
        assertTrue(TransientVocal.isActive(a, 42_650L))
    }

    @Test
    fun `not active after its window closes`() {
        val a = backing(word(42_300L, 42_650L, "(a)"))
        assertFalse(TransientVocal.isActive(a, 42_651L))
        assertFalse(TransientVocal.isActive(a, 43_000L))
    }

    @Test
    fun `a blank backing line is never active`() {
        // No metadata produced a line worth drawing, which is how a document
        // without backing-vocal timing renders as ordinary lyrics (spec 14).
        val none = LyricLine(startMs = 0L, text = "", endMs = 10_000L)
        assertFalse(TransientVocal.isActive(none, 5_000L))
        assertFalse(TransientVocal.isActive(LyricLine(0L, "   "), 0L))
    }

    // ── Seek (spec 16) ───────────────────────────────────────────────────────

    @Test
    fun `seeking backwards makes the vocal eligible again`() {
        // The same line object, asked at three positions in the order a scrub
        // would visit them. There is no state to reset, so the answer simply
        // follows the position.
        val a = backing(word(42_300L, 42_650L, "(a)"))
        assertFalse(TransientVocal.isActive(a, 10_000L))
        assertTrue(TransientVocal.isActive(a, 42_400L))
        assertFalse(TransientVocal.isActive(a, 10_000L))
        assertTrue(TransientVocal.isActive(a, 42_400L))
    }

    @Test
    fun `seeking forwards past the vocal keeps it gone`() {
        val a = backing(word(42_300L, 42_650L, "(a)"))
        assertTrue(TransientVocal.isActive(a, 42_400L))
        assertFalse(TransientVocal.isActive(a, 45_000L))
    }

    @Test
    fun `paused inside the window stays active at that position`() {
        val a = backing(word(42_300L, 42_650L, "(a)"))
        // A pause is the same position sampled repeatedly.
        repeat(5) { assertTrue(TransientVocal.isActive(a, 42_400L)) }
    }

    // ── Progress (spec 7) ────────────────────────────────────────────────────

    @Test
    fun `progress follows the playback position and clamps`() {
        val a = backing(word(1_000L, 3_000L, "(a)"))
        assertEquals(0f, TransientVocal.progress(a, 1_000L), 0.0001f)
        assertEquals(0.25f, TransientVocal.progress(a, 1_500L), 0.0001f)
        assertEquals(0.5f, TransientVocal.progress(a, 2_000L), 0.0001f)
        assertEquals(1f, TransientVocal.progress(a, 3_000L), 0.0001f)
        // Clamped on either side of the window.
        assertEquals(0f, TransientVocal.progress(a, 0L), 0.0001f)
        assertEquals(1f, TransientVocal.progress(a, 9_999L), 0.0001f)
    }

    // ── Word-level vs line-level (spec 12/15) ────────────────────────────────

    @Test
    fun `word-level timing ends at the last word's end`() {
        val phrase = backing(
            word(5_000L, 5_400L, "(oh"),
            word(5_400L, 6_100L, "baby)"),
        )
        assertEquals(6_100L, phrase.computedEndMs)
        assertTrue(TransientVocal.isActive(phrase, 5_800L))
        assertFalse(TransientVocal.isActive(phrase, 6_200L))
    }

    @Test
    fun `a word-timed vocal ends with its words, not the parent line's end`() {
        // A bracket-split backing phrase carries the parent line's stated end,
        // but it is only on screen for the words it actually sings — otherwise
        // "(a)" would linger for the rest of the line (spec 8/22).
        val a = LyricLine(
            startMs = 42_300L,
            text = "(a)",
            words = listOf(LyricWord(42_300L, 42_650L, "(a)")),
            endMs = 52_000L,
        )
        assertTrue(TransientVocal.isActive(a, 42_500L))
        assertFalse(TransientVocal.isActive(a, 42_700L))
        assertFalse(TransientVocal.isActive(a, 50_000L))
    }

    @Test
    fun `line-level timing uses the stated end without inventing words`() {
        // A line-synced provider that stated only the line's own end. The
        // group is shown whole for that window; no per-word timings appear.
        val group = LyricLine(startMs = 7_000L, text = "(yeah)", endMs = 7_900L)
        assertTrue(group.words.isEmpty())
        assertTrue(TransientVocal.isActive(group, 7_500L))
        assertFalse(TransientVocal.isActive(group, 7_901L))
    }

    // ── Overlap (spec 11) ────────────────────────────────────────────────────

    @Test
    fun `overlapping backing vocals are both supported`() {
        // Two answering voices that genuinely overlap in the source: neither
        // is discarded, and each is active in its own window when the other is.
        val a = backing(word(10_000L, 11_000L, "(a)"))
        val b = backing(word(10_500L, 12_000L, "(yeah)"))
        assertTrue(TransientVocal.isActive(a, 10_700L))
        assertTrue(TransientVocal.isActive(b, 10_700L))
        // Each ends on its own clock.
        assertFalse(TransientVocal.isActive(a, 11_500L))
        assertTrue(TransientVocal.isActive(b, 11_500L))
    }
}
