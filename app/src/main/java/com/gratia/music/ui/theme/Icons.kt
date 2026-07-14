package com.gratia.music.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Gratia Design Language (GDL) - Icon System
 */
@Immutable
data class GratiaIcons(
    /** 16dp */
    val tiny: Dp = 16.dp,
    /** 20dp */
    val small: Dp = 20.dp,
    /** 24dp */
    val normal: Dp = 24.dp,
    /** 28dp */
    val large: Dp = 28.dp,
    /** 36dp */
    val hero: Dp = 36.dp
)

val LocalGratiaIcons = staticCompositionLocalOf { GratiaIcons() }
