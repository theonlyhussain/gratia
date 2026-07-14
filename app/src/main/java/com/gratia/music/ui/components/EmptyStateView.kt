package com.gratia.music.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

@Composable
fun EmptyStateView(
    icon: ImageVector,
    headline: String,
    description: String,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    GratiaEmptyState(
        icon = icon,
        headline = headline,
        description = description,
        actionLabel = actionLabel,
        onActionClick = onActionClick,
        modifier = modifier
    )
}
