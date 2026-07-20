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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gratia.music.GratiaApp
import com.gratia.music.data.repository.SongRepository
import com.gratia.music.player.PlayerViewModel
import com.gratia.music.ui.components.CoverArtImage
import com.gratia.music.ui.components.SongRow
import com.gratia.music.ui.components.GratiaText
import com.gratia.music.ui.components.GratiaIcon
import com.gratia.music.ui.components.GratiaIconButton
import com.gratia.music.ui.components.GratiaButton
import com.gratia.music.ui.theme.GratiaTheme

@Composable
fun ArtistDetailScreen(
    artistName: String,
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit
) {
    val songRepo = remember { SongRepository(GratiaApp.instance.database.songDao()) }
    val allSongs by songRepo.getAllSongs().collectAsState(initial = emptyList())
    val artistSongs = remember(allSongs, artistName) { allSongs.filter { it.artist == artistName }.sortedBy { it.title } }
    
    val currentSong by playerViewModel.currentSong.collectAsState()
    val isPlaying by playerViewModel.isPlaying.collectAsState()

    val coverArtPath = artistSongs.firstOrNull()?.coverArtPath

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
                .padding(horizontal = GratiaTheme.spacing.medium, vertical = GratiaTheme.spacing.medium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GratiaIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                onClick = onBack,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(GratiaTheme.colors.surface),
                tint = GratiaTheme.colors.textSecondary
            )
        }

        LazyColumn(
            contentPadding = PaddingValues(bottom = GratiaTheme.spacing.heroLarge)
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = GratiaTheme.spacing.large),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CoverArtImage(
                        coverArtPath = coverArtPath,
                        title = artistName,
                        artist = artistName,
                        size = 240.dp,
                        cornerRadius = 120.dp, // Circle for Artist
                        fontSize = 40.sp
                    )
                    Spacer(Modifier.height(GratiaTheme.spacing.large))
                    GratiaText(
                        text = artistName,
                        style = GratiaTheme.typography.title,
                        color = GratiaTheme.colors.textPrimary,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                    GratiaText(
                        text = "${artistSongs.size} songs",
                        style = GratiaTheme.typography.caption,
                        color = GratiaTheme.colors.textSecondary,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(GratiaTheme.spacing.medium))
                    
                    // Apple Style Play Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = GratiaTheme.spacing.medium),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        GratiaButton(
                            text = "Play",
                            icon = Icons.Default.PlayArrow,
                            onClick = { if (artistSongs.isNotEmpty()) playerViewModel.playSong(artistSongs.first(), artistSongs) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(GratiaTheme.spacing.large))
                }
            }

            itemsIndexed(artistSongs, key = { _, s -> s.id }) { index, song ->
                SongRow(
                    song = song,
                    index = index,
                    isActive = currentSong?.id == song.id,
                    isPlaying = currentSong?.id == song.id && isPlaying,
                    onClick = { playerViewModel.playSong(song, artistSongs) },
                    modifier = Modifier.padding(horizontal = GratiaTheme.spacing.large)
                )
            }
        }
    }
}
