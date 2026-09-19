package com.gratia.music.ui.lyrics

import com.gratia.music.lyrics.LyricLine
import kotlin.math.roundToInt

// =============================================================================
// FOLLOWING THE SINGER
// =============================================================================
//
// The lyrics page does not advance by lines. It follows what is *being sung* —
// every line whose timing straddles the playhead — and only moves when that
// content would otherwise run out of room. A line ending, or a new one
// starting, is not on its own a reason to scroll: the page asks whether what is
// currently being sung still sits comfortably in the viewport, and moves only
// if the answer is no.
//
// That split matters for overlap. A song where a second voice enters over a
// line that is still going has two lines being sung at once; they are one
// group, they stay on screen together, and the older of the two ending does not
// tug the page away from the one still holding.

/**
 * The stretch of the lyrics list being sung at one moment.
 *
 * [first] and [last] bracket the group, so a line sandwiched between two sung
 * lines travels with them. Both are `-1` when nothing has been reached yet —
 * the run-in before the first line.
 */
internal data class ActiveLyricGroup(val first: Int, val last: Int) {

    val isEmpty: Boolean get() = first < 0

    operator fun contains(index: Int): Boolean = !isEmpty && index in first..last

    /**
     * How far [index] sits from the group, in lines; 0 for a line inside it.
     *
     * Feeds the brightness ladder, so two overlapping vocals read as one bright
     * block rather than the newer line being bright and the line it answers
     * dimming one step at a time as the group grows.
     */
    fun distanceTo(index: Int): Int = when {
        isEmpty -> Int.MAX_VALUE
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
 * Where one lyric row was measured to sit, relative to the top of the viewport.
 *
 * [top] is negative for a row scrolled past the top and beyond the viewport
 * height for one still below it.
 */
internal class LyricItemBounds(val index: Int, val top: Int, val height: Int) {
    val bottom: Int get() = top + height
}

/** What the auto-scroll wants to do with the list. */
internal sealed interface LyricScrollTarget {

    /** The group already sits comfortably; leave the page alone. */
    object None : LyricScrollTarget

    /**
     * Put [index]'s top [topFromViewport] pixels below the top of the viewport.
     * The caller converts this to a list scroll offset.
     */
    data class Item(val index: Int, val topFromViewport: Int) : LyricScrollTarget
}

/**
 * The band of the lyrics viewport the group being sung is kept inside, as a
 * share of its measured height.
 *
 * Taken from the measured viewport rather than a fixed pixel value, because the
 * lyrics area is not the screen: it is whatever is left between the header and
 * the transport controls, and that differs by device. The band keeps the
 * content clear of the faded top and bottom edges, so a followed line is never
 * parked under the fade.
 */
internal const val LYRIC_SAFE_TOP_FRACTION = 0.15f
internal const val LYRIC_SAFE_BOTTOM_FRACTION = 0.82f

/**
 * The smallest move that puts the group being sung back inside the safe band —
 * or [LyricScrollTarget.None] when it never left it.
 *
 * This is the whole of the scroll policy. It knows nothing about line indices
 * changing; it is handed a group and the rows currently on screen and answers
 * only whether that group fits. A newly begun overlapping vocal that is already
 * visible therefore moves nothing, and a page where one line hands over to the
 * next with room to spare stays exactly where it is.
 */
internal fun decideLyricScroll(
    groupFirst: Int,
    groupLast: Int,
    visible: List<LyricItemBounds>,
    viewportHeight: Int
): LyricScrollTarget {
    if (groupFirst < 0 || groupLast < groupFirst) return LyricScrollTarget.None
    if (viewportHeight <= 0 || visible.isEmpty()) return LyricScrollTarget.None

    val safeTop = (viewportHeight * LYRIC_SAFE_TOP_FRACTION).roundToInt()
    val safeBottom = (viewportHeight * LYRIC_SAFE_BOTTOM_FRACTION).roundToInt()
    if (safeBottom <= safeTop) return LyricScrollTarget.None

    val head = visible.firstOrNull { it.index == groupFirst }
    val tail = visible.firstOrNull { it.index == groupLast }

    // The head has scrolled up out of the viewport — a large jump, or a page
    // left behind by hand. Bring the group back down so its start can be read.
    if (head == null) return LyricScrollTarget.Item(groupFirst, safeTop)

    // The tail runs off the bottom: reveal it, resting it just inside the safe
    // band. Its height cannot be measured while it is off screen, so the lowest
    // row on screen stands in for it.
    if (tail == null) {
        val estimatedHeight = visible.last().height
        return LyricScrollTarget.Item(groupLast, (safeBottom - estimatedHeight).coerceAtLeast(0))
    }

    val groupTop = head.top
    val groupBottom = tail.bottom
    val safeHeight = safeBottom - safeTop

    return when {
        // Taller than the band. Hold its head at the safe top and follow from
        // there, rather than fighting to fit what cannot fit: a new overlapping
        // line joining an already-tall group then changes nothing on screen.
        groupBottom - groupTop > safeHeight ->
            if (groupTop == safeTop) LyricScrollTarget.None
            else LyricScrollTarget.Item(groupFirst, safeTop)

        // Reached past the top of the band: ease it back down.
        groupTop < safeTop -> LyricScrollTarget.Item(groupFirst, safeTop)

        // Reached past the bottom: move just far enough to reveal the newest
        // line, no further.
        groupBottom > safeBottom ->
            LyricScrollTarget.Item(groupLast, (safeBottom - tail.height).coerceAtLeast(0))

        // Comfortably inside. This is the case that keeps a line handover with
        // room to spare from touching the page at all.
        else -> LyricScrollTarget.None
    }
}
