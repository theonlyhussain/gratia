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
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import com.gratia.music.GratiaApp
import com.gratia.music.data.model.SongEntity
import com.gratia.music.data.repository.SongRepository
import com.gratia.music.player.PlayerViewModel
import com.gratia.music.data.scan.MediaStoreScanner
import com.gratia.music.ui.components.AppleLargeTitleHeader
import com.gratia.music.ui.components.AppleSectionHeader
import com.gratia.music.ui.components.CoverArtImage
import com.gratia.music.ui.components.GratiaEmptyState
import com.gratia.music.ui.components.GratiaText
import com.gratia.music.ui.components.RecommendedForYouCard
import com.gratia.music.ui.components.TopPickCard
import com.gratia.music.ui.components.bounceClick
import com.gratia.music.ui.components.RecentCard
import com.gratia.music.ui.theme.GratiaTheme
import com.gratia.music.data.network.ArtistImageFetcher
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
    val mostPlayedRaw by songRepo.getMostPlayed(10).collectAsState(initial = emptyList())
    val favoriteSongsRaw by songRepo.getFavorites().collectAsState(initial = emptyList())
    val recentlyPlayedRaw by songRepo.getRecentlyPlayed(10).collectAsState(initial = emptyList())
    val lastAddedRaw by songRepo.getLastAdded(10).collectAsState(initial = emptyList())
    val currentSong by playerViewModel.currentSong.collectAsState()
    val isPlaying by playerViewModel.isPlaying.collectAsState()

    val allSongs by songRepo.getAllSongs().collectAsState(initial = emptyList())
    val mostPlayed = remember(mostPlayedRaw) { mostPlayedRaw }
    val favoriteSongs = remember(favoriteSongsRaw) { favoriteSongsRaw }
    val recentlyPlayed = remember(recentlyPlayedRaw) { recentlyPlayedRaw }
    val lastAdded = remember(lastAddedRaw) { lastAddedRaw }

    // Determine Top Artist for Recommendation
    val recommendedArtist = remember(mostPlayedRaw, recentlyPlayedRaw) {
        val allSongs = mostPlayedRaw + recentlyPlayedRaw
        val artistCounts = allSongs.groupingBy { it.artist }.eachCount()
        artistCounts.maxByOrNull { it.value }?.key
    }
    
    val recommendedArtistSongs by produceState<List<SongEntity>>(initialValue = emptyList(), key1 = recommendedArtist) {
        if (recommendedArtist != null) {
            value = songRepo.getSongsByArtistDirect(recommendedArtist)
        }
    }
    
    var artistImageUrl by remember { mutableStateOf<String?>(null) }
    
    LaunchedEffect(recommendedArtist) {
        if (recommendedArtist != null) {
            artistImageUrl = ArtistImageFetcher.getArtistPictureUrl(recommendedArtist)
        }
    }

    val context = LocalContext.current
    val settingsDataStore = remember { com.gratia.music.data.SettingsDataStore(context) }
    val updateState by com.gratia.music.GratiaApp.instance.updateManager.state.collectAsState()
    val isOled by settingsDataStore.oledThemeEnabledFlow.collectAsState(initial = false)
    val initialScanCompleted by settingsDataStore.initialScanCompletedFlow.collectAsState(initial = false)
    var isScanning by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
            isScanning = true
            try {
                MediaStoreScanner.scanLocalMusic(context, songRepo)
            } finally {
                isScanning = false
                settingsDataStore.setInitialScanCompleted(true)
            }
        }
    }

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
                title = "Home",
                action = {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(GratiaTheme.colors.surface)
                                .bounceClick { onNavigateToSettings() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = GratiaTheme.colors.textSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                            if (updateState is com.gratia.music.updater.UpdateState.UpdateAvailable || updateState is com.gratia.music.updater.UpdateState.ReadyToInstall) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(4.dp)
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(GratiaTheme.colors.error)
                                )
                            }
                        }
                        
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(GratiaTheme.colors.surface)
                                .bounceClick { onNavigateToProfile() },
                            contentAlignment = Alignment.Center
                        ) {
                            if (avatarPath != null) {
                                coil.compose.AsyncImage(
                                    model = avatarPath,
                                    contentDescription = "Profile",
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = "Profile",
                                    tint = GratiaTheme.colors.textSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
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
                        TopPickCard(
                            song = song,
                            onClick = { playerViewModel.playSong(song, allSongs) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }

        // Recommended For You Card
        if (recommendedArtist != null && recommendedArtistSongs.isNotEmpty()) {
            item {
                Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                    androidx.compose.material3.Text(
                        text = "Recommended For You",
                        fontFamily = com.gratia.music.ui.theme.SpaceGrotesk,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        color = GratiaTheme.colors.textPrimary
                    )
                    androidx.compose.material3.Text(
                        text = "Based on your listening history",
                        fontFamily = com.gratia.music.ui.theme.Inter,
                        fontSize = 14.sp,
                        color = GratiaTheme.colors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    RecommendedForYouCard(
                        artistName = recommendedArtist,
                        artistImageUrl = artistImageUrl,
                        songs = recommendedArtistSongs,
                        onPlay = {
                            if (recommendedArtistSongs.isNotEmpty()) {
                                playerViewModel.playSong(recommendedArtistSongs.first(), allSongs)
                            }
                        }
                    )
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }

        if (favoriteSongs.isNotEmpty()) {
            item {
                AppleSectionHeader(title = "Your Favorites")
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(favoriteSongs) { song ->
                        RecentCard(
                            song = song,
                            onClick = { playerViewModel.playSong(song, allSongs) }
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
                        RecentCard(
                            song = song,
                            onClick = { playerViewModel.playSong(song, allSongs) }
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
                        RecentCard(
                            song = song,
                            onClick = { playerViewModel.playSong(song, allSongs) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }


        if (mostPlayed.isEmpty() && recentlyPlayed.isEmpty() && lastAdded.isEmpty()) {
            item {
                Spacer(modifier = Modifier.height(64.dp))
                if (isScanning) {
                    GratiaEmptyState(
                        icon = Icons.Default.Search,
                        headline = "Looking for music...",
                        description = "Scanning your device for audio files.",
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    GratiaEmptyState(
                        icon = Icons.Default.LibraryMusic,
                        headline = "Your Library is Empty",
                        description = "No local music found. Try syncing from settings if you just added files.",
                        actionLabel = "Go to Settings",
                        onActionClick = onNavigateToSettings,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
