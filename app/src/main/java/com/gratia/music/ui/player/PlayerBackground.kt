package com.gratia.music.ui.player

import androidx.compose.animation.animateColorAsState
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
import com.gratia.music.ui.theme.GratiaTheme
import kotlin.math.cos
import kotlin.math.sin

/**
 * Cinematic animated background for the player.
 *
 * Renders 3 large radial gradient blobs that orbit slowly, creating
 * a living, breathing atmosphere derived from the album art's dominant colors.
 *
 * Design principles:
 * - Never distracting — very slow, very subtle
 * - Crossfades between color sets on song change using GDL motion tokens
 * - Respects system reduced-motion setting
 * - Uses Canvas for 60fps performance (no recomposition per frame)
 */
@Composable
fun PlayerBackground(
    dominantColor: Color,
    darkMutedColor: Color,
    vibrantColor: Color,
    modifier: Modifier = Modifier,
    isPaused: Boolean = false
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

    val motion = GratiaTheme.motion

    // Smooth color crossfade when song changes
    val color1 by animateColorAsState(
        targetValue = dominantColor,
        animationSpec = tween(motion.hero, easing = motion.standardEasing),
        label = "bgColor1"
    )
    val color2 by animateColorAsState(
        targetValue = darkMutedColor,
        animationSpec = tween(motion.hero, easing = motion.standardEasing),
        label = "bgColor2"
    )
    val color3 by animateColorAsState(
        targetValue = vibrantColor,
        animationSpec = tween(motion.hero, easing = motion.standardEasing),
        label = "bgColor3"
    )

    val baseBlack = Color(0xFF090909)

    if (isReducedMotion) {
        // Static fallback for accessibility
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            color1.copy(alpha = 0.35f),
                            color2.copy(alpha = 0.25f),
                            baseBlack
                        )
                    )
                )
        )
        return
    }

    // Three independent orbital animations — different speeds to avoid sync
    val transition = rememberInfiniteTransition(label = "playerBg")

    // Blob 1: slow wide orbit (dominant color)
    val angle1 by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(28000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "angle1"
    )

    // Blob 2: medium counter-orbit (dark muted)
    val angle2 by transition.animateFloat(
        initialValue = 120f,
        targetValue = 120f - 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(34000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "angle2"
    )

    // Blob 3: subtle drift (vibrant accent)
    val angle3 by transition.animateFloat(
        initialValue = 240f,
        targetValue = 240f + 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(40000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "angle3"
    )

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .background(baseBlack)
    ) {
        val width = size.width
        val height = size.height
        val centerX = width * 0.5f
        val centerY = height * 0.4f

        // Orbit radius — blobs drift around center
        val orbitR = width * 0.18f

        val rad1 = Math.toRadians(angle1.toDouble())
        val rad2 = Math.toRadians(angle2.toDouble())
        val rad3 = Math.toRadians(angle3.toDouble())

        // Blob 1 — large, dominant, upper area
        val c1 = Offset(
            centerX + (orbitR * cos(rad1)).toFloat(),
            centerY * 0.7f + (orbitR * 0.6f * sin(rad1)).toFloat()
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(color1.copy(alpha = 0.28f), Color.Transparent),
                center = c1,
                radius = width * 0.85f
            ),
            center = c1,
            radius = width * 0.85f
        )

        // Blob 2 — medium, dark muted, mid area
        val c2 = Offset(
            centerX + (orbitR * 1.2f * cos(rad2)).toFloat(),
            centerY + (orbitR * 0.8f * sin(rad2)).toFloat()
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(color2.copy(alpha = 0.22f), Color.Transparent),
                center = c2,
                radius = width * 0.75f
            ),
            center = c2,
            radius = width * 0.75f
        )

        // Blob 3 — smaller, vibrant accent, lower area
        val c3 = Offset(
            centerX + (orbitR * 0.8f * cos(rad3)).toFloat(),
            height * 0.6f + (orbitR * 0.5f * sin(rad3)).toFloat()
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(color3.copy(alpha = 0.15f), Color.Transparent),
                center = c3,
                radius = width * 0.6f
            ),
            center = c3,
            radius = width * 0.6f
        )

        // Dark vignette overlay for depth and readability
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.Transparent,
                    baseBlack.copy(alpha = 0.3f),
                    baseBlack.copy(alpha = 0.7f)
                ),
                startY = height * 0.3f,
                endY = height
            )
        )
    }
}
