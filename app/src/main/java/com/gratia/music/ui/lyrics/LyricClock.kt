package com.gratia.music.ui.lyrics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import kotlin.math.abs

/**
 * The player reports its position on a timer, and that reading is not
 * monotonic: a poll that lands a few milliseconds behind the previous one makes
 * the sweep jump *backwards*, which reads on screen as the highlight stuttering
 * and — worse — as the previous word lighting up again for a frame.
 *
 * While the two readings agree to within [JITTER_TOLERANCE_MS] the furthest
 * one wins, so the highlight only ever creeps forward. Past that the gap is not
 * jitter but a real move (a seek, a track change), and the reported value is
 * taken as-is so the lyrics land where the song actually is.
 */
internal fun reconcileLyricPosition(displayedMs: Long, reportedMs: Long): Long =
    if (abs(displayedMs - reportedMs) <= JITTER_TOLERANCE_MS) {
        maxOf(displayedMs, reportedMs)
    } else {
        reportedMs
    }

/** A reported position this far from the shown one is a seek, not jitter. */
private const val JITTER_TOLERANCE_MS = 250L

/**
 * The playback position, sampled once per frame on the frame clock rather than
 * read straight from the player on every draw.
 *
 * Reading the player directly inside a draw lambda works, but the value only
 * changes when the player's polling timer fires — so the sweep advances in
 * steps and every frame between two ticks redraws identical text. Holding the
 * position here instead gives the sweep a value that is reconciled once a frame
 * and shared by every line on screen, which is what keeps a held note's glow
 * and the sweep's leading edge moving together instead of a poll apart.
 */
@Composable
fun rememberLyricClock(positionProvider: () -> Long): State<Long> {
    val provider by rememberUpdatedState(positionProvider)
    val clock = remember { mutableLongStateOf(positionProvider()) }

    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { }
            clock.longValue = reconcileLyricPosition(clock.longValue, provider())
        }
    }

    return clock
}
