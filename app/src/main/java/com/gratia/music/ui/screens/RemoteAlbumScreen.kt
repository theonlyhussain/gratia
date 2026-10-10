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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gratia.music.GratiaApp
import com.gratia.music.data.model.SongEntity
import com.gratia.music.data.repository.SavedAlbumRepository
import com.gratia.music.player.PlaybackOrigin
import com.gratia.music.player.PlayerViewModel
import com.gratia.music.provider.RemoteAlbum
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
import com.gratia.music.ui.theme.rememberArtworkPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A remote album, set in the same visual language as the artist and playlist
 * pages (spec 35/68–70).
 *
 * The sleeve is the same full-bleed [DetailHero] the artist page uses, drawn
 * with the same dissolve and over the same adaptive wash, and the actions are the
 * shared detail-page pills — so moving artist → album → playlist never feels
 * like leaving the design.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteAlbumScreen(
    browseId: String,
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit,
    onNavigateToArtist: (String) -> Unit = {},
    /** Display title known from the point of navigation, shown before the fetch answers. */
    knownTitle: String? = null,
) {
    var album by remember { mutableStateOf<RemoteAlbum?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedSongForMenu by remember { mutableStateOf<SongEntity?>(null) }
    var showAddToPlaylistFor by remember { mutableStateOf<SongEntity?>(null) }

    val currentSong by playerViewModel.currentSong.collectAsState()
    val isPlaying by playerViewModel.isPlaying.collectAsState()
    val favoriteSongIds by playerViewModel.favoriteSongIds.collectAsState()

    LaunchedEffect(browseId) {
        isLoading = true
        error = null
        try {
            val res = withContext(Dispatchers.IO) {
                GratiaApp.instance.providerManager.youtubeMusicProvider.getAlbum(browseId)
            }
            if (res != null) {
                // Same merge rule as the artist page: a partial album response
                // keeps the fields it did not supply rather than blanking them
                // (spec N/O/P).
                album = com.gratia.music.provider.RemoteDetailMerge.mergeAlbum(album, res)
            } else {
                error = "Couldn't load album details."
            }
        } catch (e: Exception) {
            error = "Failed to load album: ${e.message}"
        } finally {
            isLoading = false
        }
    }

    // Saved state, read from the same table the Library's Albums list reads.
    // Saving here and the row appearing in Library are therefore one fact, not
    // two pieces of state that can disagree (spec 19/119).
    val savedAlbumRepo = remember {
        SavedAlbumRepository(GratiaApp.instance.database.savedAlbumDao())
    }
    val isAlbumSaved by remember(browseId) { savedAlbumRepo.isSavedFlow(browseId) }
        .collectAsState(initial = false)
    val saveScope = rememberCoroutineScope()

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
            // Useful from an album page; "Go to album" would be circular.
            onGoToArtist = song.providerArtistId?.takeIf { it.isNotBlank() }?.let { id ->
                { selectedSongForMenu = null; onNavigateToArtist(id) }
            }
        )
    }

    showAddToPlaylistFor?.let { song ->
        AddToPlaylistSheet(
            song = song,
            onDismiss = { showAddToPlaylistFor = null }
        )
    }

    val palette = rememberArtworkPalette(album?.artworkUrl)

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
                    // Skeleton, not a spinner: the title the listener tapped is
                    // already known, and the skeleton's geometry matches the real
                    // page so nothing jumps when the data lands (spec 47/48).
                    DetailPageSkeleton(knownTitle = knownTitle)
                }
                error != null || album == null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            GratiaText(
                                text = error ?: "Album not found",
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
                    val currentAlbum = album!!
                    val trackEntities = remember(currentAlbum) {
                        currentAlbum.tracks.map { RemoteTrackMapper.toSongEntity(it) }
                    }
                    val bottomInset = LocalBottomPadding.current
                    val artistName = currentAlbum.artists.joinToString(", ") { it.name }
                        .ifBlank { "YouTube Music" }
                    val artistId = currentAlbum.artists.firstOrNull()?.id

                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val heroHeight = maxWidth.coerceAtMost(maxHeight * 0.52f)
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
                                    artworkUrl = currentAlbum.artworkUrl,
                                    palette = palette,
                                    title = currentAlbum.title,
                                    height = heroHeight,
                                    subtitle = artistName,
                                    meta = albumInfoLine(currentAlbum),
                                    titleSize = 30.sp,
                                    onSubtitleClick = artistId
                                        ?.takeIf { it.isNotBlank() }
                                        ?.let { id -> { onNavigateToArtist(id) } },
                                )
                            }

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
                                    // Save sits with the actions because it is a
                                    // library decision about this album — the same
                                    // one the artist page offers for an artist.
                                    DetailCircleButton(
                                        icon = if (isAlbumSaved) Icons.Default.Check else Icons.Default.Add,
                                        contentDescription = if (isAlbumSaved) "Saved" else "Save album",
                                        palette = palette,
                                        onClick = {
                                            saveScope.launch {
                                                if (isAlbumSaved) {
                                                    savedAlbumRepo.unsave(browseId)
                                                } else {
                                                    savedAlbumRepo.save(
                                                        remoteId = browseId,
                                                        title = currentAlbum.title,
                                                        artistName = currentAlbum.artists.firstOrNull()?.name,
                                                        artistId = artistId,
                                                        artworkUrl = currentAlbum.artworkUrl,
                                                        year = currentAlbum.year,
                                                    )
                                                }
                                            }
                                        },
                                    )

                                    if (trackEntities.isNotEmpty()) {
                                        DetailCircleButton(
                                            icon = Icons.Default.Shuffle,
                                            contentDescription = "Shuffle",
                                            palette = palette,
                                            onClick = {
                                                val shuffled = trackEntities.shuffled()
                                                playerViewModel.playSong(
                                                    shuffled.first(),
                                                    shuffled,
                                                    PlaybackOrigin.ALBUM,
                                                )
                                            },
                                        )
                                        DetailPlayPill(
                                            palette = palette,
                                            onClick = {
                                                playerViewModel.playSong(
                                                    trackEntities.first(),
                                                    trackEntities,
                                                    PlaybackOrigin.ALBUM,
                                                )
                                            },
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
                                            PlaybackOrigin.ALBUM,
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

/** "2024 • 12 tracks • 44 min" from whichever fields the album actually carries. */
private fun albumInfoLine(album: RemoteAlbum): String? =
    listOfNotNull(
        album.year,
        album.trackCount?.let { "$it track${if (it == 1) "" else "s"}" },
        album.durationText,
    ).joinToString(" • ").takeIf { it.isNotBlank() }
