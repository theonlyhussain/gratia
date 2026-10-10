package com.gratia.music.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gratia.music.GratiaApp
import com.gratia.music.data.model.SongEntity
import com.gratia.music.data.model.musicSource
import com.gratia.music.data.repository.SongRepository
import com.gratia.music.player.PlayerViewModel
import com.gratia.music.player.PlaybackOrigin
import com.gratia.music.ui.LocalNavController
import com.gratia.music.ui.components.*
import com.gratia.music.ui.selection.SelectableSongRow
import com.gratia.music.ui.selection.SelectionManager
import com.gratia.music.ui.selection.SelectionToolbar
import com.gratia.music.ui.theme.GratiaTheme
import com.gratia.music.ui.theme.Inter

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun LibraryScreen(
    playerViewModel: PlayerViewModel,
    onNavigateToAlbum: (String) -> Unit = {},
    onNavigateToArtist: (String) -> Unit = {},
    onNavigateToFolder: (String) -> Unit = {},
    onNavigateToRemoteArtist: (String) -> Unit = {},
    onNavigateToRemoteAlbum: (String) -> Unit = {},
    onNavigateToStats: () -> Unit = {},
    initialTab: String? = null
) {
    val songRepo = remember { SongRepository(GratiaApp.instance.database.songDao()) }
    val allSongs by songRepo.getAllSongs().collectAsState(initial = emptyList())
    
    // activeSubView: null means root library menu
    var activeSubView: String? by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(initialTab) }
    
    val navController = LocalNavController.current

    BackHandler(enabled = activeSubView != null) {
        activeSubView = null
    }

    val springSpec = GratiaTheme.motion.springStandard<androidx.compose.ui.unit.IntOffset>()

    AnimatedContent<String?>(
        targetState = activeSubView,
        transitionSpec = {
            if (targetState != null) {
                // Entering subview
                slideInHorizontally(initialOffsetX = { it }, animationSpec = springSpec) + fadeIn() togetherWith
                        slideOutHorizontally(targetOffsetX = { -it / 2 }, animationSpec = springSpec) + fadeOut()
            } else {
                // Returning to root
                slideInHorizontally(initialOffsetX = { -it / 2 }, animationSpec = springSpec) + fadeIn() togetherWith
                        slideOutHorizontally(targetOffsetX = { it }, animationSpec = springSpec) + fadeOut()
            }
        }, label = "Library Navigation"
    ) { currentSubView ->
        if (currentSubView == null) {
            LibraryRootView(
                allSongs = allSongs,
                onNavigateToPlaylists = { navController.navigate("playlists") },
                onNavigateToNewPlaylist = { navController.navigate("playlists?create=true") },
                onNavigateToFavorites = { activeSubView = "Favorites" },
                onNavigateToArtists = { activeSubView = "Artists" },
                onNavigateToAlbums = { activeSubView = "Albums" },
                onNavigateToSongs = { activeSubView = "Songs" },
                onNavigateToDownloads = { activeSubView = "Downloads" },
                onNavigateToLocalMusic = { navController.navigate("local_music") },
                onNavigateToStats = onNavigateToStats,
                onNavigateToAlbum = onNavigateToAlbum
            )
        } else {
            LibrarySubView(
                title = currentSubView,
                allSongs = allSongs,
                playerViewModel = playerViewModel,
                onBack = { activeSubView = null },
                onNavigateToAlbum = onNavigateToAlbum,
                onNavigateToArtist = onNavigateToArtist,
                onNavigateToRemoteArtist = onNavigateToRemoteArtist,
                onNavigateToRemoteAlbum = onNavigateToRemoteAlbum,
                onNavigateToFolder = onNavigateToFolder
            )
        }
    }
}

@Composable
fun LibraryRootView(
    allSongs: List<SongEntity>,
    onNavigateToPlaylists: () -> Unit,
    onNavigateToNewPlaylist: () -> Unit = {},
    onNavigateToFavorites: () -> Unit,
    onNavigateToArtists: () -> Unit,
    onNavigateToRemoteArtist: (String) -> Unit = {},
    onNavigateToAlbums: () -> Unit,
    onNavigateToSongs: () -> Unit,
    onNavigateToDownloads: () -> Unit,
    onNavigateToLocalMusic: () -> Unit,
    onNavigateToStats: () -> Unit = {},
    onNavigateToAlbum: (String) -> Unit
) {
    val bottomInset = com.gratia.music.ui.LocalBottomPadding.current

    // The two On Device counts, kept as counts rather than derived per row so
    // the summary line can say how much is actually there before you tap it.
    val downloadsCount = remember(allSongs) { allSongs.count { it.isDownloaded } }
    val localMusicCount = remember(allSongs) {
        allSongs.count { it.storageProvider == "local" && !it.isDownloaded }
    }

    // Computed here rather than inside the lazy scope: a lazy list's content
    // lambda is not a composable context, so a `remember` cannot live there.
    val recentAlbums = remember(allSongs) {
        allSongs
            .filter { it.album != null }
            .sortedByDescending { it.createdAt }
            .distinctBy { it.album }
            .take(10)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(GratiaTheme.colors.background)
            .statusBarsPadding(),
        contentPadding = PaddingValues(bottom = bottomInset + GratiaTheme.spacing.heroLarge)
    ) {
        item {
            AppleLargeTitleHeader(title = "Library")
            Spacer(modifier = Modifier.height(8.dp))
        }

        // ── Your Replay ────────────────────────────────────────────────────
        // First on the page and above the shelves: the summary of what the
        // listener has actually played, and the way into the full Listening
        // Stats page. It sits here rather than in Settings because it is
        // library content, not a preference.
        item {
            AppleListRow(
                title = "Listening Stats",
                subtitle = "Your Replay",
                leadingContent = {
                    Icon(
                        imageVector = Icons.Default.BarChart,
                        contentDescription = "Listening Stats",
                        tint = GratiaTheme.colors.accent,
                        modifier = Modifier.size(28.dp)
                    )
                },
                onClick = onNavigateToStats,
                showDivider = false
            )
        }

        // ── On Device ──────────────────────────────────────────────────────
        // Two distinct things, deliberately not one list: a Gratia-managed
        // download is not the same object as an audio file that was already on
        // the phone, and a user-imported MP3 must never read as a download.
        // A combined "All On Device" row used to sit here; it was a third view
        // of the same two populations with no question of its own to answer,
        // so the two real ones remain and the alias is gone.
        item {
            Spacer(modifier = Modifier.height(12.dp))
            AppleSectionHeader(title = "On Device")
        }
        item {
            AppleListRow(
                title = "Downloads",
                subtitle = countLabel(downloadsCount),
                leadingContent = {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Downloads",
                        tint = GratiaTheme.colors.accent,
                        modifier = Modifier.size(28.dp)
                    )
                },
                onClick = onNavigateToDownloads
            )
        }
        item {
            AppleListRow(
                title = "Local Music",
                subtitle = countLabel(localMusicCount),
                leadingContent = {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = "Local Music",
                        tint = GratiaTheme.colors.accent,
                        modifier = Modifier.size(28.dp)
                    )
                },
                onClick = onNavigateToLocalMusic,
                showDivider = false
            )
        }

        // ── Your Library ───────────────────────────────────────────────────
        // Collections the listener built by hand: playlists they made, tracks
        // they liked, artists and albums they saved from the remote catalogue.
        item {
            Spacer(modifier = Modifier.height(12.dp))
            AppleSectionHeader(title = "Your Library")
        }
        item {
            AppleListRow(
                title = "Playlists",
                leadingContent = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                        contentDescription = "Playlists",
                        tint = GratiaTheme.colors.accent,
                        modifier = Modifier.size(28.dp)
                    )
                },
                onClick = onNavigateToPlaylists
            )
        }
        item {
            AppleListRow(
                title = "New Playlist",
                leadingContent = {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Create a new playlist",
                        tint = GratiaTheme.colors.accent,
                        modifier = Modifier.size(28.dp)
                    )
                },
                onClick = onNavigateToNewPlaylist
            )
        }
        item {
            AppleListRow(
                title = "Favorites",
                leadingContent = {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = "Favorites",
                        tint = GratiaTheme.colors.accent,
                        modifier = Modifier.size(28.dp)
                    )
                },
                onClick = onNavigateToFavorites
            )
        }
        item {
            AppleListRow(
                title = "Artists",
                leadingContent = {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Artists",
                        tint = GratiaTheme.colors.accent,
                        modifier = Modifier.size(28.dp)
                    )
                },
                onClick = onNavigateToArtists
            )
        }
        item {
            AppleListRow(
                title = "Albums",
                leadingContent = {
                    Icon(
                        imageVector = Icons.Default.Album,
                        contentDescription = "Albums",
                        tint = GratiaTheme.colors.accent,
                        modifier = Modifier.size(28.dp)
                    )
                },
                onClick = onNavigateToAlbums,
                showDivider = false
            )
        }
        // "Songs" and "Folders" are deliberately absent from the top level
        // (spec 7): Local Music is the management space for files on the phone
        // and already offers Songs / Artists / Albums, so a second Songs entry
        // point duplicated it while Folders is purely a local-file concern.
        // Both stay reachable inside Local Music and by deep link.

        // ── Recently Added ────────────────────────────────────────────────
        // Only content Gratia itself brought into the library: a download it
        // fetched, or a file it indexed into its own tables. The heading says
        // "to Library" because that is what actually happened — the user did
        // not necessarily add these, and a bare "Recently Added" claimed they
        // did. The section is omitted entirely when there is nothing to show
        // rather than rendering an empty shelf.
        if (recentAlbums.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(24.dp))
                AppleSectionHeader(title = "Recently Added to Library")
            }

            val columns = 2
            val chunkedAlbums = recentAlbums.chunked(columns)

            items(chunkedAlbums) { rowAlbums ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    rowAlbums.forEach { song ->
                        Box(modifier = Modifier.weight(1f)) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickableWithScale { song.album?.let { onNavigateToAlbum(it) } }
                            ) {
                                CoverArtImage(
                                    coverArtPath = song.coverArtPath,
                                    title = song.album ?: "Unknown",
                                    size = 160.dp,
                                    cornerRadius = 8.dp,
                                    modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                                )
                                Spacer(Modifier.height(8.dp))
                                GratiaText(
                                    text = song.album ?: "Unknown",
                                    style = GratiaTheme.typography.body.copy(fontWeight = FontWeight.Medium),
                                    maxLines = 1,
                                    color = GratiaTheme.colors.textPrimary
                                )
                                GratiaText(
                                    text = song.artist,
                                    style = GratiaTheme.typography.caption,
                                    maxLines = 1,
                                    color = GratiaTheme.colors.textSecondary
                                )
                            }
                        }
                    }
                    // Fill empty spots if any
                    val emptySpots = columns - rowAlbums.size
                    for (i in 0 until emptySpots) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
fun LibrarySubView(
    title: String,
    allSongs: List<SongEntity>,
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit,
    onNavigateToAlbum: (String) -> Unit,
    onNavigateToArtist: (String) -> Unit,
    onNavigateToRemoteArtist: (String) -> Unit = {},
    onNavigateToRemoteAlbum: (String) -> Unit = {},
    onNavigateToFolder: (String) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val selectionManager = remember { SelectionManager() }
    val selectedIds by selectionManager.selectedIds.collectAsState()
    val isSelectionMode by selectionManager.isSelectionMode.collectAsState()
    val currentSong by playerViewModel.currentSong.collectAsState()
    val isPlaying by playerViewModel.isPlaying.collectAsState()

    var sortOption by remember { mutableStateOf("Title") }
    val sortedSongs = remember(allSongs, sortOption) {
        when (sortOption) {
            "Title" -> allSongs.sortedBy { it.title.lowercase() }
            "Artist" -> allSongs.sortedBy { it.artist.lowercase() }
            "Play count" -> allSongs.sortedByDescending { it.playCount }
            else -> allSongs.sortedByDescending { it.createdAt }
        }
    }

    // What this sub-view actually lists, so selection's "select all" and delete
    // act on the rows on screen rather than on the whole library.
    val activeSongs: List<SongEntity> = when (title) {                "Favorites" -> allSongs.filter { it.isFavorite }
                "Downloads" -> allSongs.filter { it.isDownloaded }
                // A Gratia-managed download is excluded: it is on the device, but it is
                // not a file the user put there, and the two must not be conflated.
                // (The dedicated Local Music screen lives at "local_music"; this
                // sub-view remains for direct tab navigation.)
                "LocalMusic" -> allSongs.filter { it.musicSource == com.gratia.music.data.model.MusicSource.LOCAL }
        else -> sortedSongs
    }

    // Internal ids stay camel-cased for routing; the header shows the label.
    val displayTitle = when (title) {
        "LocalMusic" -> "Local Music"
        else -> title
    }

    Box(modifier = Modifier.fillMaxSize().background(GratiaTheme.colors.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Apple-style back header. The sub-view is drawn edge-to-edge like
            // every other full-screen destination, so the row clears the
            // status bar itself — without this the back arrow sat in the clock
            // and battery readout (spec 24/26).
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = GratiaTheme.colors.accent
                    )
                }
                GratiaText(
                    text = displayTitle,
                    style = GratiaTheme.typography.title,
                    color = GratiaTheme.colors.textPrimary
                )
            }

            // Content
            when (title) {
                "Favorites" -> {
                    val favoriteSongs = activeSongs
                    if (favoriteSongs.isEmpty()) {
                        EmptyStateView(
                            icon = Icons.Default.FavoriteBorder,
                            headline = "No favorites yet",
                            description = "Like some songs to see them here."
                        )
                    } else {
                        val bottomInset = com.gratia.music.ui.LocalBottomPadding.current
                        LazyColumn(
                            contentPadding = PaddingValues(bottom = bottomInset + GratiaTheme.spacing.heroLarge, top = GratiaTheme.spacing.small),
                            verticalArrangement = Arrangement.spacedBy(GratiaTheme.spacing.small)
                        ) {
                            itemsIndexed(
                                items = favoriteSongs,
                                key = { _, song -> song.id }
                            ) { index, song ->
                                SelectableSongRow(
                                    song = song,
                                    index = index,
                                    isActive = currentSong?.id == song.id,
                                    isPlaying = currentSong?.id == song.id && isPlaying,
                                    isSelectionMode = isSelectionMode,
                                    isSelected = selectedIds.contains(song.id),
                                    onPlay = { playerViewModel.playSong(song, favoriteSongs, PlaybackOrigin.FAVORITES) },
                                    onLongPress = { selectionManager.startSelection(song.id) },
                                    onToggleSelection = { selectionManager.toggle(song.id) },
                                    modifier = Modifier.padding(horizontal = GratiaTheme.spacing.mediumSmall)
                                )
                            }
                        }
                    }
                }
                // Downloads, Local Music and All On Device share one list shape;
                // what differs is which songs they hold and what an empty one
                // says. The copy matters: "no downloads" and "no local music"
                // are different facts, and a shared empty state would blur them.
                "Downloads", "LocalMusic" -> {
                    if (activeSongs.isEmpty()) {
                        EmptyStateView(
                            icon = when (title) {
                                "Downloads" -> Icons.Default.Download
                                else -> Icons.Default.Folder
                            },
                            headline = when (title) {
                                "Downloads" -> "No downloads yet"
                                else -> "No local music found"
                            },
                            description = when (title) {
                                "Downloads" -> "Download songs to listen offline."
                                else -> "Audio files already on this device will appear here."
                            }
                        )
                    } else {
                        val bottomInset = com.gratia.music.ui.LocalBottomPadding.current
                        LazyColumn(
                            contentPadding = PaddingValues(bottom = bottomInset + GratiaTheme.spacing.heroLarge, top = GratiaTheme.spacing.small),
                            verticalArrangement = Arrangement.spacedBy(GratiaTheme.spacing.small)
                        ) {
                            itemsIndexed(
                                items = activeSongs,
                                key = { _, song -> song.id }
                            ) { index, song ->
                                SelectableSongRow(
                                    song = song,
                                    index = index,
                                    isActive = currentSong?.id == song.id,
                                    isPlaying = currentSong?.id == song.id && isPlaying,
                                    isSelectionMode = isSelectionMode,
                                    isSelected = selectedIds.contains(song.id),
                                    // The origin a track inherits from this screen,
                                    // told apart by which on-device view it was played
                                    // from — Downloads and Local Music are different
                                    // facts, and the player must say which it was.
                                    onPlay = {
                                        playerViewModel.playSong(song, activeSongs, when (title) {
                                            "Downloads" -> PlaybackOrigin.DOWNLOADS
                                            "LocalMusic" -> PlaybackOrigin.LOCAL_MUSIC
                                            else -> PlaybackOrigin.LIBRARY
                                        })
                                    },
                                    onLongPress = { selectionManager.startSelection(song.id) },
                                    onToggleSelection = { selectionManager.toggle(song.id) },
                                    modifier = Modifier.padding(horizontal = GratiaTheme.spacing.mediumSmall)
                                )
                            }
                        }
                    }
                }
                "Songs" -> {
                    if (sortedSongs.isEmpty()) {
                        EmptyStateView(
                            icon = Icons.Default.LibraryMusic,
                            headline = "Your library is empty",
                            description = "Add music to begin listening."
                        )
                    } else {
                        val bottomInset = com.gratia.music.ui.LocalBottomPadding.current
                        LazyColumn(
                            contentPadding = PaddingValues(bottom = bottomInset + GratiaTheme.spacing.heroLarge, top = GratiaTheme.spacing.small),
                            verticalArrangement = Arrangement.spacedBy(GratiaTheme.spacing.small)
                        ) {
                            itemsIndexed(
                                items = sortedSongs,
                                key = { _, song -> song.id }
                            ) { index, song ->
                                SelectableSongRow(
                                    song = song,
                                    index = index,
                                    isActive = currentSong?.id == song.id,
                                    isPlaying = currentSong?.id == song.id && isPlaying,
                                    isSelectionMode = isSelectionMode,
                                    isSelected = selectedIds.contains(song.id),
                                    onPlay = { playerViewModel.playSong(song, sortedSongs, PlaybackOrigin.LIBRARY) },
                                    onLongPress = { selectionManager.startSelection(song.id) },
                                    onToggleSelection = { selectionManager.toggle(song.id) },
                                    modifier = Modifier.padding(horizontal = GratiaTheme.spacing.mediumSmall)
                                )
                            }
                        }
                    }
                }
                "Albums" -> {
                    // Saved remote albums only. A local album is a folder of
                    // files and belongs under Local Music → Albums; listing it
                    // here as well would present two different objects under one
                    // heading, and an album the listener actually bookmarked
                    // would be buried among folders they merely own (spec 16/19).
                    val albumRepo = remember {
                        com.gratia.music.data.repository.SavedAlbumRepository(
                            GratiaApp.instance.database.savedAlbumDao()
                        )
                    }
                    val savedAlbums by albumRepo.savedAlbums.collectAsState(initial = emptyList())

                    if (savedAlbums.isEmpty()) {
                        EmptyStateView(
                            icon = Icons.Default.Album,
                            headline = "No saved albums yet",
                            description = "Save an album from its page to keep it here. Albums on this device are under Local Music."
                        )
                    } else {
                        val bottomInset = com.gratia.music.ui.LocalBottomPadding.current
                        LazyColumn(
                            contentPadding = PaddingValues(bottom = bottomInset + GratiaTheme.spacing.heroLarge, top = GratiaTheme.spacing.small, start = GratiaTheme.spacing.large, end = GratiaTheme.spacing.large),
                            verticalArrangement = Arrangement.spacedBy(GratiaTheme.spacing.mediumSmall)
                        ) {
                            items(savedAlbums, key = { it.remoteId }) { album ->
                                AppleListRow(
                                    title = album.title,
                                    subtitle = listOfNotNull(album.artistName, album.year)
                                        .takeIf { it.isNotEmpty() }
                                        ?.joinToString(" · "),
                                    leadingContent = {
                                        if (album.artworkUrl != null) {
                                            coil.compose.SubcomposeAsyncImage(
                                                model = com.gratia.music.ui.components.rememberArtworkRequest(
                                                    album.artworkUrl,
                                                    sizePx = 240,
                                                    crossfadeMs = 200
                                                ),
                                                contentDescription = album.title,
                                                modifier = Modifier
                                                    .size(56.dp)
                                                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp)),
                                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                            )
                                        } else {
                                            CoverArtImage(
                                                coverArtPath = null,
                                                title = album.title,
                                                size = 56.dp,
                                                cornerRadius = 6.dp
                                            )
                                        }
                                    },
                                    onClick = { onNavigateToRemoteAlbum(album.remoteId) }
                                )
                            }
                        }
                    }
                }
                "Artists" -> {
                    // Saved remote artists only, read from the actual saved state
                    // — never from "this artist was viewed recently", and never
                    // from the scanned local collection (spec 15/18). An artist
                    // derived off the phone's files is a different object from a
                    // remote channel the listener bookmarked, and Local Music →
                    // Artists is where the former lives.
                    val savedRepo = remember {
                        com.gratia.music.data.repository.SavedArtistRepository(GratiaApp.instance.database.savedArtistDao())
                    }
                    val savedArtists by savedRepo.savedArtists.collectAsState(initial = emptyList())

                    if (savedArtists.isEmpty()) {
                        EmptyStateView(
                            icon = Icons.Default.Person,
                            headline = "No saved artists yet",
                            description = "Save an artist from their page to keep it here. Artists on this device are under Local Music."
                        )
                    } else {
                        val bottomInset = com.gratia.music.ui.LocalBottomPadding.current
                        LazyColumn(
                            contentPadding = PaddingValues(bottom = bottomInset + GratiaTheme.spacing.heroLarge, top = GratiaTheme.spacing.small, start = GratiaTheme.spacing.large, end = GratiaTheme.spacing.large),
                            verticalArrangement = Arrangement.spacedBy(GratiaTheme.spacing.mediumSmall)
                        ) {
                            items(savedArtists, key = { it.remoteId }) { saved ->
                                AppleListRow(
                                    title = saved.name,
                                    leadingContent = {
                                        if (saved.artworkUrl != null) {
                                            coil.compose.SubcomposeAsyncImage(
                                                model = com.gratia.music.ui.components.rememberArtworkRequest(
                                                    saved.artworkUrl,
                                                    sizePx = 240,
                                                    crossfadeMs = 200
                                                ),
                                                contentDescription = saved.name,
                                                modifier = Modifier
                                                    .size(56.dp)
                                                    .clip(androidx.compose.foundation.shape.CircleShape),
                                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                            )
                                        } else {
                                            ArtistRowImage(artistName = saved.name, fallbackPath = null, size = 56.dp)
                                        }
                                    },
                                    onClick = { onNavigateToRemoteArtist(saved.remoteId) }
                                )
                            }
                        }
                    }
                }
                "Folders" -> {
                    val folders = remember(allSongs) { allSongs.mapNotNull { it.storagePath?.substringBeforeLast("/")?.substringAfterLast("/") }.distinct().filter { it.isNotBlank() }.sorted() }
                    if (folders.isEmpty()) {
                        EmptyStateView(
                            icon = Icons.Default.Folder,
                            headline = "No folders found",
                            description = "Your music folders will appear here."
                        )
                    } else {
                        val bottomInset = com.gratia.music.ui.LocalBottomPadding.current
                        LazyColumn(
                            contentPadding = PaddingValues(bottom = bottomInset + GratiaTheme.spacing.heroLarge, top = GratiaTheme.spacing.small, start = GratiaTheme.spacing.large, end = GratiaTheme.spacing.large),
                            verticalArrangement = Arrangement.spacedBy(GratiaTheme.spacing.mediumSmall)
                        ) {
                            items(folders) { folder ->
                                val coverArtPath = allSongs.firstOrNull { it.storagePath?.substringBeforeLast("/")?.substringAfterLast("/") == folder && it.coverArtPath != null }?.coverArtPath
                                AppleListRow(
                                    title = folder,
                                    leadingContent = {
                                        if (coverArtPath != null) {
                                            CoverArtImage(coverArtPath = coverArtPath, title = folder, size = 56.dp, cornerRadius = 6.dp)
                                        } else {
                                            FolderFallback(size = 56.dp)
                                        }
                                    },
                                    onClick = { onNavigateToFolder(folder) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Selection Toolbar overlay
        AnimatedVisibility(
            visible = isSelectionMode,
            enter = slideInVertically(
                initialOffsetY = { -it },
                animationSpec = spring(
                    dampingRatio = 0.8f,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) + fadeIn(),
            exit = slideOutVertically(
                targetOffsetY = { -it }
            ) + fadeOut()
        ) {
            SelectionToolbar(
                selectedCount = selectedIds.size,
                totalCount = activeSongs.size,
                onAddToQueue = {
                    val selectedSongs = activeSongs.filter { selectedIds.contains(it.id) }
                    selectedSongs.forEach { playerViewModel.addToQueue(it) }
                    android.widget.Toast.makeText(context, "${selectedSongs.size} songs added to queue", android.widget.Toast.LENGTH_SHORT).show()
                    selectionManager.clearSelection()
                },
                onAddToPlaylist = {
                    selectionManager.clearSelection()
                },
                onDelete = {
                    val selectedSongs = activeSongs.filter { selectedIds.contains(it.id) }
                    selectedSongs.forEach { song ->
                        playerViewModel.deleteSong(song) {
                            try {
                                val uri = android.net.Uri.parse(song.localUri)
                                val file = java.io.File(uri.path ?: "")
                                if (file.exists()) file.delete()
                            } catch (e: Exception) { e.printStackTrace() }
                        }
                    }
                    android.widget.Toast.makeText(context, "${selectedSongs.size} songs deleted", android.widget.Toast.LENGTH_SHORT).show()
                    selectionManager.clearSelection()
                },
                onSelectAll = {
                    if (selectedIds.size == activeSongs.size) {
                        selectionManager.clearSelection()
                    } else {
                        selectionManager.selectAll(activeSongs.map { it.id })
                    }
                },
                onClose = { selectionManager.clearSelection() }
            )
        }
    }
}

/** "12 songs" for a row's summary line, or null so an empty row stays a one-liner. */
private fun countLabel(count: Int): String? =
    if (count > 0) "$count song${if (count == 1) "" else "s"}" else null

@Composable
fun ArtistRowImage(artistName: String, fallbackPath: String?, size: androidx.compose.ui.unit.Dp) {
    val artistRepo = remember { com.gratia.music.data.repository.ArtistRepository(com.gratia.music.GratiaApp.instance.database.artistDao()) }
    val artistEntity by artistRepo.getArtistFlow(artistName).collectAsState(initial = null)
    
    val displayImagePath = artistEntity?.localPicturePath ?: artistEntity?.pictureUrl

    if (displayImagePath != null) {
        val model = if (displayImagePath.startsWith("/")) java.io.File(displayImagePath) else displayImagePath
        coil.compose.SubcomposeAsyncImage(
            model = model,
            contentDescription = artistName,
            modifier = Modifier.size(size).clip(androidx.compose.foundation.shape.CircleShape),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            loading = {
                Box(modifier = Modifier.fillMaxSize().background(com.gratia.music.ui.components.shimmerBrush()))
            }
        )
    } else if (fallbackPath != null) {
        CoverArtImage(coverArtPath = fallbackPath, title = artistName, size = size, cornerRadius = size / 2)
    } else {
        ArtistFallback(artistName = artistName, size = size)
    }
}
