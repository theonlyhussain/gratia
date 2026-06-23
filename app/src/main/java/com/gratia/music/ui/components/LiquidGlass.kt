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
    backgroundColor: Color = Color.White.copy(alpha = 0.05f),
    borderColorStart: Color = Color.White.copy(alpha = 0.15f),
    borderColorEnd: Color = Color.White.copy(alpha = 0.02f),
    borderWidth: Dp = 1.dp
): Modifier = composed {
    this
        .clip(shape)
        .background(backgroundColor)
        .border(
            width = borderWidth,
            brush = Brush.linearGradient(
                colors = listOf(borderColorStart, borderColorEnd)
            ),
            shape = shape
        )
}
