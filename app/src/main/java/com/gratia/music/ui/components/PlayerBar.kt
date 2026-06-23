package com.gratia.music.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gratia.music.player.PlayerViewModel
import com.gratia.music.ui.theme.GratiaTheme
import com.gratia.music.ui.theme.Inter
import com.gratia.music.ui.theme.SpaceGrotesk
import com.gratia.music.ui.components.liquidGlass

/**
 * Sticky bottom mini-player bar.
 * Warm premium style: Maroon/Noir surface, Cotton text, Cherry Red accents.
 */
@Composable
fun PlayerBar(playerViewModel: PlayerViewModel) {
    val currentSong by playerViewModel.currentSong.collectAsState()
    val isPlaying by playerViewModel.isPlaying.collectAsState()
    val currentTimeMs by playerViewModel.currentTimeMs.collectAsState()
    val durationMs by playerViewModel.durationMs.collectAsState()

    val song = currentSong ?: return
    val progress = if (durationMs > 0) currentTimeMs.toFloat() / durationMs.toFloat() else 0f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlass(
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                backgroundColor = GratiaTheme.colors.miniPlayerSurface.copy(alpha = 0.95f),
                borderColorStart = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.2f),
                borderColorEnd = androidx.compose.ui.graphics.Color.Transparent
            )
    ) {
        // Progress bar at top — Cherry Red
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(2.dp),
            color = GratiaTheme.colors.cherryRed,
            trackColor = GratiaTheme.colors.noirBlack.copy(alpha = 0.3f),
        )

        // Main player row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { playerViewModel.setExpandedPlayerOpen(true) }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Cover art
            CoverArtImage(
                coverArtPath = song.coverArtPath,
                title = song.title,
                artist = song.artist,
                size = 44.dp,
                cornerRadius = 8.dp,
                fontSize = 12.sp
            )

            Spacer(Modifier.width(12.dp))

            // Title + Artist — Cotton text on dark surface
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    song.title,
                    fontFamily = SpaceGrotesk,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    color = GratiaTheme.colors.textOnDark,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    song.artist,
                    fontFamily = Inter,
                    fontSize = 11.sp,
                    color = GratiaTheme.colors.textOnDarkSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Controls
            IconButton(onClick = { playerViewModel.prevSong() }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.SkipPrevious, null, tint = GratiaTheme.colors.textOnDarkSecondary, modifier = Modifier.size(18.dp))
            }

            IconButton(
                onClick = { playerViewModel.togglePlay() },
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(GratiaTheme.colors.maroon)
            ) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = GratiaTheme.colors.cotton,
                    modifier = Modifier.size(20.dp)
                )
            }

            IconButton(onClick = { playerViewModel.nextSong() }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.SkipNext, null, tint = GratiaTheme.colors.textOnDarkSecondary, modifier = Modifier.size(18.dp))
            }
        }
    }
}

fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSecs = ms / 1000
    val mins = totalSecs / 60
    val secs = totalSecs % 60
    return "$mins:${secs.toString().padStart(2, '0')}"
}

fun getInitials(title: String): String {
    return title.split(" ").filter { it.isNotBlank() }.take(2).map { it.first().uppercase() }.joinToString("")
}
