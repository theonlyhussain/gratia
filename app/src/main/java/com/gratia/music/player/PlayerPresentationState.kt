package com.gratia.music.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * How the player is *presented* — a fact about the UI, not about audio.
 *
 * Kept as its own type rather than folded into [PlayerManager] on purpose:
 * playback continues identically in both states, and the two questions
 * ("what is playing?" and "is the player open?") changed for different
 * reasons at different times. Coupling them is what produced the old bug
 * family where collapsing the player could take the playback UI down with
 * it: an `expanded = currentSong != null && isOpen` expression, evaluated
 * in three different composables, that could disagree with itself between
 * recompositions.
 *
 * One owner ([PlayerViewModel]), one StateFlow, and every surface — mini
 * player, expanded player, bottom-bar padding — reads the same value.
 */
sealed interface PlayerPresentationState {
    /** Only the mini player is shown. Playback is unaffected. */
    data object Collapsed : PlayerPresentationState

    /** The full-screen player is shown. Playback is unaffected. */
    data object Expanded : PlayerPresentationState

    companion object {
        fun fromOpen(open: Boolean): PlayerPresentationState =
            if (open) Expanded else Collapsed
    }
}

/**
 * Whether a downward drag should collapse the player — the one decision both
 * the drag-handle gesture and the content-area fallback must agree on.
 *
 * A pure function so the thresholds are unit-testable without a device: the
 * acceptance bar is "a tiny nudge must never collapse, a deliberate drag must
 * always", and that is exactly the kind of claim a test can hold the line on.
 *
 * The rule is deliberately asymmetric:
 *  - Distance alone can collapse ([draggedPx] at or past [thresholdPx]).
 *  - Velocity alone cannot. A fast flick with almost no travel is a tap that
 *    got away with something, not a dismissal — it must also have covered
 *    [minTravelForVelocityPx], most of the way to the threshold.
 */
object CollapseGesture {

    /**
     * How far the player must be dragged down before releasing collapses it.
     *
     * A physical distance, not a raw pixel count: 120dp is roughly a thumb's
     * width on every density this app ships to, which is far enough that a
     * stray scroll tick cannot reach it and close enough that the gesture
     * never feels like a fight.
     */
    const val THRESHOLD_DP = 120

    /**
     * Flick speed that can stand in for distance — but only for a drag that
     * already covered most of the way there. px/s.
     */
    const val FLING_VELOCITY_PX_S = 1800f

    /**
     * The travel a fling must already have covered before its speed counts.
     * Sixty percent of the threshold: enough travel to prove intent, short
     * enough that a confident flick does not have to be dragged all the way.
     */
    const val FLING_MIN_TRAVEL_FRACTION = 0.6f

    fun decide(
        draggedPx: Float,
        thresholdPx: Float,
        velocityPxS: Float = 0f,
    ): Boolean {
        if (draggedPx >= thresholdPx) return true
        if (velocityPxS < FLING_VELOCITY_PX_S) return false
        return draggedPx >= thresholdPx * FLING_MIN_TRAVEL_FRACTION
    }
}
