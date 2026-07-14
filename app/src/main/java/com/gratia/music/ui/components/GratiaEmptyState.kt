package com.gratia.music.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import com.gratia.music.ui.theme.GratiaTheme
import kotlinx.coroutines.delay

/**
 * GDL Empty State Component
 */
@Composable
fun GratiaEmptyState(
    icon: ImageVector,
    headline: String,
    description: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(150)
        isVisible = true
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(animationSpec = tween(GratiaTheme.motion.normal)) + 
                slideInVertically(
                    initialOffsetY = { 40 },
                    animationSpec = tween(GratiaTheme.motion.normal, easing = GratiaTheme.motion.standardEasing)
                )
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = GratiaTheme.spacing.heroSmall, horizontal = GratiaTheme.spacing.mediumLarge),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            GratiaIcon(
                imageVector = icon,
                contentDescription = headline,
                size = GratiaTheme.icons.hero,
                tint = GratiaTheme.colors.textSecondary
            )
            
            Spacer(modifier = Modifier.height(GratiaTheme.spacing.mediumLarge))
            
            GratiaText(
                text = headline,
                style = GratiaTheme.typography.title,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(GratiaTheme.spacing.small))
            
            GratiaText(
                text = description,
                style = GratiaTheme.typography.body,
                color = GratiaTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = GratiaTheme.spacing.base)
            )
            
            if (actionLabel != null && onActionClick != null) {
                Spacer(modifier = Modifier.height(GratiaTheme.spacing.large))
                GratiaButton(
                    text = actionLabel,
                    onClick = onActionClick,
                    backgroundColor = GratiaTheme.colors.accent,
                    contentColor = GratiaTheme.colors.surface
                )
            }
        }
    }
}
