package com.gratia.music.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.style.TextAlign
import com.gratia.music.ui.theme.GratiaTheme

/**
 * GDL Loading State Component
 * Handles various semantic loading states: Loading, Refreshing, Downloading, Importing, Scanning, Buffering.
 */
@Composable
fun GratiaLoadingState(
    message: String,
    modifier: Modifier = Modifier,
    type: LoadingType = LoadingType.Spinner
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = GratiaTheme.spacing.heroSmall, horizontal = GratiaTheme.spacing.mediumLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        when (type) {
            LoadingType.Spinner -> {
                CircularProgressIndicator(
                    color = GratiaTheme.colors.accent,
                    trackColor = GratiaTheme.colors.progressTrack,
                    strokeWidth = GratiaTheme.spacing.micro
                )
            }
            LoadingType.Scanning -> {
                val infiniteTransition = rememberInfiniteTransition(label = "scanning")
                val rotation by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1500, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "scanning_rotate"
                )
                GratiaIcon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = "Scanning",
                    size = GratiaTheme.icons.hero,
                    tint = GratiaTheme.colors.accent,
                    modifier = Modifier.rotate(rotation)
                )
            }
            // Add other distinct animations for Downloading, Importing, Buffering later
            else -> {
                CircularProgressIndicator(
                    color = GratiaTheme.colors.accent,
                    trackColor = GratiaTheme.colors.progressTrack,
                    strokeWidth = GratiaTheme.spacing.micro
                )
            }
        }
        
        Spacer(modifier = Modifier.height(GratiaTheme.spacing.mediumLarge))
        
        GratiaText(
            text = message,
            style = GratiaTheme.typography.section,
            color = GratiaTheme.colors.textSecondary,
            textAlign = TextAlign.Center
        )
    }
}

enum class LoadingType {
    Spinner,
    Refreshing,
    Downloading,
    Importing,
    Scanning,
    Buffering
}
