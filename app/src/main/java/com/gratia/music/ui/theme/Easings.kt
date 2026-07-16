package com.gratia.music.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing

/**
 * Custom easings based on Emil Kowalski's design engineering principles.
 * 
 * Never use the built-in ease-in or ease-out. They lack the "punch" that makes 
 * interfaces feel alive and intentional.
 */
object Easings {
    // Strong ease-out for UI interactions. Feels instantly responsive.
    val FastEaseOut: Easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

    // Strong ease-in-out for on-screen movement (morphing, sliding).
    val FastEaseInOut: Easing = CubicBezierEasing(0.77f, 0f, 0.175f, 1f)

    // iOS-like drawer curve for sheets and heavy entering elements.
    val DrawerEasing: Easing = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)
}
