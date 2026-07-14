package com.gratia.music.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Gratia Design Language (GDL) - Shapes System
 */
@Immutable
data class GratiaShapes(
    /** 4dp - Tiny controls, small chips */
    val tiny: Shape = RoundedCornerShape(4.dp),
    /** 8dp - Small buttons, minor elements */
    val small: Shape = RoundedCornerShape(8.dp),
    /** 12dp - Inner cards, list items, standard image thumbnails */
    val medium: Shape = RoundedCornerShape(12.dp),
    /** 16dp - Prominent cards (Album Cards), Dialogs */
    val large: Shape = RoundedCornerShape(16.dp),
    /** 24dp - Bottom sheets, Expanded Player container */
    val extraLarge: Shape = RoundedCornerShape(24.dp),
    /** 24dp Top Corners - Bottom Sheets */
    val sheet: Shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    /** 32dp - Hero Player artwork, ultra-premium spotlight items */
    val hero: Shape = RoundedCornerShape(32.dp),
    /** CircleShape - Icon buttons, primary play/pause */
    val pill: Shape = CircleShape
)

val LocalGratiaShapes = staticCompositionLocalOf { GratiaShapes() }
