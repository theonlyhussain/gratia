package com.gratia.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gratia.music.GratiaApp
import com.gratia.music.player.PlayerViewModel
import com.gratia.music.ui.components.CollageArtwork
import com.gratia.music.ui.components.SongRow
import com.gratia.music.ui.theme.GratiaTheme
import com.gratia.music.ui.theme.Inter
import com.gratia.music.ui.theme.SpaceGrotesk

@Composable
fun PlaylistDetailScreen(
    playlistId: String,
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit
) {
    val playlistDao = remember { GratiaApp.instance.database.playlistDao() }
    val playlistFlow by playlistDao.getPlaylist(playlistId).collectAsState(initial = null)
    val playlistSongs by playlistDao.getSongsForPlaylist(playlistId).collectAsState(initial = emptyList())
    
    val currentSong by playerViewModel.currentSong.collectAsState()
    val isPlaying by playerViewModel.isPlaying.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GratiaTheme.colors.background)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(GratiaTheme.colors.surface)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = GratiaTheme.colors.textSecondary)
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(bottom = 120.dp)
        ) {
            item {
                val playlist = playlistFlow
                if (playlist != null) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val paths = playlistSongs.take(4).map { it.coverArtPath }
                        CollageArtwork(
                            paths = paths,
                            size = 180.dp,
                            cornerRadius = 16.dp
                        )
                        Spacer(Modifier.height(24.dp))
                        Text(
                            text = playlist.name,
                            fontFamily = SpaceGrotesk,
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp,
                            color = GratiaTheme.colors.textPrimary,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Text(
                            text = "${playlistSongs.size} songs",
                            fontFamily = Inter,
                            fontSize = 14.sp,
                            color = GratiaTheme.colors.textSecondary,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(Modifier.height(16.dp))
                        
                        // Play Button
                        Button(
                            onClick = { if (playlistSongs.isNotEmpty()) playerViewModel.playSong(playlistSongs.first(), playlistSongs) },
                            modifier = Modifier.fillMaxWidth(0.6f).height(48.dp),
                            shape = RoundedCornerShape(24.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = GratiaTheme.colors.accent, contentColor = GratiaTheme.colors.background)
                        ) {
                            Icon(Icons.Default.PlayArrow, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Play", fontFamily = Inter, fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.height(32.dp))
                    }
                }
            }

            if (playlistSongs.isEmpty()) {
                item {
                    Text(
                        text = "Add songs to this playlist from your library",
                        fontFamily = Inter,
                        fontSize = 14.sp,
                        color = GratiaTheme.colors.textSecondary,
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                itemsIndexed(playlistSongs, key = { _, s -> s.id }) { index, song ->
                    SongRow(
                        song = song,
                        index = index,
                        isActive = currentSong?.id == song.id,
                        isPlaying = currentSong?.id == song.id && isPlaying,
                        onClick = { playerViewModel.playSong(song, playlistSongs) },
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            }
        }
    }
}
