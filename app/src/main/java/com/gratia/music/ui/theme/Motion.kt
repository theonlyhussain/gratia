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
    
    // Custom easings
    val emphasizedEasing: Easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f),
    val standardEasing: Easing = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f)
) {
    /** stiffness = 300, dampingRatio = 0.8 */
    fun <T> springStandard() = spring<T>(
        dampingRatio = 0.8f,
        stiffness = 300f
    )

    /** stiffness = 500, dampingRatio = 0.9 */
    fun <T> springStiff() = spring<T>(
        dampingRatio = 0.9f,
        stiffness = 500f
    )
}

val LocalGratiaMotion = staticCompositionLocalOf { GratiaMotion() }
