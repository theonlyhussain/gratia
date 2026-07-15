package com.gratia.music.ui.player

import android.view.View
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.gratia.music.ui.components.GratiaIcon
import com.gratia.music.ui.theme.GratiaTheme

/**
 * Primary playback controls: Previous — Play/Pause — Next.
 * Mimics Apple Music's large, icon-only style.
 */
@Composable
fun PlayerControls(
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    glowColor: Color = Color.White, // Kept for signature compatibility
    canGoPrevious: Boolean = true,
    canGoNext: Boolean = true
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Previous
        PlayerButton(
            icon = Icons.Default.SkipPrevious,
            onClick = onPrevious,
            contentDescription = "Previous",
            size = 64.dp, // Large touch area
            iconSize = 40.dp, // Large icon
            tint = Color.White,
            enabled = canGoPrevious
        )

        Spacer(Modifier.width(GratiaTheme.spacing.large)) // 32dp

        // Play / Pause — hero button
        PlayPauseButton(
            isPlaying = isPlaying,
            onClick = onPlayPause
        )

        Spacer(Modifier.width(GratiaTheme.spacing.large)) // 32dp

        // Next
        PlayerButton(
            icon = Icons.Default.SkipNext,
            onClick = onNext,
            contentDescription = "Next",
            size = 64.dp,
            iconSize = 40.dp,
            tint = Color.White,
            enabled = canGoNext
        )
    }
}

/**
 * The hero play/pause button.
 * Large, icon-only, with press scale animation.
 */
@Composable
private fun PlayPauseButton(
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val view = LocalView.current
    val haptics = GratiaTheme.haptics
    val motion = GratiaTheme.motion

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = tween(motion.fast, easing = motion.standardEasing),
        label = "playBtnScale"
    )

    Box(
        modifier = Modifier
            .size(80.dp) // Large touch area
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
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
        // Crossfade between Play and Pause icons
        AnimatedContent(
            targetState = isPlaying,
            transitionSpec = {
                (fadeIn(tween(motion.normal)) + scaleIn(tween(motion.normal), initialScale = 0.8f)) togetherWith
                    (fadeOut(tween(motion.fast)) + scaleOut(tween(motion.fast), targetScale = 0.8f))
            },
            label = "playPauseIcon"
        ) { playing ->
            GratiaIcon(
                imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (playing) "Pause" else "Play",
                tint = Color.White, // White icon for Apple Music style over dark blurred background
                size = 52.dp // Very large icon
            )
        }
    }
}

