package com.gratia.music.ui.lyrics

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun MusicLine(
    isActiveLine: Boolean,
    durationMs: Int
) {
    val infiniteTransition = rememberInfiniteTransition(label = "MusicLineTransition")

    val radius by if (isActiveLine) {
        infiniteTransition.animateFloat(
            initialValue = 8f,
            targetValue = 12f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "MusicLineRadius"
        )
    } else {
        remember { mutableFloatStateOf(8f) }
    }

    val opacity by animateFloatAsState(
        targetValue = if (isActiveLine) 1f else 0.1f,
        animationSpec = tween(
            durationMillis = if (isActiveLine) durationMs else 100,
            easing = LinearEasing
        ),
        label = "MusicLineOpacity"
    )

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(63.dp)
    ) {
        val y = 12.dp.toPx()
        val spacing = 12.dp.toPx()
        
        drawCircle(
            color = Color.White.copy(alpha = opacity),
            radius = radius,
            center = Offset(spacing, y)
        )
        drawCircle(
            color = Color.White.copy(alpha = opacity),
            radius = radius,
            center = Offset(spacing * 4, y)
        )
        drawCircle(
            color = Color.White.copy(alpha = opacity),
            radius = radius,
            center = Offset(spacing * 7, y)
        )
    }
}
