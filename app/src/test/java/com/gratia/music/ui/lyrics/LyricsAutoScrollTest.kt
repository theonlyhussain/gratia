package com.gratia.music.ui.lyrics

import com.gratia.music.lyrics.LyricLine
import com.gratia.music.lyrics.LyricWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

/**
 * Unit tests for the anchored lyrics auto-scroll.
 *
 * Two things are worth pinning down: what counts as "being sung" — including
 * overlapping vocals and the run-in before the first line — and where the row
 * being sung is held, so that the page follows the voice upward through a song
 * instead of drifting down it, first and last lines included.
 */
class LyricsAutoScrollTest {

    /** A 1000px lyrics viewport, so the anchor sits 220px down it. */
    private val viewportHeight = 1000

    /**
     * The list's leading content padding, which the view sets to the anchor
     * itself: the position the row being sung is held at, and the space above
     * the first row of the list.
     */
    private val anchorPadding = (viewportHeight * LYRIC_ANCHOR_FRACTION).roundToInt()

    private fun row(index: Int, top: Int, height: Int) = LyricItemBounds(index, top, height)

    /**
     * A page of equal rows, scrolled [scroll] pixels into the document: what a
     * real list would report, in its own layout space, where 0 is the top of the
     * content area and the viewport runs from the leading padding above that to
     * the viewport's own height below it.
     */
    private fun visibleRows(scroll: Int, count: Int, rowHeight: Int = 100): List<LyricItemBounds> =
        (0 until count)
            .map { row(it, it * rowHeight - scroll, rowHeight) }
            .filter { it.bottom > -anchorPadding && it.top < viewportHeight - anchorPadding }

    /** Where the page ends up once [target] is taken. */
    private fun applyMove(scroll: Int, target: LyricScrollTarget, rowHeight: Int = 100): Int =
        when (target) {
            is LyricScrollTarget.Item -> target.index * rowHeight - target.anchorOffset
            LyricScrollTarget.None -> scroll
        }

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
    }

    @Test
    fun withNothingReachedTheLadderStartsAtTheFirstLine() {
        val group = ActiveLyricGroup.EMPTY

        assertEquals(0, group.distanceTo(0))
        assertEquals(1, group.distanceTo(1))
        assertEquals(4, group.distanceTo(4))
    }

    // ─── A long intro ────────────────────────────────────────────
    //
    // The case a line-synced song makes common and nothing else does: the first
    // stamp is half a minute in, so for thirty seconds no line has started at
    // all. The page must not follow — there is nothing being sung to keep in the
    // band — and it must not flatten to the far end of the ladder either, which
    // is what measuring the distance from nothing used to do.

    /**
     * The lines a line-synced song arrives as: each stamped to the next, and no
     * word timings anywhere. `withInstrumentalGaps` adds no intro row here — it
     * runs on the word-synced paths only — so there is no row at all until the
     * first stamp.
     */
    private fun lineSyncedIntro(): List<LyricLine> = listOf(
        LyricLine(startMs = 30_000, text = "A", endMs = 35_000),
        LyricLine(startMs = 35_000, text = "B", endMs = 40_000),
        LyricLine(startMs = 40_000, text = "C", endMs = 45_000),
        LyricLine(startMs = 45_000, text = "D", endMs = 50_000),
        LyricLine(startMs = 50_000, text = "E")
    )

    @Test
    fun aLongIntroIsNotFollowed() {
        val lines = lineSyncedIntro()

        assertEquals(ActiveLyricGroup.EMPTY, activeLyricGroup(lines, 0))
        assertEquals(ActiveLyricGroup.EMPTY, activeLyricGroup(lines, 29_999))
        // The first stamp lands: now there is something to follow.
        assertEquals(ActiveLyricGroup(0, 0), activeLyricGroup(lines, 30_000))

        // An empty group asks the page for nothing, whatever is on screen.
        assertEquals(
            LyricScrollTarget.None,
            decideLyricScroll(
                anchorIndex = -1,
                visible = listOf(row(0, 0, 201), row(1, 201, 201)),
                viewportHeight = viewportHeight,
                beforeContentPadding = anchorPadding
            )
        )
    }

    @Test
    fun aLongIntroLeavesThePageReadableRatherThanOnItsFarRung() {
        val intro = activeLyricGroup(lineSyncedIntro(), 29_999)

        assertEquals(0, intro.distanceTo(0))
        assertEquals(1, intro.distanceTo(1))
        assertEquals(3, intro.distanceTo(3))
    }

    @Test
    fun aFirstLineLeftBehindByHandIsBroughtBackToTheAnchor() {
        // Read by hand through the intro, the page is left further down the
        // song; at the first stamp the follow pulls line 0 back to the anchor —
        // which is the first row's own resting place, so the page moves no
        // further than it has to.
        val target = decideLyricScroll(
            anchorIndex = 0,
            visible = listOf(row(0, 260, 201), row(1, 461, 201)),
            viewportHeight = viewportHeight,
            beforeContentPadding = anchorPadding
        )

        assertEquals(LyricScrollTarget.Item(index = 0, anchorOffset = 0), target)
    }

    @Test
    fun aMarkedBreakIsCurrentForTheWholeOfIt() {
        // What a line-synced file with a bare stamp mid-song looks like once
        // parsed: the break is a row of its own, stamped to the line it waits
        // for. Being the current line for that whole span is what makes the
        // break count itself out — the dots are driven off the group, not off a
        // line index changing.
        val lines = listOf(
            LyricLine(startMs = 20_000, text = "Line two", endMs = 135_000),
            LyricLine(startMs = 135_000, text = "", endMs = 150_000),
            LyricLine(startMs = 150_000, text = "Line three")
        )

        assertEquals(ActiveLyricGroup(0, 0), activeLyricGroup(lines, 134_999))
        assertEquals(ActiveLyricGroup(1, 1), activeLyricGroup(lines, 135_000))
        assertEquals(ActiveLyricGroup(1, 1), activeLyricGroup(lines, 149_999))
        assertEquals(ActiveLyricGroup(2, 2), activeLyricGroup(lines, 150_000))
    }

    // ─── The reading anchor ──────────────────────────────────────
    //
    // The page holds the row being sung at one fixed place in the upper part of
    // the lyrics area and lets the document travel upward under it. The anchor is
    // the same position for every row, the first and the last included, so the
    // move a handover asks for is always the same small one — and the row that
    // has just been sung leaves through the top of the viewport rather than the
    // page walking down the screen.

    @Test
    fun theAnchorSitsInTheUpperPartOfTheLyricsViewport() {
        // Just under a quarter of the way down the lyrics area — the box between
        // the track header and the transport controls, not the screen.
        assertEquals(220, lyricAnchorOffset(viewportHeight, beforeContentPadding = 0))
    }

    @Test
    fun theAnchorIsTheListsOwnLeadingScrollSpace() {
        // The view gives the list a leading content padding equal to the anchor,
        // so the row being sung is held at offset 0 — the top of the content
        // area, which that padding draws exactly at the anchor. Nothing has to
        // carry a pixel offset around, and no row needs a negative or clamped
        // scroll position to be read at it.
        assertEquals(0, lyricAnchorOffset(viewportHeight, beforeContentPadding = anchorPadding))
    }

    @Test
    fun noMoveIsAskedForWhileTheSameRowIsAtTheAnchor() {
        // The page as it stands once line 3 has been followed: asking again — a
        // thousand ticks later, or because a browse has just ended — must not
        // launch a second scroll behind the first.
        val page = visibleRows(scroll = 300, count = 40)

        assertEquals(
            LyricScrollTarget.None,
            decideLyricScroll(3, page, viewportHeight, anchorPadding)
        )
    }

    @Test
    fun aRowLeftAwayFromTheAnchorIsBroughtBackToIt() {
        // Read by hand, then left with line 3 halfway up the viewport.
        val page = listOf(row(3, -40, 100), row(4, 60, 100))

        assertEquals(
            LyricScrollTarget.Item(index = 3, anchorOffset = 0),
            decideLyricScroll(3, page, viewportHeight, anchorPadding)
        )
    }

    @Test
    fun aRowThatIsNotOnScreenIsAnsweredWithTheSameAnchor() {
        // A seek: the line being sung is nowhere near the page in front of it,
        // and the answer is still the anchor rather than a scroll through
        // everything in between. Which row happens to be first on screen is
        // never the thing being followed.
        val page = visibleRows(scroll = 0, count = 40)

        assertEquals(
            LyricScrollTarget.Item(index = 25, anchorOffset = 0),
            decideLyricScroll(25, page, viewportHeight, anchorPadding)
        )
    }

    @Test
    fun theFirstRowRestsAtTheAnchorWithoutBeingMoved() {
        // At its start the list draws row 0 at the top of the content area, which
        // the leading padding puts exactly at the anchor: the line the singing
        // will begin on is already waiting where it will be read from.
        val page = visibleRows(scroll = 0, count = 40)

        assertEquals(0, page.first().top)
        assertEquals(
            LyricScrollTarget.None,
            decideLyricScroll(0, page, viewportHeight, anchorPadding)
        )
    }

    @Test
    fun theLastRowCanReachTheAnchorFromTheTrailingSpace() {
        val rowHeight = 100
        val trailing = viewportHeight - anchorPadding
        val anchorOnScreen = (viewportHeight * LYRIC_ANCHOR_FRACTION).roundToInt()

        assertTrue(
            "the last line cannot be read at the anchor",
            lastRowTopAtEnd(viewportHeight, trailing, rowHeight) <= anchorOnScreen
        )
        // The space below the last line is what does it. Without it the end of a
        // song could only be read from the foot of the page — a viewport's worth
        // of drift away from every other line, which is the jump the anchor
        // exists to remove.
        assertTrue(
            lastRowTopAtEnd(viewportHeight, afterContentPadding = 0, rowHeight = rowHeight) >
                anchorOnScreen
        )
    }

    // ─── Handovers ───────────────────────────────────────────────

    @Test
    fun eachHandoverLeavesTheNewRowAtTheAnchorAndMovesTheOldOneUpward() {
        val rowHeight = 100
        val count = 40
        var scroll = 0

        // The intro: the page rests on the first row, already at the anchor.
        assertEquals(
            LyricScrollTarget.None,
            decideLyricScroll(
                0,
                visibleRows(scroll, count, rowHeight),
                viewportHeight,
                anchorPadding
            )
        )

        for (index in 1 until 8) {
            val target = decideLyricScroll(
                anchorIndex = index,
                visible = visibleRows(scroll, count, rowHeight),
                viewportHeight = viewportHeight,
                beforeContentPadding = anchorPadding
            )
            val moved = applyMove(scroll, target, rowHeight)
            val page = visibleRows(moved, count, rowHeight)

            // One row, every time: the page follows upward and never jumps.
            assertEquals("line $index moved the page by the wrong amount", scroll + rowHeight, moved)
            assertEquals(
                "line $index is not at the anchor",
                0,
                page.first { it.index == index }.top
            )
            assertEquals(
                "line ${index - 1} did not leave the reading position",
                -rowHeight,
                page.first { it.index == index - 1 }.top
            )
            scroll = moved
        }
    }

    @Test
    fun aWrappedRowIsAnchoredByItsOwnTopEdge() {
        // Rows are not all the same height once a line wraps or carries a
        // translation. The anchor is the row's top edge, so a taller row pushes
        // the rows beneath it further down without moving its own top: the
        // surrounding lines breathe, the reading position does not.
        val page = listOf(row(1, 0, 220), row(2, 220, 100), row(3, 320, 100))

        assertEquals(
            LyricScrollTarget.None,
            decideLyricScroll(1, page, viewportHeight, anchorPadding)
        )
        assertEquals(
            LyricScrollTarget.Item(index = 3, anchorOffset = 0),
            decideLyricScroll(3, page, viewportHeight, anchorPadding)
        )
    }

    @Test
    fun aSeekLandsAtOnceWhileAHandoverIsAnimated() {
        // A row already on screen is a handover — one row's worth of movement —
        // and reads as the page moving.
        assertTrue(animateLyricScroll(targetOnScreen = true, reduceAnimation = false))
        // A row that is not is a seek, and lands where the song is: animating
        // across it would show every skipped line sliding past as though it were
        // being sung.
        assertFalse(animateLyricScroll(targetOnScreen = false, reduceAnimation = false))
        // And with the animation reduction on, nothing moves smoothly at all.
        assertFalse(animateLyricScroll(targetOnScreen = true, reduceAnimation = true))
    }

    @Test
    fun wordProgressInsideALineCannotChangeTheRowBeingFollowed() {
        // The question the scroll is asked is which row is being sung, and the
        // row being sung does not change with the word: a whole line's sweep is
        // one anchor. A word tick therefore has no way to launch a scroll, how
        // ever many of them pass.
        val lines = listOf(
            LyricLine(
                startMs = 0,
                text = "a b",
                words = listOf(LyricWord(0, 500, "a"), LyricWord(500, 1_000, "b"))
            ),
            LyricLine(startMs = 5_000, text = "c", endMs = 6_000)
        )

        assertEquals(ActiveLyricGroup(0, 0), activeLyricGroup(lines, 100))
        assertEquals(ActiveLyricGroup(0, 0), activeLyricGroup(lines, 400))
        assertEquals(ActiveLyricGroup(0, 0), activeLyricGroup(lines, 999))
    }

    @Test
    fun aSecondVoiceJoiningTheRowBeingSungCannotMoveThePage() {
        // The page follows the oldest row of the group, so a backing vocal
        // entering over the line already being sung leaves it where it is: the
        // group changes, the row being followed does not.
        val lines = listOf(
            LyricLine(startMs = 0, text = "lead", endMs = 20_000),
            LyricLine(startMs = 8_000, text = "(ooh)", endMs = 12_000)
        )

        assertEquals(ActiveLyricGroup(0, 0), activeLyricGroup(lines, 7_999))
        assertEquals(ActiveLyricGroup(0, 1), activeLyricGroup(lines, 8_000))
        // The second voice has finished, and the lead is still being sung: the
        // group shrinks, and the row being followed does not move.
        assertEquals(ActiveLyricGroup(0, 0), activeLyricGroup(lines, 13_000))
        // The lead's own end has passed, and only now does the page hand over to
        // the row that began beneath it.
        assertEquals(ActiveLyricGroup(1, 1), activeLyricGroup(lines, 20_000))
    }

    @Test
    fun nothingToFollowOrNothingMeasuredLeavesThePageAlone() {
        assertEquals(
            LyricScrollTarget.None,
            decideLyricScroll(-1, listOf(row(0, 100, 80)), viewportHeight, anchorPadding)
        )
        assertEquals(
            LyricScrollTarget.None,
            decideLyricScroll(0, emptyList(), viewportHeight, anchorPadding)
        )
    }
}
