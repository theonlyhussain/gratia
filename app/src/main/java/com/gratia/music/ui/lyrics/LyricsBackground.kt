package com.gratia.music.ui.lyrics

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * Premium ambient animated background.
 * Renders large blurred gradient blobs moving slowly.
 * Safe for performance and respects system transition scale.
 */
@Composable
fun LyricsBackground(
    modifier: Modifier = Modifier,
    customDominantColor: Color? = null,
    customDarkMutedColor: Color? = null
) {
    val context = LocalContext.current
    val isReducedMotion = remember {
        try {
            val resolver = context.contentResolver
            val transitionScale = android.provider.Settings.Global.getFloat(
                resolver,
                android.provider.Settings.Global.TRANSITION_ANIMATION_SCALE,
                1.0f
            )
            transitionScale == 0.0f
        } catch (_: Exception) {
            false
        }
    }

    val noirBlack = Color(0xFF1B1716)
    val maroon = Color(0xFF630102)
    val cherryRed = Color(0xFF810100)

    val color1 = customDominantColor ?: cherryRed
    val color2 = customDarkMutedColor ?: maroon
    val colorBg = noirBlack

    if (isReducedMotion) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            color1.copy(alpha = 0.4f),
                            color2.copy(alpha = 0.5f),
                            colorBg
                        )
                    )
                )
        )
        return
    }

    val transition = rememberInfiniteTransition(label = "ambientBgTransition")

    val animX1 by transition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(24000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "animX1"
    )

    val animY1 by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(28000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "animY1"
    )

    val animX2 by transition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(32000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "animX2"
    )

    val animY2 by transition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(26000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "animY2"
    )

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .background(colorBg)
    ) {
        val width = size.width
        val height = size.height

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(color1.copy(alpha = 0.2f), Color.Transparent),
                center = Offset(width * animX1, height * animY1),
                radius = width * 0.8f
            ),
            center = Offset(width * animX1, height * animY1),
            radius = width * 0.8f
        )

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(color2.copy(alpha = 0.22f), Color.Transparent),
                center = Offset(width * animX2, height * animY2),
                radius = width * 0.9f
            ),
            center = Offset(width * animX2, height * animY2),
            radius = width * 0.9f
        )
    }
}
