package com.gratia.music.ui.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.gratia.music.ui.components.CoverArtFallback
import com.gratia.music.ui.theme.GratiaTheme
import java.io.File

/**
 * Hero album artwork — full-width, edge-to-edge with a gradient fade at the bottom.
 * Mimics the industry standard's immersive artwork experience.
 *
 * Design details:
 * - Scale 0.85 → 1.0 on play (distinct pop effect)
 * - Bottom gradient fades artwork into the dominant color background
 * - No horizontal padding — artwork bleeds to edges
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
    val context = LocalContext.current

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

    val hasCover = !coverArtPath.isNullOrBlank() && File(coverArtPath).exists()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .scale(scale),
        contentAlignment = Alignment.Center
    ) {
        if (hasCover) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(File(coverArtPath!!))
                    .crossfade(300)
                    .build(),
                contentDescription = "$title cover art",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            CoverArtFallback(
                title = title,
                artist = artist,
                size = 400.dp,
                cornerRadius = 0.dp,
                fontSize = GratiaTheme.typography.display.fontSize,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Bottom gradient fade — artwork dissolves into the background color
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            glowColor.copy(alpha = 0.5f),
                            glowColor.copy(alpha = 0.9f)
                        )
                    )
                )
        )
    }
}
