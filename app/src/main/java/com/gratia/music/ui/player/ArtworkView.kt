package com.gratia.music.ui.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.gratia.music.ui.components.CoverArtImage
import com.gratia.music.ui.theme.GratiaTheme

/**
 * Hero album artwork with floating shadow and distinct scale animation.
 * Mimics Apple Music's artwork behavior.
 *
 * Design details:
 * - Scale 0.85 → 1.0 on play (distinct pop effect)
 * - Shadow deepens when playing
 * - GDL Hero rounded corners (32dp or smaller if preferred, sticking to theme)
 */
@Composable
fun ArtworkView(
    coverArtPath: String?,
    title: String,
    artist: String,
    isPlaying: Boolean,
    glowColor: Color, // Kept for compatibility but unused
    modifier: Modifier = Modifier,
    isDragging: Boolean = false
) {
    val motion = GratiaTheme.motion

    // Scale: paused/dragging -> 0.85, playing -> 1.0
    val targetScale = when {
        isDragging -> 0.85f
        isPlaying -> 1.0f
        else -> 0.85f
    }

    val scale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = spring(
            dampingRatio = 0.7f,
            stiffness = 300f
        ),
        label = "artworkScale"
    )

    // Shadow depth animates between states
    val shadowElevation by animateFloatAsState(
        targetValue = if (isPlaying) 24f else 8f,
        animationSpec = tween(motion.normal),
        label = "artworkShadow"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp)
            .aspectRatio(1f)
            .scale(scale),
        contentAlignment = Alignment.Center
    ) {
        // The artwork itself with shadow
        CoverArtImage(
            coverArtPath = coverArtPath,
            title = title,
            artist = artist,
            size = 340.dp,
            cornerRadius = 16.dp, // Apple Music has slightly tighter rounded corners, e.g., 12dp-16dp
            fontSize = GratiaTheme.typography.display.fontSize,
            modifier = Modifier
                .fillMaxSize()
                .shadow(
                    elevation = shadowElevation.dp,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                    spotColor = Color.Black.copy(alpha = 0.5f),
                    ambientColor = Color.Black.copy(alpha = 0.25f)
                )
        )
    }
}

