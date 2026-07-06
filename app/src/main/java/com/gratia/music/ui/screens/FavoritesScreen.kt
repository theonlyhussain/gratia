package com.gratia.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gratia.music.GratiaApp
import com.gratia.music.data.repository.SongRepository
import com.gratia.music.player.PlayerViewModel
import com.gratia.music.ui.components.SongRow
import com.gratia.music.ui.theme.GratiaTheme
import com.gratia.music.ui.theme.Inter
import com.gratia.music.ui.theme.SpaceGrotesk

@Composable
fun FavoritesScreen(playerViewModel: PlayerViewModel) {
    val songRepo = remember { SongRepository(GratiaApp.instance.database.songDao()) }
    val favorites by songRepo.getFavorites().collectAsState(initial = emptyList())
    val currentSong by playerViewModel.currentSong.collectAsState()
    val isPlaying by playerViewModel.isPlaying.collectAsState()

    val filters = listOf("Songs", "Albums", "Playlists")
    var selectedFilter by remember { mutableStateOf(filters[0]) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GratiaTheme.colors.background)
    ) {
        Text(
            "Favorites",
            fontFamily = SpaceGrotesk,
            fontWeight = FontWeight.Bold,
            fontSize = 28.sp,
            color = GratiaTheme.colors.textPrimary,
            modifier = Modifier.padding(start = 24.dp, top = 20.dp, end = 24.dp, bottom = 12.dp)
        )

        // Filter Chips
        androidx.compose.foundation.lazy.LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filters) { filter ->
                val isSelected = selectedFilter == filter
                Surface(
                    modifier = Modifier.clickable { selectedFilter = filter },
                    shape = RoundedCornerShape(16.dp),
                    color = if (isSelected) GratiaTheme.colors.accent else GratiaTheme.colors.surface,
                    border = if (isSelected) null else androidx.compose.foundation.BorderStroke(1.dp, GratiaTheme.colors.glassBorder)
                ) {
                    Text(
                        text = filter,
                        fontFamily = Inter,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (isSelected) GratiaTheme.colors.background else GratiaTheme.colors.textPrimary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        if (selectedFilter == "Songs") {
            if (favorites.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 100.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.FavoriteBorder,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = GratiaTheme.colors.textSecondary
                    )
                    Spacer(Modifier.height(20.dp))
                    Text(
                        "No favorites yet",
                        fontFamily = Inter,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = GratiaTheme.colors.textSecondary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Tap the heart on songs you love",
                        fontFamily = Inter,
                        fontSize = 12.sp,
                        color = GratiaTheme.colors.textSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                Text(
                    "${favorites.size} favorite${if (favorites.size > 1) "s" else ""}",
                    fontFamily = Inter,
                    fontSize = 11.sp,
                    color = GratiaTheme.colors.textSecondary,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
                )
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 120.dp, top = 8.dp)
                ) {
                    itemsIndexed(favorites, key = { _, s -> s.id }) { index, song ->
                        SongRow(
                            song = song,
                            index = index,
                            isActive = currentSong?.id == song.id,
                            isPlaying = currentSong?.id == song.id && isPlaying,
                            onClick = { playerViewModel.playSong(song, favorites) },
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                    }
                }
            }
        } else {
            // Placeholders for Albums/Playlists
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 100.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "No favorite ${selectedFilter.lowercase()} yet",
                    fontFamily = Inter,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = GratiaTheme.colors.textSecondary,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
