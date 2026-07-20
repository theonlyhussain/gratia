package com.gratia.music.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import com.gratia.music.ui.theme.Easings
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import com.gratia.music.ui.theme.GratiaTheme

/**
 * Text that crossfades when its content changes.
 *
 * Used wherever song title, artist, or album text would otherwise snap
 * during song transitions. The fade duration is intentionally calm (GDL normal).
 */
@Composable
fun AnimatedText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = GratiaTheme.colors.textPrimary,
    maxLines: Int = 1,
    overflow: TextOverflow = TextOverflow.Ellipsis,
    fadeDurationMs: Int? = null,
    isMarquee: Boolean = false
) {
    val motion = GratiaTheme.motion
    val duration = fadeDurationMs ?: motion.normal

    AnimatedContent(
        targetState = text,
        transitionSpec = {
            fadeIn(animationSpec = tween(duration, easing = Easings.FastEaseOut)) togetherWith
                fadeOut(animationSpec = tween(duration, easing = Easings.FastEaseOut))
        },
        label = "animatedText",
        modifier = modifier
    ) { animatedText ->
        if (isMarquee) {
            MarqueeText(
                text = animatedText,
                color = color,
                style = style
            )
        } else {
            GratiaText(
                text = animatedText,
                color = color,
                style = style,
                maxLines = maxLines,
                overflow = overflow
            )
        }
    }
}
