package com.gratia.music.ui.components

import android.view.View
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import com.gratia.music.ui.theme.GratiaTheme

/**
 * GDL Interaction Modifier
 * Tap -> Scale down to 95% -> Release -> Scale to 100% -> Haptic (Light)
 */
fun Modifier.clickableWithScale(
    onClick: () -> Unit
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val view = LocalView.current
    val haptics = GratiaTheme.haptics
    val motion = GratiaTheme.motion

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = 0.6f, 
            stiffness = 400f
        ),
        label = "press_scale"
    )

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            // Optional: slight opacity drop for more feedback
            alpha = if (isPressed) 0.97f else 1f
        }
        .clickable(
            interactionSource = interactionSource,
            indication = null, // Removed default ripple in favor of scale feedback
            onClick = {
                haptics.light(view)
                onClick()
            }
        )
}
