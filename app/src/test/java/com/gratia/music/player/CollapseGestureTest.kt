package com.gratia.music.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The acceptance bar for the collapse gesture, held as a test:
 *
 *  - a tiny downward nudge must never collapse the player;
 *  - a deliberate drag past the threshold must always collapse it;
 *  - a fast flick counts only when the drag also travelled most of the way.
 */
class CollapseGestureTest {

    // 120dp at a realistic density: whatever the device, the physics being
    // tested are relative to this threshold, not to any particular pixel count.
    private val threshold = 300f

    @Test
    fun `tiny nudge never collapses`() {
        assertFalse(CollapseGesture.decide(draggedPx = 10f, thresholdPx = threshold))
        assertFalse(CollapseGesture.decide(draggedPx = 50f, thresholdPx = threshold))
    }

    @Test
    fun `small drift below threshold springs back even with some velocity`() {
        // The exact reported bug: a small finger movement threw the player away.
        assertFalse(CollapseGesture.decide(draggedPx = 60f, thresholdPx = threshold, velocityPxS = 1200f))
        assertFalse(CollapseGesture.decide(draggedPx = 100f, thresholdPx = threshold, velocityPxS = 1700f))
    }

    @Test
    fun `drag past threshold collapses on distance alone`() {
        assertTrue(CollapseGesture.decide(draggedPx = threshold, thresholdPx = threshold))
        assertTrue(CollapseGesture.decide(draggedPx = threshold + 1f, thresholdPx = threshold))
    }

    @Test
    fun `drag well past threshold collapses regardless of velocity`() {
        assertTrue(CollapseGesture.decide(draggedPx = threshold + 50f, thresholdPx = threshold, velocityPxS = 0f))
    }

    @Test
    fun `flick counts only with meaningful travel`() {
        // Fast enough to qualify, and far enough along (>= 60% of threshold).
        assertTrue(
            CollapseGesture.decide(
                draggedPx = threshold * CollapseGesture.FLING_MIN_TRAVEL_FRACTION,
                thresholdPx = threshold,
                velocityPxS = CollapseGesture.FLING_VELOCITY_PX_S,
            )
        )
        // Same speed, but barely any travel: a tap that got away, not a dismissal.
        assertFalse(
            CollapseGesture.decide(
                draggedPx = 50f,
                thresholdPx = threshold,
                velocityPxS = 4000f,
            )
        )
    }

    @Test
    fun `upward velocity never collapses`() {
        assertFalse(CollapseGesture.decide(draggedPx = 100f, thresholdPx = threshold, velocityPxS = -3000f))
    }

    @Test
    fun `slow release below threshold springs back`() {
        assertFalse(CollapseGesture.decide(draggedPx = threshold - 1f, thresholdPx = threshold, velocityPxS = 200f))
    }
}
