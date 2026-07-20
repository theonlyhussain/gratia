package com.gratia.music.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Gratia Design Language (GDL) - Motion System
 */
@Immutable
data class GratiaMotion(
    /** 0ms */
    val instant: Int = 0,
    /** 150ms - Button presses, simple state toggles */
    val fast: Int = 150,
    /** 250ms - Card expansions, crossfades */
    val normal: Int = 250,
    /** 350ms - Page transitions */
    val slow: Int = 350,
    /** 500ms - Special animations, player entry */
    val hero: Int = 500,
    
    // Custom easings (Apple / Emil Kowalski)
    val emphasizedEasing: Easing = CubicBezierEasing(0.23f, 1.0f, 0.32f, 1.0f), // Strong ease-out
    val standardEasing: Easing = CubicBezierEasing(0.77f, 0.0f, 0.175f, 1.0f) // Strong ease-in-out
) {
    /** Apple Default Spring: Damping 1.0 (Critically damped, no bounce) */
    fun <T> springStandard() = spring<T>(
        dampingRatio = 1.0f,
        stiffness = 400f // Snappy response
    )

    /** Apple Momentum Spring: Damping 0.8 (Slight bounce for physical interactions) */
    fun <T> springStiff() = spring<T>(
        dampingRatio = 0.8f,
        stiffness = 300f
    )
}

val LocalGratiaMotion = staticCompositionLocalOf { GratiaMotion() }
