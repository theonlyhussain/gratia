package com.gratia.music.ui.components

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.gratia.music.ui.theme.GratiaTheme

private val EaseInOut = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)

/**
 * Animated playing bars (equalizer animation).
 */
@Composable
fun PlayingIndicator(
    modifier: Modifier = Modifier,
    color: Color = GratiaTheme.colors.accent,
    isPaused: Boolean = false
) {
    val infiniteTransition = rememberInfiniteTransition(label = "playingBars")

    val bar1Height by infiniteTransition.animateFloat(
        initialValue = if (isPaused) 4f else 4f,
        targetValue = if (isPaused) 4f else 14f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ), label = "bar1"
    )
    val bar2Height by infiniteTransition.animateFloat(
        initialValue = if (isPaused) 4f else 8f,
        targetValue = if (isPaused) 4f else 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = EaseInOut, delayMillis = 150),
            repeatMode = RepeatMode.Reverse
        ), label = "bar2"
    )
    val bar3Height by infiniteTransition.animateFloat(
        initialValue = if (isPaused) 4f else 6f,
        targetValue = if (isPaused) 4f else 12f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = EaseInOut, delayMillis = 300),
            repeatMode = RepeatMode.Reverse
        ), label = "bar3"
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom,
        modifier = modifier.height(14.dp)
    ) {
        listOf(bar1Height, bar2Height, bar3Height).forEach { height ->
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(height.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(color)
            )
        }
    }
}
