package com.gratia.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gratia.music.GratiaApp
import com.gratia.music.data.model.SongEntity
import com.gratia.music.player.PlayerViewModel
import com.gratia.music.provider.BrowseCategory
import com.gratia.music.provider.BrowseEntry
import com.gratia.music.provider.BrowsePage
import com.gratia.music.provider.RemoteTrackMapper
import com.gratia.music.ui.components.AppleSectionHeader
import com.gratia.music.ui.components.GratiaLoadingState
import com.gratia.music.ui.components.GratiaText
import com.gratia.music.ui.components.MusicCard
import com.gratia.music.ui.components.RemoteMediaCard
import com.gratia.music.ui.theme.GratiaTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One Explore category (Hindi, Chill, 1990s, …), rendered as the shelves the
 * catalogue actually returns.
 *
 * Every shelf scrolls on its own and every card knows where it goes: albums to
 * album pages, artists to artist pages, playlists to playlist pages, tracks
 * straight into the unified player.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseCategoryScreen(
    browseId: String,
    params: String?,
    title: String,
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit,
    onNavigateToAlbum: (String) -> Unit = {},
    onNavigateToArtist: (String) -> Unit = {},
    onNavigateToPlaylist: (String) -> Unit = {}
) {
    val category = remember(browseId, params, title) {
        BrowseCategory(
            id = if (params.isNullOrBlank()) browseId else "$browseId:$params",
            title = title,
            browseId = browseId,
            params = params
        )
    }

    var page by remember { mutableStateOf<BrowsePage?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var isOffline by remember { mutableStateOf(false) }
    var refreshNonce by remember { mutableStateOf(0) }

    val currentSong by playerViewModel.currentSong.collectAsState()
    val isPlaying by playerViewModel.isPlaying.collectAsState()
    val isOnline by com.gratia.music.data.network.NetworkMonitor.isOnline.collectAsState()

    LaunchedEffect(category, refreshNonce, isOnline) {
        if (!isOnline) {
            // A category page is entirely remote: with no network there is
            // nothing to render and nothing worth pretending about.
            isOffline = page == null
            isLoading = false
            return@LaunchedEffect
        }

        isLoading = true
        error = null
        isOffline = false
        try {
            val result = withContext(Dispatchers.IO) {
                GratiaApp.instance.providerManager.youtubeMusicProvider.getBrowsePage(category)
            }
            page = result
            if (result.sections.isEmpty()) {
                error = "Nothing to show for $title right now."
            }
        } catch (e: Exception) {
            error = "Couldn't load $title: ${e.message}"
        } finally {
            isLoading = false
        }
    }

    Scaffold(
        containerColor = GratiaTheme.colors.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title,
                        style = GratiaTheme.typography.title,
                        fontWeight = FontWeight.Bold,
                        color = GratiaTheme.colors.textPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = GratiaTheme.colors.textPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val sections = page?.sections.orEmpty()
            when {
                isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    GratiaLoadingState(message = "Loading $title…")
                }

                sections.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        GratiaText(
                            text = if (isOffline) "You're offline" else error ?: "Nothing here yet.",
                            style = GratiaTheme.typography.body,
                            color = GratiaTheme.colors.textPrimary,
                            modifier = Modifier.padding(horizontal = 32.dp)
                        )
                        if (isOffline) {
                            Spacer(Modifier.height(4.dp))
                            GratiaText(
                                text = "This page needs a connection. Your library and downloads still work.",
                                style = GratiaTheme.typography.caption,
                                color = GratiaTheme.colors.textSecondary,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 32.dp)
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = { refreshNonce++ },
                            colors = ButtonDefaults.buttonColors(containerColor = GratiaTheme.colors.accent)
                        ) {
                            Text("Retry")
                        }
                    }
                }

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 48.dp)
                ) {
                    sections.forEachIndexed { sectionIndex, section ->
                        item(key = "header_${sectionIndex}_${section.title}") {
                            Spacer(Modifier.height(if (sectionIndex == 0) 8.dp else 24.dp))
                            AppleSectionHeader(title = section.title)
                        }
                        item(key = "row_${sectionIndex}_${section.title}") {
                            val trackQueue = remember(section) {
                                section.entries
                                    .filterIsInstance<BrowseEntry.Track>()
                                    .map { RemoteTrackMapper.toSongEntity(it.track) }
                            }
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 24.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(section.entries, key = { it.stableId }) { entry ->
                                    BrowseEntryItem(
                                        entry = entry,
                                        currentSongId = currentSong?.id,
                                        isPlaying = isPlaying,
                                        onPlayTrack = { song -> playerViewModel.playSong(song, trackQueue) },
                                        onNavigateToAlbum = onNavigateToAlbum,
                                        onNavigateToArtist = onNavigateToArtist,
                                        onNavigateToPlaylist = onNavigateToPlaylist
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BrowseEntryItem(
    entry: BrowseEntry,
    currentSongId: String?,
    isPlaying: Boolean,
    onPlayTrack: (SongEntity) -> Unit,
    onNavigateToAlbum: (String) -> Unit,
    onNavigateToArtist: (String) -> Unit,
    onNavigateToPlaylist: (String) -> Unit
) {
    when (entry) {
        is BrowseEntry.Track -> {
            val song = remember(entry) { RemoteTrackMapper.toSongEntity(entry.track) }
            MusicCard(
                song = song,
                isActive = currentSongId == song.id,
                isPlaying = currentSongId == song.id && isPlaying,
                onClick = { onPlayTrack(song) }
            )
        }

        is BrowseEntry.Album -> RemoteMediaCard(
            title = entry.album.title,
            subtitle = listOfNotNull(
                entry.album.artists.firstOrNull()?.name,
                entry.album.year
            ).joinToString(" • ").takeIf { it.isNotBlank() },
            artworkUrl = entry.album.artworkUrl,
            onClick = { onNavigateToAlbum(entry.album.id) }
        )

        is BrowseEntry.Artist -> {
            val artistId = entry.artist.id
            RemoteMediaCard(
                title = entry.artist.name,
                subtitle = null,
                artworkUrl = entry.artist.artworkUrl,
                circular = true,
                onClick = { if (artistId != null) onNavigateToArtist(artistId) }
            )
        }

        is BrowseEntry.Playlist -> RemoteMediaCard(
            title = entry.playlist.title,
            subtitle = entry.playlist.author,
            artworkUrl = entry.playlist.artworkUrl,
            onClick = { onNavigateToPlaylist(entry.playlist.id) }
        )
    }
}
