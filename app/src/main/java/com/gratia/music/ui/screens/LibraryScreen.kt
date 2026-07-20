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
    onNavigateToFolder: (String) -> Unit = {}
) {
    val songRepo = remember { SongRepository(GratiaApp.instance.database.songDao()) }
    val allSongs by songRepo.getAllSongs().collectAsState(initial = emptyList())
    
    // activeSubView: null means root library menu
    var activeSubView by remember { mutableStateOf<String?>(null) }
    
    val navController = LocalNavController.current

    BackHandler(enabled = activeSubView != null) {
        activeSubView = null
    }

    val springSpec = GratiaTheme.motion.springStandard<androidx.compose.ui.unit.IntOffset>()

    AnimatedContent(
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
                onNavigateToArtists = { activeSubView = "Artists" },
                onNavigateToAlbums = { activeSubView = "Albums" },
                onNavigateToSongs = { activeSubView = "Songs" },
                onNavigateToFolders = { activeSubView = "Folders" },
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
    onNavigateToArtists: () -> Unit,
    onNavigateToAlbums: () -> Unit,
    onNavigateToSongs: () -> Unit,
    onNavigateToFolders: () -> Unit,
    onNavigateToAlbum: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(GratiaTheme.colors.background),
        contentPadding = PaddingValues(bottom = GratiaTheme.spacing.heroLarge)
    ) {
        item {
            AppleLargeTitleHeader(title = "Library")
            Spacer(modifier = Modifier.height(8.dp))
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
                    text = title,
                    style = GratiaTheme.typography.title,
                    color = GratiaTheme.colors.textPrimary
                )
            }

            // Content
            when (title) {
                "Songs" -> {
                    if (sortedSongs.isEmpty()) {
                        EmptyStateView(
                            icon = Icons.Default.LibraryMusic,
                            headline = "Your library is empty",
                            description = "Add music to begin listening."
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(bottom = GratiaTheme.spacing.heroLarge, top = GratiaTheme.spacing.small),
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
                        LazyColumn(
                            contentPadding = PaddingValues(bottom = GratiaTheme.spacing.heroLarge, top = GratiaTheme.spacing.small, start = GratiaTheme.spacing.large, end = GratiaTheme.spacing.large),
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
                    val artists = remember(allSongs) { allSongs.map { it.artist }.distinct().sorted() }
                    if (artists.isEmpty()) {
                        EmptyStateView(
                            icon = Icons.Default.Person,
                            headline = "No artists found",
                            description = "Artists will appear here automatically."
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(bottom = GratiaTheme.spacing.heroLarge, top = GratiaTheme.spacing.small, start = GratiaTheme.spacing.large, end = GratiaTheme.spacing.large),
                            verticalArrangement = Arrangement.spacedBy(GratiaTheme.spacing.mediumSmall)
                        ) {
                            items(artists) { artist ->
                                val coverArtPath = allSongs.firstOrNull { it.artist == artist && it.coverArtPath != null }?.coverArtPath
                                AppleListRow(
                                    title = artist,
                                    leadingContent = {
                                        if (coverArtPath != null) {
                                            CoverArtImage(coverArtPath = coverArtPath, title = artist, size = 56.dp, cornerRadius = 28.dp)
                                        } else {
                                            ArtistFallback(artistName = artist, size = 56.dp)
                                        }
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
                        LazyColumn(
                            contentPadding = PaddingValues(bottom = GratiaTheme.spacing.heroLarge, top = GratiaTheme.spacing.small, start = GratiaTheme.spacing.large, end = GratiaTheme.spacing.large),
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
                totalCount = sortedSongs.size,
                onAddToQueue = {
                    val selectedSongs = sortedSongs.filter { selectedIds.contains(it.id) }
                    selectedSongs.forEach { playerViewModel.addToQueue(it) }
                    selectionManager.clearSelection()
                },
                onAddToPlaylist = {
                    selectionManager.clearSelection()
                },
                onDelete = {
                    val selectedSongs = sortedSongs.filter { selectedIds.contains(it.id) }
                    selectedSongs.forEach { song ->
                        playerViewModel.deleteSong(song) {
                            try {
                                val uri = android.net.Uri.parse(song.localUri)
                                val file = java.io.File(uri.path ?: "")
                                if (file.exists()) file.delete()
                            } catch (e: Exception) { e.printStackTrace() }
                        }
                    }
                    selectionManager.clearSelection()
                },
                onSelectAll = {
                    if (selectedIds.size == sortedSongs.size) {
                        selectionManager.clearSelection()
                    } else {
                        selectionManager.selectAll(sortedSongs.map { it.id })
                    }
                },
                onClose = { selectionManager.clearSelection() }
            )
        }
    }
}
