package com.gratia.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import com.gratia.music.ui.theme.GratiaTheme

/**
 * GDL Card
 * Base component for all cards (Albums, Playlists, etc).
 * Enforces correct corner radius and interaction scaling.
 */
@Composable
fun GratiaCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = GratiaTheme.shapes.large,
    backgroundColor: Color = GratiaTheme.colors.surface,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(backgroundColor)
            .clickableWithScale(onClick = onClick),
        content = content
    )
}

/**
 * GDL Static Card (No click action)
 */
@Composable
fun GratiaCardStatic(
    modifier: Modifier = Modifier,
    shape: Shape = GratiaTheme.shapes.large,
    backgroundColor: Color = GratiaTheme.colors.surface,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(backgroundColor),
        content = content
    )
}
