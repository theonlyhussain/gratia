package com.gratia.music.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.gratia.music.player.RepeatMode
import com.gratia.music.ui.components.GratiaIcon
import com.gratia.music.ui.theme.GratiaTheme

/**
 * Secondary action rows beneath the primary controls.
 * Mimics Apple Music's bottom area:
 *
 * Row 1: Volume Slider (Placeholder)
 * Row 2: Lyrics · Cast · Queue
 */
@Composable
fun SecondaryActionRow(
    shuffleEnabled: Boolean, // Kept in signature, but unused in this view to match Apple Music
    repeatMode: RepeatMode, // Kept in signature, but unused
    isFavorite: Boolean, // Kept in signature, but unused (moved to header or menu usually)
    hasLyrics: Boolean = true,
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
        // Row 1: Volume Slider Placeholder
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GratiaIcon(
                imageVector = Icons.AutoMirrored.Filled.VolumeDown,
                contentDescription = "Volume down",
                tint = Color.White.copy(alpha = 0.5f),
                size = 16.dp
            )
            
            Spacer(Modifier.width(8.dp))
            
            // Reusing GratiaProgressBar for a consistent look, static at 50% for now
            GratiaProgressBar(
                progress = 0.5f,
                currentTimeMs = 0,
                durationMs = 0,
                onSeek = {},
                modifier = Modifier.weight(1f).height(24.dp), // Thinner touch area for volume
                thumbColor = Color.White
            )
            
            Spacer(Modifier.width(8.dp))
            
            GratiaIcon(
                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = "Volume up",
                tint = Color.White.copy(alpha = 0.5f),
                size = 16.dp
            )
        }

        Spacer(Modifier.height(GratiaTheme.spacing.large))

        // Row 2: Lyrics · Cast (Placeholder) · Queue
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Lyrics
            PlayerButton(
                icon = Icons.Default.ChatBubbleOutline, // Apple music uses a quote icon, ChatBubbleOutline is close
                onClick = if (hasLyrics) onOpenLyrics else { {} },
                contentDescription = "Lyrics",
                size = 48.dp,
                iconSize = 24.dp,
                tint = if (hasLyrics) Color.White.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.2f)
            )

            // Cast (Placeholder for Airplay/Cast)
            PlayerButton(
                icon = Icons.Default.Cast,
                onClick = { /* TODO: Implement Cast */ },
                contentDescription = "Cast",
                size = 48.dp,
                iconSize = 24.dp,
                tint = Color.White.copy(alpha = 0.6f)
            )

            // Queue
            PlayerButton(
                icon = Icons.AutoMirrored.Filled.QueueMusic,
                onClick = onOpenQueue,
                contentDescription = "Queue",
                size = 48.dp,
                iconSize = 24.dp,
                tint = Color.White.copy(alpha = 0.6f)
            )
        }
    }
}

