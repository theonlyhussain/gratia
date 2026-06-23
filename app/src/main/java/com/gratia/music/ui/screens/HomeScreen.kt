package com.gratia.music.ui.screens

import android.content.Context
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gratia.music.GratiaApp
import com.gratia.music.data.model.SongEntity
import com.gratia.music.data.repository.SongRepository
import com.gratia.music.player.PlayerViewModel
import com.gratia.music.ui.components.CoverArtFallback
import com.gratia.music.ui.components.CoverArtImage
import com.gratia.music.ui.components.MusicCard
import com.gratia.music.ui.components.SongRow
import com.gratia.music.ui.components.ListeningCalendarPreview
import com.gratia.music.ui.components.ListeningCalendarBottomSheet
import com.gratia.music.data.model.DailyListeningSummary
import com.gratia.music.ui.theme.GratiaTheme
import com.gratia.music.ui.theme.SpaceGrotesk
import com.gratia.music.ui.theme.Inter
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.gratia.music.ui.components.liquidGlass

@Composable
fun HomeScreen(
    playerViewModel: PlayerViewModel,
    onNavigateToUpload: () -> Unit,
    onNavigateToProfile: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {}
) {
    val songRepo = remember { SongRepository(GratiaApp.instance.database.songDao()) }
    val songs by songRepo.getAllSongs().collectAsState(initial = emptyList())
    val recentlyPlayed by songRepo.getRecentlyPlayed(10).collectAsState(initial = emptyList())
    val mostPlayed by songRepo.getMostPlayed(10).collectAsState(initial = emptyList())
    val lastAdded by songRepo.getLastAdded(8).collectAsState(initial = emptyList())
    val recentArtists by songRepo.getRecentArtists(10).collectAsState(initial = emptyList())
    val currentSong by playerViewModel.currentSong.collectAsState()
    val isPlaying by playerViewModel.isPlaying.collectAsState()

    val listeningRepo = remember { com.gratia.music.data.repository.ListeningEventRepository(GratiaApp.instance.database.listeningEventDao()) }
    var calendarSummaries by remember { mutableStateOf<List<DailyListeningSummary>>(emptyList()) }
    var showCalendarSheet by remember { mutableStateOf(false) }

    LaunchedEffect(currentSong, isPlaying) {
        calendarSummaries = listeningRepo.getDailySummaries(30)
    }

    // Load profile from Room database
    val profileDao = remember { GratiaApp.instance.database.userProfileDao() }
    val profileFlow by profileDao.getProfile().collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    
    val displayName = profileFlow?.displayName ?: "Music Lover"
    val avatarPath = profileFlow?.avatarPath

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(GratiaTheme.colors.cotton),
    ) {
        // Top bar
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Gratia branding
                Text(
                    "Gratia",
                    fontFamily = SpaceGrotesk,
                    fontWeight = FontWeight.Bold,
                    fontSize = 26.sp,
                    color = GratiaTheme.colors.cherryRed
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Upload button
                    FilledTonalButton(
                        onClick = onNavigateToUpload,
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = GratiaTheme.colors.surfaceCard,
                            contentColor = GratiaTheme.colors.textSecondary
                        ),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Add Music", fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                    }

                    // Settings button
                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(GratiaTheme.colors.surfaceCard)
                    ) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Settings",
                            modifier = Modifier.size(18.dp),
                            tint = GratiaTheme.colors.textSecondary
                        )
                    }
                }
            }
        }

        // Welcome area
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Avatar
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape,
                    color = GratiaTheme.colors.maroon.copy(alpha = 0.15f),
                ) {
                    if (avatarPath != null && java.io.File(avatarPath!!).exists()) {
                        coil.compose.AsyncImage(
                            model = java.io.File(avatarPath!!),
                            contentDescription = "Avatar",
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                displayName.take(1).uppercase(),
                                fontFamily = SpaceGrotesk,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                                color = GratiaTheme.colors.maroon
                            )
                        }
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        "Welcome,",
                        fontFamily = Inter,
                        fontSize = 13.sp,
                        color = GratiaTheme.colors.textMuted
                    )
                    Text(
                        displayName,
                        fontFamily = SpaceGrotesk,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = GratiaTheme.colors.textPrimary
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        // Listening Calendar Preview
        item {
            ListeningCalendarPreview(
                summaries = calendarSummaries,
                onPreviewClick = { showCalendarSheet = true },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            Spacer(Modifier.height(8.dp))
        }

        // Empty state
        if (songs.isEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 60.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        modifier = Modifier.size(72.dp),
                        shape = RoundedCornerShape(20.dp),
                        color = GratiaTheme.colors.surfaceCard,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.MusicNote,
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                                tint = GratiaTheme.colors.textMuted
                            )
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    Text(
                        "Your private library is ready",
                        fontFamily = Inter,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = GratiaTheme.colors.textPrimary
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Add your first song to start listening.",
                        fontFamily = Inter,
                        fontSize = 13.sp,
                        color = GratiaTheme.colors.textMuted
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = onNavigateToUpload,
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GratiaTheme.colors.maroon,
                            contentColor = GratiaTheme.colors.cotton
                        )
                    ) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Add music", fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
                }
            }
        }

        // Quick action cards (2×2 grid)
        if (songs.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    QuickActionCard(
                        icon = Icons.Outlined.History,
                        title = "History",
                        subtitle = "${recentlyPlayed.size} songs",
                        color = GratiaTheme.colors.cherryRed,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (recentlyPlayed.isNotEmpty()) {
                                playerViewModel.playSong(recentlyPlayed.first(), recentlyPlayed)
                            }
                        }
                    )
                    QuickActionCard(
                        icon = Icons.Outlined.LibraryMusic,
                        title = "Last Added",
                        subtitle = "${lastAdded.size} songs",
                        color = GratiaTheme.colors.maroon,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (lastAdded.isNotEmpty()) {
                                playerViewModel.playSong(lastAdded.first(), lastAdded)
                            }
                        }
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    QuickActionCard(
                        icon = Icons.Outlined.TrendingUp,
                        title = "Most Played",
                        subtitle = if (mostPlayed.isNotEmpty()) "${mostPlayed.size} songs" else "Play more",
                        color = GratiaTheme.colors.accentWarm,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (mostPlayed.isNotEmpty()) {
                                playerViewModel.playSong(mostPlayed.first(), mostPlayed)
                            }
                        }
                    )
                    QuickActionCard(
                        icon = Icons.Outlined.Shuffle,
                        title = "Shuffle",
                        subtitle = "${songs.size} songs",
                        color = GratiaTheme.colors.cherryRed,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            scope.launch {
                                val shuffled = songs.shuffled()
                                if (shuffled.isNotEmpty()) {
                                    playerViewModel.playSong(shuffled.first(), shuffled)
                                }
                            }
                        }
                    )
                }
                Spacer(Modifier.height(24.dp))
            }

            // Suggestions section (for small libraries, show tips)
            if (songs.size <= 5) {
                item {
                    SectionHeader("Suggestions")
                }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(bottom = 20.dp)
                    ) {
                        val suggestions = buildList {
                            add(SuggestionItem("Shuffle your library", "Mix it up", Icons.Outlined.Shuffle) {
                                val shuffled = songs.shuffled()
                                if (shuffled.isNotEmpty()) playerViewModel.playSong(shuffled.first(), shuffled)
                            })
                            add(SuggestionItem("Add more music", "Grow your collection", Icons.Outlined.Add, onNavigateToUpload))
                            val noLyrics = songs.count { it.lyricsPlain.isNullOrBlank() && it.lyrics.isNullOrBlank() && it.lyricsSynced.isNullOrBlank() }
                            if (noLyrics > 0) {
                                add(SuggestionItem("Add lyrics", "$noLyrics songs without lyrics", Icons.Outlined.Lyrics, null))
                            }
                            val noCover = songs.count { it.coverArtPath.isNullOrBlank() }
                            if (noCover > 0) {
                                add(SuggestionItem("Add cover art", "$noCover songs without covers", Icons.Outlined.Image, null))
                            }
                        }
                        items(suggestions) { suggestion ->
                            SuggestionCard(suggestion)
                        }
                    }
                }
            }

            // Recently Added horizontal shelf
            if (lastAdded.isNotEmpty()) {
                item { SectionHeader("Recently Added") }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(bottom = 24.dp)
                    ) {
                        items(lastAdded, key = { it.id }) { song ->
                            MusicCard(
                                song = song,
                                isActive = currentSong?.id == song.id,
                                isPlaying = currentSong?.id == song.id && isPlaying,
                                onClick = { playerViewModel.playSong(song, songs) }
                            )
                        }
                    }
                }
            }

            // Recent Artists
            if (recentArtists.isNotEmpty()) {
                item { SectionHeader("Recent Artists") }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(bottom = 24.dp)
                    ) {
                        items(recentArtists) { artist ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .width(72.dp)
                                    .clickable {
                                        val artistSongs = songs.filter { it.artist == artist }
                                        if (artistSongs.isNotEmpty()) {
                                            playerViewModel.playSong(artistSongs.first(), artistSongs)
                                        }
                                    }
                            ) {
                                CoverArtFallback(
                                    title = artist,
                                    size = 64.dp,
                                    cornerRadius = 32.dp,
                                    fontSize = 20.sp
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    artist,
                                    fontFamily = Inter,
                                    fontSize = 11.sp,
                                    color = GratiaTheme.colors.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }

            // Library list
            item { SectionHeader("Library") }
            itemsIndexed(songs, key = { _, s -> s.id }) { index, song ->
                SongRow(
                    song = song,
                    index = index,
                    isActive = currentSong?.id == song.id,
                    isPlaying = currentSong?.id == song.id && isPlaying,
                    onClick = { playerViewModel.playSong(song, songs) }
                )
            }
            // Bottom spacer
            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    if (showCalendarSheet) {
        ListeningCalendarBottomSheet(
            summaries = calendarSummaries,
            onDismiss = { showCalendarSheet = false }
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        fontFamily = Inter,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        color = GratiaTheme.colors.textPrimary,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
    )
}

@Composable
private fun QuickActionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .liquidGlass(
                shape = RoundedCornerShape(14.dp),
                backgroundColor = GratiaTheme.colors.surfaceCard,
                borderColorStart = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.1f),
                borderColorEnd = androidx.compose.ui.graphics.Color.Transparent
            )
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = androidx.compose.ui.graphics.Color.Transparent,
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                modifier = Modifier.size(36.dp),
                shape = RoundedCornerShape(10.dp),
                color = color.copy(alpha = 0.12f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
                }
            }
            Column {
                Text(title, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = GratiaTheme.colors.textPrimary)
                Text(subtitle, fontFamily = Inter, fontSize = 10.sp, color = GratiaTheme.colors.textMuted)
            }
        }
    }
}

private data class SuggestionItem(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val onClick: (() -> Unit)?
)

@Composable
private fun SuggestionCard(item: SuggestionItem) {
    Surface(
        modifier = Modifier
            .width(160.dp)
            .liquidGlass(
                shape = RoundedCornerShape(12.dp),
                backgroundColor = GratiaTheme.colors.surfaceCard,
                borderColorStart = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.1f),
                borderColorEnd = androidx.compose.ui.graphics.Color.Transparent
            )
            .then(if (item.onClick != null) Modifier.clickable(onClick = item.onClick) else Modifier),
        shape = RoundedCornerShape(12.dp),
        color = androidx.compose.ui.graphics.Color.Transparent,
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Icon(item.icon, null, tint = GratiaTheme.colors.cherryRed, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(10.dp))
            Text(item.title, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = GratiaTheme.colors.textPrimary)
            Text(item.subtitle, fontFamily = Inter, fontSize = 11.sp, color = GratiaTheme.colors.textMuted)
        }
    }
}
