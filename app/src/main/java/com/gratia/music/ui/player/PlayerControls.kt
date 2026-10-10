package com.gratia.music.ui.player

import android.view.View
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.gratia.music.ui.components.GratiaIcon
import com.gratia.music.ui.theme.GratiaTheme

/**
 * Primary playback controls: Previous — Play/Pause — Next.
 *
 * Design details:
 * - Centered layout with generous spacing
 * - Play/Pause: largest element (72dp), filled white circle, soft glow + shadow
 * - Play/Pause icon morphs between states with crossfade
 * - Previous/Next: smaller (48dp), subtle fade when at queue edge
 * - Scale animation on tap (GDL normal, no bounce)
 * - Haptic feedback on every interaction
 */
@Composable
fun PlayerControls(
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    glowColor: Color = Color.White,
    canGoPrevious: Boolean = true,
    canGoNext: Boolean = true,
    /**
     * True while the selected track is being fetched — resolving a stream or
     * buffering it. The hero control becomes a spinner for exactly that

     * window, in the same 72dp footprint, so loading is communicated from the
     * place the listener's eye already rests (spec 39–41).
     */
    isLoading: Boolean = false
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Previous
        PlayerButton(
            icon = Icons.Rounded.FastRewind,
            onClick = onPrevious,
            contentDescription = "Previous",
            size = GratiaTheme.spacing.heroMedium, // 56dp
            iconSize = GratiaTheme.spacing.large, // 32dp
            tint = Color.White,
            enabled = canGoPrevious
        )

        Spacer(Modifier.width(GratiaTheme.spacing.large)) // 32dp

        // Play / Pause — hero button
        PlayPauseButton(
            isPlaying = isPlaying,
            onClick = onPlayPause,
            glowColor = glowColor,
            isLoading = isLoading
        )

        Spacer(Modifier.width(GratiaTheme.spacing.large)) // 32dp

        // Next
        PlayerButton(
            icon = Icons.Rounded.FastForward,
            onClick = onNext,
            contentDescription = "Next",
            size = GratiaTheme.spacing.heroMedium,
            iconSize = GratiaTheme.spacing.large,
            tint = Color.White,
            enabled = canGoNext
        )
    }
}

/**
 * The hero play/pause button — clean, standalone icon.
 * No circular background, matching the minimal inspiration design.
 * Fast 150ms crossfade for responsive 120Hz-friendly transitions.
 */
@Composable
private fun PlayPauseButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    glowColor: Color,
    isLoading: Boolean = false
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val view = LocalView.current
    val haptics = GratiaTheme.haptics
    val motion = GratiaTheme.motion

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.85f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 600f),
        label = "playBtnScale"
    )

    Box(
        modifier = Modifier
            .size(72.dp)
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
        // Loading owns the control while the chosen track is on its way.
        // Same footprint, same place — the icon crossfade below simply does
        // not run until a state that can play or pause exists.
        if (isLoading) {
            androidx.compose.material3.CircularProgressIndicator(
                modifier = Modifier.size(40.dp),
                strokeWidth = 3.dp,
                color = Color.White
            )
        } else {
            // The shared glyph. Same component the mini player renders — the
            // expanded surface differs only in size (48 vs 24), never in icon
            // shape or animation, so expanding the player cannot change what
            // the control looks like.
            PlayPauseControl(
                isPlaying = isPlaying,
                isLoading = false, // loading is handled by the branch above
                size = 48.dp,
                tint = Color.White,
            )
        }
    }
}
