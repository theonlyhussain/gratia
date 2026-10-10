/*
 * Copyright (C) 2026 Kushagra Singh / Gratia contributors
 * Modified for Gratia by Hussain / Gratia contributors, 2026
 * Licensed under the GNU General Public License v3.0
 *
 * Source: ui/screens/DetailScreen.kt (artist page), ui/theme/ArtworkPalette.kt,
 * ui/components/ArtworkBackdrop.kt.
 *
 * Modifications for Gratia: navigated by Gratia's NavHost routes and
 * RemoteArtist/RemoteTrack models instead of DetailPage; Coil 2
 * request API; gratia theme fonts (Space Grotesk / Inter) and GlassSurface
 * tokens; Data loading is Gratia's providerManager pattern rather than UiState ViewModel.
 */
package com.gratia.music.ui.screens

import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.util.Log
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.lerp as colorLerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.gratia.music.GratiaApp
import com.gratia.music.data.model.SongEntity
import com.gratia.music.data.network.NetworkMonitor
import com.gratia.music.data.repository.SavedArtistRepository
import com.gratia.music.player.PlayerViewModel
import com.gratia.music.player.PlaybackOrigin
import com.gratia.music.provider.RemoteAlbumSummary
import com.gratia.music.provider.RemoteArtist
import com.gratia.music.provider.RemoteTrack
import com.gratia.music.provider.RemoteDetailMerge
import com.gratia.music.provider.RemoteTrackMapper
import com.gratia.music.ui.LocalBottomPadding
import com.gratia.music.ui.components.DetailHeroArtwork
import com.gratia.music.ui.components.DetailPageBackground
import com.gratia.music.ui.components.artworkAtSize
import com.gratia.music.ui.components.LARGE_ARTWORK_PX
import com.gratia.music.ui.components.ROW_ARTWORK_PX
import com.gratia.music.ui.components.GratiaLoadingState
import com.gratia.music.ui.components.GratiaText
import com.gratia.music.ui.components.SongMenuSheet
import com.gratia.music.ui.theme.ArtworkPalette
import com.gratia.music.ui.theme.GratiaTheme
import com.gratia.music.ui.theme.SpaceGrotesk
import com.gratia.music.ui.theme.rememberArtworkPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import com.gratia.music.ui.components.shimmer

/** The artist photo, very slightly taller than it is wide — the original ARTIST_PHOTO_RATIO. */
private const val ARTIST_PHOTO_RATIO = 0.95f

/** A release's sleeve, given a little more height than the artist photo. */
private const val SLEEVE_RATIO = 0.92f

private val SLEEVE_SHAPE = RoundedCornerShape(12.dp)
private val PILL_SHAPE = RoundedCornerShape(12.dp)

/** The inset the header text and the action pills share — the original HEADER_GUTTER. */
private val HEADER_GUTTER = 24.dp + 14.dp

/** Extra breathing room for the editorial copy — the original ABOUT_GUTTER. */
private val ABOUT_GUTTER = 24.dp + 12.dp

/**
 * How far past the foot of the artwork the title block hangs — the original
 * HEADER_DROP: the title sits on the page's own colour rather than on the
 * picture, which is why it hangs below the foot of the artwork.
 */
private val HEADER_DROP = 44.dp

/** How many of a shelf's cards a detail row shows before it offers Show all. */
private const val SHELF_ROW_MAX_ITEMS = 5

/**
 * The vertical rhythm between the artist page's sections (spec B). One token
 * rather than a different number at each call site: the page used to run its
 * shelves together at 22dp while Top Songs sat flush against the action row,
 * which is what made it read as cramped. Every section gap and section top
 * comes from here, so the rhythm stays even as sections are added or removed.
 */
private val SECTION_GAP = 28.dp

/**
 * The remote artist destination, ported from the original artist detail page.
 *
 * The page paints itself in the artwork's own colours — a tint behind
 * everything, the artwork itself across the top of it, and an accent taken off
 * the picture for the credit line and the Play/Shuffle pair. See
 * [rememberArtworkPalette] for how those are derived and kept legible.
 *
 * It is built in two layers and the order is the trick:
 *
 *  1. [ArtistPageBackground] — the wash and the artwork, and nothing you can read.
 *  2. The list — titles, buttons and rows, drawn over it and so sharp.
 *
 * The photograph is neither blurred nor masked. It is simply allowed to run out:
 * sharp and opaque through its upper half, then dissolving along an eased alpha
 * curve until its last row of pixels *is* the colour the page is made of. There
 * is no join to soften, because at the join the two sides are the same colour.
 *
 * An earlier version blurred the artwork instead. That is a different effect and
 * a worse one: a blurred photograph reads as a load that has not finished, and
 * it takes the artist's face with it. Softening the picture is not the same as
 * letting it fade.
 */
@Composable
fun RemoteArtistScreen(
    channelId: String,
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit,
    onNavigateToAlbum: (String) -> Unit = {},
    onNavigateToArtist: (String) -> Unit = {},
    /** Opens the full Top Songs list as its own destination (spec G/H). */
    onShowAllSongs: (playlistId: String?, artistName: String?) -> Unit = { _, _ -> },
    /** Opens the full Albums list as its own destination. */
    onShowAllAlbums: (artistName: String?) -> Unit = {},
    /** Opens the full Singles & EPs list as its own destination. */
    onShowAllSingles: (artistName: String?) -> Unit = {}
) {
    val context = LocalContext.current
    var artist by remember { mutableStateOf<RemoteArtist?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var isOffline by remember { mutableStateOf(false) }
    var refreshNonce by remember { mutableStateOf(0) }
    var selectedSongForMenu by remember { mutableStateOf<SongEntity?>(null) }
    var showAddToPlaylistFor by remember { mutableStateOf<SongEntity?>(null) }

    val currentSong by playerViewModel.currentSong.collectAsState()
    val isPlaying by playerViewModel.isPlaying.collectAsState()
    val favoriteSongIds by playerViewModel.favoriteSongIds.collectAsState()
    val scope = rememberCoroutineScope()

    // Gratia's local saved-artist state. The button is instant: it writes the
    // local row and flips the icon in the same tap, with no network in the way.
    val savedArtistRepo = remember { SavedArtistRepository(GratiaApp.instance.database.savedArtistDao()) }
    val isArtistSaved by remember(channelId) { savedArtistRepo.isSavedFlow(channelId) }
        .collectAsState(initial = false)

    // Cache-for-offline: a previously saved artist keeps name/artwork here even
    // when the page itself cannot be fetched (spec 39). The cached data is
    // used only when the live fetch fails.
    var cachedSavedArtist by remember(channelId) {
        mutableStateOf<com.gratia.music.data.model.SavedArtistEntity?>(null)
    }
    LaunchedEffect(channelId) {
        cachedSavedArtist = savedArtistRepo.get(channelId)
    }

    fun toggleSavedArtist() {
        val artist = artist
        scope.launch {
            if (isArtistSaved) {
                savedArtistRepo.unsave(channelId)
            } else {
                // The saved entry keeps the remote identity plus whatever is on
                // screen at save time; a later refresh or Library visit fills
                // the rest. Saving must not depend on the page having finished
                // loading.
                savedArtistRepo.save(
                    remoteId = channelId,
                    name = artist?.name ?: cachedSavedArtist?.name ?: "Artist",
                    artworkUrl = artist?.artworkUrl ?: cachedSavedArtist?.artworkUrl,
                )
            }
        }
    }

    val listState = rememberLazyListState()
    val isDark = GratiaTheme.colors.isDark
    val palette = rememberArtworkPalette(artist?.artworkUrl, dark = isDark)

    // Full artist song list, paged from the playlist the page links.
    var allSongs by remember(channelId) { mutableStateOf<List<RemoteTrack>>(emptyList()) }
    var songsContinuation by remember(channelId) { mutableStateOf<String?>(null) }
    var songsLoadingMore by remember { mutableStateOf(false) }

    val artistCache = remember { com.gratia.music.data.cache.ArtistCache() }

    // Cache first, network second. A page visited before renders instantly from
    // the last answer — the shell of the screen with real data, not a spinner —
    // and the refresh below replaces it when the network answers (spec 51/52).
    LaunchedEffect(channelId) {
        val cached = withContext(Dispatchers.IO) { artistCache.get(channelId) }
        if (cached != null && artist == null) {
            Log.d("RemoteArtistScreen", "ARTIST_OPEN id=$channelId source=cache")
            artist = cached
            isLoading = false
            cached.songsPlaylistId?.let { playlistId ->
                runCatching {
                    withContext(Dispatchers.IO) {
                        GratiaApp.instance.providerManager.youtubeMusicProvider.getArtistSongs(playlistId)
                    }
                }.onSuccess { page ->
                    if (page.items.isNotEmpty()) {
                        allSongs = page.items
                        songsContinuation = page.continuation
                    }
                }
            }
        } else {
            Log.d("RemoteArtistScreen", "ARTIST_OPEN id=$channelId source=network")
        }
    }

    LaunchedEffect(channelId, refreshNonce) {
        Log.d("RemoteArtistScreen", "ARTIST_FETCH id=$channelId nonce=$refreshNonce")
        // Only the very first open waits behind a spinner; a cache hit has
        // already given the listener the page, and the refresh simply improves it.
        if (artist == null) isLoading = true
        error = null
        isOffline = false
        try {
            if (!NetworkMonitor.isCurrentlyOnline()) {
                // Offline with a cached page is a usable page, not an error
                // state — the cache branch above already filled it in.
                if (artist == null) isOffline = true
                isLoading = false
                return@LaunchedEffect
            }
            val res = withContext(Dispatchers.IO) {
                GratiaApp.instance.providerManager.youtubeMusicProvider.getArtist(channelId)
            }
            if (res != null) {
                Log.d("RemoteArtistScreen", "ARTIST_FETCH_OK id=$channelId songs=${res.topSongs.size} albums=${res.albums.size}")
                // Merge, never overwrite: a partial refresh must not replace a
                // known name with "Unknown Artist" or a good cover with null
                // (spec C/N/O/P). The merged result is what gets cached, so the
                // cache cannot be poisoned by a partial response either.
                val merged = RemoteDetailMerge.mergeArtist(artist, res)
                artist = merged
                artistCache.put(merged)
                // Keep the cached saved entry's identity fresh so offline
                // reopen has a real name and artwork to show (spec 39).
                if (isArtistSaved) {
                    savedArtistRepo.save(
                        remoteId = channelId,
                        name = res.name,
                        artworkUrl = res.artworkUrl,
                    )
                }
                // Kick off the full song list in the background — the landing
                // page only carries the first handful.
                res.songsPlaylistId?.let { playlistId ->
                    val page = runCatching {
                        withContext(Dispatchers.IO) {
                            GratiaApp.instance.providerManager.youtubeMusicProvider
                                .getArtistSongs(playlistId)
                        }
                    }.getOrNull()
                    if (page != null && page.items.isNotEmpty()) {
                        allSongs = page.items
                        songsContinuation = page.continuation
                    }
                }
            } else {
                // A cached page stands even when the refresh fails — stale
                // beats blank, and the next open retries.
                if (artist == null) error = "Couldn't load artist details."
            }
        } catch (e: Exception) {
            if (artist == null) error = "Failed to load artist: ${e.message}"
        } finally {
            isLoading = false
        }
    }

    // "Show all" on Top songs pages through the artist songs playlist.
    LaunchedEffect(songsContinuation, songsLoadingMore) {
        if (!songsLoadingMore) return@LaunchedEffect
        val token = songsContinuation ?: return@LaunchedEffect
        val playlistId = artist?.songsPlaylistId ?: return@LaunchedEffect
        val page = runCatching {
            withContext(Dispatchers.IO) {
                GratiaApp.instance.providerManager.youtubeMusicProvider
                    .getArtistSongs(playlistId, token)
            }
        }.getOrNull()
        if (page != null) {
            allSongs = (allSongs + page.items).distinctBy { it.videoId }
            songsContinuation = page.continuation
        }
        songsLoadingMore = false
    }

    selectedSongForMenu?.let { song ->
        val isFav = favoriteSongIds.contains(song.id)
        SongMenuSheet(
            song = song,
            isFavorite = isFav,
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
            // Useful from an artist page; "Go to artist" would be circular.
            onGoToAlbum = song.providerAlbumId?.takeIf { it.isNotBlank() }?.let { id ->
                { selectedSongForMenu = null; onNavigateToAlbum(id) }
            }
        )
    }

    showAddToPlaylistFor?.let { song ->
        com.gratia.music.ui.components.AddToPlaylistSheet(
            song = song,
            onDismiss = { showAddToPlaylistFor = null }
        )
    }

    val bottomInset = LocalBottomPadding.current

    Box(modifier = Modifier.fillMaxSize().background(palette.background)) {
        when {
            isLoading && artist == null -> {
                ArtistSkeletonScreen()
            }
            error != null && artist == null -> Box(
                Modifier.fillMaxSize().background(GratiaTheme.colors.background),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    GratiaText(
                        text = if (isOffline) "You're offline" else (error ?: "Artist not found"),
                        style = GratiaTheme.typography.body,
                        color = GratiaTheme.colors.textSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                    if (isOffline) {
                        Spacer(Modifier.height(4.dp))
                        GratiaText(
                            text = "This page needs a connection. Your library and downloads still work.",
                            style = GratiaTheme.typography.caption,
                            color = GratiaTheme.colors.textSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp)
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    androidx.compose.material3.Button(
                        onClick = { refreshNonce++ },
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = GratiaTheme.colors.accent
                        )
                    ) { Text("Retry", color = Color.White) }
                }
            }
            else -> {
                // Safe: this branch is only reached when `artist != null` (the
                // loading and error branches above both require it to be null),
                // but `!!` converts any future reordering of those branches
                // into a crash — the crash class this screen is here to end.
                val currentArtist = artist ?: return@Box
                val pageSongs = remember(currentArtist, allSongs) {
                    (allSongs.ifEmpty { currentArtist.topSongs })
                        .distinctBy { it.videoId }
                }

                ArtistDetailContent(
                            artist = currentArtist,
                            songs = pageSongs,
                            songsExhausted = songsContinuation == null,
                            onLoadMoreSongs = { songsLoadingMore = true },
                            palette = palette,
                            listState = listState,
                            currentSong = currentSong,
                            isPlaying = isPlaying,
                            bottomInset = bottomInset,
                            isSaved = isArtistSaved,
                            onToggleSaved = { toggleSavedArtist() },
                            onSongClick = { queue, index ->
                                // An out-of-range tap (stale index after the
                                // list refreshed under a gesture) must be a
                                // no-op, not an IndexOutOfBounds crash — the
                                // one crash class this screen was reworked to
                                // end (spec 24–31).
                                val entities = queue.map { RemoteTrackMapper.toSongEntity(it) }
                                val target = entities.getOrNull(index)
                                    ?: entities.firstOrNull()
                                    ?: return@ArtistDetailContent
                                playerViewModel.playSong(target, entities, PlaybackOrigin.ARTIST)
                            },
                            onSongLongPress = { track ->
                                selectedSongForMenu = RemoteTrackMapper.toSongEntity(track)
                            },
                            onShuffle = { queue ->
                                val entities = queue.map { RemoteTrackMapper.toSongEntity(it) }
                                    .ifEmpty { return@ArtistDetailContent }
                                playerViewModel.playSong(
                                    entities.random(),
                                    entities.shuffled(),
                                    PlaybackOrigin.ARTIST,
                                )
                            },
                            // "Show all" is real navigation: the list opens as
                            // its own destination, so Back returns to it rather
                            // than to this artist page (spec G/H/K).
                            onShowAllSongs = {
                                onShowAllSongs(currentArtist.songsPlaylistId, currentArtist.name)
                            },
                            onShelfShowAll = { shelf ->
                                if (shelf.title.contains("single", ignoreCase = true)) {
                                    onShowAllSingles(currentArtist.name)
                                } else {
                                    onShowAllAlbums(currentArtist.name)
                                }
                            },
                            onNavigateToAlbum = onNavigateToAlbum,
                            onNavigateToArtist = onNavigateToArtist,
                        )
            }
        }

        // Back arrow over everything, theme-aware tint like the rest of the page.
        //
        // The photo is deliberately full-bleed and runs under the status bar —
        // that is the look. This control is not: it used to sit 4dp from the top
        // of the *window*, which under edge-to-edge put the circle on top of the
        // system clock and battery readout. It now clears the status bar and then
        // takes its own margin, so it is the first thing below the clock rather
        // than sitting in it.
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(start = 8.dp, top = 6.dp)
                .background(palette.background.copy(alpha = 0.35f), CircleShape),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = palette.onBackground
            )
        }
    }
}

/** One discography shelf shown full-screen as a grid — the original ArtistShelfGridPage. */
private data class DiscographyShelf(
    val title: String,
    val albums: List<RemoteAlbumSummary>,
)

@Composable
private fun ArtistDetailContent(
    artist: RemoteArtist,
    songs: List<RemoteTrack>,
    songsExhausted: Boolean,
    onLoadMoreSongs: () -> Unit,
    palette: ArtworkPalette,
    listState: LazyListState,
    currentSong: SongEntity?,
    isPlaying: Boolean,
    bottomInset: androidx.compose.ui.unit.Dp,
    isSaved: Boolean,
    onToggleSaved: () -> Unit,
    onSongClick: (List<RemoteTrack>, Int) -> Unit,
    onSongLongPress: (RemoteTrack) -> Unit,
    onShuffle: (List<RemoteTrack>) -> Unit,
    onShowAllSongs: () -> Unit,
    onShelfShowAll: (DiscographyShelf) -> Unit,
    onNavigateToAlbum: (String) -> Unit = {},
    onNavigateToArtist: (String) -> Unit = {},
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val artHeight = (maxWidth / ARTIST_PHOTO_RATIO)
            .coerceAtMost(maxHeight * 0.6f)
            
        ArtistPageBackground(
            artist = artist,
            palette = palette,
            artHeight = artHeight,
            listState = listState,
            modifier = Modifier.matchParentSize(),
        )

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = bottomInset + 48.dp),
        ) {
            item(key = "header") {
                ArtistHeader(artist = artist, palette = palette, artHeight = artHeight)
            }

            // Subscriber count only, where YouTube provides a real value.
            // Monthly listeners was removed by product decision: the number is
            // platform-wide bookkeeping rather than something the listener can
            // act on, and it cluttered the header for it. Absent data hides the
            // pill; it is never invented.
            if (!artist.subscribers.isNullOrBlank()) {
                item(key = "artist-stats") {
                    ArtistStatsRow(
                        subscribers = artist.subscribers,
                        palette = palette,
                    )
                }
            }

            if (songs.isNotEmpty()) {
                item(key = "actions") {
                    ActionRow(
                        palette = palette,
                        isSaved = isSaved,
                        onToggleSaved = onToggleSaved,
                        onPlay = { onSongClick(songs, 0) },
                        onShuffle = { onShuffle(songs) },
                        // Halved when an About section follows directly — the
                        // About section's own top inset makes up the rest.
                        bottomSpace = if (artist.description.isNullOrBlank()) 22.dp else 11.dp,
                    )
                }
            }

            if (!artist.description.isNullOrBlank()) {
                item(key = "about") {
                    AboutSection(
                        title = "About the artist",
                        text = artist.description,
                        palette = palette,
                    )
                }
            }

            // Top songs, paged sideways four at a time like the original grid.
            if (songs.isNotEmpty()) {
                item(key = "top-songs") {
                    val top = remember(songs) { songs.take(MAX_ARTIST_SONGS) }
                    Column(Modifier.padding(top = SECTION_GAP)) {
                    SectionHeading(
                        title = "Top songs",
                        palette = palette,
                        onShowAll = if (songs.size > MAX_ARTIST_SONGS || !songsExhausted) {
                            { onShowAllSongs() }
                        } else null,
                    )
                    BoxWithConstraints {
                        val columnWidth = trackColumnWidth(maxWidth)
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = PAGE_GUTTER),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(top.chunked(SONGS_PER_COLUMN)) { column ->
                                Column(Modifier.width(columnWidth)) {
                                    column.forEach { track ->
                                        CompactSongRow(
                                            track = track,
                                            palette = palette,
                                            isCurrent = currentSong?.providerTrackId == track.videoId,
                                            isPlaying = isPlaying &&
                                                currentSong?.providerTrackId == track.videoId,
                                            onClick = { onSongClick(top, top.indexOf(track)) },
                                            onLongPress = { onSongLongPress(track) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                    }
                }
            }

            // Discography shelves with Show all.
            val shelves = artist.discographyShelves()
            items(shelves.size, key = { "shelf_$it" }) { index ->
                val shelf = shelves[index]
                Column(Modifier.padding(top = SECTION_GAP)) {
                    SectionHeading(
                        title = shelf.title,
                        palette = palette,
                        onShowAll = if (shelf.albums.size > SHELF_ROW_MAX_ITEMS) {
                            { onShelfShowAll(shelf) }
                        } else null,
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = PAGE_GUTTER),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        items(shelf.albums.take(SHELF_ROW_MAX_ITEMS)) { album ->
                            ReleaseCard(
                                title = album.title,
                                subtitle = listOfNotNull(album.year, album.type)
                                    .joinToString(" • ")
                                    .ifBlank { null },
                                artworkUrl = album.artworkUrl,
                                palette = palette,
                                onClickNavigate = { onNavigateToAlbum(album.id) },
                            )
                        }
                    }
                }
            }

            // Related artists — only real ones from the catalogue.
            if (artist.relatedArtists.isNotEmpty()) {
                item(key = "related") {
                    Column(Modifier.padding(top = SECTION_GAP)) {
                        SectionHeading(title = "Fans also like", palette = palette)
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = PAGE_GUTTER),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            items(artist.relatedArtists.filter { !it.id.isNullOrBlank() }) { rel ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .width(96.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { rel.id?.let(onNavigateToArtist) }
                                        .padding(vertical = 4.dp),
                                ) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data(rel.artworkUrl)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = rel.name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(88.dp)
                                            .clip(CircleShape)
                                            .background(palette.elevated),
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        text = rel.name,
                                        style = GratiaTheme.typography.caption,
                                        color = palette.onBackground,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center,
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

// ── The three layers ─────────────────────────────────────────────────────────

/**
 * Everything on the artist page that is colour rather than words: the page wash,
 * and the artwork sitting on top of it.
 *
 * It is a layer of its own so that the dissolve has the join to itself: the
 * picture's scroll is carried here rather than scrolled with the list, because
 * the list owns the gesture and reserves the room. This box is offset to follow
 * whatever the list did with item zero, so a scroll moves the photograph
 * without recomposing anything.
 */
@Composable
private fun ArtistPageBackground(
    artist: RemoteArtist,
    palette: ArtworkPalette,
    artHeight: androidx.compose.ui.unit.Dp,
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    Box(modifier.clipToBounds()) {
        // The wash: one shared implementation for artist, album and playlist,
        // so the three pages cannot drift apart (spec 70).
        DetailPageBackground(palette = palette, modifier = Modifier.matchParentSize())

        // The artwork + dissolve, drawn behind the list rather than in it, and
        // offset to follow whatever the list did with item zero. This is the
        // same hero the album and playlist pages use (spec 68/69).
        DetailHeroArtwork(
            artworkUrl = artist.artworkUrl,
            contentDescription = artist.name,
            palette = palette,
            modifier = Modifier
                .fillMaxWidth()
                .height(artHeight)
                .offset { IntOffset(0, listState.headerTop(artHeight.toPx()).roundToInt()) },
        )
    }
}

/** Where the top of the artwork currently is — the original LazyListState.headerTop. */
private fun LazyListState.headerTop(artHeightPx: Float): Float =
    if (firstVisibleItemIndex == 0) {
        -firstVisibleItemScrollOffset.toFloat()
    } else {
        -artHeightPx * 2f
    }

// ── Header ───────────────────────────────────────────────────────────────────

/**
 * An artist: their name across the foot of the photo [ArtistPageBackground] is
 * drawing behind this. See the original ArtistHeader.
 */
@Composable
private fun ArtistHeader(artist: RemoteArtist, palette: ArtworkPalette, artHeight: androidx.compose.ui.unit.Dp) {
    Box(Modifier.fillMaxWidth()) {
        Spacer(Modifier.fillMaxWidth().height(artHeight + HEADER_DROP))
        Text(
            text = artist.name,
            fontFamily = SpaceGrotesk,
            fontWeight = FontWeight.Bold,
            fontSize = 40.sp,
            lineHeight = 46.sp,
            letterSpacing = (-1).sp,
            color = palette.onBackground,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = HEADER_GUTTER, end = HEADER_GUTTER, top = 14.dp, bottom = 7.dp),
        )
    }
}

/**
 * "1.2M subscribers · YouTube" — the catalogue's own count, rendered through
 * the typed metric so it can only ever be described as what it is (artist
 * spec 2/3). Monthly listeners is a different metric from a different
 * surface and is never shown in its place. An unparsable count omits the
 * chip rather than displaying a guess (artist spec 37).
 */
@Composable
private fun ArtistStatsRow(
    subscribers: String?,
    palette: ArtworkPalette,
) {
    val metric = subscribers?.let { raw ->
        com.gratia.music.data.model.ArtistMetric.parseCompact(raw.substringBefore(' '))?.let { value ->
            com.gratia.music.data.model.ArtistMetric(
                value = value,
                type = com.gratia.music.data.model.ArtistMetricType.SUBSCRIBERS,
                source = "YouTube",
            )
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = PAGE_GUTTER, end = PAGE_GUTTER, bottom = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        metric?.let {
            StatChip(
                icon = Icons.Filled.Person,
                text = it.label,
                palette = palette,
            )
        }
    }
}

@Composable
private fun StatChip(icon: ImageVector, text: String, palette: ArtworkPalette) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(CircleShape)
            .background(palette.elevated.copy(alpha = 0.7f))
            .border(0.5.dp, palette.divider, CircleShape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = palette.onBackgroundVariant,
            modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = text,
            style = GratiaTheme.typography.caption,
            color = palette.onBackgroundVariant,
        )
    }
}

/**
 * Save • Shuffle • Play — the action row with Gratia's local save state on the
 * left. The + and ✓ swap in place, with a scale bump so the state change is
 * felt and not merely assumed; the row's meaning stays the listener's own
 * local follow, never a claim about the remote account.
 */
@Composable
private fun ActionRow(
    palette: ArtworkPalette,
    isSaved: Boolean,
    onToggleSaved: () -> Unit,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    bottomSpace: androidx.compose.ui.unit.Dp = 22.dp,
) {
    val context = LocalContext.current
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = 0.5f,
            stiffness = 400f,
        ),
        label = "saveScale",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = HEADER_GUTTER),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(50.dp)
                .scale(scale)
                .clip(CircleShape)
                .background(palette.elevated.copy(alpha = 0.75f))
                .border(0.5.dp, palette.divider, CircleShape)
                .clickable {
                    haptics.performHapticFeedback(
                        androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress
                    )
                    onToggleSaved()
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (isSaved) Icons.Filled.Check else Icons.Filled.Add,
                contentDescription = if (isSaved) "Saved" else "Save artist",
                tint = palette.onBackground,
                modifier = Modifier.size(50.dp * 0.44f),
            )
        }
        CircleIconButton(
            icon = Icons.Filled.Shuffle,
            contentDescription = "Shuffle",
            palette = palette,
            onClick = onShuffle,
        )
        PlayPill(palette = palette, onClick = onPlay)
    }
    Spacer(Modifier.height(bottomSpace))
}

@Composable
private fun PlayPill(palette: ArtworkPalette, onClick: () -> Unit, horizontalPadding: androidx.compose.ui.unit.Dp = 32.dp) {
    Row(
        modifier = Modifier
            .height(50.dp)
            .clip(CircleShape)
            .background(palette.elevated.copy(alpha = 0.75f))
            .border(0.5.dp, palette.divider, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = horizontalPadding),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.PlayArrow,
            contentDescription = null,
            tint = palette.onBackground,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "Play",
            style = GratiaTheme.typography.section,
            color = palette.onBackground,
        )
    }
}

@Composable
private fun CircleIconButton(
    icon: ImageVector,
    contentDescription: String,
    palette: ArtworkPalette,
    onClick: () -> Unit,
    size: androidx.compose.ui.unit.Dp = 50.dp,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(palette.elevated.copy(alpha = 0.75f))
            .border(0.5.dp, palette.divider, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = palette.onBackground,
            modifier = Modifier.size(size * 0.44f),
        )
    }
}

// ── Editorial / about ────────────────────────────────────────────────────────

/**
 * The editorial note, collapsed to a few lines with a tap to read the rest —
 * the original AboutSection, with its clipped-measurement "More" gate.
 */
@Composable
private fun AboutSection(title: String, text: String, palette: ArtworkPalette) {
    var expanded by remember(text) { mutableStateOf(false) }
    var clipped by remember(text) { mutableStateOf(false) }
    Column {
        Text(
            text = title,
            style = GratiaTheme.typography.section,
            color = palette.onBackground,
            modifier = Modifier.padding(
                start = ABOUT_GUTTER, end = ABOUT_GUTTER, top = 2.dp, bottom = 6.dp,
            ),
        )
        Text(
            text = text,
            style = GratiaTheme.typography.body,
            fontWeight = FontWeight.Medium,
            color = palette.onBackgroundVariant,
            maxLines = if (expanded) Int.MAX_VALUE else 3,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { result -> if (!expanded) clipped = result.hasVisualOverflow },
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize()
                .padding(horizontal = ABOUT_GUTTER)
                .let { m -> if (clipped || expanded) m.clickable { expanded = !expanded } else m },
        )
        if (clipped || expanded) {
            Text(
                text = if (expanded) "Less" else "More",
                style = GratiaTheme.typography.caption,
                color = palette.accent,
                modifier = Modifier
                    .padding(horizontal = ABOUT_GUTTER, vertical = 4.dp)
                    .clickable { expanded = !expanded },
            )
        }
    }
}

// ── Shelves / cards / rows ───────────────────────────────────────────────────

@Composable
private fun SectionHeading(
    title: String,
    palette: ArtworkPalette,
    onShowAll: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = PAGE_GUTTER, end = PAGE_GUTTER, top = 10.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = GratiaTheme.typography.title,
            color = palette.onBackground,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (onShowAll != null) {
            Text(
                text = "Show all",
                style = GratiaTheme.typography.section,
                color = palette.accent,
                modifier = Modifier
                    .clickable(onClick = onShowAll)
                    .padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
            )
        }
    }
}

/** the original CompactSongRow, for the artist top-songs grid. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CompactSongRow(
    track: RemoteTrack,
    palette: ArtworkPalette,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(artworkAtSize(track.artworkUrl, ROW_ARTWORK_PX))
                .crossfade(true)
                .build(),
            contentDescription = null,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(palette.elevated),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = track.title,
                    style = GratiaTheme.typography.section,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (isCurrent && isPlaying) palette.accent else palette.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (track.isExplicit) {
                    Spacer(Modifier.width(4.dp))
                    ExplicitBadge(palette = palette)
                }
            }
            Text(
                text = track.artistDisplay,
                style = GratiaTheme.typography.caption,
                color = palette.onBackgroundVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .clickable(onClick = onLongPress),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.MoreVert,
                contentDescription = "More",
                tint = palette.onBackgroundVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun ExplicitBadge(palette: ArtworkPalette) {
    Text(
        text = "E",
        style = GratiaTheme.typography.caption.copy(fontSize = 9.sp),
        color = palette.onBackgroundVariant,
        modifier = Modifier
            .border(1.dp, palette.onBackgroundVariant, RoundedCornerShape(2.dp))
            .padding(horizontal = 2.dp),
    )
}

/** the original SectionCard — a release card on a shelf or in the grid. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReleaseCard(
    title: String,
    subtitle: String?,
    artworkUrl: String?,
    palette: ArtworkPalette,
    modifier: Modifier = Modifier.width(150.dp),
    onClickNavigate: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .combinedClickable(
                onClick = { onClickNavigate?.invoke() },
                onLongClick = { onLongPress?.invoke() },
            ),
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(artworkAtSize(artworkUrl, LARGE_ARTWORK_PX))
                .crossfade(true)
                .build(),
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(10.dp))
                .background(palette.elevated),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = title,
            style = GratiaTheme.typography.section,
            color = palette.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = GratiaTheme.typography.caption,
                color = palette.onBackgroundVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** the original ArtistShelfGridPage — the Show all destination for a shelf. */
@Composable
private fun ShelfGridPage(
    shelf: DiscographyShelf,
    palette: ArtworkPalette,
    bottomInset: androidx.compose.ui.unit.Dp,
    hasMore: Boolean,
    isLoadingMore: Boolean,
    onLoadMore: () -> Unit,
    onBack: () -> Unit,
    onAlbumClick: (String) -> Unit,
) {
    val gridState = rememberLazyGridState()
    // Follows the listener to the foot of the grid: when they near the last
    // row and the catalogue still has pages left, the next one is requested.
    val nearEnd by remember {
        derivedStateOf {
            val info = gridState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= info.totalItemsCount - 4
        }
    }
    LaunchedEffect(nearEnd, hasMore, isLoadingMore) {
        if (nearEnd && hasMore && !isLoadingMore) onLoadMore()
    }
    Column(Modifier.fillMaxSize().background(palette.background)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = palette.onBackground,
                )
            }
            Text(
                text = shelf.title,
                style = GratiaTheme.typography.title,
                color = palette.onBackground,
            )
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            state = gridState,
            contentPadding = PaddingValues(
                start = PAGE_GUTTER, end = PAGE_GUTTER,
                bottom = bottomInset + 16.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(shelf.albums, key = { it.id }) { album ->
                ReleaseCard(
                    title = album.title,
                    subtitle = listOfNotNull(album.year, album.type)
                        .joinToString(" • ")
                        .ifBlank { null },
                    artworkUrl = album.artworkUrl,
                    palette = palette,

                    modifier = Modifier.fillMaxWidth(),
                    onClickNavigate = { onAlbumClick(album.id) },
                )
            }
            if (isLoadingMore || hasMore) {
                item(key = "shelf_grid_loading") {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        if (isLoadingMore) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp,
                                color = palette.onBackgroundVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Grid of one column of top songs — the width the original trackColumnWidth computes. */
private fun trackColumnWidth(available: androidx.compose.ui.unit.Dp): androidx.compose.ui.unit.Dp =
    ((available - PAGE_GUTTER * 2) / 2.4f)

/** The discography shelves an artist page offers, from what the catalogue returned. */
private fun RemoteArtist.discographyShelves(): List<DiscographyShelf> = buildList {
    if (albums.isNotEmpty()) add(DiscographyShelf("Albums", albums))
    if (singles.isNotEmpty()) add(DiscographyShelf("Singles & EPs", singles))
}

/** The page gutter Gratia's shelves already use. */
private val PAGE_GUTTER = 24.dp

/** the original MAX_ARTIST_SONGS / SONGS_PER_COLUMN. */
private const val MAX_ARTIST_SONGS = 20
private const val SONGS_PER_COLUMN = 4

@Composable
fun ArtistSkeletonScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GratiaTheme.colors.background)
    ) {
        // Fake Header (Image + Title)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .shimmer()
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Fake "Songs" section title
        Box(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .width(120.dp)
                .height(24.dp)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                .shimmer()
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Fake Song Rows
        repeat(5) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Fake artwork
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                        .shimmer()
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    // Fake title
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .height(16.dp)
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                            .shimmer()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    // Fake artist
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.4f)
                            .height(14.dp)
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                            .shimmer()
                    )
                }
            }
        }
    }
}
