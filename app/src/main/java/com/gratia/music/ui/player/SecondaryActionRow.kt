package com.gratia.music.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.gratia.music.player.RepeatMode
import com.gratia.music.ui.theme.GratiaTheme

/**
 * Secondary action rows beneath the primary controls.
 *
 * Row 1: Shuffle · Repeat · Favorite · Queue
 * Row 2: Lyrics
 *
 * All buttons use [PlayerButton] for consistent:
 * - Size (GDL heroSmall 48dp)
 * - Spacing
 * - Animation (press-scale + normal speed)
 * - Haptic feedback
 *
 * Active states (shuffle on, repeat on, favorited) use accent color.
 */
@Composable
fun SecondaryActionRow(
    shuffleEnabled: Boolean,
    repeatMode: RepeatMode,
    isFavorite: Boolean,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onToggleFavorite: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenLyrics: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = GratiaTheme.colors.accent
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = GratiaTheme.spacing.large), // 32dp
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Row 1: Shuffle · Repeat · Favorite · Queue
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Shuffle
            PlayerButton(
                icon = Icons.Default.Shuffle,
                onClick = onToggleShuffle,
                contentDescription = "Shuffle",
                size = GratiaTheme.spacing.heroSmall,
                iconSize = GratiaTheme.icons.normal,
                tint = if (shuffleEnabled) accentColor else Color.White.copy(alpha = 0.5f)
            )

            // Repeat
            PlayerButton(
                icon = when (repeatMode) {
                    RepeatMode.ONE -> Icons.Default.RepeatOne
                    else -> Icons.Default.Repeat
                },
                onClick = onCycleRepeat,
                contentDescription = "Repeat",
                size = GratiaTheme.spacing.heroSmall,
                iconSize = GratiaTheme.icons.normal,
                tint = if (repeatMode != RepeatMode.OFF) accentColor else Color.White.copy(alpha = 0.5f)
            )

            // Favorite
            PlayerButton(
                icon = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                onClick = onToggleFavorite,
                contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                size = GratiaTheme.spacing.heroSmall,
                iconSize = GratiaTheme.icons.normal,
                tint = if (isFavorite) accentColor else Color.White.copy(alpha = 0.5f)
            )

            // Queue
            PlayerButton(
                icon = Icons.AutoMirrored.Filled.QueueMusic,
                onClick = onOpenQueue,
                contentDescription = "Queue",
                size = GratiaTheme.spacing.heroSmall,
                iconSize = GratiaTheme.icons.normal,
                tint = Color.White.copy(alpha = 0.5f)
            )
        }

        Spacer(Modifier.height(GratiaTheme.spacing.extraSmall))

        // Row 2: Lyrics (centered)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            PlayerButton(
                icon = Icons.Default.Lyrics,
                onClick = onOpenLyrics,
                contentDescription = "Lyrics",
                size = GratiaTheme.spacing.heroSmall,
                iconSize = GratiaTheme.icons.normal,
                tint = Color.White.copy(alpha = 0.5f)
            )
        }
    }
}
