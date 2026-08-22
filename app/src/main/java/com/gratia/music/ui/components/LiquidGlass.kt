package com.gratia.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Applies a premium "Liquid Glass" effect.
 * Creates a translucent background with a subtle gradient border.
 */
fun Modifier.liquidGlass(
    shape: Shape = RoundedCornerShape(16.dp),
    backgroundColor: Color? = null,
    borderColorStart: Color? = null,
    borderColorEnd: Color? = null,
    borderWidth: Dp = 1.dp
): Modifier = composed {
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val baseColor = if (isDark) Color.White else Color.Black
    
    val finalBgColor = backgroundColor ?: baseColor.copy(alpha = if (isDark) 0.05f else 0.08f)
    val finalBorderStart = borderColorStart ?: baseColor.copy(alpha = if (isDark) 0.15f else 0.20f)
    val finalBorderEnd = borderColorEnd ?: baseColor.copy(alpha = if (isDark) 0.02f else 0.05f)

    this
        .clip(shape)
        .background(finalBgColor)
        .border(
            width = borderWidth,
            brush = Brush.linearGradient(
                colors = listOf(finalBorderStart, finalBorderEnd)
            ),
            shape = shape
        )
}
