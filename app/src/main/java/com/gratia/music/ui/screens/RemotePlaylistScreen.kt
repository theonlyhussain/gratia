package com.gratia.music.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gratia.music.GratiaApp
import com.gratia.music.data.model.SongEntity
import com.gratia.music.player.PlaybackOrigin
import com.gratia.music.player.PlayerViewModel
import com.gratia.music.provider.RemotePlaylist
import com.gratia.music.provider.RemoteTrackMapper
import com.gratia.music.ui.LocalBottomPadding
import com.gratia.music.ui.components.AddToPlaylistSheet
import com.gratia.music.ui.components.DetailCircleButton
import com.gratia.music.ui.components.DetailHero
import com.gratia.music.ui.components.DetailPageBackground
import com.gratia.music.ui.components.DetailPageSkeleton
import com.gratia.music.ui.components.DetailPlayPill
import com.gratia.music.ui.components.GratiaText
import com.gratia.music.ui.components.SongMenuSheet
import com.gratia.music.ui.components.SongRow
import com.gratia.music.ui.theme.GratiaTheme
import com.gratia.music.ui.theme.Inter
import com.gratia.music.ui.theme.SpaceGrotesk
import com.gratia.music.ui.theme.rememberArtworkPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A remote playlist, set in the same visual language as the artist and album
 * pages (spec 28–31).
 *
 * What this replaces: a flat page with a 200dp rounded square of artwork on it
 * and a single two-stop gradient behind. Playlist used to be the odd page out —
 * no full-bleed sleeve, no dissolve, no mesh. It now opens with the shared
 * [DetailHero], so the sleeve dissolves into the page's own colours exactly as
 * the artist page's photograph does, and the back control and action row are the
 * shared detail-page chrome (spec 68–71).
 *
 * Everything on the page is drawn from one palette read off the playlist's own
 * artwork, so the ground, the wash under the sleeve and the text all agree with
 * the cover the listener tapped.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemotePlaylistScreen(
    playlistId: String,
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit,
    onNavigateToArtist: (String) -> Unit = {},
    onNavigateToAlbum: (String) -> Unit = {},
    /** Display title known from the point of navigation, shown before the fetch answers. */
    knownTitle: String? = null,
) {
    var playlist by remember { mutableStateOf<RemotePlaylist?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedSongForMenu by remember { mutableStateOf<SongEntity?>(null) }
    var showAddToPlaylistFor by remember { mutableStateOf<SongEntity?>(null) }

    val currentSong by playerViewModel.currentSong.collectAsState()
    val isPlaying by playerViewModel.isPlaying.collectAsState()
    val favoriteSongIds by playerViewModel.favoriteSongIds.collectAsState()

    LaunchedEffect(playlistId) {
        isLoading = true
        error = null
        try {
            val res = withContext(Dispatchers.IO) {
                GratiaApp.instance.providerManager.youtubeMusicProvider.getPlaylist(playlistId)
            }
            if (res != null) {
                playlist = res
            } else {
                error = "Couldn't load playlist details."
            }
        } catch (e: Exception) {
            error = "Failed to load playlist: ${e.message}"
        } finally {
            isLoading = false
        }
    }

    selectedSongForMenu?.let { song ->
        SongMenuSheet(
            song = song,
            isFavorite = favoriteSongIds.contains(song.id),
            onDismiss = { selectedSongForMenu = null },
            onPlayNext = { playerViewModel.playNext(song) },
            onAddToQueue = { playerViewModel.addToQueue(song) },
            onToggleLike = { playerViewModel.toggleFavorite(song) },
            onAddToPlaylist = {
                selectedSongForMenu = null
                showAddToPlaylistFor = song
            },
            downloadState = playerViewModel.downloadStateFor(song),
            onDownload = { playerViewModel.downloadSong(song) },
            onRemoveDownload = { playerViewModel.removeDownload(song) },
            onGoToArtist = song.providerArtistId?.takeIf { it.isNotBlank() }?.let { id ->
                { selectedSongForMenu = null; onNavigateToArtist(id) }
            },
            onGoToAlbum = song.providerAlbumId?.takeIf { it.isNotBlank() }?.let { id ->
                { selectedSongForMenu = null; onNavigateToAlbum(id) }
            }
        )
    }

    showAddToPlaylistFor?.let { song ->
        AddToPlaylistSheet(
            song = song,
            onDismiss = { showAddToPlaylistFor = null }
        )
    }

    // The page's ground is the playlist's own colour from the first frame the
    // artwork is known — before the tracks land, the sleeve on the skeleton is
    // already tinting the page it sits on (spec 31/70).
    val palette = rememberArtworkPalette(playlist?.artworkUrl)

    Scaffold(
        containerColor = palette.background,
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = palette.onBackground,
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
            when {
                isLoading -> {
                    // Skeleton, not a spinner: the known title first, page-shaped
                    // geometry for the rest (spec 47/48).
                    DetailPageSkeleton(knownTitle = knownTitle)
                }
                error != null || playlist == null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            GratiaText(
                                text = error ?: "Playlist not found",
                                style = GratiaTheme.typography.body,
                                color = GratiaTheme.colors.textSecondary
                            )
                            Spacer(Modifier.height(16.dp))
                            Button(
                                onClick = onBack,
                                colors = ButtonDefaults.buttonColors(containerColor = GratiaTheme.colors.accent)
                            ) {
                                Text("Go Back", color = Color.White)
                            }
                        }
                    }
                }
                else -> {
                    val currentPlaylist = playlist!!
                    val trackEntities = remember(currentPlaylist) {
                        currentPlaylist.tracks.map { RemoteTrackMapper.toSongEntity(it) }
                    }
                    val bottomInset = LocalBottomPadding.current

                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        // A full-bleed sleeve, matching the artist page's
                        // proportions: as tall as the page is wide, capped so the
                        // actions stay reachable without scrolling on short
                        // screens.
                        val heroHeight = maxWidth.coerceAtMost(maxHeight * 0.52f)
                        // The wash holds exactly under the sleeve and then starts
                        // its settle — so the artwork's last row of pixels meets
                        // the colour the page is made of (spec 30/70).
                        val washFraction = (heroHeight / maxHeight).coerceIn(0.28f, 0.72f)

                        DetailPageBackground(
                            palette = palette,
                            modifier = Modifier.matchParentSize(),
                            washFraction = washFraction,
                        )

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                bottom = bottomInset + GratiaTheme.spacing.heroLarge
                            ),
                        ) {
                            item(key = "hero") {
                                DetailHero(
                                    artworkUrl = currentPlaylist.artworkUrl,
                                    palette = palette,
                                    title = currentPlaylist.title,
                                    height = heroHeight,
                                    subtitle = currentPlaylist.author?.takeIf { it.isNotBlank() },
                                    meta = trackCountText(currentPlaylist),
                                    titleSize = 30.sp,
                                )
                            }

                            // Play / Shuffle in the shared detail-page chrome. A
                            // playlist has no local save state, so nothing is
                            // offered that cannot be honoured (spec 29).
                            if (trackEntities.isNotEmpty()) {
                                item(key = "actions") {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 24.dp, vertical = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(
                                            10.dp, Alignment.CenterHorizontally
                                        ),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        DetailCircleButton(
                                            icon = Icons.Default.Shuffle,
                                            contentDescription = "Shuffle",
                                            palette = palette,
                                            onClick = {
                                                val shuffled = trackEntities.shuffled()
                                                playerViewModel.playSong(
                                                    shuffled.first(),
                                                    shuffled,
                                                    PlaybackOrigin.PLAYLIST,
                                                )
                                            },
                                        )
                                        DetailPlayPill(
                                            palette = palette,
                                            onClick = {
                                                playerViewModel.playSong(
                                                    trackEntities.first(),
                                                    trackEntities,
                                                    PlaybackOrigin.PLAYLIST,
                                                )
                                            },
                                        )
                                    }
                                }
                            }

                            // About the playlist — where a description exists, in
                            // the same continuing wash as the artist and album
                            // About sections, with no separate card (spec 36/71).
                            if (!currentPlaylist.description.isNullOrBlank()) {
                                item(key = "about") {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 24.dp, end = 24.dp, top = 14.dp, bottom = 10.dp)
                                    ) {
                                        Text(
                                            text = "About this playlist",
                                            fontFamily = SpaceGrotesk,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 16.sp,
                                            color = palette.onBackground,
                                        )
                                        Spacer(Modifier.height(6.dp))
                                        Text(
                                            text = currentPlaylist.description,
                                            fontFamily = Inter,
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 14.sp,
                                            lineHeight = 20.sp,
                                            color = palette.onBackgroundVariant,
                                            maxLines = 4,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }

                            itemsIndexed(trackEntities, key = { _, s -> s.id }) { index, song ->
                                SongRow(
                                    song = song,
                                    index = index,
                                    isActive = currentSong?.id == song.id,
                                    isPlaying = currentSong?.id == song.id && isPlaying,
                                    onClick = {
                                        playerViewModel.playSong(
                                            song,
                                            trackEntities,
                                            PlaybackOrigin.PLAYLIST,
                                        )
                                    },
                                    onMoreClick = { selectedSongForMenu = song },
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** "42 tracks" from whichever count the page actually knows. */
private fun trackCountText(playlist: RemotePlaylist): String? {
    val count = playlist.trackCount ?: playlist.tracks.size.takeIf { it > 0 }
    return count?.let { "$it track${if (it == 1) "" else "s"}" }
}
