package com.gratia.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gratia.music.GratiaApp
import com.gratia.music.data.repository.SongRepository
import com.gratia.music.player.PlayerViewModel
import com.gratia.music.ui.components.*
import com.gratia.music.ui.theme.GratiaTheme

/**
 * Displays all songs in a specific genre.
 * Navigated to from the Search screen genre cards.
 */
@Composable
fun GenreDetailScreen(
    genre: String,
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit
) {
    val songRepo = remember { SongRepository(GratiaApp.instance.database.songDao()) }
    val songs by songRepo.getSongsByGenre(genre).collectAsState(initial = emptyList())
    val currentSong by playerViewModel.currentSong.collectAsState()
    val isPlaying by playerViewModel.isPlaying.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GratiaTheme.colors.background)
    ) {
        AppleLargeTitleHeader(
            title = genre,
            onBack = onBack
        )

        LazyColumn(
            contentPadding = PaddingValues(bottom = 120.dp, top = 8.dp)
        ) {
            itemsIndexed(songs, key = { _, s -> s.id }) { index, song ->
                SongRow(
                    song = song,
                    index = index,
                    isActive = currentSong?.id == song.id,
                    isPlaying = currentSong?.id == song.id && isPlaying,
                    onClick = { playerViewModel.playSong(song, songs) },
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
        }
    }
}
