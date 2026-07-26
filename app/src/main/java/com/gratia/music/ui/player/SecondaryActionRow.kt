package com.gratia.music.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.gratia.music.ui.theme.GratiaTheme

/**
 * Bottom action row with only two buttons: Lyrics and Queue.
 * Matches Apple Music's minimalist bottom bar.
 */
@Composable
fun SecondaryActionRow(
    hasLyrics: Boolean = true,
    onOpenLyrics: () -> Unit,
    onOpenQueue: () -> Unit,
    modifier: Modifier = Modifier,
    isLyricsActive: Boolean = false
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = GratiaTheme.spacing.large),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Lyrics
        PlayerButton(
            icon = Icons.Default.ChatBubbleOutline,
            onClick = if (hasLyrics) onOpenLyrics else { {} },
            contentDescription = "Lyrics",
            size = 48.dp,
            iconSize = 24.dp,
            tint = when {
                isLyricsActive -> Color.White
                hasLyrics -> Color.White.copy(alpha = 0.6f)
                else -> Color.White.copy(alpha = 0.2f)
            }
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
