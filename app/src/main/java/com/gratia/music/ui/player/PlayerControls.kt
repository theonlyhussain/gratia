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
import androidx.compose.ui.draw.drawBehind
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
            glowColor = glowColor
        )

        Spacer(Modifier.width(GratiaTheme.spacing.large)) // 32dp

        // Next
        PlayerButton(
            icon = Icons.Default.SkipNext,
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
 * The hero play/pause button — largest element, filled circle,
 * soft glow, shadow, scale animation, icon morph.
 */
@Composable
private fun PlayPauseButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    glowColor: Color
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val view = LocalView.current
    val haptics = GratiaTheme.haptics
    val motion = GratiaTheme.motion

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = tween(motion.fast, easing = motion.standardEasing),
        label = "playBtnScale"
    )

    val glowAlpha by animateFloatAsState(
        targetValue = if (isPlaying) 0.25f else 0.1f,
        animationSpec = tween(motion.normal),
        label = "playGlow"
    )

    Box(
        modifier = Modifier
            .size(72.dp) // Intentionally slightly larger than hero spacing (64dp) for impact
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            // Ambient glow behind the button
            .drawBehind {
                drawCircle(
                    color = glowColor.copy(alpha = glowAlpha),
                    radius = size.minDimension * 0.85f
                )
            }
            .shadow(
                elevation = GratiaTheme.elevation.hero, // 16dp
                shape = GratiaTheme.shapes.pill,
                spotColor = Color.Black.copy(alpha = 0.4f),
                ambientColor = Color.Black.copy(alpha = 0.2f)
            )
            .clip(GratiaTheme.shapes.pill)
            .background(Color.White)
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
                tint = Color.Black,
                size = GratiaTheme.icons.hero // 36dp
            )
        }
    }
}
