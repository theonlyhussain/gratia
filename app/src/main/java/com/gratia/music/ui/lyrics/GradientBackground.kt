package com.gratia.music.ui.lyrics

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Colors ported from lyrics-animation repository constants
private val PrimaryColor = Color(0xFFF9263E)
private val TertiaryColor = Color(0xFF122620)

@Composable
fun GradientBackground(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "GradientTransition")

    val colorStart by infiniteTransition.animateColor(
        initialValue = PrimaryColor,
        targetValue = TertiaryColor,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ColorStart"
    )

    val colorEnd by infiniteTransition.animateColor(
        initialValue = TertiaryColor,
        targetValue = PrimaryColor,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ColorEnd"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(colorStart, colorEnd)
                )
            )
    )
}
