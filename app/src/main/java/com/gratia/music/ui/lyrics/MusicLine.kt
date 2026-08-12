package com.gratia.music.ui.lyrics

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gratia.music.ui.theme.SpaceGrotesk

/**
 * Instrumental section indicator.
 *
 * Instead of raw canvas circles, this renders a clean "• • •" text indicator
 * with a gentle pulsing animation when active. It fades in smoothly when the
 * instrumental section begins and dims when it ends — looking intentional,
 * not broken.
 */
@Composable
fun MusicLine(
    isActiveLine: Boolean,
    durationMs: Int
) {
    // Smooth fade in/out
    val baseAlpha by animateFloatAsState(
        targetValue = if (isActiveLine) 0.7f else 0.12f,
        animationSpec = tween(
            durationMillis = 400,
            easing = FastOutSlowInEasing
        ),
        label = "MusicLineAlpha"
    )

    // Gentle pulsing when active (subtle breathing effect)
    val infiniteTransition = rememberInfiniteTransition(label = "MusicLinePulse")
    val pulseAlpha by if (isActiveLine) {
        infiniteTransition.animateFloat(
            initialValue = 0.5f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "PulseAlpha"
        )
    } else {
        remember { mutableFloatStateOf(1f) }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .alpha(baseAlpha * pulseAlpha),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = "• • •",
            fontFamily = SpaceGrotesk,
            fontWeight = FontWeight.Medium,
            fontSize = 24.sp,
            color = Color.White,
            letterSpacing = 6.sp
        )
    }
}
