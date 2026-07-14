package com.gratia.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.gratia.music.ui.theme.GratiaTheme

/**
 * GDL Primary Button
 * Uses `clickableWithScale` for GDL Interaction Language.
 */
@Composable
fun GratiaButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    contentDescription: String? = null,
    backgroundColor: Color = GratiaTheme.colors.surfaceHover,
    contentColor: Color = GratiaTheme.colors.textPrimary,
    contentPadding: PaddingValues = PaddingValues(horizontal = GratiaTheme.spacing.mediumLarge, vertical = GratiaTheme.spacing.mediumSmall)
) {
    Box(
        modifier = modifier
            .clip(GratiaTheme.shapes.small)
            .background(backgroundColor)
            .clickableWithScale(onClick = onClick)
            .padding(contentPadding),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                GratiaIcon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    size = GratiaTheme.icons.small,
                    tint = contentColor
                )
                Spacer(modifier = Modifier.width(GratiaTheme.spacing.small))
            }
            GratiaText(
                text = text,
                style = GratiaTheme.typography.section,
                color = contentColor
            )
        }
    }
}

/**
 * GDL Icon Button
 * Pill shaped by default, uses GDL Interaction Language.
 */
@Composable
fun GratiaIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    backgroundColor: Color = Color.Transparent,
    tint: Color = GratiaTheme.colors.textPrimary,
    size: androidx.compose.ui.unit.Dp = GratiaTheme.icons.normal,
    padding: PaddingValues = PaddingValues(GratiaTheme.spacing.small)
) {
    Box(
        modifier = modifier
            .clip(GratiaTheme.shapes.pill)
            .background(backgroundColor)
            .clickableWithScale(onClick = onClick)
            .padding(padding),
        contentAlignment = Alignment.Center
    ) {
        GratiaIcon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            size = size
        )
    }
}

@Composable
fun GratiaIconButton(
    painterId: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    backgroundColor: Color = Color.Transparent,
    tint: Color = GratiaTheme.colors.textPrimary,
    size: androidx.compose.ui.unit.Dp = GratiaTheme.icons.normal,
    padding: PaddingValues = PaddingValues(GratiaTheme.spacing.small)
) {
    Box(
        modifier = modifier
            .clip(GratiaTheme.shapes.pill)
            .background(backgroundColor)
            .clickableWithScale(onClick = onClick)
            .padding(padding),
        contentAlignment = Alignment.Center
    ) {
        GratiaIcon(
            painterId = painterId,
            contentDescription = contentDescription,
            tint = tint,
            size = size
        )
    }
}
