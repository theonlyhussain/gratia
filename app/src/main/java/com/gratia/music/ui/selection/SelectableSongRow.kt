package com.gratia.music.ui.selection

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.gratia.music.data.model.SongEntity
import com.gratia.music.ui.components.SongRow
import com.gratia.music.ui.theme.GratiaTheme

/**
 * Wraps [SongRow] with multi-selection behaviour.
 *
 * - **Normal mode:** tap plays the song, long-press enters selection mode and selects it.
 * - **Selection mode:** tap toggles selection, a leading animated checkbox appears.
 *
 * Selected rows get a subtle accent background tint and the checkbox
 * entry uses a spring animation with slight overshoot for premium feel.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SelectableSongRow(
    song: SongEntity,
    index: Int,
    isActive: Boolean,
    isPlaying: Boolean,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onPlay: () -> Unit,
    onLongPress: () -> Unit,
    onToggleSelection: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Scale bounce on selection toggle
    val selectionScale by animateFloatAsState(
        targetValue = if (isSelected) 0.97f else 1.0f,
        animationSpec = spring(
            dampingRatio = 0.6f,
            stiffness = Spring.StiffnessMedium
        ),
        label = "selectionScale"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = selectionScale
                scaleY = selectionScale
            }
            .then(
                if (isSelected) {
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(GratiaTheme.colors.accent.copy(alpha = 0.08f))
                } else {
                    Modifier
                }
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Leading checkbox — appears only in selection mode
        AnimatedVisibility(
            visible = isSelectionMode,
            enter = scaleIn(
                animationSpec = spring(
                    dampingRatio = 0.6f,
                    stiffness = Spring.StiffnessMedium
                )
            ) + fadeIn(),
            exit = scaleOut() + fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .padding(start = GratiaTheme.spacing.mediumSmall)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) GratiaTheme.colors.accent
                        else GratiaTheme.colors.surfaceHover
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = GratiaTheme.colors.background,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // The actual SongRow
        SongRow(
            song = song,
            index = index,
            isActive = isActive && !isSelectionMode,
            isPlaying = isPlaying && !isSelectionMode,
            onClick = {
                if (isSelectionMode) {
                    onToggleSelection()
                } else {
                    onPlay()
                }
            },
            onLongClick = {
                if (!isSelectionMode) {
                    onLongPress()
                }
            },
            modifier = Modifier.weight(1f)
        )
    }
}
