package com.gratia.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.gratia.music.GratiaApp
import com.gratia.music.data.model.SongEntity
import com.gratia.music.data.repository.SongRepository
import com.gratia.music.player.PlayerViewModel
import com.gratia.music.data.scan.MediaStoreScanner
import com.gratia.music.provider.RemoteHomeSection
import com.gratia.music.provider.RemoteTrackMapper
import com.gratia.music.ui.components.AppleLargeTitleHeader
import com.gratia.music.ui.components.AppleListRow
import com.gratia.music.ui.components.AppleSectionHeader
import com.gratia.music.ui.components.GratiaEmptyState
import com.gratia.music.ui.components.GratiaLoadingState
import com.gratia.music.ui.components.GratiaText
import com.gratia.music.ui.components.MusicCard
import com.gratia.music.ui.components.RecommendedForYouCard
import com.gratia.music.ui.components.TopPickCard
import com.gratia.music.ui.components.bounceClick
import com.gratia.music.ui.components.RecentCard
import com.gratia.music.ui.components.RemoteMediaCard
import com.gratia.music.ui.theme.GratiaTheme

@Composable
fun HomeScreen(
    playerViewModel: PlayerViewModel,
    isDark: Boolean,
    onToggleTheme: () -> Unit,
    onNavigateToUpload: () -> Unit,
    onNavigateToYou: () -> Unit = {},
    onNavigateToRemotePlaylist: (String) -> Unit = {},
    onNavigateToLibraryTab: (String) -> Unit = {}
) {
    val songRepo = remember { SongRepository(GratiaApp.instance.database.songDao()) }
    // Fifty, not ten: Top Picks displays the first handful, but how *personal*
    // Home is allowed to be is decided from the whole set — see the signals
    // below, which cannot reach their own threshold off a truncated list.
    val mostPlayedRaw by songRepo.getMostPlayed(50).collectAsState(initial = emptyList())
    val favoriteSongsRaw by songRepo.getFavorites().collectAsState(initial = emptyList())
    val recentlyPlayedRaw by songRepo.getRecentlyPlayed(10).collectAsState(initial = emptyList())
    val lastAddedRaw by songRepo.getLastAdded(10).collectAsState(initial = emptyList())
    val allSongs by songRepo.getAllSongs().collectAsState(initial = emptyList())
    val currentSong by playerViewModel.currentSong.collectAsState()
    val isPlaying by playerViewModel.isPlaying.collectAsState()

    val mostPlayed = remember(mostPlayedRaw) { mostPlayedRaw }
    val favoriteSongs = remember(favoriteSongsRaw) { favoriteSongsRaw }
    val recentlyPlayed = remember(recentlyPlayedRaw) { recentlyPlayedRaw }
    val lastAdded = remember(lastAddedRaw) { lastAddedRaw }

    val context = LocalContext.current
    val settingsDataStore = remember { com.gratia.music.data.SettingsDataStore(context) }
    val updateState by com.gratia.music.GratiaApp.instance.updateManager.state.collectAsState()
    val isOled by settingsDataStore.oledThemeEnabledFlow.collectAsState(initial = false)
    val initialScanCompleted by settingsDataStore.initialScanCompletedFlow.collectAsState(initial = false)
    var isScanning by remember { mutableStateOf(false) }

    // ── Remote catalog ──────────────────────────────────────────────────────
    // Painted from the last good page immediately, then refreshed behind it, so
    // Home opens with content rather than on a spinner every visit.
    var remoteHomeSections by remember {
        mutableStateOf(GratiaApp.instance.providerManager.cachedHome().orEmpty())
    }
    var isRemoteLoading by remember { mutableStateOf(false) }
    var remoteError by remember { mutableStateOf<String?>(null) }
    var isOffline by remember { mutableStateOf(false) }
    var refreshNonce by remember { mutableStateOf(0) }

    // Re-keyed on connectivity, so Home refills itself the moment a network
    // comes back rather than waiting for the listener to leave and return.
    val isOnline by com.gratia.music.data.network.NetworkMonitor.isOnline.collectAsState()

    LaunchedEffect(refreshNonce, isOnline) {
        if (!isOnline) {
            // Nobody to ask. Better to say so at once than to let a doomed
            // request take its time saying the same thing. Only surfaced when
            // there is no cached page to keep showing in the meantime.
            isOffline = remoteHomeSections.isEmpty()
            isRemoteLoading = false
            return@LaunchedEffect
        }

        isRemoteLoading = true
        remoteError = null
        isOffline = false
        val sections = withContext(Dispatchers.IO) {
            GratiaApp.instance.providerManager.getHomeCached(
                limit = 8,
                forceRefresh = refreshNonce > 0
            )
        }
        if (sections.isEmpty()) {
            // Not a silent failure: an empty Home from a dead endpoint used to
            // read as "Gratia has no music", which is a different and wrong story.
            remoteError = "Couldn't reach YouTube Music. Check your connection and try again."
        } else {
            remoteHomeSections = sections
        }
        isRemoteLoading = false
    }

    // ── Local scan (unchanged) ──────────────────────────────────────────────
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

    val dailyMixSongs by playerViewModel.dailyMixSongs.collectAsState()

    // ── Personalization state ───────────────────────────────────────────────
    // What Home is honest to show is a function of the listening data actually
    // on this device, not of the app having been opened before. See
    // resolveHomePersonalization.
    val personalization = remember(mostPlayed, favoriteSongs, recentlyPlayed) {
        resolveHomePersonalization(
            HomeListeningSignals(
                playedTracks = mostPlayed.size,
                distinctArtists = mostPlayed
                    .map { it.artist }
                    .filter { it.isNotBlank() && it != "<unknown>" }
                    .distinct()
                    .size,
                favorites = favoriteSongs.size,
                hasRecentHistory = recentlyPlayed.isNotEmpty(),
            )
        )
    }

    // ── On Device ───────────────────────────────────────────────────────────
    val downloadedSongs = remember(allSongs) { allSongs.filter { it.isDownloaded } }
    val localSongs = remember(allSongs) { allSongs.filter { it.storageProvider == "local" } }

    val profileDao = remember { GratiaApp.instance.database.userProfileDao() }
    val profileFlow by profileDao.getProfile().collectAsState(initial = null)
    val avatarPath = profileFlow?.avatarPath

    // Listening stats for the calendar card — unchanged from the local-first
    // Home, just moved below the discovery shelves where it belongs.
    val eventRepo = remember { com.gratia.music.data.repository.ListeningEventRepository(GratiaApp.instance.database.listeningEventDao()) }
    var topArtists by remember { mutableStateOf<List<com.gratia.music.data.model.ArtistListenSummary>>(emptyList()) }
    var totalListeningSeconds by remember { mutableStateOf(0L) }

    LaunchedEffect(Unit) {
        val todayStart = java.time.LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        topArtists = eventRepo.getTopArtists(todayStart, Long.MAX_VALUE, 3)
        totalListeningSeconds = eventRepo.getTotalListeningSeconds(todayStart, Long.MAX_VALUE)
    }

    val bottomInset = com.gratia.music.ui.LocalBottomPadding.current

    val hasAnything = remoteHomeSections.isNotEmpty() ||
        remoteError != null ||
        isOffline ||
        allSongs.isNotEmpty() ||
        mostPlayed.isNotEmpty() ||
        recentlyPlayed.isNotEmpty()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(GratiaTheme.colors.background)
            .statusBarsPadding(),
        contentPadding = PaddingValues(bottom = bottomInset + GratiaTheme.spacing.heroLarge)
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
                                .bounceClick { onNavigateToYou() },
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
                    }
                }
            )
        }

        // ── Remote catalog: the backbone of Home ────────────────────────────
        // These shelves (Quick picks, Trending, New releases, moods…) are the
        // online-first content. Before this they were fetched and thrown away.
        remoteHomeSections.forEach { section ->
            item(key = "remote_${section.title}") {
                RemoteShelf(
                    section = section,
                    currentSongId = currentSong?.id,
                    isPlaying = isPlaying,
                    onPlayTrack = { song, queue -> playerViewModel.playSong(song, queue) },
                    onOpenCollection = onNavigateToRemotePlaylist
                )
                Spacer(modifier = Modifier.height(32.dp))
            }
        }

        if (isRemoteLoading && remoteHomeSections.isEmpty() && allSongs.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    contentAlignment = Alignment.Center
                ) {
                    GratiaLoadingState(message = "Loading music…")
                }
            }
        }

        if (remoteHomeSections.isEmpty() && (isOffline || remoteError != null)) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    GratiaText(
                        text = if (isOffline) "You're offline" else remoteError ?: "Couldn't load Home",
                        style = GratiaTheme.typography.body,
                        color = GratiaTheme.colors.textPrimary
                    )
                    if (isOffline) {
                        Spacer(modifier = Modifier.height(4.dp))
                        GratiaText(
                            text = "Downloads and local music are still available below.",
                            style = GratiaTheme.typography.caption,
                            color = GratiaTheme.colors.textSecondary
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = { refreshNonce++ }) {
                        Text(
                            text = "Retry",
                            color = GratiaTheme.colors.accent,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // ── Personalized shelves (only once there is data to personalize) ───
        if (personalization != HomePersonalization.FIRST_RUN && mostPlayed.isNotEmpty()) {
            item {
                AppleSectionHeader(title = "Top Picks for You")
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(mostPlayed.take(10), key = { it.id }) { song ->
                        TopPickCard(
                            song = song,
                            onClick = { playerViewModel.playSong(song, allSongs.ifEmpty { mostPlayed }) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }

        // Daily Mix is deliberately absent until the device has enough listening
        // history to build an honest one. See MIN_PERSONALIZED_* thresholds.
        if (personalization == HomePersonalization.PERSONALIZED && dailyMixSongs.isNotEmpty()) {
            item {
                Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                    Text(
                        text = "Daily Mix",
                        fontFamily = com.gratia.music.ui.theme.SpaceGrotesk,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        color = GratiaTheme.colors.textPrimary
                    )
                    Text(
                        text = "A personalized mix of your favorites and discoveries",
                        fontFamily = com.gratia.music.ui.theme.Inter,
                        fontSize = 14.sp,
                        color = GratiaTheme.colors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    RecommendedForYouCard(
                        artistName = "Daily Mix",
                        artistImageUrl = null, // the card draws its own collage
                        songs = dailyMixSongs,
                        onPlay = {
                            if (dailyMixSongs.isNotEmpty()) {
                                playerViewModel.playDailyMix(dailyMixSongs.first(), dailyMixSongs)
                            }
                        }
                    )
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
                    items(recentlyPlayed, key = { it.id }) { song ->
                        RecentCard(
                            song = song,
                            onClick = { playerViewModel.playSong(song, allSongs.ifEmpty { recentlyPlayed }) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }

        if (favoriteSongs.isNotEmpty()) {
            item {
                AppleSectionHeader(title = "Liked Music")
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(favoriteSongs, key = { it.id }) { song ->
                        RecentCard(
                            song = song,
                            onClick = { playerViewModel.playSong(song, allSongs.ifEmpty { favoriteSongs }) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }

        // ── On Device: a lower section, not the main event ──────────────────
        if (downloadedSongs.isNotEmpty() || localSongs.isNotEmpty()) {
            item {
                AppleSectionHeader(title = "On Device")
            }
            if (downloadedSongs.isNotEmpty()) {
                item {
                    AppleListRow(
                        title = "Downloads",
                        subtitle = "${downloadedSongs.size} song${if (downloadedSongs.size == 1) "" else "s"}",
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "Downloads",
                                tint = GratiaTheme.colors.accent,
                                modifier = Modifier.size(28.dp)
                            )
                        },
                        onClick = { onNavigateToLibraryTab("Downloads") },
                        showDivider = localSongs.isNotEmpty()
                    )
                }
            }
            if (localSongs.isNotEmpty()) {
                item {
                    AppleListRow(
                        title = "Local Music",
                        subtitle = "${localSongs.size} song${if (localSongs.size == 1) "" else "s"} on this device",
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = "Local Music",
                                tint = GratiaTheme.colors.accent,
                                modifier = Modifier.size(28.dp)
                            )
                        },
                        onClick = { onNavigateToLibraryTab("LocalMusic") },
                        showDivider = false
                    )
                }
            }
            item { Spacer(modifier = Modifier.height(32.dp)) }
        }

        if (lastAdded.isNotEmpty()) {
            item {
                AppleSectionHeader(title = "Recently Added")
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(lastAdded, key = { it.id }) { song ->
                        RecentCard(
                            song = song,
                            onClick = { playerViewModel.playSong(song, allSongs.ifEmpty { lastAdded }) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }

        if (totalListeningSeconds > 0 || topArtists.isNotEmpty()) {
            item {
                com.gratia.music.ui.components.ListeningStatsCard(
                    totalListeningSeconds = totalListeningSeconds,
                    topArtists = topArtists,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
                Spacer(modifier = Modifier.height(32.dp))
            }
        }

        if (!hasAnything) {
            item {
                Spacer(modifier = Modifier.height(64.dp))
                if (isScanning) {
                    GratiaEmptyState(
                        icon = Icons.Default.Search,
                        headline = "Looking for music…",
                        description = "Scanning your device for audio files.",
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    GratiaEmptyState(
                        icon = Icons.Default.LibraryMusic,
                        headline = "Your Library is Empty",
                        description = "No local music found. Try syncing from settings or search YouTube Music.",
                        actionLabel = "Go to You",
                        onActionClick = onNavigateToYou,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

/**
 * One remote Home shelf.
 *
 * A shelf is either mostly tracks or mostly collections, never meaningfully
 * both, so the track row wins when it has anything in it and the collection row
 * is the fallback. Both are converted into Gratia's existing card components so
 * the remote catalog looks like the rest of the app rather than a second design.
 */
@Composable
private fun RemoteShelf(
    section: RemoteHomeSection,
    currentSongId: String?,
    isPlaying: Boolean,
    onPlayTrack: (SongEntity, List<SongEntity>) -> Unit,
    onOpenCollection: (String) -> Unit
) {
    val tracks = remember(section) {
        section.tracks
            .filter { it.videoId.isNotBlank() }
            .map { RemoteTrackMapper.toSongEntity(it) }
    }

    AppleSectionHeader(title = section.title)

    if (tracks.isNotEmpty()) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(tracks, key = { it.id }) { song ->
                MusicCard(
                    song = song,
                    isActive = currentSongId == song.id,
                    isPlaying = currentSongId == song.id && isPlaying,
                    onClick = { onPlayTrack(song, tracks) }
                )
            }
        }
    } else if (section.collections.isNotEmpty()) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(section.collections, key = { it.id }) { collection ->
                RemoteMediaCard(
                    title = collection.title,
                    subtitle = collection.author,
                    artworkUrl = collection.artworkUrl,
                    onClick = { onOpenCollection(collection.id) }
                )
            }
        }
    }
}

