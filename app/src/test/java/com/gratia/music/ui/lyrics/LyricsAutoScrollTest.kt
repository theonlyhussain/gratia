package com.gratia.music.ui.lyrics

import com.gratia.music.lyrics.LyricLine
import com.gratia.music.lyrics.LyricWord
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for the viewport-aware lyrics auto-scroll.
 *
 * The two things worth pinning down are what counts as "being sung" — including
 * overlapping vocals — and that the page moves only when that content no longer
 * fits the safe band of the viewport.
 */
class LyricsAutoScrollTest {

    /** A 1000px viewport, so the safe band runs from 150 to 820. */
    private val viewportHeight = 1000

    private fun row(index: Int, top: Int, height: Int) = LyricItemBounds(index, top, height)

    // ─── What is being sung ──────────────────────────────────────

    @Test
    fun lineSyncedHoldsUntilTheNextStamp() {
        val lines = listOf(
            LyricLine(startMs = 0, text = "A"),
            LyricLine(startMs = 10_000, text = "B"),
            LyricLine(startMs = 20_000, text = "C")
        )

        assertEquals(ActiveLyricGroup(0, 0), activeLyricGroup(lines, 9_999))
        // The handover lands on the stamp: A ends where B begins.
        assertEquals(ActiveLyricGroup(1, 1), activeLyricGroup(lines, 10_000))
        assertEquals(ActiveLyricGroup(1, 1), activeLyricGroup(lines, 19_999))
    }

    @Test
    fun overlappingVocalsAreOneGroup() {
        val lines = listOf(
            LyricLine(startMs = 50_000, text = "A", endMs = 55_000),
            LyricLine(startMs = 52_000, text = "B", endMs = 56_000)
        )

        assertEquals(ActiveLyricGroup(0, 0), activeLyricGroup(lines, 51_000))
        assertEquals(ActiveLyricGroup(0, 1), activeLyricGroup(lines, 53_000))
        // A has finished; B carries on alone.
        assertEquals(ActiveLyricGroup(1, 1), activeLyricGroup(lines, 55_500))
    }

    @Test
    fun heldWordRunsIntoTheNextLine() {
        val lines = listOf(
            LyricLine(startMs = 0, text = "a", words = listOf(LyricWord(0, 12_000, "a"))),
            LyricLine(startMs = 10_000, text = "b", endMs = 14_000)
        )

        // The held note is still being sung over the line that starts beneath it.
        assertEquals(ActiveLyricGroup(0, 1), activeLyricGroup(lines, 11_000))
    }

    @Test
    fun aPauseKeepsTheLineJustSungCurrent() {
        val lines = listOf(
            LyricLine(startMs = 50_000, text = "A", endMs = 55_000),
            LyricLine(startMs = 60_000, text = "B", endMs = 65_000)
        )

        // Nothing is mid-line at 57s; the page stays on the line just sung.
        assertEquals(ActiveLyricGroup(0, 0), activeLyricGroup(lines, 57_000))
        assertEquals(ActiveLyricGroup(1, 1), activeLyricGroup(lines, 60_000))
    }

    @Test
    fun beforeTheFirstLineNothingIsActive() {
        val lines = listOf(LyricLine(startMs = 50_000, text = "A"))

        assertEquals(ActiveLyricGroup.EMPTY, activeLyricGroup(lines, 1_000))
        assertEquals(ActiveLyricGroup.EMPTY, activeLyricGroup(emptyList(), 1_000))
    }

    @Test
    fun distanceToNearestLineOfTheGroup() {
        val group = ActiveLyricGroup(3, 5)

        assertEquals(0, group.distanceTo(3))
        assertEquals(0, group.distanceTo(4))
        assertEquals(0, group.distanceTo(5))
        assertEquals(1, group.distanceTo(2))
        assertEquals(1, group.distanceTo(6))
        assertEquals(Int.MAX_VALUE, ActiveLyricGroup.EMPTY.distanceTo(0))
    }

    // ─── When the page moves ─────────────────────────────────────

    @Test
    fun aGroupThatFitsDoesNotMoveThePage() {
        val target = decideLyricScroll(
            groupFirst = 3,
            groupLast = 4,
            visible = listOf(row(3, 300, 80), row(4, 400, 80)),
            viewportHeight = viewportHeight
        )

        assertEquals(LyricScrollTarget.None, target)
    }

    @Test
    fun aNewLineAlreadyOnScreenDoesNotMoveThePage() {
        // The handover from line 3 to line 4 with line 4 comfortably visible.
        val target = decideLyricScroll(
            groupFirst = 4,
            groupLast = 4,
            visible = listOf(row(3, 420, 80), row(4, 520, 80), row(5, 620, 80)),
            viewportHeight = viewportHeight
        )

        assertEquals(LyricScrollTarget.None, target)
    }

    @Test
    fun aGroupPastTheBottomIsRevealedJustEnough() {
        val target = decideLyricScroll(
            groupFirst = 3,
            groupLast = 4,
            visible = listOf(row(3, 600, 80), row(4, 760, 80)),
            viewportHeight = viewportHeight
        )

        // Line 4's bottom is put on the safe bottom edge — 820 - 80.
        assertEquals(LyricScrollTarget.Item(index = 4, topFromViewport = 740), target)
    }

    @Test
    fun aGroupPastTheTopIsEasedBackDown() {
        val target = decideLyricScroll(
            groupFirst = 3,
            groupLast = 4,
            visible = listOf(row(3, 100, 80), row(4, 180, 80)),
            viewportHeight = viewportHeight
        )

        assertEquals(LyricScrollTarget.Item(index = 3, topFromViewport = 150), target)
    }

    @Test
    fun aGroupScrolledOffTheTopIsBroughtBack() {
        val target = decideLyricScroll(
            groupFirst = 2,
            groupLast = 4,
            visible = listOf(row(3, 120, 80), row(4, 200, 80)),
            viewportHeight = viewportHeight
        )

        assertEquals(LyricScrollTarget.Item(index = 2, topFromViewport = 150), target)
    }

    @Test
    fun aGroupBelowTheViewportIsBroughtIntoIt() {
        val target = decideLyricScroll(
            groupFirst = 20,
            groupLast = 21,
            visible = listOf(row(3, 300, 80), row(4, 400, 80)),
            viewportHeight = viewportHeight
        )

        assertEquals(LyricScrollTarget.Item(index = 20, topFromViewport = 150), target)
    }

    @Test
    fun aTailBelowTheViewportIsRevealedFromTheMeasuredRows() {
        val target = decideLyricScroll(
            groupFirst = 3,
            groupLast = 9,
            visible = listOf(row(3, 400, 110), row(4, 500, 80)),
            viewportHeight = viewportHeight
        )

        // The lowest row seen (80px) stands in for the off-screen line's height.
        assertEquals(LyricScrollTarget.Item(index = 9, topFromViewport = 740), target)
    }

    @Test
    fun aGroupTallerThanTheBandHoldsItsHead() {
        val target = decideLyricScroll(
            groupFirst = 3,
            groupLast = 12,
            visible = listOf(row(3, 200, 80), row(12, 900, 80)),
            viewportHeight = viewportHeight
        )

        assertEquals(LyricScrollTarget.Item(index = 3, topFromViewport = 150), target)
    }

    @Test
    fun anOversizedGroupAlreadyHeldAtTheTopIsLeftAlone() {
        val target = decideLyricScroll(
            groupFirst = 3,
            groupLast = 12,
            visible = listOf(row(3, 150, 80), row(12, 900, 80)),
            viewportHeight = viewportHeight
        )

        assertEquals(LyricScrollTarget.None, target)
    }

    @Test
    fun nothingToFollowOrNothingMeasuredLeavesThePageAlone() {
        assertEquals(
            LyricScrollTarget.None,
            decideLyricScroll(-1, -1, listOf(row(0, 100, 80)), viewportHeight)
        )
        assertEquals(
            LyricScrollTarget.None,
            decideLyricScroll(0, 0, emptyList(), viewportHeight)
        )
        assertEquals(
            LyricScrollTarget.None,
            decideLyricScroll(0, 0, listOf(row(0, 100, 80)), viewportHeight = 0)
        )
    }
}
