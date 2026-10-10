package com.gratia.music.ui.lyrics

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.gratia.music.lyrics.LyricLine
import com.gratia.music.lyrics.TransientVocal

/**
 * The entrance/exit that every temporary lyric event shares.
 *
 * Extracted from the instrumental indicator ([MusicLine]) so the backing vocal
 * gets the *same* motion rather than a re-implementation of it (spec 5/23): the
 * break's dots swell in over [TRANSIENT_IN_MS] and let go over [TRANSIENT_OUT_MS]
 * on [LYRIC_EASING], and so does this. The two are now literally one animation,
 * which is the only way they stay identical when one of them is tuned.
 */
internal const val TRANSIENT_IN_MS = 400
internal const val TRANSIENT_OUT_MS = 350

/** Animate 0→1 as [visible] turns on, 1→0 as it turns off, on the shared curve. */
@Composable
internal fun rememberTransientSwell(
    visible: Boolean,
    reduceAnimation: Boolean,
    label: String,
): Float {
    val swell by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = if (reduceAnimation) {
            snap()
        } else {
            tween(
                durationMillis = if (visible) TRANSIENT_IN_MS else TRANSIENT_OUT_MS,
                easing = LYRIC_EASING,
            )
        },
        label = label,
    )
    return swell
}

/**
 * A timed backing/answering vocal, drawn as a transient overlay (spec 1–12).
 *
 * This replaces the previous treatment, which hung the backing line under the
 * lead as a *permanent* second row: it occupied height for the whole song,
 * changed the line's measured size, and sat there whether or not the second
 * voice was singing. The spec is explicit that a timed backing vocal is not a
 * lyric line — it appears inside its own window, highlights across its own words
 * while it is sung, and then gets out of the way (spec 2/8/22).
 *
 * Two rules make it safe:
 *
 *  - **It derives everything from the clock.** Visibility comes from
 *    [TransientVocal.isActive], recomputed whenever the position crosses a
 *    boundary — there is no "shown once" state, so seeking backwards re-arms it
 *    and seeking past it dismisses it (spec 16).
 *  - **It never changes layout.** The caller hands it `matchParentSize()`, so it
 *    draws inside the lead line's existing box — over the padding at its foot —
 *    and adds no row, no height and no scroll item (spec 19). Because it is an
 *    inert drawing beneath the clickable lead, it steals no gestures (spec 20).
 *
 * The fill reuses [SweptLyricsLine], so the word-level highlight is the same
 * stable, clip-based sweep the primary lyrics use — never a spring, never a
 * geometry change (spec 7). A phrase with no word timings is shown whole for its
 * line window; no per-word timing is invented (spec 12/15).
 */
@Composable
internal fun TransientLyricOverlay(
    line: LyricLine,
    clock: State<Long>,
    style: TextStyle,
    reduceAnimation: Boolean,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Start,
) {
    // Deferred: the clock ticks several times a second, but this boolean flips
    // only at the window's two edges, so the overlay recomposes at most twice
    // per backing vocal instead of on every frame.
    val active by remember(line, clock) {
        derivedStateOf { TransientVocal.isActive(line, clock.value) }
    }
    val swell = rememberTransientSwell(active, reduceAnimation, "BackingVocalSwell")

    // Nothing to draw before the window opens and once the exit has finished.
    if (!active && swell <= 0.01f) return

    val backingStyle = style.copy(
        fontSize = (style.fontSize.value * (BACKING_FONT_SIZE / LYRIC_FONT_SIZE)).sp,
        lineHeight = (style.lineHeight.value * (BACKING_LINE_HEIGHT / LYRIC_LINE_HEIGHT)).sp,
    )
    val alignment = when (textAlign) {
        TextAlign.Center -> Alignment.BottomCenter
        TextAlign.End -> Alignment.BottomEnd
        else -> Alignment.BottomStart
    }

    Box(modifier, contentAlignment = alignment) {
        SweptLyricsLine(
            line = line,
            clock = clock,
            style = backingStyle,
            // Subordinate to the lead, on the shared entrance/exit: it fades and
            // lifts the last few pixels into place rather than cutting in, but
            // never becomes brighter than the line it answers (spec 21/22).
            modifier = Modifier.graphicsLayer {
                alpha = BACKING_ALPHA * swell
                translationY = (1f - swell) * BACKING_RISE_PX
            },
            dimAlpha = UNSUNG_ALPHA,
            rise = false,
            textAlign = textAlign,
        )
    }
}

/** How far the overlay lifts into place as it appears. */
private const val BACKING_RISE_PX = 10f
