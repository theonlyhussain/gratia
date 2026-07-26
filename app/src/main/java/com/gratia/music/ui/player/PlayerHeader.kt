package com.gratia.music.ui.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gratia.music.ui.components.AnimatedText
import com.gratia.music.ui.components.GratiaIcon
import com.gratia.music.ui.components.GratiaIconButton
import com.gratia.music.ui.theme.GratiaTheme

/**
 * Player header with song info, favorite star button, and more options.
 * Matches Apple Music's layout: title + artist on left, star + dots on right.
 */
@Composable
fun PlayerHeader(
    title: String,
    artist: String,
    album: String?,
    isFavorite: Boolean = false,
    playingFrom: String = "GRATIA",
    onClickTitle: () -> Unit = {},
    onClickArtist: () -> Unit = {},
    onClickAlbum: () -> Unit = {},
    onToggleFavorite: () -> Unit = {},
    onMoreClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = GratiaTheme.spacing.large),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Song info — left side
        Column(
            modifier = Modifier.weight(1f)
        ) {
            // Song title — hero text
            AnimatedText(
                text = title,
                style = GratiaTheme.typography.largeTitle,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fadeDurationMs = GratiaTheme.motion.slow,
                isMarquee = true,
                modifier = Modifier.clickable { onClickTitle() }
            )

            Spacer(Modifier.height(GratiaTheme.spacing.micro)) // 2dp

            // Artist
            AnimatedText(
                text = artist,
                style = GratiaTheme.typography.section,
                color = Color.White.copy(alpha = 0.55f),
                maxLines = 1,
                fadeDurationMs = GratiaTheme.motion.slow,
                isMarquee = true,
                modifier = Modifier.clickable { onClickArtist() }
            )
        }

        Spacer(Modifier.width(12.dp))

        // Favorite star button with circle outline
        FavoriteStarButton(
            isFavorite = isFavorite,
            onToggle = onToggleFavorite
        )

        Spacer(Modifier.width(8.dp))

        // More options button
        GratiaIconButton(
            icon = Icons.Default.MoreHoriz,
            onClick = onMoreClick,
            contentDescription = "More",
            tint = Color.White.copy(alpha = 0.8f),
            size = GratiaTheme.icons.normal,
            modifier = Modifier.padding(8.dp)
        )
    }
}

/**
 * Animated favorite star button with circle border.
 * Unfavorited: outlined circle + outlined star.
 * Favorited: filled circle + filled star with spring pop animation.
 */
@Composable
private fun FavoriteStarButton(
    isFavorite: Boolean,
    onToggle: () -> Unit
) {
    val view = LocalView.current
    val haptics = GratiaTheme.haptics
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Spring pop animation on state change
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.8f else 1.0f,
        animationSpec = spring(
            dampingRatio = 0.5f,
            stiffness = 600f
        ),
        label = "favScale"
    )

    // Circle background color
    val circleColor by animateColorAsState(
        targetValue = if (isFavorite) Color.White else Color.Transparent,
        animationSpec = tween(250),
        label = "circleColor"
    )

    // Star icon color
    val starColor by animateColorAsState(
        targetValue = if (isFavorite) Color(0xFF1C1C1E) else Color.White.copy(alpha = 0.8f),
        animationSpec = tween(250),
        label = "starColor"
    )

    Box(
        modifier = Modifier
            .size(36.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(circleColor)
            .border(
                width = 1.5.dp,
                color = Color.White.copy(alpha = if (isFavorite) 0f else 0.5f),
                shape = CircleShape
            )
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
            size = 20.dp
        )
    }
}
