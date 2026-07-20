package com.gratia.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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

    // Simulate "Discover" or "Browse" by randomly shuffling some songs/albums based on day
    val discoverSongs = remember(allSongs) {
        if (allSongs.isNotEmpty()) {
            allSongs.shuffled().take(10)
        } else {
            emptyList()
        }
    }

    val featuredAlbums = remember(allSongs) {
        allSongs.filter { it.album != null }.distinctBy { it.album }.shuffled().take(5)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(GratiaTheme.colors.background),
        contentPadding = PaddingValues(bottom = GratiaTheme.spacing.heroLarge)
    ) {
        item {
            AppleLargeTitleHeader(title = "Browse")
        }

        if (allSongs.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().height(400.dp), contentAlignment = Alignment.Center) {
                    EmptyStateView(
                        icon = Icons.Default.Explore,
                        headline = "Nothing to explore",
                        description = "Add some music to your library first."
                    )
                }
            }
        } else {
            if (featuredAlbums.isNotEmpty()) {
                item {
                    AppleSectionHeader(title = "Featured Albums")
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(featuredAlbums) { song ->
                            MusicCard(
                                song = song,
                                isActive = currentSong?.id == song.id,
                                isPlaying = currentSong?.id == song.id && isPlaying,
                                onClick = { playerViewModel.playSong(song, featuredAlbums) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }

            if (discoverSongs.isNotEmpty()) {
                item {
                    AppleSectionHeader(title = "Rediscover")
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(discoverSongs) { song ->
                            MusicCard(
                                song = song,
                                isActive = currentSong?.id == song.id,
                                isPlaying = currentSong?.id == song.id && isPlaying,
                                onClick = { playerViewModel.playSong(song, discoverSongs) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}
