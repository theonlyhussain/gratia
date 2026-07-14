package com.gratia.music.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Gratia Design Language (GDL) - Spacing System
 * NO RANDOM VALUES ALLOWED. Use these tokens for all margins, paddings, and gaps.
 */
@Immutable
data class GratiaSpacing(
    /** 2dp */
    val micro: Dp = 2.dp,
    /** 4dp */
    val extraSmall: Dp = 4.dp,
    /** 8dp */
    val small: Dp = 8.dp,
    /** 12dp */
    val mediumSmall: Dp = 12.dp,
    /** 16dp - Base screen padding */
    val base: Dp = 16.dp,
    /** 20dp */
    val medium: Dp = 20.dp,
    /** 24dp - Major structural sections */
    val mediumLarge: Dp = 24.dp,
    /** 32dp - Primary CTA separation */
    val large: Dp = 32.dp,
    /** 40dp */
    val extraLarge: Dp = 40.dp,
    /** 48dp - Hero section spacing */
    val heroSmall: Dp = 48.dp,
    /** 56dp */
    val heroMedium: Dp = 56.dp,
    /** 64dp - Intentional luxury whitespace */
    val hero: Dp = 64.dp,
    /** 80dp */
    val heroLarge: Dp = 80.dp,
    /** 96dp */
    val heroExtraLarge: Dp = 96.dp
)

val LocalGratiaSpacing = staticCompositionLocalOf { GratiaSpacing() }
