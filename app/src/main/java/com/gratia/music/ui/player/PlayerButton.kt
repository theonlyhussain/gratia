package com.gratia.music.ui.player

import android.view.View
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gratia.music.ui.components.GratiaIcon
import com.gratia.music.ui.theme.GratiaTheme

/**
 * Reusable animated icon button for all player controls.
 *
 * Every player button shares:
 * - Press scale animation (0.95 → 1.0, GDL normal)
 * - Configurable size, tint, and glow
 * - Light Haptic feedback on tap
 * - Disabled state with alpha fade (0.3)
 * - Large touch target (minimum 48dp)
 */
@Composable
fun PlayerButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    size: Dp = GratiaTheme.spacing.heroSmall, // 48dp
    iconSize: Dp = GratiaTheme.icons.normal,
    tint: Color = Color.White,
    enabled: Boolean = true,
    haptic: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val view = LocalView.current
    val haptics = GratiaTheme.haptics
    val motion = GratiaTheme.motion

    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.95f else 1f,
        animationSpec = tween(durationMillis = motion.fast, easing = motion.standardEasing),
        label = "btnScale"
    )

    val alpha = if (enabled) 1f else 0.3f

    Box(
        modifier = modifier
            .size(size.coerceAtLeast(48.dp)) // Accessibility: 48dp minimum
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else Modifier
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = {
                    if (haptic) {
                        haptics.light(view)
                    }
                    onClick()
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        GratiaIcon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            size = iconSize
        )
    }
}
