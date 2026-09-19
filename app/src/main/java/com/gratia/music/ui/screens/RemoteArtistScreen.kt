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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
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
import com.gratia.music.player.PlayerViewModel
import com.gratia.music.provider.RemoteAlbumSummary
import com.gratia.music.provider.RemoteArtist
import com.gratia.music.provider.RemoteTrack
import com.gratia.music.provider.RemoteTrackMapper
import com.gratia.music.ui.LocalBottomPadding
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
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import com.gratia.music.ui.components.optimizedHazeEffect

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
 * HEADER_DROP: the title sits on the blurred colour, not on a busy picture.
 */
private val HEADER_DROP = 44.dp

/** How tall the merge glass is — the original MERGE_BAND. */
private val MERGE_BAND = 320.dp

/** How many of a shelf's cards a detail row shows before it offers Show all. */
private const val SHELF_ROW_MAX_ITEMS = 5

/**
 * The remote artist destination, ported from the original artist detail page.
 *
 * The page paints itself in the artwork's own colours — a tint behind
 * everything, the artwork itself across the top of it, and an accent taken off
 * the picture for the credit line and the Play/Shuffle pair. See
 * [rememberArtworkPalette] for how those are derived and kept legible.
 *
 * It is built in three layers rather than the obvious one, and the order is the
 * whole trick:
 *
 *  1. [ArtistPageBackground] — the wash and the artwork, and nothing you can read.
 *  2. [MergeBand] — one pane of softening laid across the join.
 *  3. The list — titles, buttons and rows, drawn over the band and so sharp.
 *
 * Blurring only the artwork leaves the artwork and the page as two surfaces
 * that have been made to *resemble* each other, and the eye finds that edge
 * every time. Colour from the picture is carried down past where the picture
 * ends and the page's colour is carried up into it, and the line that used to
 * be there has nothing left to be a line between.
 */
@Composable
fun RemoteArtistScreen(
    channelId: String,
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit,
    onNavigateToAlbum: (String) -> Unit = {},
    onNavigateToArtist: (String) -> Unit = {}
) {
    val context = LocalContext.current
    var artist by remember { mutableStateOf<RemoteArtist?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var isOffline by remember { mutableStateOf(false) }
    var refreshNonce by remember { mutableStateOf(0) }
    var selectedSongForMenu by remember { mutableStateOf<SongEntity?>(null) }

    val currentSong by playerViewModel.currentSong.collectAsState()
    val isPlaying by playerViewModel.isPlaying.collectAsState()
    val favoriteSongIds by playerViewModel.favoriteSongIds.collectAsState()
    val scope = rememberCoroutineScope()

    val listState = rememberLazyListState()
    val isDark = GratiaTheme.colors.isDark
    val palette = rememberArtworkPalette(artist?.artworkUrl, dark = isDark)

    // the original shelf-grid: tapping a shelf's "Show all" turns the page into
    // a proper grid of that shelf's contents.
    var activeShelf by remember(channelId) { mutableStateOf<DiscographyShelf?>(null) }

    // Full artist song list, paged from the playlist the page links.
    var allSongs by remember(channelId) { mutableStateOf<List<RemoteTrack>>(emptyList()) }
    var songsContinuation by remember(channelId) { mutableStateOf<String?>(null) }
    var songsLoadingMore by remember { mutableStateOf(false) }

    LaunchedEffect(channelId, refreshNonce) {
        isLoading = true
        error = null
        isOffline = false
        try {
            if (!NetworkMonitor.isCurrentlyOnline()) {
                isOffline = true
                isLoading = false
                return@LaunchedEffect
            }
            val res = withContext(Dispatchers.IO) {
                GratiaApp.instance.providerManager.youtubeMusicProvider.getArtist(channelId)
            }
            if (res != null) {
                artist = res
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
                error = "Couldn't load artist details."
            }
        } catch (e: Exception) {
            error = "Failed to load artist: ${e.message}"
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
            onToggleLike = { playerViewModel.toggleFavorite(song) }
        )
    }

    val bottomInset = LocalBottomPadding.current

    Box(modifier = Modifier.fillMaxSize().background(palette.background)) {
        when {
            isLoading && artist == null -> Box(
                Modifier.fillMaxSize().background(GratiaTheme.colors.background),
                contentAlignment = Alignment.Center,
            ) {
                GratiaLoadingState(message = "Loading artist…")
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
                val currentArtist = artist!!
                val pageSongs = remember(currentArtist, allSongs) {
                    (allSongs.ifEmpty { currentArtist.topSongs })
                        .distinctBy { it.videoId }
                }

                AnimatedContent(
                    targetState = activeShelf,
                    transitionSpec = {
                        fadeIn(tween(220)) togetherWith fadeOut(tween(180))
                    },
                    label = "artist_shelf_transition",
                    modifier = Modifier.fillMaxSize(),
                ) { targetShelf ->
                    if (targetShelf == null) {
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
                            onSongClick = { queue, index ->
                                val entities = queue.map { RemoteTrackMapper.toSongEntity(it) }
                                playerViewModel.playSong(entities[index], entities)
                            },
                            onSongLongPress = { track ->
                                selectedSongForMenu = RemoteTrackMapper.toSongEntity(track)
                            },
                            onShuffle = { queue ->
                                val entities = queue.map { RemoteTrackMapper.toSongEntity(it) }
                                entities.firstOrNull()?.let {
                                    playerViewModel.playSong(
                                        it,
                                        entities.shuffled(),
                                    )
                                }
                            },
                            onShowAllSongs = { songsLoadingMore = true },
                            onShelfShowAll = { activeShelf = it },
                            onNavigateToAlbum = onNavigateToAlbum,
                            onNavigateToArtist = onNavigateToArtist,
                        )
                    } else {
                        ShelfGridPage(
                            shelf = targetShelf,
                            palette = palette,
                            bottomInset = bottomInset,
                            onBack = { activeShelf = null },
                            onAlbumClick = onNavigateToAlbum,
                        )
                    }
                }
            }
        }

        // Back arrow over everything, theme-aware tint like the rest of the page.
        IconButton(
            onClick = { if (activeShelf != null) activeShelf = null else onBack() },
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 4.dp, top = 4.dp)
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
            
        val hazeState = remember { HazeState() }

        ArtistPageBackground(
            artist = artist,
            palette = palette,
            artHeight = artHeight,
            listState = listState,
            hazeState = hazeState,
            modifier = Modifier.matchParentSize(),
        )

        MergeBand(
            palette = palette,
            artHeight = artHeight,
            listState = listState,
            hazeState = hazeState,
        )

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = bottomInset + 48.dp),
        ) {
            item(key = "header") {
                ArtistHeader(artist = artist, palette = palette, artHeight = artHeight)
            }

            // Stat pills only where YouTube provides real values.
            if (!artist.subscribers.isNullOrBlank()) {
                item(key = "artist-stats") {
                    ArtistStatsRow(
                        subscribers = artist.subscribers,
                        monthlyListeners = null,
                        palette = palette,
                    )
                }
            }

            if (songs.isNotEmpty()) {
                item(key = "actions") {
                    ActionRow(
                        palette = palette,
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

            // Discography shelves with Show all.
            val shelves = artist.discographyShelves()
            items(shelves.size, key = { "shelf_$it" }) { index ->
                val shelf = shelves[index]
                Column(Modifier.padding(top = 22.dp)) {
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
                    Column(Modifier.padding(top = 22.dp)) {
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
 * This is the whole of what [MergeBand] softens, and the reason it is a layer of
 * its own: split out, the merge has the join to itself. It carries the
 * artwork's scroll instead of being scrolled: the list owns the gesture and
 * reserves the room, and the picture is offset to follow whatever the list did
 * with item zero. Read in a layer block, so a scroll moves it without
 * recomposing anything.
 */
@Composable
private fun ArtistPageBackground(
    artist: RemoteArtist,
    palette: ArtworkPalette,
    artHeight: androidx.compose.ui.unit.Dp,
    listState: LazyListState,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val canBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    Box(modifier.clipToBounds().hazeSource(hazeState)) {
        // The wash: the eased colour the page is made of below the artwork.
        Canvas(modifier = Modifier.matchParentSize()) {
            val washFraction = 0.62f
            drawRect(
                Brush.verticalGradient(
                    0f to palette.wash,
                    washFraction to palette.wash,
                    lerp(washFraction, 1f, 0.35f) to colorLerp(palette.wash, palette.background, 0.12f),
                    lerp(washFraction, 1f, 0.70f) to colorLerp(palette.wash, palette.background, 0.55f),
                    1f to palette.background,
                ),
            )
            blob(palette.accent.copy(alpha = 0.13f), Offset(0.12f, washFraction + 0.08f), 0.80f)
            blob(palette.elevated.copy(alpha = 0.30f), Offset(0.96f, washFraction + 0.30f), 0.95f)
        }

        // The artwork, drawn behind the list rather than in it.
        Box(
            Modifier
                .fillMaxWidth()
                .height(artHeight)
                .offset { IntOffset(0, listState.headerTop(artHeight.toPx()).roundToInt()) },
        ) {
            if (!artist.artworkUrl.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(artworkAtSize(artist.artworkUrl, LARGE_ARTWORK_PX))
                        .crossfade(true)
                        .build(),
                    contentDescription = artist.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .matchParentSize()
                        .then(
                            if (canBlur) {
                                // Softened where it meets the page: the merge
                                // band below carries the colour across the join.
                                Modifier.blur(24.dp)
                            } else Modifier
                        ),
                )
            } else {
                Box(Modifier.matchParentSize().background(palette.elevated))
            }

            // Shade under the back arrow, drawn in the page's own tint rather
            // than black, so the themed arrow keeps contrast in light mode.
            Box(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.28f)
                    .background(
                        Brush.verticalGradient(
                            listOf(palette.background.copy(alpha = 0.55f), Color.Transparent),
                        ),
                    ),
            )

            // Settles the foot of the picture onto the colour the page is made
            // of, so the two sides of the join are already close before the
            // merge band goes over them — a blur averages what it is given and
            // cannot invent agreement that isn't there.
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            0.55f to Color.Transparent,
                            1.00f to palette.wash.copy(alpha = 0.88f),
                        ),
                    ),
            )
        }
    }
}

/** One mesh lobe: a colour at its centre, gone by [radiusFraction] of the width. */
private fun DrawScope.blob(color: Color, at: Offset, radiusFraction: Float) {
    val center = Offset(at.x * size.width, at.y * size.height)
    val radius = size.width * radiusFraction
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color, color.copy(alpha = 0f)),
            center = center,
            radius = radius,
        ),
        radius = radius,
        center = center,
    )
}

/**
 * The band laid across the artwork-to-content join.
 *
 * Gratia implements this with Haze blurring the PageBackground layer; Gratia
 * has no Haze dependency, so this is the original own reduceDynamicBlur fallback
 * carried out explicitly: the artwork's foot is already settled onto
 * [ArtworkPalette.wash] inside [ArtistPageBackground], and the band adds the
 * eased colour carry-down from the seam. The list draws over it and stays
 * sharp, exactly as in the original layering.
 */
@Composable
private fun MergeBand(
    palette: ArtworkPalette,
    artHeight: androidx.compose.ui.unit.Dp,
    listState: LazyListState,
    hazeState: HazeState,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(MERGE_BAND)
            .offset {
                IntOffset(
                    x = 0,
                    y = (
                        listState.headerTop(artHeight.toPx()) +
                            artHeight.toPx() - MERGE_BAND.toPx() / 2f
                        ).roundToInt(),
                )
            }
            .optimizedHazeEffect(hazeState) {
                // Without this the band draws nothing at all.
                fallbackTint = dev.chrisbanes.haze.HazeTint(palette.wash.copy(alpha = 0.45f))
            },
    )
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

/** "1.2M subscribers" / "3.4M monthly listeners" — real values only. */
@Composable
private fun ArtistStatsRow(
    subscribers: String?,
    monthlyListeners: String?,
    palette: ArtworkPalette,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = PAGE_GUTTER, end = PAGE_GUTTER, bottom = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        subscribers?.let {
            StatChip(
                icon = Icons.Filled.Person,
                text = it.substringBefore(' ') + " subscribers",
                palette = palette,
            )
        }
        monthlyListeners?.let {
            StatChip(
                icon = Icons.Filled.GraphicEq,
                text = it.substringBefore(' ') + " monthly listeners",
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

/** Shuffle • Play — the Apple Music action row, in the original glass treatment. */
@Composable
private fun ActionRow(
    palette: ArtworkPalette,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    bottomSpace: androidx.compose.ui.unit.Dp = 22.dp,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = HEADER_GUTTER),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
    onBack: () -> Unit,
    onAlbumClick: (String) -> Unit,
) {
    val gridState = rememberLazyGridState()
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

