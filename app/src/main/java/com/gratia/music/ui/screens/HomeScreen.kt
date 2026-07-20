package com.gratia.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gratia.music.GratiaApp
import com.gratia.music.data.model.SongEntity
import com.gratia.music.data.repository.SongRepository
import com.gratia.music.player.PlayerViewModel
import com.gratia.music.ui.components.AppleLargeTitleHeader
import com.gratia.music.ui.components.AppleSectionHeader
import com.gratia.music.ui.components.CoverArtImage
import com.gratia.music.ui.components.GratiaText
import com.gratia.music.ui.components.MusicCard
import com.gratia.music.ui.theme.GratiaTheme
import java.util.Calendar

@Composable
fun HomeScreen(
    playerViewModel: PlayerViewModel,
    isDark: Boolean,
    onToggleTheme: () -> Unit,
    onNavigateToUpload: () -> Unit,
    onNavigateToProfile: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {}
) {
    val songRepo = remember { SongRepository(GratiaApp.instance.database.songDao()) }
    val recentlyPlayed by songRepo.getRecentlyPlayed(10).collectAsState(initial = emptyList())
    val mostPlayed by songRepo.getMostPlayed(10).collectAsState(initial = emptyList())
    val lastAdded by songRepo.getLastAdded(10).collectAsState(initial = emptyList())
    val currentSong by playerViewModel.currentSong.collectAsState()
    val isPlaying by playerViewModel.isPlaying.collectAsState()

    val profileDao = remember { GratiaApp.instance.database.userProfileDao() }
    val profileFlow by profileDao.getProfile().collectAsState(initial = null)
    
    val avatarPath = profileFlow?.avatarPath

    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val greeting = remember(hour) {
        when (hour) {
            in 0..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            else -> "Good evening"
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(GratiaTheme.colors.background),
        contentPadding = PaddingValues(bottom = GratiaTheme.spacing.heroLarge)
    ) {
        item {
            AppleLargeTitleHeader(
                title = "Listen Now",
                action = {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(GratiaTheme.colors.surface)
                            .clickable(onClick = onNavigateToProfile),
                        contentAlignment = Alignment.Center
                    ) {
                        if (avatarPath != null) {
                            // Ideally load real avatar image here, for now fallback to icon
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "Profile",
                                tint = GratiaTheme.colors.accent,
                                modifier = Modifier.size(20.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "Profile",
                                tint = GratiaTheme.colors.accent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            )
        }

        // Top Picks (greeting)
        if (mostPlayed.isNotEmpty()) {
            item {
                AppleSectionHeader(title = "Top Picks for You")
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(mostPlayed) { song ->
                        MusicCard(
                            song = song,
                            isActive = currentSong?.id == song.id,
                            isPlaying = currentSong?.id == song.id && isPlaying,
                            onClick = { playerViewModel.playSong(song, mostPlayed) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }

        if (recentlyPlayed.isNotEmpty()) {
            item {
                AppleSectionHeader(title = "Recently Played")
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(recentlyPlayed) { song ->
                        MusicCard(
                            song = song,
                            isActive = currentSong?.id == song.id,
                            isPlaying = currentSong?.id == song.id && isPlaying,
                            onClick = { playerViewModel.playSong(song, recentlyPlayed) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }

        if (lastAdded.isNotEmpty()) {
            item {
                AppleSectionHeader(title = "Recently Added")
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(lastAdded) { song ->
                        MusicCard(
                            song = song,
                            isActive = currentSong?.id == song.id,
                            isPlaying = currentSong?.id == song.id && isPlaying,
                            onClick = { playerViewModel.playSong(song, lastAdded) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}
