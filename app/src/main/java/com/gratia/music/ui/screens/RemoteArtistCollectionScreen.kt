package com.gratia.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.gratia.music.GratiaApp
import com.gratia.music.data.model.SongEntity
import com.gratia.music.player.PlaybackOrigin
import com.gratia.music.player.PlayerViewModel
import com.gratia.music.provider.RemoteAlbumSummary
import com.gratia.music.provider.RemoteTrackMapper
import com.gratia.music.ui.LocalBottomPadding
import com.gratia.music.ui.components.ROW_ARTWORK_PX
import com.gratia.music.ui.components.SongRow
import com.gratia.music.ui.components.rememberArtworkRequest
import com.gratia.music.ui.theme.GratiaTheme
import com.gratia.music.ui.theme.Inter
import com.gratia.music.ui.theme.SpaceGrotesk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The full list behind an artist page's "Show all" (spec G/H/K).
 *
 * This is the real destination the previous build lacked: "Show all" used to be
 * an in-page grid held in `remember` state, so a navigation away and back lost
 * it, and Android's Back had nothing to return to but the artist page itself —
 * which is why Albums → Album → Back landed on the artist instead of the Albums
 * list (spec H/10).
 *
 * Because the list is now its own route on the nav stack, the platform Back
 * button, the gesture, and the top-bar arrow all do the same ordinary thing:
 * `popBackStack()` to whatever screen actually opened this one — Albums, Singles
 * & EPs, or an artist page.
 */
enum class ArtistCollectionKind {
    TOP_SONGS,
    ALBUMS,
    SINGLES;

    val label: String
        get() = when (this) {
            TOP_SONGS -> "Top Songs"
            ALBUMS -> "Albums"
            SINGLES -> "Singles & EPs"
        }

    companion object {
        fun fromKey(key: String?): ArtistCollectionKind = when (key) {
            "albums" -> ALBUMS
            "singles" -> SINGLES
            else -> TOP_SONGS
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteArtistCollectionScreen(
    channelId: String,
    kind: ArtistCollectionKind,
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit,
    onNavigateToAlbum: (String) -> Unit = {},
    /** The artist's songs-playlist id, when the caller already knows it. */
    songsPlaylistId: String? = null,
    /** The title the caller tapped, shown before the network answers. */
    knownArtistName: String? = null,
) {
    val provider = GratiaApp.instance.providerManager.youtubeMusicProvider

    var songs by remember(channelId, kind) { mutableStateOf<List<com.gratia.music.provider.RemoteTrack>>(emptyList()) }
    var albums by remember(channelId, kind) { mutableStateOf<List<RemoteAlbumSummary>>(emptyList()) }
    var continuation by remember(channelId, kind) { mutableStateOf<String?>(null) }
    var isLoading by remember(channelId, kind) { mutableStateOf(true) }
    var isLoadingMore by remember(channelId, kind) { mutableStateOf(false) }
    var error by remember(channelId, kind) { mutableStateOf<String?>(null) }
    var resolvedPlaylistId by remember(channelId) { mutableStateOf(songsPlaylistId) }
    var artistName by remember(channelId) { mutableStateOf(knownArtistName) }

    val currentSong by playerViewModel.currentSong.collectAsState()
    val isPlaying by playerViewModel.isPlaying.collectAsState()

    val songEntities: List<SongEntity> = remember(songs) {
        songs.map { RemoteTrackMapper.toSongEntity(it) }
    }

    // First page. Only the very first load shows a spinner; everything after is
    // appended in place (spec P).
    LaunchedEffect(channelId, kind) {
        isLoading = true
        error = null
        try {
            when (kind) {
                ArtistCollectionKind.TOP_SONGS -> {
                    // The artist's own page links the playlist holding the
                    // complete song list; fetch it when the caller did not pass
                    // it. Never derived from a name search (spec G).
                    if (resolvedPlaylistId == null) {
                        val artist = withContext(Dispatchers.IO) { provider.getArtist(channelId) }
                        resolvedPlaylistId = artist?.songsPlaylistId
                        if (artistName == null) artistName = artist?.name
                        if (resolvedPlaylistId == null && artist != null) {
                            songs = artist.topSongs
                        }
                    }
                    resolvedPlaylistId?.let { id ->
                        val page = withContext(Dispatchers.IO) { provider.getArtistSongs(id) }
                        if (page.items.isNotEmpty()) {
                            songs = page.items
                            continuation = page.continuation
                        }
                    }
                }
                ArtistCollectionKind.ALBUMS, ArtistCollectionKind.SINGLES -> {
                    val type = if (kind == ArtistCollectionKind.SINGLES) "single" else "album"
                    val page = withContext(Dispatchers.IO) { provider.getArtistAlbums(channelId, type) }
                    albums = page.items
                    continuation = page.continuation
                }
            }
        } catch (e: Exception) {
            error = "Couldn't load this list. Check your connection and try again."
        } finally {
            isLoading = false
        }
    }

    // "Load more" pages the same source, appended rather than replaced.
    LaunchedEffect(isLoadingMore) {
        if (!isLoadingMore) return@LaunchedEffect
        val token = continuation
        try {
            when (kind) {
                ArtistCollectionKind.TOP_SONGS -> {
                    val id = resolvedPlaylistId
                    if (token != null && id != null) {
                        val page = withContext(Dispatchers.IO) { provider.getArtistSongs(id, token) }
                        songs = (songs + page.items).distinctBy { it.videoId }
                        continuation = page.continuation
                    } else {
                        continuation = null
                    }
                }
                ArtistCollectionKind.ALBUMS, ArtistCollectionKind.SINGLES -> {
                    val type = if (kind == ArtistCollectionKind.SINGLES) "single" else "album"
                    if (token != null) {
                        val page = withContext(Dispatchers.IO) { provider.getArtistAlbums(channelId, type, token) }
                        albums = (albums + page.items).distinctBy { it.id }
                        continuation = page.continuation
                    } else {
                        continuation = null
                    }
                }
            }
        } catch (_: Exception) {
            continuation = null
        } finally {
            isLoadingMore = false
        }
    }

    val bottomInset = LocalBottomPadding.current
    val headerTitle = remember(kind, artistName, knownArtistName) {
        val artistPart = artistName ?: knownArtistName
        if (artistPart.isNullOrBlank()) kind.label else "${kind.label} · $artistPart"
    }

    Scaffold(
        containerColor = GratiaTheme.colors.background,
        topBar = {
            // Exactly one back affordance, in a top bar that honours the
            // status-bar/cutout insets (spec I). Nothing is layered over it.
            TopAppBar(
                title = {
                    Text(
                        text = headerTitle,
                        fontFamily = SpaceGrotesk,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = GratiaTheme.colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = GratiaTheme.colors.textPrimary,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                isLoading && songs.isEmpty() && albums.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = GratiaTheme.colors.accent)
                    }
                }
                error != null && songs.isEmpty() && albums.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = error ?: "Nothing here",
                                fontFamily = Inter,
                                fontSize = 14.sp,
                                color = GratiaTheme.colors.textSecondary,
                                modifier = Modifier.padding(horizontal = 32.dp),
                            )
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            bottom = bottomInset + GratiaTheme.spacing.heroLarge
                        ),
                    ) {
                        if (kind == ArtistCollectionKind.TOP_SONGS) {
                            itemsIndexed(songEntities, key = { _, s -> s.id }) { index, song ->
                                SongRow(
                                    song = song,
                                    index = index,
                                    isActive = currentSong?.id == song.id,
                                    isPlaying = currentSong?.id == song.id && isPlaying,
                                    onClick = {
                                        playerViewModel.playSong(song, songEntities, PlaybackOrigin.ARTIST)
                                    },
                                    onMoreClick = { },
                                    modifier = Modifier.padding(horizontal = 8.dp),
                                )
                            }
                        } else {
                            items(albums, key = { it.id }) { album ->
                                ReleaseListRow(
                                    album = album,
                                    onClick = { onNavigateToAlbum(album.id) },
                                )
                            }
                        }

                        if (continuation != null) {
                            item(key = "load_more") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 16.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (isLoadingMore) {
                                        CircularProgressIndicator(
                                            color = GratiaTheme.colors.accent,
                                            modifier = Modifier.size(22.dp),
                                        )
                                    } else {
                                        Text(
                                            text = "Load more",
                                            fontFamily = Inter,
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 14.sp,
                                            color = GratiaTheme.colors.accent,
                                            modifier = Modifier
                                                .clickable { isLoadingMore = true }
                                                .padding(8.dp),
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
}

/** One release row: artwork at a size it is drawn at, title, and its year/type. */
@Composable
private fun ReleaseListRow(
    album: RemoteAlbumSummary,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = rememberArtworkRequest(album.artworkUrl, ROW_ARTWORK_PX, crossfadeMs = 0),
            contentDescription = album.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(GratiaTheme.colors.surface),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = album.title,
                fontFamily = SpaceGrotesk,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = GratiaTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val subtitle = listOfNotNull(album.year, album.type)
                .joinToString(" • ")
            if (subtitle.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontFamily = Inter,
                    fontSize = 12.sp,
                    color = GratiaTheme.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
