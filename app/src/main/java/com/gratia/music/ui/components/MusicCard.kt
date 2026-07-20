package com.gratia.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gratia.music.data.model.SongEntity
import com.gratia.music.ui.theme.GratiaTheme
import com.gratia.music.ui.theme.Inter
import com.gratia.music.ui.theme.SpaceGrotesk

/**
 * Horizontal shelf music card.
 * Translates MusicCard.tsx from the web prototype.
 */
@Composable
fun MusicCard(
    song: SongEntity,
    isActive: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(148.dp)
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null, // Custom scale animation should be applied for press feedback ideally
                onClick = onClick
            )
    ) {
        // Cover art
        CoverArtImage(
            coverArtPath = song.coverArtPath,
            title = song.title,
            artist = song.artist,
            size = 148.dp,
            cornerRadius = 8.dp, // Apple Music uses slight corner radii for albums
            fontSize = 22.sp,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f)
        )

        Spacer(Modifier.height(8.dp))

        Text(
            song.title,
            style = GratiaTheme.typography.body,
            fontWeight = FontWeight.Medium,
            color = if (isActive) GratiaTheme.colors.accent else GratiaTheme.colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            song.artist,
            style = GratiaTheme.typography.caption,
            color = GratiaTheme.colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
