package com.gratia.music.ui.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gratia.music.ui.components.AnimatedText
import com.gratia.music.ui.components.CoverArtImage
import com.gratia.music.ui.components.GratiaIcon
import com.gratia.music.ui.theme.GratiaTheme

/**
 * Compact player header shown at the top when in Lyrics or Queue mode.
 *
 * Layout: [Artwork 48dp] [Title / Artist] [★] [⋯]
 *
 * Matches the Apple Music compact header pattern visible in the reference
 * screenshots — small artwork thumbnail inline with track info and action buttons.
 */
@Composable
fun CompactPlayerHeader(
    coverArtPath: String?,
    title: String,
    artist: String,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = GratiaTheme.spacing.mediumLarge, vertical = GratiaTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Small artwork thumbnail
        CoverArtImage(
            coverArtPath = coverArtPath,
            title = title,
            artist = artist,
            size = 48.dp,
            cornerRadius = 10.dp
        )

        Spacer(Modifier.width(GratiaTheme.spacing.mediumSmall))

        // Song title + artist
        Column(modifier = Modifier.weight(1f)) {
            AnimatedText(
                text = title,
                style = GratiaTheme.typography.body.copy(
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                ),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fadeDurationMs = GratiaTheme.motion.normal,
                isMarquee = true
            )
            AnimatedText(
                text = artist,
                style = GratiaTheme.typography.caption,
                color = Color.White.copy(alpha = 0.55f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fadeDurationMs = GratiaTheme.motion.normal,
                isMarquee = false
            )
        }

        Spacer(Modifier.width(GratiaTheme.spacing.small))

        // Favorite star
        CompactFavoriteButton(
            isFavorite = isFavorite,
            onToggle = onToggleFavorite
        )

        Spacer(Modifier.width(GratiaTheme.spacing.small))

        // More button
        CompactMoreButton(onClick = onMoreClick)
    }
}

@Composable
private fun CompactFavoriteButton(
    isFavorite: Boolean,
    onToggle: () -> Unit
) {
    val view = LocalView.current
    val haptics = GratiaTheme.haptics
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.85f else 1.0f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 600f),
        label = "compactFavScale"
    )

    val starColor by animateColorAsState(
        targetValue = if (isFavorite) Color.White else Color.White.copy(alpha = 0.7f),
        animationSpec = tween(200),
        label = "compactStarColor"
    )

    Box(
        modifier = Modifier
            .size(32.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.12f))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    haptics.medium(view)
                    onToggle()
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        GratiaIcon(
            imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
            contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
            tint = starColor,
            size = 18.dp
        )
    }
}

@Composable
private fun CompactMoreButton(onClick: () -> Unit) {
    val view = LocalView.current
    val haptics = GratiaTheme.haptics
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.85f else 1.0f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 600f),
        label = "compactMoreScale"
    )

    Box(
        modifier = Modifier
            .size(32.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.12f))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    haptics.medium(view)
                    onClick()
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        GratiaIcon(
            imageVector = Icons.Default.MoreHoriz,
            contentDescription = "More options",
            tint = Color.White.copy(alpha = 0.7f),
            size = 18.dp
        )
    }
}
