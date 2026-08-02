package com.gratia.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gratia.music.GratiaApp
import com.gratia.music.data.repository.SongRepository
import com.gratia.music.player.PlayerViewModel
import com.gratia.music.ui.components.AppleLargeTitleHeader
import com.gratia.music.ui.components.AppleSectionHeader
import com.gratia.music.ui.components.EmptyStateView
import com.gratia.music.ui.components.MusicCard
import com.gratia.music.ui.theme.GratiaTheme

@Composable
fun BrowseScreen(playerViewModel: PlayerViewModel) {
    val songRepo = remember { SongRepository(GratiaApp.instance.database.songDao()) }
    val allSongs by songRepo.getAllSongs().collectAsState(initial = emptyList())
    val currentSong by playerViewModel.currentSong.collectAsState()
    val isPlaying by playerViewModel.isPlaying.collectAsState()

    val discoverSongs = remember(allSongs) {
        if (allSongs.isNotEmpty()) allSongs.shuffled().take(10) else emptyList()
    }

    val featuredAlbums = remember(allSongs) {
        allSongs.filter { it.album != null }.distinctBy { it.album }.shuffled().take(5)
    }

    if (allSongs.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(GratiaTheme.colors.background),
            contentAlignment = Alignment.Center
        ) {
            EmptyStateView(
                icon = Icons.Default.Explore,
                headline = "Nothing to explore",
                description = "Add some music to your library first."
            )
        }
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        modifier = Modifier
            .fillMaxSize()
            .background(GratiaTheme.colors.background),
        contentPadding = PaddingValues(
            start = 24.dp, 
            end = 24.dp, 
            bottom = GratiaTheme.spacing.heroLarge
        ),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            AppleLargeTitleHeader(title = "Browse")
        }

        if (featuredAlbums.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                AppleSectionHeader(title = "Featured Albums")
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(featuredAlbums) { song ->
                        MusicCard(
                            song = song,
                            isActive = currentSong?.id == song.id,
                            isPlaying = currentSong?.id == song.id && isPlaying,
                            onClick = { playerViewModel.playSong(song, allSongs) }
                        )
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        if (discoverSongs.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                AppleSectionHeader(title = "Rediscover")
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(discoverSongs) { song ->
                        MusicCard(
                            song = song,
                            isActive = currentSong?.id == song.id,
                            isPlaying = currentSong?.id == song.id && isPlaying,
                            onClick = { playerViewModel.playSong(song, allSongs) }
                        )
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            AppleSectionHeader(title = "All Songs")
        }

        items(allSongs) { song ->
            MusicCard(
                song = song,
                isActive = currentSong?.id == song.id,
                isPlaying = currentSong?.id == song.id && isPlaying,
                onClick = { playerViewModel.playSong(song, allSongs) }
            )
        }
    }
}
