package com.gratia.music.ui.selection

import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gratia.music.ui.theme.GratiaTheme
import com.gratia.music.ui.theme.Inter
import com.gratia.music.ui.theme.SpaceGrotesk

/**
 * Animated selection toolbar that slides in from the top when selection mode activates.
 *
 * Shows the selection count and provides batch action buttons:
 * Add to Queue, Add to Playlist, Delete, Select All, Close.
 */
@Composable
fun SelectionToolbar(
    selectedCount: Int,
    totalCount: Int,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onDelete: () -> Unit,
    onSelectAll: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
            .background(GratiaTheme.colors.surface)
            .statusBarsPadding()
            .padding(
                horizontal = GratiaTheme.spacing.base,
                vertical = GratiaTheme.spacing.small
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Close button
        IconButton(onClick = onClose) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Exit selection",
                tint = GratiaTheme.colors.textSecondary
            )
        }

        // Count label
        Text(
            text = "$selectedCount selected",
            fontFamily = SpaceGrotesk,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            color = GratiaTheme.colors.textPrimary,
            modifier = Modifier.weight(1f)
        )

        // Select All / Deselect toggle
        IconButton(onClick = onSelectAll) {
            Icon(
                imageVector = Icons.Default.SelectAll,
                contentDescription = if (selectedCount == totalCount) "Deselect all" else "Select all",
                tint = if (selectedCount == totalCount) GratiaTheme.colors.accent else GratiaTheme.colors.textSecondary
            )
        }

        // Add to Queue
        IconButton(onClick = onAddToQueue) {
            Icon(
                imageVector = Icons.Outlined.QueueMusic,
                contentDescription = "Add to Queue",
                tint = GratiaTheme.colors.textPrimary
            )
        }

        // Add to Playlist
        IconButton(onClick = onAddToPlaylist) {
            Icon(
                imageVector = Icons.Default.PlaylistAdd,
                contentDescription = "Add to Playlist",
                tint = GratiaTheme.colors.textPrimary
            )
        }

        // Delete
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Delete selected",
                tint = GratiaTheme.colors.error
            )
        }
    }
}
