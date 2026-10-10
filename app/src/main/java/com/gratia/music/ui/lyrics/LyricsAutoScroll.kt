package com.gratia.music.ui.lyrics

import com.gratia.music.lyrics.LyricLine
import kotlin.math.abs
import kotlin.math.roundToInt

// =============================================================================
// FOLLOWING THE SINGER
// =============================================================================
//
// The lyrics page is a document that travels upward under the line being sung.
// That line is held at a fixed reading position in the upper part of the page —
// the anchor — and everything else moves past it: the lines just sung leave
// through the top of the viewport, and the lines still to come fill the space
// beneath. That is the whole of the motion, and it is why the page never walks
// down the screen as a song goes on.
//
// What is being *sung* is not one line but a group of them: every line whose
// timing straddles the playhead. A song where a second voice enters over a line
// that is still going has two lines being sung at once, and they stay lit
// together. The page follows the oldest of that group — the row the voice
// started on — so a backing vocal entering over a line does not tug the page
// away from it, while the handover when the lead line ends lands on the voice
// still singing.

/**
 * Where a row sits relative to what is being sung.
 *
 * The page is told apart this way rather than by a single brightness ladder,
 * because the two sides are read differently. A row already sung has been read
 * and is where an eye that lost its place goes back to, so it wants to stay
 * legible; a row not yet reached has not been read and wants to be held well
 * back, so that nothing near the playing line shares its brightness.
 */
internal enum class LyricFocusSide { Active, Sung, Upcoming }

/**
 * The stretch of the lyrics list being sung at one moment.
 *
 * [first] and [last] bracket the group, so a line sandwiched between two sung
 * lines travels with them. Both are `-1` when nothing has been reached yet —
 * the run-in before the first line.
 */
internal data class ActiveLyricGroup(val first: Int, val last: Int) {

    /**
     * Which side of the singing [index] is on.
     *
     * Before anything has been reached — the intro, where [isEmpty] — every row
     * is still ahead of the voice, so the whole page reads as upcoming and the
     * anchor row at distance 0 is the one the singing will start on.
     */
    fun focusSide(index: Int): LyricFocusSide = when {
        contains(index) -> LyricFocusSide.Active
        isEmpty -> LyricFocusSide.Upcoming
        index < first -> LyricFocusSide.Sung
        else -> LyricFocusSide.Upcoming
    }

    val isEmpty: Boolean get() = first < 0

    operator fun contains(index: Int): Boolean = !isEmpty && index in first..last

    /**
     * How far [index] sits from the group, in lines; 0 for a line inside it.
     *
     * Feeds the brightness ladder, so two overlapping vocals read as one bright
     * block rather than the newer line being bright and the line it answers
     * dimming one step at a time as the group grows.
     *
     * Nothing reached yet — the run-in before the first line, which is the intro
     * of every song and the better part of a minute of some — has no group to
     * measure against, so the ladder is anchored on the line the singing starts
     * on and runs down the list from there. Measuring from nothing instead put
     * every line on the far rung at once: the whole page arrived dimmed and
     * blurred, and cleared only when the first stamp finally landed on it.
     * Nothing is being *sung* in that state — [contains] is still false for every
     * line — so this is the ladder's anchor and no more.
     */
    fun distanceTo(index: Int): Int = when {
        isEmpty -> index.coerceAtLeast(0)
        index < first -> first - index
        index > last -> index - last
        else -> 0
    }

    companion object {
        val EMPTY = ActiveLyricGroup(-1, -1)
    }
}

/**
 * When the line at [index] stops being sung.
 *
 * A provider that states an end — word timings, or the line's own
 * [LyricLine.endMs] — is taken at its word. That is what lets a held note or an
 * answering vocal run into the line beneath it instead of being cut off at its
 * neighbour's stamp, which is what makes overlapping vocals visible as
 * overlapping.
 *
 * Where nothing states an end, the next line's stamp stands in: the line is
 * current until the singing moves on. The final line, with nothing after it,
 * runs indefinitely.
 */
internal fun lyricPlaybackEndMs(lines: List<LyricLine>, index: Int): Long {
    val line = lines[index]
    if (line.hasKnownEnd) return line.computedEndMs
    return lines.getOrNull(index + 1)?.startMs ?: Long.MAX_VALUE
}

/**
 * Every line being sung at [positionMs], as one group.
 *
 * Lines run through [lyricPlaybackEndMs] rather than being cut at the next
 * stamp, so two vocals that overlap are both found. When nothing is mid-line —
 * a pause, an unmarked instrumental — the line that has just been sung stays
 * current until the next one begins, so the page doesn't let go of a line and
 * pick it back up a beat later.
 */
internal fun activeLyricGroup(lines: List<LyricLine>, positionMs: Long): ActiveLyricGroup {
    var first = -1
    var last = -1
    lines.forEachIndexed { index, line ->
        val start = line.startMs
        if (positionMs >= start && positionMs < lyricPlaybackEndMs(lines, index)) {
            if (first < 0) first = index
            last = index
        }
    }
    if (first >= 0) return ActiveLyricGroup(first, last)

    val current = lines.indexOfLast { it.startMs <= positionMs }
    return if (current < 0) ActiveLyricGroup.EMPTY else ActiveLyricGroup(current, current)
}

// =============================================================================
// THE SCROLL DECISION
// =============================================================================

/**
 * Where one lyric row was measured to sit, in the list's own layout space.
 *
 * That space begins at the top of the content area — below the list's leading
 * content padding — so a row at 0 is drawn exactly `beforeContentPadding` pixels
 * down the lyrics viewport, and the rows a real list reports as visible are the
 * ones between `-beforeContentPadding` and the viewport height less that. This
 * is the space `LazyListItemInfo.offset` reports in, and the space
 * `LazyListState.scrollToItem`'s offset argument sets, negated: a row asked to
 * land at `-offset` reports `offset` afterwards.
 */
internal class LyricItemBounds(val index: Int, val top: Int, val height: Int) {
    val bottom: Int get() = top + height
}

/** What the auto-scroll wants to do with the list. */
internal sealed interface LyricScrollTarget {

    /** The row being sung already sits at the anchor; leave the page alone. */
    object None : LyricScrollTarget

    /**
     * Put [index]'s top at [anchorOffset] in the list's own layout space. The
     * caller negates it into a list scroll offset.
     */
    data class Item(val index: Int, val anchorOffset: Int) : LyricScrollTarget
}

/**
 * How far down the lyrics viewport the row being sung is held, as a share of
 * that viewport's own height.
 *
 * Measured against the lyrics area and not the screen: the area is whatever is
 * left between the track header above it, the transport controls below it and
 * the system insets around them, and all of those are outside this list, so its
 * own height is already the usable measurement.
 *
 * Just under a quarter of the way down it. The line being sung is read from the
 * upper part of the page — below the header, clear of the fade — with the lines
 * still to come filling the space beneath it, which is what makes the document
 * travel upward through the viewport as the song goes on.
 */
internal const val LYRIC_ANCHOR_FRACTION = 0.22f

/**
 * How far the row being sung may sit from the anchor before the page is moved.
 *
 * Small, and only there to absorb the pixel the layout lands on. It is what
 * makes asking the same question twice — the same line, the same page — a no-op
 * rather than a fresh animation shoving the list by a pixel.
 */
internal const val LYRIC_ANCHOR_TOLERANCE_PX = 6

/**
 * The offset in the list's layout space that puts a row's top at the anchor.
 *
 * [beforeContentPadding] is the list's leading content padding in pixels, and it
 * comes off because the layout space begins *below* it. The list is given a
 * leading padding equal to the anchor itself — see [SyncedLyricsView] — so this
 * answers 0 for every row, and 0 is the one position a lazy list can always
 * reach: the anchor is enforced by the list's own layout rather than by a number
 * carried around in the scroll code, and no row, first or last, ever needs a
 * negative or a clamped scroll position to be read at it.
 */
internal fun lyricAnchorOffset(viewportHeight: Int, beforeContentPadding: Int): Int =
    (viewportHeight * LYRIC_ANCHOR_FRACTION).roundToInt() - beforeContentPadding

/**
 * Where the row being sung belongs — or [LyricScrollTarget.None] when it is
 * already there.
 *
 * This is the whole of the scroll policy. It is handed the row being sung and
 * the rows the list currently reports, and answers only where that row's top
 * goes. It knows nothing about the playback position, the word being sung,
 * which row is first on screen, or how many ticks have passed: the caller asks
 * it when the line being sung has changed, and never on a tick.
 *
 * A row already at the anchor is left alone, so an animation that has just
 * finished cannot be launched again behind itself, and a page read by hand that
 * happens to be sitting right is not nudged. A row that is not on screen at all
 * is answered with the same move — the target is still the anchor, and the list
 * works out how far away it is — which is what makes a forward seek a jump
 * rather than a scroll through every line the song skipped.
 */
internal fun decideLyricScroll(
    anchorIndex: Int,
    visible: List<LyricItemBounds>,
    viewportHeight: Int,
    beforeContentPadding: Int
): LyricScrollTarget {
    if (anchorIndex < 0 || visible.isEmpty()) return LyricScrollTarget.None

    val anchor = lyricAnchorOffset(viewportHeight, beforeContentPadding)
    val row = visible.firstOrNull { it.index == anchorIndex }
    if (row != null && abs(row.top - anchor) <= LYRIC_ANCHOR_TOLERANCE_PX) {
        return LyricScrollTarget.None
    }
    return LyricScrollTarget.Item(index = anchorIndex, anchorOffset = anchor)
}

/**
 * Whether that move should be animated, or landed at once.
 *
 * Only a row already on screen is animated: that is a line handing over to the
 * next one, one row's worth of movement, and it should read as the page moving.
 * A row that is not on screen is a seek — a scrub, a tap on a lyric further
 * down, a jump backward — and animating the page across it would show every line
 * in between sliding past as though it were being sung. Those land where the
 * song is.
 */
internal fun animateLyricScroll(targetOnScreen: Boolean, reduceAnimation: Boolean): Boolean =
    targetOnScreen && !reduceAnimation

/**
 * Where the last row's top sits, in pixels from the top of the lyrics viewport,
 * when the list is scrolled as far as it will go.
 *
 * The leading padding does not appear here: it moves the whole page down and the
 * end of the list with it, so whether the *last* line can be read at the anchor
 * is decided by the space below it and nothing else. That space is the trailing
 * content padding [SyncedLyricsView] gives the list — the viewport less the
 * anchor — which leaves the end of a song sitting exactly where the rest of it
 * was read instead of stranded at the foot of the page.
 */
internal fun lastRowTopAtEnd(
    viewportHeight: Int,
    afterContentPadding: Int,
    rowHeight: Int
): Int = viewportHeight - afterContentPadding - rowHeight
