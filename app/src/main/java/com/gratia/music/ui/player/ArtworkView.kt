package com.gratia.music.ui.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
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
 * Hero album artwork, matching the inspiration UI.
 * Beautifully spaced, rounded corners, gracefully scales.
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
        targetValue = if (isPlaying) 24f else 8f,
        animationSpec = tween(motion.slow),
        label = "artworkShadow"
    )
    
    val cornerRadius = 16.dp
    val hasCover = !coverArtPath.isNullOrBlank() && File(coverArtPath).exists()

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 16.dp)
            .scale(scale),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .aspectRatio(1f) // Ensures it's a perfect square
                .shadow(
                    elevation = shadowElevation.dp,
                    shape = RoundedCornerShape(cornerRadius),
                    spotColor = Color.Black.copy(alpha = 0.5f),
                    ambientColor = Color.Black.copy(alpha = 0.25f)
                )
                .clip(RoundedCornerShape(cornerRadius))
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
        }
    }
}
