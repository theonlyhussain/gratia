package com.gratia.music.ui.lyrics

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for how bright a lyric row is allowed to be.
 *
 * The page is read while it plays, so the rows around the line being sung are
 * the page — not decoration. These pin the floors that keep them readable,
 * because the failure mode (a far rung held so low, with a blur over it, that
 * most of a verse looked absent) is invisible in a screenshot and easy to
 * reintroduce while tuning the emphasis.
 */
class LyricsReadabilityTest {

    /** Every rung of both ladders, out to well past the bottom of the list. */
    private val ladder = (0 until 8).flatMap { distance ->
        listOf(
            LyricFocusSide.Active to distance,
            LyricFocusSide.Sung to distance,
            LyricFocusSide.Upcoming to distance
        )
    }

    @Test
    fun theRowBeingSungIsTheBrightestThingOnThePage() {
        assertEquals(1f, lyricRowAlpha(LyricFocusSide.Active, 0), 0f)

        // From one row out, on either side. A row inside the group is Active
        // whatever its distance — two overlapping vocals read as two bright
        // lines — so the other two ladders only ever take effect past the group,
        // and their own head rung is the one that never renders.
        ladder.filterNot { (side, _) -> side == LyricFocusSide.Active }
            .filterNot { (_, distance) -> distance == 0 }
            .forEach { (side, distance) ->
                val alpha = lyricRowAlpha(side, distance)
                assertTrue(
                    "$side at $distance is as bright as the row being sung",
                    alpha < 1f
                )
            }
    }

    @Test
    fun rowsEitherSideOfTheSungLineStayReadable() {
        listOf(LyricFocusSide.Sung, LyricFocusSide.Upcoming).forEach { side ->
            val alpha = lyricRowAlpha(side, 1)
            assertTrue("$side at distance 1 is too faint: $alpha", alpha >= 0.6f)
            assertTrue("$side at distance 1 is too strong: $alpha", alpha <= 0.75f)
        }
    }

    @Test
    fun noRowStillOnScreenIsFadedAway() {
        // The old ladders ran down to 0.16, which over dark artwork is a row
        // that is no longer read as being there at all. Every rung is now a
        // floor a reader looking ahead or back can still read.
        ladder.forEach { (side, distance) ->
            val alpha = lyricRowAlpha(side, distance)
            assertTrue("$side at $distance is effectively invisible: $alpha", alpha >= 0.3f)
            assertTrue("$side at $distance is out of range: $alpha", alpha <= 1f)
        }
    }

    @Test
    fun theFalloffFromTheSungLineIsModerate() {
        // A step or two of brightness between neighbouring rows, never a cliff:
        // the point of the ladder is emphasis, not erasure. Measured from one
        // row out, since the step onto the first neighbouring row is the
        // emphasis itself rather than part of the falloff.
        listOf(LyricFocusSide.Sung, LyricFocusSide.Upcoming).forEach { side ->
            var previous = lyricRowAlpha(side, 1)
            (2 until 8).forEach { distance ->
                val alpha = lyricRowAlpha(side, distance)
                assertTrue(
                    "$side gets brighter further from the sung line at $distance",
                    alpha <= previous + 0.001f
                )
                assertTrue(
                    "$side drops too far between ${distance - 1} and $distance",
                    previous - alpha <= 0.15f
                )
                previous = alpha
            }
        }
    }

    @Test
    fun theSungLineAndItsNeighboursAreNeverBlurred() {
        // These are the rows people read along with, and the word being sung has
        // to stay the crispest thing on the page.
        (0..2).forEach { distance ->
            assertEquals(
                "the row at distance $distance is blurred",
                0.dp,
                LINE_FALLOFF_BLUR[distance.coerceAtMost(LINE_FALLOFF_BLUR.lastIndex)]
            )
        }
    }

    @Test
    fun theBlurFallingAwayFromTheSungLineStaysSubtle() {
        // Enough to sit behind the reading line, never enough to eat a letter of
        // a row that is still inside the viewport.
        val faintest = LINE_FALLOFF_BLUR.max()
        assertTrue("the unfocused blur is too heavy: $faintest", faintest <= 2.dp)
    }

    @Test
    fun theUnsungHalfOfTheSungLineIsStillReadable() {
        // The words ahead of the sweep arrive from somewhere dimmer — but they
        // are the line being read, so they stay inside reading range.
        val alpha = UNSUNG_ALPHA
        assertTrue("the unsung words are too faint: $alpha", alpha >= 0.5f)
        assertTrue("the unsung words leave the sweep no contrast: $alpha", alpha <= 0.75f)
    }

    @Test
    fun theBloomCannotSwampTheWordItBelongsTo() {
        // Drawn from the same white as the text: at full strength it stops
        // reading as light off the word and starts reading as a second, blurred
        // copy of it.
        assertTrue("the bloom is too strong: $GLOW_ALPHA", GLOW_ALPHA <= 0.5f)
        assertTrue("the bloom spreads too far: $GLOW_RADIUS", GLOW_RADIUS <= 6.dp)
    }
}
