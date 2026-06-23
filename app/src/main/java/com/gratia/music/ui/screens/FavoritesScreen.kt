package com.gratia.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GratiaTheme.colors.cotton)
    ) {
        Text(
            "Favorites",
            fontFamily = SpaceGrotesk,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            color = GratiaTheme.colors.textPrimary,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp)
        )

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
                    tint = GratiaTheme.colors.textMuted
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
                    color = GratiaTheme.colors.textMuted,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            Text(
                "${favorites.size} favorite${if (favorites.size > 1) "s" else ""}",
                fontFamily = Inter,
                fontSize = 11.sp,
                color = GratiaTheme.colors.textMuted,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )
            LazyColumn {
                itemsIndexed(favorites, key = { _, s -> s.id }) { index, song ->
                    SongRow(
                        song = song,
                        index = index,
                        isActive = currentSong?.id == song.id,
                        isPlaying = currentSong?.id == song.id && isPlaying,
                        onClick = { playerViewModel.playSong(song, favorites) }
                    )
                }
            }
        }
    }
}
