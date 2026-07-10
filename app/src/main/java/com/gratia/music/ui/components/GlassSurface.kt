package com.gratia.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A premium glass container with depth, glow, and subtle border.
 *
 * Unlike the simpler [liquidGlass] modifier, this composable provides:
 * - Configurable elevation shadow
 * - Ambient glow color (from cover art)
 * - Inner highlight gradient border
 * - Layered depth through shadow + background + border
 *
 * Use for player surfaces, lyrics cards, queue rows —
 * anywhere that needs to feel physically placed in the UI.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    backgroundColor: Color = Color.White.copy(alpha = 0.06f),
    glowColor: Color = Color.Transparent,
    elevation: Dp = 0.dp,
    borderWidth: Dp = 0.5.dp,
    borderColorStart: Color = Color.White.copy(alpha = 0.12f),
    borderColorEnd: Color = Color.White.copy(alpha = 0.02f),
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .then(
                if (elevation > 0.dp) {
                    Modifier.shadow(
                        elevation = elevation,
                        shape = shape,
                        spotColor = glowColor.copy(alpha = (glowColor.alpha * 0.6f).coerceAtMost(1f)),
                        ambientColor = glowColor.copy(alpha = (glowColor.alpha * 0.3f).coerceAtMost(1f))
                    )
                } else Modifier
            )
            .clip(shape)
            .background(backgroundColor)
            .then(
                if (borderWidth > 0.dp) {
                    Modifier.border(
                        width = borderWidth,
                        brush = Brush.linearGradient(
                            colors = listOf(borderColorStart, borderColorEnd)
                        ),
                        shape = shape
                    )
                } else Modifier
            ),
        content = content
    )
}
