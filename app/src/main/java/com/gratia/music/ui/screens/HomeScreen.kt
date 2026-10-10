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
import androidx.compose.material.icons.filled.CloudOff
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
import com.gratia.music.data.model.musicSource
import com.gratia.music.data.repository.SongRepository
import com.gratia.music.player.PlayerViewModel
import com.gratia.music.player.PlaybackOrigin
import com.gratia.music.data.scan.MediaStoreScanner
import com.gratia.music.provider.RemoteHomeSection
import com.gratia.music.provider.RemoteTrackMapper
import com.gratia.music.ui.components.AppleLargeTitleHeader
import com.gratia.music.ui.components.AppleListRow
import com.gratia.music.ui.components.AppleSectionHeader
import com.gratia.music.ui.components.CoverArtFallback
import com.gratia.music.ui.components.GratiaEmptyState
import com.gratia.music.ui.components.GratiaLoadingState
import com.gratia.music.ui.components.GratiaText
import com.gratia.music.ui.components.MusicCard
import com.gratia.music.ui.components.RecommendedForYouCard
import com.gratia.music.ui.components.TopPickCard
import com.gratia.music.ui.components.bounceClick
import com.gratia.music.ui.components.RecentCard
import com.gratia.music.ui.components.RemoteMediaCard
import com.gratia.music.ui.components.OnDeviceTile
import com.gratia.music.ui.theme.GratiaTheme
import com.gratia.music.ui.theme.Inter

@Composable
fun HomeScreen(
    playerViewModel: PlayerViewModel,
    isDark: Boolean,
    onToggleTheme: () -> Unit,
    onNavigateToUpload: () -> Unit,
    onNavigateToYou: () -> Unit = {},
    onNavigateToRemotePlaylist: (String) -> Unit = {},
    onNavigateToStats: () -> Unit = {},
    onNavigateToLibraryTab: (String) -> Unit = {},
    onNavigateToHistory: () -> Unit = {}
) {
    val songRepo = remember { SongRepository(GratiaApp.instance.database.songDao()) }
    // Fifty, not ten: Top Picks displays the first handful, but how *personal*
    // Home is allowed to be is decided from the whole set — see the signals
    // below, which cannot reach their own threshold off a truncated list.
    val mostPlayedRaw by songRepo.getMostPlayed(50).collectAsState(initial = emptyList())
    val favoriteSongsRaw by songRepo.getFavorites().collectAsState(initial = emptyList())
    // Genuine add events only — downloads Gratia itself performed. A scanned
    // local file is *discovered*, not added, so it is not eligible here.
    val recentlyAddedRaw by songRepo.getRecentlyDownloaded(10).collectAsState(initial = emptyList())
    val allSongs by songRepo.getAllSongs().collectAsState(initial = emptyList())
    val currentSong by playerViewModel.currentSong.collectAsState()
    val isPlaying by playerViewModel.isPlaying.collectAsState()

    // ── Unified playback history (spec 17–22) ─────────────────────────────
    // The Recently Played shelf is the actual playback timeline — online,
    // local and downloaded plays in one source-aware list — not a column of
    // the songs table that remote plays never reached.
    val historyRepository = remember { com.gratia.music.data.repository.PlaybackHistoryRepository(GratiaApp.instance.database.playbackHistoryDao()) }
    val recentHistoryRaw by historyRepository.recent(20).collectAsState(initial = emptyList())
    val recentHistory = remember(recentHistoryRaw) { recentHistoryRaw }

    val mostPlayed = remember(mostPlayedRaw) { mostPlayedRaw }
    val favoriteSongs = remember(favoriteSongsRaw) { favoriteSongsRaw }
    val recentlyAdded = remember(recentlyAddedRaw) { recentlyAddedRaw }

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
    // The pull gesture's own spinner. Separate from [isRemoteLoading] because
    // the two mean different things: one says "the user asked for this", the
    // other says "the first paint has nothing to show yet".
    var isRefreshing by remember { mutableStateOf(false) }

    // A pull that lands while a fetch is already running must not create a
    // second writer for the same state. Every request takes a generation, and
    // only the newest generation is allowed to publish its result.
    val requestGeneration = remember { java.util.concurrent.atomic.AtomicInteger(0) }

    // Re-keyed on connectivity, so Home refills itself the moment a network
    // comes back rather than waiting for the listener to leave and return.
    val isOnline by com.gratia.music.data.network.NetworkMonitor.isOnline.collectAsState()

    LaunchedEffect(refreshNonce, isOnline) {
        val generation = requestGeneration.incrementAndGet()

        if (!isOnline) {
            // Nobody to ask. Better to say so at once than to let a doomed
            // request take its time saying the same thing. Only surfaced when
            // there is no cached page to keep showing in the meantime.
            android.util.Log.d("GratiaHome", "HOME_REFRESH_SKIPPED reason=offline hasCache=${remoteHomeSections.isNotEmpty()}")
            isOffline = remoteHomeSections.isEmpty()
            isRemoteLoading = false
            isRefreshing = false
            return@LaunchedEffect
        }

        val isManualRefresh = refreshNonce > 0
        isRemoteLoading = true
        remoteError = null
        isOffline = false

        val sections = withContext(Dispatchers.IO) {
            GratiaApp.instance.providerManager.getHomeCached(
                limit = 8,
                forceRefresh = isManualRefresh
            )
        }

        // A superseded request must not write. Otherwise a slow first fetch
        // could land after a fast refresh and undo it.
        if (generation != requestGeneration.get()) {
            android.util.Log.d("GratiaHome", "HOME_REFRESH_DISCARDED generation=$generation")
            return@LaunchedEffect
        }

        if (sections.isEmpty()) {
            // Not a silent failure: an empty Home from a dead endpoint used to
            // read as "Gratia has no music", which is a different and wrong story.
            remoteError = "Couldn't reach YouTube Music. Check your connection and try again."
        } else {
            if (sections !== remoteHomeSections) {
                android.util.Log.d("GratiaHome", "HOME_REFRESH_APPLIED shelves=${sections.size}")
            }
            remoteHomeSections = sections
        }
        isRemoteLoading = false
        isRefreshing = false
    }

    // ── Local scan (unchanged) ──────────────────────────────────────────────
    // ── Local scan ──────────────────────────────────────────────────────
    // Once per process, not once per Home visit. A full MediaStore walk ran on
    // every first composition of this screen — including the return trip from
    // every other tab — and each song found costs a Room round trip plus, for
    // new files, embedded-art extraction. That walk sat behind Home's first
    // frame and read as a stall between tabs. The scan itself already runs on
    // IO via [MediaStoreScanner]; it only needed gating, not moving.
    LaunchedEffect(Unit) {
        if (!GratiaApp.instance.hasScannedLocalMusicThisProcess) {
            GratiaApp.instance.hasScannedLocalMusicThisProcess = true
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
    }

    val dailyMixSongs by playerViewModel.dailyMixSongs.collectAsState()

    // ── Personalization state ───────────────────────────────────────────────
    // What Home is honest to show is a function of the listening data actually
    // on this device, not of the app having been opened before. See
    // resolveHomePersonalization. Distinct artists are drawn from the playback
    // timeline, so remote plays count toward personalization too.
    val personalization = remember(mostPlayed, favoriteSongs, recentHistory) {
        val timelineArtists = recentHistory
            .map { it.artist }
            .filter { it.isNotBlank() && it != "<unknown>" }
            .distinct()
            .toSet()
        val localArtists = mostPlayed
            .map { it.artist }
            .filter { it.isNotBlank() && it != "<unknown>" }
            .distinct()
        resolveHomePersonalization(
            HomeListeningSignals(
                playedTracks = mostPlayed.size + recentHistory.size,
                distinctArtists = (localArtists + timelineArtists).size,
                favorites = favoriteSongs.size,
                hasRecentHistory = recentHistory.isNotEmpty() || mostPlayed.isNotEmpty(),
            )
        )
    }

    // ── On Device ───────────────────────────────────────────────────────────
    // Classified by *source*, not by a field that happens to look right.
    val downloadedSongs = remember(allSongs) {
        allSongs.filter { it.musicSource == com.gratia.music.data.model.MusicSource.DOWNLOADED_REMOTE }
    }
    val localSongs = remember(allSongs) {
        allSongs.filter { it.musicSource == com.gratia.music.data.model.MusicSource.LOCAL }
    }

    // ── Top Picks (remote-only, seeded by local listening) ──────────────────
    // The bug this replaces: the actual local files from [mostPlayed] were
    // rendered straight into an online shelf. Personalisation must go through
    // the catalogue — local history supplies the *signals*, and the shelf shows
    // what the catalogue returns for them, never the local rows themselves.
    var topPicks by remember { mutableStateOf<List<SongEntity>>(emptyList()) }

    // Keyed on the *seed artists*, not on the play counts that produced them.
    //
    // Keying this on `mostPlayed` meant every play-count write — which is every
    // song start — restarted the effect and fired a fresh catalogue search per
    // seed artist. Five taps in Home's Top Picks was fifteen extra network
    // requests. A remembered list of artist names compares by value, so the
    // effect only restarts when the seeds genuinely change.
    val topPickSeeds = remember(personalization, mostPlayed, recentHistory) {
        if (personalization == HomePersonalization.FIRST_RUN) {
            emptyList()
        } else {
            // Signals from the timeline first (remote plays are real plays),
            // then the local play counts. Distinct, in affinity order.
            (recentHistory.map { it.artist } + mostPlayed.map { it.artist })
                .filter { it.isNotBlank() && it != "<unknown>" }
                .distinct()
                .take(3)
        }
    }

    LaunchedEffect(topPickSeeds) {
        if (topPickSeeds.isEmpty()) {
            topPicks = emptyList()
            return@LaunchedEffect
        }
        topPicks = withContext(Dispatchers.IO) {
            // One batch per seed artist, in each batch's own relevance order;
            // the interleave decides the shelf order so no single artist can
            // fill it alone (spec 8–9).
            val batches = topPickSeeds.map { artist ->
                runCatching {
                    GratiaApp.instance.providerManager.youtubeMusicProvider
                        .search(artist, "songs", limit = 6)
                        .tracks
                        .map { RemoteTrackMapper.toSongEntity(it) }
                }.getOrDefault(emptyList())
            }
            TopPicksDiversity.rank(batches, limit = 10)
        }
    }

    val profileDao = remember { GratiaApp.instance.database.userProfileDao() }
    val profileFlow by profileDao.getProfile().collectAsState(initial = null)
    val avatarPath = profileFlow?.avatarPath

    // Listening stats for the calendar card — read from the one canonical
    // summariser ([StatsRepository]), never a second counting path.
    var topArtists by remember { mutableStateOf<List<com.gratia.music.data.model.ArtistListenSummary>>(emptyList()) }
    var totalListeningSeconds by remember { mutableStateOf(0L) }
    // The period's #1 song — its artwork is the stats card's background
    // (spec 57/58). Null when nothing has been listened to yet.
    var topSong by remember { mutableStateOf<SongEntity?>(null) }
    val isOnlineNow by com.gratia.music.data.network.NetworkMonitor.isOnline.collectAsState()

    // Re-keyed on playback state so today's totals move while the user
    // listens (spec 96/142) — the repository call itself is a single indexed
    // SQL aggregate, not a scan.
    val isPlayingNow by playerViewModel.isPlaying.collectAsState()
    LaunchedEffect(isPlayingNow, isOnlineNow) {
        val todayStart = java.time.LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val summary = withContext(Dispatchers.IO) {
            com.gratia.music.data.repository.StatsRepository.summary(
                start = todayStart,
                end = Long.MAX_VALUE,
            )
        }
        totalListeningSeconds = summary.totalSeconds
        topArtists = summary.topArtists.take(3).map {
            com.gratia.music.data.model.ArtistListenSummary(it.name, it.totalSeconds, it.artworkUrl)
        }
        // The #1 track comes off its songs row when there is one, and off the
        // listening event's own snapshot otherwise — a streamed track has no row,
        // and the card must still draw real artwork (spec 58/60). It is never
        // "no song" while there is something to show.
        topSong = summary.topSongs.firstOrNull()?.let { t ->
            songRepo.getSongById(t.songId) ?: SongEntity(
                id = t.songId,
                title = t.title,
                artist = t.artist,
                artworkUrl = t.artworkUrl,
                coverArtPath = t.artworkUrl,
            )
        }
    }

    val bottomInset = com.gratia.music.ui.LocalBottomPadding.current

    val hasAnything = remoteHomeSections.isNotEmpty() ||
        remoteError != null ||
        isOffline ||
        allSongs.isNotEmpty() ||
        mostPlayed.isNotEmpty() ||
        recentHistory.isNotEmpty()

    // Pull-to-refresh: cached shelves stay on screen, the indicator reports the
    // refresh, and the scroll position is untouched — a refresh is not a
    // navigation.
    com.gratia.music.ui.components.GratiaPullToRefresh(
        isRefreshing = isRefreshing,
        onRefresh = {
            isRefreshing = true
            refreshNonce++
        }
    ) {
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

        // ── Offline ────────────────────────────────────────────────────────
        // A quiet, truthful status line rather than an error screen. Offline is
        // a state the app is in, not something that went wrong: the shelves
        // below are the ones that still work. When a cached remote page is on
        // screen it says so, because cached content shown as live is the one
        // thing this line exists to prevent.
        if (!isOnline) {
            item(key = "offline_banner") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudOff,
                        contentDescription = null,
                        tint = GratiaTheme.colors.textSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    GratiaText(
                        text = when {
                            remoteHomeSections.isNotEmpty() ->
                                "Offline — these shelves are from the last time you were online"
                            else ->
                                "Offline — downloads and local music are ready"
                        },
                        style = GratiaTheme.typography.caption,
                        color = GratiaTheme.colors.textSecondary
                    )
                }
            }
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
                    onPlayTrack = { song, queue -> playerViewModel.playSong(song, queue, PlaybackOrigin.HOME) },
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

        // Only a real fetch failure gets a Retry affordance. Offline is already
        // stated once, at the top of the page, and repeating it here with a
        // button that cannot possibly succeed would be noise — the shelves
        // below are the answer, not an error.
        if (!isOffline && remoteHomeSections.isEmpty() && remoteError != null) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    GratiaText(
                        text = remoteError ?: "Couldn't load Home",
                        style = GratiaTheme.typography.body,
                        color = GratiaTheme.colors.textPrimary
                    )
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
        // Remote-only by construction: topPicks is built from catalogue search
        // results above, never from the local rows that seeded it.
        if (personalization != HomePersonalization.FIRST_RUN && topPicks.isNotEmpty()) {
            item {
                AppleSectionHeader(title = "Top Picks for You")
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(topPicks, key = { it.id }) { song ->
                        TopPickCard(
                            song = song,
                            onClick = { playerViewModel.playSong(song, topPicks, PlaybackOrigin.HOME) }
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
                                // The Daily Mix is its own context: the queue
                                // header must say "Playing from Daily Mix", not
                                // "Home" — the mix is a collection the listener
                                // pressed play on, distinct from the shelves
                                // around it.
                                playerViewModel.playDailyMix(dailyMixSongs.first(), dailyMixSongs, PlaybackOrigin.DAILY_MIX)
                            }
                        }
                    )
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }

        if (recentHistory.isNotEmpty()) {
            item(key = "recently_played_shelf") {
                Column {
                    AppleSectionHeader(
                        title = "Recently Played",
                        // The shelf is a window onto one timeline; the door to
                        // the whole of it sits on the shelf itself (spec 21).
                        action = if (recentHistory.size >= 5) {
                            {
                                GratiaText(
                                    text = "See all",
                                    style = GratiaTheme.typography.body.copy(fontWeight = FontWeight.Medium),
                                    color = GratiaTheme.colors.accent,
                                    modifier = Modifier
                                        .clickable { onNavigateToHistory() }
                                        .padding(4.dp)
                                )
                            }
                        } else null,
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // One unified timeline: online, local and downloaded
                        // plays, newest first (spec 17–19). Each card plays its
                        // own recorded track — a remote entry resolves from the
                        // catalogue, a local one from its file.
                        items(recentHistory, key = { it.id }) { entry ->
                            PlaybackHistoryCard(
                                entry = entry,
                                onClick = { playerViewModel.playFromHistory(entry) }
                            )
                        }
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
                            onClick = { playerViewModel.playSong(song, allSongs.ifEmpty { favoriteSongs }, PlaybackOrigin.FAVORITES) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }

        // ── On Device: an artwork shelf, not a settings row ────────────────
        // Music the phone already holds, drawn the way Recently Played is:
        // covers, horizontal, tappable. The two entries at the end name the
        // management spaces; the cards before them are the music itself
        // (spec 50–52).
        if (downloadedSongs.isNotEmpty() || localSongs.isNotEmpty()) {
            item {
                Column {
                    AppleSectionHeader(title = "On Device")
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Five to seven cards, not a token two: a shelf with two
                        // items on it reads as "you have almost nothing" to a
                        // listener who has dozens, and discovering the rest
                        // should not require guessing that Local Music is the
                        // door (spec 11/12).
                        val onDeviceCards = (recentlyAddedRaw.take(4) +
                            localSongs.sortedByDescending { it.lastPlayedAt ?: 0 }.take(3))
                            .distinctBy { it.id }
                        items(onDeviceCards, key = { "od_${it.id}" }) { song ->
                            RecentCard(
                                song = song,
                                onClick = { playerViewModel.playSong(song, onDeviceCards, PlaybackOrigin.HOME) }
                            )
                        }
                        // The two management entries close the shelf. "See all"
                        // was removed: it duplicated whichever of these two it
                        // pointed at, and two controls for one destination is
                        // exactly the confusion the shelf was rebuilt to end
                        // (spec 9/10). Downloads and Local Music are different
                        // destinations — downloaded-remote vs. already-on-phone
                        // — so both stay, and the cards before them are the
                        // music itself.
                        if (downloadedSongs.isNotEmpty()) {
                            item(key = "od_downloads") {
                                OnDeviceTile(
                                    icon = Icons.Default.Download,
                                    label = "Downloads",
                                    count = downloadedSongs.size,
                                    onClick = { onNavigateToLibraryTab("Downloads") }
                                )
                            }
                        }
                        if (localSongs.isNotEmpty()) {
                            item(key = "od_local") {
                                OnDeviceTile(
                                    icon = Icons.Default.Folder,
                                    label = "Local Music",
                                    count = localSongs.size,
                                    onClick = { onNavigateToLibraryTab("LocalMusic") }
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }

        // Hidden entirely when there are no genuine add events — never faked
        // from scan discovery.
        if (recentlyAdded.isNotEmpty()) {
            item {
                AppleSectionHeader(title = "Recently Added")
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(recentlyAdded, key = { it.id }) { song ->
                        RecentCard(
                            song = song,
                            onClick = { playerViewModel.playSong(song, recentlyAdded, PlaybackOrigin.HOME) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }

        // ── Listening Stats card: real data, real #1 artwork ──────────────
        item {
            com.gratia.music.ui.components.ListeningStatsCard(
                totalListeningSeconds = totalListeningSeconds,
                topArtists = topArtists,
                topSong = topSong,
                onClick = onNavigateToStats,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Spacer(modifier = Modifier.height(32.dp))
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

/**
 * One entry of the unified playback timeline, as a Home card.
 *
 * Draws from the history row's own snapshot — artwork included — so a track
 * that was streamed once and never persisted still shows its face. Remote
 * entries are recognisable by the small badge: the shelf is source-aware by
 * design, and the listener should be able to tell which plays came from
 * where (spec 18).
 */
@Composable
private fun PlaybackHistoryCard(
    entry: com.gratia.music.data.model.PlaybackHistoryEntity,
    onClick: () -> Unit
) {
    Column(modifier = Modifier.width(140.dp)) {
        Box(
            modifier = Modifier
                .size(140.dp)
                .clip(RoundedCornerShape(8.dp))
                .bounceClick(onClick = onClick)
        ) {
            if (!entry.artworkUrl.isNullOrBlank()) {
                coil.compose.AsyncImage(
                    model = com.gratia.music.ui.components.rememberArtworkRequest(
                        entry.artworkUrl,
                        com.gratia.music.ui.components.LARGE_ARTWORK_PX,
                        crossfadeMs = 200,
                    ),
                    contentDescription = "${entry.title} cover art",
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                CoverArtFallback(
                    title = entry.title,
                    artist = entry.artist,
                    size = 140.dp,
                    cornerRadius = 8.dp,
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = entry.title,
            fontFamily = Inter,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            color = GratiaTheme.colors.textPrimary,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
        Text(
            text = entry.artist,
            fontFamily = Inter,
            fontSize = 11.sp,
            color = GratiaTheme.colors.textSecondary,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}

