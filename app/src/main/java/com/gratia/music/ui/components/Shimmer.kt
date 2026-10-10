package com.gratia.music.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import com.gratia.music.ui.theme.GratiaTheme

fun Modifier.shimmer(): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_translate"
    )

    val color = GratiaTheme.colors.surface
    val shimmerColor = GratiaTheme.colors.surface.copy(alpha = 0.5f)

    background(
        brush = Brush.linearGradient(
            colors = listOf(color, shimmerColor, color),
            start = Offset.Zero,
            end = Offset(x = translateAnim, y = translateAnim)
        )
    )
}
