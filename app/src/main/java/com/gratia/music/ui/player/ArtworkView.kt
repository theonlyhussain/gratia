package com.gratia.music.ui.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gratia.music.ui.components.CoverArtImage
import com.gratia.music.ui.theme.GratiaTheme

/**
 * Hero album artwork with floating shadow, ambient glow, and scale animation.
 *
 * Design details:
 * - Scale 0.98 → 1.0 on play (subtle, never bounce)
 * - Shadow deepens when playing
 * - Soft glow halo from dominant cover color radiates behind the art
 * - Beautiful GDL Hero rounded corners (32dp)
 * - When dragging the progress bar, scales down slightly (0.97)
 */
@Composable
fun ArtworkView(
    coverArtPath: String?,
    title: String,
    artist: String,
    isPlaying: Boolean,
    glowColor: Color,
    modifier: Modifier = Modifier,
    isDragging: Boolean = false
) {
    val motion = GratiaTheme.motion

    // Scale: paused → 0.98, playing → 1.0, dragging → 0.97
    val targetScale = when {
        isDragging -> 0.97f
        isPlaying -> 1.0f
        else -> 0.98f
    }

    val scale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = tween(
            durationMillis = motion.slow,
            easing = motion.standardEasing
        ),
        label = "artworkScale"
    )

    // Shadow depth animates between states
    val shadowElevation by animateFloatAsState(
        targetValue = if (isPlaying) GratiaTheme.elevation.hero.value else GratiaTheme.elevation.level3.value,
        animationSpec = tween(motion.slow),
        label = "artworkShadow"
    )

    // Glow opacity
    val glowAlpha by animateFloatAsState(
        targetValue = if (isPlaying) 0.35f else 0.15f,
        animationSpec = tween(motion.slow),
        label = "artworkGlow"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 36.dp)
            .aspectRatio(1f)
            .scale(scale),
        contentAlignment = Alignment.Center
    ) {
        // Ambient glow layer behind the artwork
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    // Soft radial glow from dominant color
                    drawCircle(
                        color = glowColor.copy(alpha = glowAlpha),
                        radius = size.minDimension * 0.65f,
                        center = center
                    )
                }
        )

        // The artwork itself with shadow
        CoverArtImage(
            coverArtPath = coverArtPath,
            title = title,
            artist = artist,
            size = 300.dp,
            cornerRadius = 32.dp, // Matches GratiaTheme.shapes.hero
            fontSize = GratiaTheme.typography.display.fontSize,
            modifier = Modifier
                .fillMaxSize()
                .shadow(
                    elevation = shadowElevation.dp,
                    shape = GratiaTheme.shapes.hero,
                    spotColor = Color.Black.copy(alpha = 0.5f),
                    ambientColor = Color.Black.copy(alpha = 0.25f)
                )
        )
    }
}
