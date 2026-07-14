package com.gratia.music.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Gratia Design Language (GDL) - Elevation System
 */
@Immutable
data class GratiaElevation(
    val level0: Dp = 0.dp,
    val level1: Dp = 1.dp,
    val level2: Dp = 3.dp,
    val level3: Dp = 6.dp,
    val hero: Dp = 12.dp
)

val LocalGratiaElevation = staticCompositionLocalOf { GratiaElevation() }

/**
 * Gratia Design Language (GDL) - Glass Material System
 */
@Immutable
data class GratiaGlass(
    /** Minimal blur, high transparency */
    val ultraThinBlur: Dp = 16.dp,
    val ultraThinAlpha: Float = 0.05f,
    
    /** Standard sheet blur */
    val thinBlur: Dp = 32.dp,
    val thinAlpha: Float = 0.15f,
    
    /** Sticky header blur */
    val mediumBlur: Dp = 48.dp,
    val mediumAlpha: Float = 0.35f,
    
    /** Modal background blur */
    val thickBlur: Dp = 64.dp,
    val thickAlpha: Float = 0.65f
)

val LocalGratiaGlass = staticCompositionLocalOf { GratiaGlass() }
