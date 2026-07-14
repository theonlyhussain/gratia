package com.gratia.music.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import com.gratia.music.ui.theme.GratiaTheme

@Composable
fun GratiaIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = GratiaTheme.icons.normal,
    tint: Color = GratiaTheme.colors.textPrimary
) {
    Icon(
        imageVector = imageVector,
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        tint = tint
    )
}

@Composable
fun GratiaIcon(
    painterId: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = GratiaTheme.icons.normal,
    tint: Color = GratiaTheme.colors.textPrimary
) {
    Icon(
        painter = painterResource(id = painterId),
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        tint = tint
    )
}
