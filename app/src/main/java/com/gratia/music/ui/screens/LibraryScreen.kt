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
import com.gratia.music.GratiaApp
import com.gratia.music.data.model.SongEntity
import com.gratia.music.data.repository.SongRepository
import com.gratia.music.player.PlayerViewModel
import com.gratia.music.ui.LocalNavController
import com.gratia.music.ui.components.*
import com.gratia.music.ui.selection.SelectableSongRow
import com.gratia.music.ui.selection.SelectionManager
import com.gratia.music.ui.selection.SelectionToolbar
import com.gratia.music.ui.theme.GratiaTheme

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun LibraryScreen(
    playerViewModel: PlayerViewModel,
    onNavigateToAlbum: (String) -> Unit = {},
    onNavigateToArtist: (String) -> Unit = {},
    onNavigateToFolder: (String) -> Unit = {},
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
                onNavigateToFavorites = { activeSubView = "Favorites" },
                onNavigateToArtists = { activeSubView = "Artists" },
                onNavigateToAlbums = { activeSubView = "Albums" },
                onNavigateToSongs = { activeSubView = "Songs" },
                onNavigateToFolders = { activeSubView = "Folders" },
                onNavigateToDownloads = { activeSubView = "Downloads" },
                onNavigateToLocalMusic = { activeSubView = "LocalMusic" },
                onNavigateToAllOnDevice = { activeSubView = "AllOnDevice" },
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
                onNavigateToFolder = onNavigateToFolder
            )
        }
    }
}

@Composable
fun LibraryRootView(
    allSongs: List<SongEntity>,
    onNavigateToPlaylists: () -> Unit,
    onNavigateToFavorites: () -> Unit,
    onNavigateToArtists: () -> Unit,
    onNavigateToAlbums: () -> Unit,
    onNavigateToSongs: () -> Unit,
    onNavigateToFolders: () -> Unit,
    onNavigateToDownloads: () -> Unit,
    onNavigateToLocalMusic: () -> Unit,
    onNavigateToAllOnDevice: () -> Unit,
    onNavigateToAlbum: (String) -> Unit
) {
    val bottomInset = com.gratia.music.ui.LocalBottomPadding.current

    // The three On Device counts, kept as counts rather than derived per row so
    // the summary line can say how much is actually there before you tap it.
    val downloadsCount = remember(allSongs) { allSongs.count { it.isDownloaded } }
    val localMusicCount = remember(allSongs) {
        allSongs.count { it.storageProvider == "local" && !it.isDownloaded }
    }
    val onDeviceCount = downloadsCount + localMusicCount

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

        // ── On Device ──────────────────────────────────────────────────────
        // Three distinct things, deliberately not one list: a Gratia-managed
        // download is not the same object as an audio file that was already on
        // the phone, and a user-imported MP3 must never read as a download.
        item {
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
                onClick = onNavigateToLocalMusic
            )
        }
        item {
            AppleListRow(
                title = "All On Device",
                subtitle = countLabel(onDeviceCount),
                leadingContent = {
                    Icon(
                        imageVector = Icons.Default.LibraryMusic,
                        contentDescription = "All On Device",
                        tint = GratiaTheme.colors.accent,
                        modifier = Modifier.size(28.dp)
                    )
                },
                onClick = onNavigateToAllOnDevice,
                showDivider = false
            )
        }

        // Library Menu Items
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
                onClick = onNavigateToAlbums
            )
        }
        item {
            AppleListRow(
                title = "Songs",
                leadingContent = {
                    Icon(
                        imageVector = Icons.Default.LibraryMusic,
                        contentDescription = "Songs",
                        tint = GratiaTheme.colors.accent,
                        modifier = Modifier.size(28.dp)
                    )
                },
                onClick = onNavigateToSongs
            )
        }

        item {
            AppleListRow(
                title = "Folders",
                leadingContent = {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = "Folders",
                        tint = GratiaTheme.colors.accent,
                        modifier = Modifier.size(28.dp)
                    )
                },
                onClick = onNavigateToFolders,
                showDivider = false
            )
        }

        // Recently Added Section
        item {
            Spacer(modifier = Modifier.height(24.dp))
            AppleSectionHeader(title = "Recently Added")
        }

        // We can display the latest albums as a grid, or just the latest songs. Apple shows albums/playlists usually.
        // Let's show recent albums.
        val recentAlbums = allSongs.filter { it.album != null }.sortedByDescending { it.createdAt }.distinctBy { it.album }.take(10)
        
        // Grid display using chunked list
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

@Composable
fun LibrarySubView(
    title: String,
    allSongs: List<SongEntity>,
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit,
    onNavigateToAlbum: (String) -> Unit,
    onNavigateToArtist: (String) -> Unit,
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
    val activeSongs: List<SongEntity> = when (title) {
        "Favorites" -> allSongs.filter { it.isFavorite }
        "Downloads" -> allSongs.filter { it.isDownloaded }
        // A Gratia-managed download is excluded: it is on the device, but it is
        // not a file the user put there, and the two must not be conflated.
        "LocalMusic" -> allSongs.filter { it.storageProvider == "local" && !it.isDownloaded }
        "AllOnDevice" -> allSongs.filter { it.isDownloaded || it.storageProvider == "local" }
        else -> sortedSongs
    }

    // Internal ids stay camel-cased for routing; the header shows the label.
    val displayTitle = when (title) {
        "LocalMusic" -> "Local Music"
        "AllOnDevice" -> "All On Device"
        else -> title
    }

    Box(modifier = Modifier.fillMaxSize().background(GratiaTheme.colors.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Apple-style back header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
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
                                    onPlay = { playerViewModel.playSong(song, favoriteSongs) },
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
                "Downloads", "LocalMusic", "AllOnDevice" -> {
                    if (activeSongs.isEmpty()) {
                        EmptyStateView(
                            icon = when (title) {
                                "Downloads" -> Icons.Default.Download
                                "LocalMusic" -> Icons.Default.Folder
                                else -> Icons.Default.LibraryMusic
                            },
                            headline = when (title) {
                                "Downloads" -> "No downloads yet"
                                "LocalMusic" -> "No local music found"
                                else -> "Nothing on this device yet"
                            },
                            description = when (title) {
                                "Downloads" -> "Download songs to listen offline."
                                "LocalMusic" -> "Audio files already on this device will appear here."
                                else -> "Downloads and local music will appear here."
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
                                    onPlay = { playerViewModel.playSong(song, activeSongs) },
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
                                    onPlay = { playerViewModel.playSong(song, sortedSongs) },
                                    onLongPress = { selectionManager.startSelection(song.id) },
                                    onToggleSelection = { selectionManager.toggle(song.id) },
                                    modifier = Modifier.padding(horizontal = GratiaTheme.spacing.mediumSmall)
                                )
                            }
                        }
                    }
                }
                "Albums" -> {
                    val albums = remember(allSongs) { allSongs.mapNotNull { it.album }.distinct().sorted() }
                    if (albums.isEmpty()) {
                        EmptyStateView(
                            icon = Icons.Default.Album,
                            headline = "No albums found",
                            description = "Albums will appear here automatically."
                        )
                    } else {
                        val bottomInset = com.gratia.music.ui.LocalBottomPadding.current
                        LazyColumn(
                            contentPadding = PaddingValues(bottom = bottomInset + GratiaTheme.spacing.heroLarge, top = GratiaTheme.spacing.small, start = GratiaTheme.spacing.large, end = GratiaTheme.spacing.large),
                            verticalArrangement = Arrangement.spacedBy(GratiaTheme.spacing.mediumSmall)
                        ) {
                            items(albums) { album ->
                                val coverArtPath = allSongs.firstOrNull { it.album == album && it.coverArtPath != null }?.coverArtPath
                                AppleListRow(
                                    title = album,
                                    leadingContent = {
                                        CoverArtImage(coverArtPath = coverArtPath, title = album, size = 56.dp, cornerRadius = 6.dp)
                                    },
                                    onClick = { onNavigateToAlbum(album) }
                                )
                            }
                        }
                    }
                }
                "Artists" -> {
                    val artists = remember(allSongs) { 
                        allSongs.flatMap { com.gratia.music.utils.ArtistParser.parseArtists(it.artist) }.distinct().sorted() 
                    }
                    if (artists.isEmpty()) {
                        EmptyStateView(
                            icon = Icons.Default.Person,
                            headline = "No artists found",
                            description = "Artists will appear here automatically."
                        )
                    } else {
                        val bottomInset = com.gratia.music.ui.LocalBottomPadding.current
                        LazyColumn(
                            contentPadding = PaddingValues(bottom = bottomInset + GratiaTheme.spacing.heroLarge, top = GratiaTheme.spacing.small, start = GratiaTheme.spacing.large, end = GratiaTheme.spacing.large),
                            verticalArrangement = Arrangement.spacedBy(GratiaTheme.spacing.mediumSmall)
                        ) {
                            items(artists) { artist ->
                                val coverArtPath = allSongs.firstOrNull { com.gratia.music.utils.ArtistParser.parseArtists(it.artist).contains(artist) && it.coverArtPath != null }?.coverArtPath
                                AppleListRow(
                                    title = artist,
                                    leadingContent = {
                                        ArtistRowImage(artistName = artist, fallbackPath = coverArtPath, size = 56.dp)
                                    },
                                    onClick = { onNavigateToArtist(artist) }
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
