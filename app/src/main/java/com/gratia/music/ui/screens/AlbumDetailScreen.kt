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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gratia.music.GratiaApp
import com.gratia.music.data.repository.SongRepository
import com.gratia.music.player.PlayerViewModel
import com.gratia.music.ui.components.CoverArtImage
import com.gratia.music.ui.components.SongRow
import com.gratia.music.ui.theme.GratiaTheme
import com.gratia.music.ui.theme.Inter
import com.gratia.music.ui.theme.SpaceGrotesk

@Composable
fun AlbumDetailScreen(
    albumName: String,
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit
) {
    val songRepo = remember { SongRepository(GratiaApp.instance.database.songDao()) }
    val allSongs by songRepo.getAllSongs().collectAsState(initial = emptyList())
    val albumSongs = remember(allSongs, albumName) { allSongs.filter { it.album == albumName }.sortedBy { it.title } }
    
    val currentSong by playerViewModel.currentSong.collectAsState()
    val isPlaying by playerViewModel.isPlaying.collectAsState()

    val artistName = albumSongs.firstOrNull()?.artist ?: "Unknown Artist"
    val coverArtPath = albumSongs.firstOrNull()?.coverArtPath

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
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CoverArtImage(
                        coverArtPath = coverArtPath,
                        title = albumName,
                        artist = artistName,
                        size = 200.dp,
                        cornerRadius = 16.dp,
                        fontSize = 40.sp
                    )
                    Spacer(Modifier.height(24.dp))
                    Text(
                        text = albumName,
                        fontFamily = SpaceGrotesk,
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp,
                        color = GratiaTheme.colors.textPrimary,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Text(
                        text = artistName,
                        fontFamily = Inter,
                        fontSize = 16.sp,
                        color = GratiaTheme.colors.textSecondary,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(Modifier.height(16.dp))
                    
                    // Play Button
                    Button(
                        onClick = { if (albumSongs.isNotEmpty()) playerViewModel.playSong(albumSongs.first(), albumSongs) },
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

            itemsIndexed(albumSongs, key = { _, s -> s.id }) { index, song ->
                SongRow(
                    song = song,
                    index = index,
                    isActive = currentSong?.id == song.id,
                    isPlaying = currentSong?.id == song.id && isPlaying,
                    onClick = { playerViewModel.playSong(song, albumSongs) },
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }
        }
    }
}
