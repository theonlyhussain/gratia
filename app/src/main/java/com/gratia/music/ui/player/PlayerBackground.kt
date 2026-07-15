package com.gratia.music.ui.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.gratia.music.ui.theme.GratiaTheme

/**
 * Cinematic blurred background for the player.
 * Mimics Apple Music by heavily blurring the album artwork.
 */
@Composable
fun PlayerBackground(
    coverArtPath: String?,
    dominantColor: Color,
    modifier: Modifier = Modifier
) {
    val motion = GratiaTheme.motion

    // Smooth color crossfade for the overlay when song changes
    val overlayColor by animateColorAsState(
        targetValue = dominantColor.copy(alpha = 0.5f),
        animationSpec = tween(motion.hero, easing = motion.standardEasing),
        label = "bgOverlayColor"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF090909))
    ) {
        if (coverArtPath != null) {
            AsyncImage(
                model = coverArtPath,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    // Extreme blur to create the atmospheric effect
                    .blur(radius = 80.dp)
                    // Dim the image slightly to ensure text legibility
                    .graphicsLayer { alpha = 0.8f }
            )
        }

        // Tint overlay based on the dominant color and dark gradient
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(overlayColor)
                .background(Color.Black.copy(alpha = 0.4f)) // Additional darkening
        )
    }
}

