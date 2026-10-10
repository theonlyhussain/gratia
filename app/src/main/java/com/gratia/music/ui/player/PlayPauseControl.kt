package com.gratia.music.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.CircularProgressIndicator
import com.gratia.music.ui.components.GratiaIcon

/**
 * The one play/pause glyph every player surface shows.
 *
 * Before this, the mini player used `Icons.Default.PlayArrow/Pause` and the
 * expanded player used `Icons.Rounded.*` — two different shapes for the same
 * control, so the icon visibly changed shape in the instant the player
 * expanded. Both surfaces now render this component with only the size
 * differing, which is the whole degree of freedom the rule allows: the icon
 * shape, the crossfade, and the loading behaviour are shared.
 *
 * Loading is part of the component rather than a caller branch, because the
 * two callers branch differently on what counts as "loading" and one of them
 * got it wrong: the mini player showed a spinner whenever a resolution was in
 * flight even if it was for a *different track* than the one displayed (the
 * `pageSong.id == currentSong?.id` check guarded it, but it read as a separate
 * decision at each call site). Here the caller passes the single resolved
 * boolean; the component guarantees the spinner occupies exactly the icon's
 * box, so the control never changes size when states swap.
 */
@Composable
fun PlayPauseControl(
    isPlaying: Boolean,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    tint: Color,
) {
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        if (isLoading) {
            // Slightly smaller than the box so the spinner reads as content
            // within the control, not as the control itself.
            CircularProgressIndicator(
                modifier = Modifier.size(size * 0.75f),
                strokeWidth = size * 0.08f,
                color = tint,
            )
        } else {
            AnimatedContent(
                targetState = isPlaying,
                transitionSpec = {
                    (fadeIn(tween(150)) + scaleIn(tween(150), initialScale = 0.85f)) togetherWith
                        (fadeOut(tween(100)) + scaleOut(tween(100), targetScale = 0.85f))
                },
                label = "sharedPlayPause",
            ) { playing ->
                GratiaIcon(
                    // Icons.Rounded on both paths: the mini player previously
                    // used the sharp Default set, which is what made the glyph
                    // jump between surfaces.
                    imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (playing) "Pause" else "Play",
                    tint = tint,
                    size = size,
                )
            }
        }
    }
}
