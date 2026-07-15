package com.gratia.music.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gratia.music.ui.components.AnimatedText
import com.gratia.music.ui.components.GratiaIconButton
import com.gratia.music.ui.theme.GratiaTheme

/**
 * Left-aligned song information hierarchy for the expanded player.
 * Mimics Apple Music's header.
 *
 * Visual hierarchy (top → bottom):
 * 1. Song title — large, bold, bright
 * 2. Artist — medium, slightly muted
 */
@Composable
fun PlayerHeader(
    title: String,
    artist: String,
    album: String?,
    playingFrom: String = "GRATIA",
    onClickTitle: () -> Unit = {},
    onClickArtist: () -> Unit = {},
    onClickAlbum: () -> Unit = {},
    onMoreClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = GratiaTheme.spacing.large),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            // Song title — hero text
            AnimatedText(
                text = title,
                style = GratiaTheme.typography.largeTitle,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fadeDurationMs = GratiaTheme.motion.slow,
                modifier = Modifier.clickable { onClickTitle() }
            )

            Spacer(Modifier.height(GratiaTheme.spacing.micro)) // 2dp

            // Artist
            AnimatedText(
                text = artist,
                style = GratiaTheme.typography.section,
                color = Color.White.copy(alpha = 0.55f),
                maxLines = 1,
                fadeDurationMs = GratiaTheme.motion.slow,
                modifier = Modifier.clickable { onClickArtist() }
            )
        }
        
        Spacer(Modifier.width(16.dp))

        // More options button (mimicking Apple Music's layout)
        GratiaIconButton(
            icon = Icons.Default.MoreHoriz,
            onClick = onMoreClick,
            contentDescription = "More",
            tint = Color.White.copy(alpha = 0.8f),
            size = GratiaTheme.icons.normal,
            modifier = Modifier.padding(8.dp)
        )
    }
}

