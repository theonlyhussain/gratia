package com.gratia.music.ui.screens

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.gratia.music.GratiaApp
import com.gratia.music.data.SettingsDataStore
import com.gratia.music.data.model.SongEntity
import com.gratia.music.data.model.musicSource
import com.gratia.music.data.repository.SongRepository
import com.gratia.music.player.PlayerViewModel
import com.gratia.music.player.PlaybackOrigin
import com.gratia.music.provider.RemoteSearchResponse
import com.gratia.music.provider.RemoteTrackMapper
import com.gratia.music.ui.components.*
import com.gratia.music.ui.theme.GratiaTheme
import com.gratia.music.ui.theme.Inter
import com.gratia.music.ui.theme.SpaceGrotesk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * Result *type* filter — deliberately not a source switch.
 *
 * The previous screen asked the listener to choose between "Library", "YouTube
 * Music" and "All", which framed search as a choice of where to look. Search is
 * one act, across one catalogue first, so this filter only says what kind of
 * result is wanted; "All" means every result *type*, never every source.
 */
enum class SearchFilterType(val label: String) {
    ALL("All"),
    SONGS("Songs"),
    ARTISTS("Artists"),
    ALBUMS("Albums"),
    PLAYLISTS("Playlists");
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    playerViewModel: PlayerViewModel,
    onNavigateToGenre: (String) -> Unit = {},
    onNavigateToRemoteArtist: (String) -> Unit = {},
    onNavigateToRemoteAlbum: (String) -> Unit = {},
    onNavigateToRemotePlaylist: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val settingsDataStore = remember { SettingsDataStore(context) }
    val searchHistory by settingsDataStore.searchHistoryFlow.collectAsState(initial = emptySet())

    val songRepo = remember { SongRepository(GratiaApp.instance.database.songDao()) }
    var query by remember { mutableStateOf("") }
    val localResults by songRepo.search(query.ifBlank { "§§NOMATCH§§" }).collectAsState(initial = emptyList())
    val genres by songRepo.getDistinctGenres().collectAsState(initial = null)
    val currentSong by playerViewModel.currentSong.collectAsState()
    val isPlaying by playerViewModel.isPlaying.collectAsState()
    val favoriteSongIds by playerViewModel.favoriteSongIds.collectAsState()
    val scope = rememberCoroutineScope()
    var lyricsMatchIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    var selectedFilter by remember { mutableStateOf(SearchFilterType.ALL) }
    var remoteSearchResponse by remember { mutableStateOf<RemoteSearchResponse?>(null) }
    var isSearchingRemote by remember { mutableStateOf(false) }
    var remoteError by remember { mutableStateOf<String?>(null) }
    // Bumped by Retry so the search effect re-runs for the same query.
    var searchNonce by remember { mutableStateOf(0) }
    var selectedSongForMenu by remember { mutableStateOf<SongEntity?>(null) }
    var showAddToPlaylistFor by remember { mutableStateOf<SongEntity?>(null) }

    // Local lyric-text matches are a supplement, not a primary result set.
    LaunchedEffect(query) {
        if (query.length >= 2) {
            scope.launch {
                lyricsMatchIds = songRepo.searchByLyrics(query).map { it.id }.toSet()
            }
        } else {
            lyricsMatchIds = emptySet()
        }
    }

    // ── Remote-first search ────────────────────────────────────────────────
    // Every search hits the catalogue. Local/on-device matches are shown as a
    // secondary section, so search reads as "music search", not "which library".
    LaunchedEffect(query, selectedFilter, searchNonce) {
        if (query.length >= 2) {
            isSearchingRemote = true
            remoteError = null
            delay(350) // Debounce typing
            val filterParam = when (selectedFilter) {
                SearchFilterType.SONGS -> "songs"
                SearchFilterType.ARTISTS -> "artists"
                SearchFilterType.ALBUMS -> "albums"
                SearchFilterType.PLAYLISTS -> "playlists"
                SearchFilterType.ALL -> null
            }
            try {
                remoteSearchResponse = withContext(Dispatchers.IO) {
                    GratiaApp.instance.providerManager.youtubeMusicProvider
                        .search(query, filterParam, limit = 25)
                }
            } catch (e: Exception) {
                Log.e("SearchScreen", "Remote search error: ${e.message}")
                remoteError = "Couldn't reach YouTube Music. Check your connection and try again."
            } finally {
                isSearchingRemote = false
            }
        } else {
            remoteSearchResponse = null
            remoteError = null
            isSearchingRemote = false
        }
    }

    // ── Local / on-device matches ──────────────────────────────────────────
    // Local-only for this section: a downloaded remote track already appears in
    // the catalogue results above, so showing it here would duplicate it.
    val onDeviceMatches = remember(localResults, query, lyricsMatchIds) {
        if (query.isBlank()) return@remember emptyList<SongEntity>()
        localResults
            .filter { it.musicSource == com.gratia.music.data.model.MusicSource.LOCAL || it.id in lyricsMatchIds }
            .sortedByDescending { relevanceScore(query, it.title, it.artist, it.album) }
    }

    val remoteSongs = remember(remoteSearchResponse, query) {
        remoteSearchResponse?.tracks
            ?.map { RemoteTrackMapper.toSongEntity(it) }
            ?.distinctBy { it.id }
            ?.sortedByDescending { relevanceScore(query, it.title, it.artist, it.album) }
            ?: emptyList()
    }
    val remoteArtists = remember(remoteSearchResponse, query, remoteSongs) {
        // Artist relevance is not just the artist's own name match: when the
        // top song result is a confident match for the query, its artist is
        // almost certainly the artist being looked for (spec 75). Searching
        // "Creep" must float Radiohead, not whoever alphabetically contains
        // the query. Ties remain stable — the catalogue's own order stands.
        val boostName = remoteSongs.firstOrNull()
            ?.takeIf { relevanceScore(query, it.title, it.artist, it.album) >= 80 }
            ?.artist
        remoteSearchResponse?.artists
            ?.distinctBy { it.id }
            ?.sortedWith(
                compareByDescending<com.gratia.music.provider.RemoteArtistRef> { artist ->
                    // Name match first, then the associated-artist signal: when
                    // the top song is a confident match, its artist floats up
                    // even though their name does not contain the query (the
                    // "Creep" → Radiohead case, spec A).
                    artistNameScore(query, artist.name) +
                        if (!boostName.isNullOrBlank() &&
                            normalizeForMatch(artist.name).contains(normalizeForMatch(boostName))
                        ) 60 else 0
                }
            )
            ?: emptyList()
    }

    // The query is plainly an artist name and the catalogue agreed: the Artists
    // shelf is surfaced above Songs (spec A). Computed here, so the results
    // composable stays a dumb consumer of already-ranked data.
    val artistFirst = remember(remoteArtists, query) { isArtistFirstQuery(query, remoteArtists) }
    val remoteAlbums = remember(remoteSearchResponse, query) {
        remoteSearchResponse?.albums
            ?.distinctBy { it.id }
            ?.sortedByDescending { relevanceScore(query, it.title, it.artists.joinToString(", ") { a -> a.name }, null) }
            ?: emptyList()
    }
    val remotePlaylists = remember(remoteSearchResponse, query) {
        remoteSearchResponse?.playlists
            ?.distinctBy { it.id }
            ?.sortedByDescending { relevanceScore(query, it.title, it.author ?: "", null) }
            ?: emptyList()
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
                { selectedSongForMenu = null; onNavigateToRemoteArtist(id) }
            },
            onGoToAlbum = song.providerAlbumId?.takeIf { it.isNotBlank() }?.let { id ->
                { selectedSongForMenu = null; onNavigateToRemoteAlbum(id) }
            }
        )
    }

    showAddToPlaylistFor?.let { song ->
        com.gratia.music.ui.components.AddToPlaylistSheet(
            song = song,
            onDismiss = { showAddToPlaylistFor = null }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GratiaTheme.colors.background)
            .statusBarsPadding()
    ) {
        AppleLargeTitleHeader(title = "Search")

        // Search Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 6.dp)
                .height(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(GratiaTheme.colors.surface),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = GratiaTheme.colors.textSecondary,
                modifier = Modifier.padding(start = 8.dp).size(20.dp)
            )

            Box(modifier = Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) {
                    GratiaText(
                        text = "Search music",
                        style = GratiaTheme.typography.body,
                        color = GratiaTheme.colors.textSecondary,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
                androidx.compose.foundation.text.BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    textStyle = GratiaTheme.typography.body.copy(color = GratiaTheme.colors.textPrimary),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    singleLine = true,
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(GratiaTheme.colors.accent)
                )
            }

            if (query.isNotEmpty()) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Clear",
                    tint = GratiaTheme.colors.textSecondary,
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(GratiaTheme.colors.textSecondary.copy(alpha = 0.2f))
                        .clickable { query = "" }
                        .padding(2.dp)
                )
            }
        }

        if (isSearchingRemote) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = GratiaTheme.colors.accent,
                trackColor = Color.Transparent
            )
        } else {
            Spacer(Modifier.height(2.dp))
        }

        // Result-type filter pills (never a source switch)
        if (query.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(SearchFilterType.entries) { filter ->
                    val isSelected = selectedFilter == filter
                    Surface(
                        modifier = Modifier.clickable { selectedFilter = filter },
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) GratiaTheme.colors.textPrimary else GratiaTheme.colors.surface,
                    ) {
                        GratiaText(
                            text = filter.label,
                            style = GratiaTheme.typography.caption.copy(
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                            ),
                            color = if (isSelected) GratiaTheme.colors.background else GratiaTheme.colors.textPrimary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        if (query.isBlank()) {
            SearchLanding(
                searchHistory = searchHistory,
                genres = genres,
                onHistoryClick = { query = it },
                onRemoveHistory = { scope.launch { settingsDataStore.removeSearchHistory(it) } },
                onClearHistory = { scope.launch { settingsDataStore.clearSearchHistory() } },
                onGenreClick = onNavigateToGenre
            )
        } else {
            SearchResults(
                query = query,
                filter = selectedFilter,
                isSearchingRemote = isSearchingRemote,
                remoteError = remoteError,
                onRetry = { remoteSearchResponse = null; searchNonce++ },
                remoteSongs = remoteSongs,
                remoteArtists = remoteArtists,
                artistFirst = artistFirst,
                remoteAlbums = remoteAlbums,
                remotePlaylists = remotePlaylists,
                onDeviceMatches = onDeviceMatches,
                lyricsMatchIds = lyricsMatchIds,
                currentSongId = currentSong?.id,
                isPlaying = isPlaying,
                onPlaySong = { song, queue ->
                    scope.launch { settingsDataStore.addSearchHistory(query) }
                    playerViewModel.playSong(song, queue, PlaybackOrigin.SEARCH)
                },
                onMoreClick = { selectedSongForMenu = it },
                onNavigateToRemoteArtist = {
                    scope.launch { settingsDataStore.addSearchHistory(query) }
                    onNavigateToRemoteArtist(it)
                },
                onNavigateToRemoteAlbum = {
                    scope.launch { settingsDataStore.addSearchHistory(query) }
                    onNavigateToRemoteAlbum(it)
                },
                onNavigateToRemotePlaylist = {
                    scope.launch { settingsDataStore.addSearchHistory(query) }
                    onNavigateToRemotePlaylist(it)
                }
            )
        }
    }
}

@Composable
private fun SearchLanding(
    searchHistory: Set<String>,
    genres: List<String>?,
    onHistoryClick: (String) -> Unit,
    onRemoveHistory: (String) -> Unit,
    onClearHistory: () -> Unit,
    onGenreClick: (String) -> Unit
) {
    if (searchHistory.isEmpty() && genres.isNullOrEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyStateView(
                icon = Icons.Default.Search,
                headline = "Search music",
                description = "Find songs, artists, albums, or playlists."
            )
        }
        return
    }

    val bottomInset = com.gratia.music.ui.LocalBottomPadding.current
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = bottomInset + GratiaTheme.spacing.heroLarge)
    ) {
        if (searchHistory.isNotEmpty()) {
            item {
                AppleSectionHeader(
                    title = "Recent Searches",
                    action = {
                        GratiaText(
                            text = "Clear",
                            style = GratiaTheme.typography.body.copy(fontWeight = FontWeight.Medium),
                            color = GratiaTheme.colors.accent,
                            modifier = Modifier.clickable { onClearHistory() }.padding(4.dp)
                        )
                    }
                )
            }
            items(searchHistory.toList().reversed()) { historyQuery ->
                AppleListRow(
                    title = historyQuery,
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = GratiaTheme.colors.textSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    trailingContent = {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Remove",
                            tint = GratiaTheme.colors.textSecondary,
                            modifier = Modifier
                                .size(20.dp)
                                .clickable { onRemoveHistory(historyQuery) }
                                .padding(2.dp)
                        )
                    },
                    onClick = { onHistoryClick(historyQuery) }
                )
            }
        }

        if (!genres.isNullOrEmpty()) {
            item {
                Spacer(Modifier.height(8.dp))
                AppleSectionHeader(title = "Browse Categories")
                Spacer(Modifier.height(8.dp))
            }
            items(genres.chunked(2)) { rowGenres ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    for (genre in rowGenres) {
                        Box(modifier = Modifier.weight(1f)) {
                            GenreCard(genre = genre, onClick = { onGenreClick(genre) })
                        }
                    }
                    if (rowGenres.size == 1) Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SearchResults(
    query: String,
    filter: SearchFilterType,
    isSearchingRemote: Boolean,
    remoteError: String?,
    onRetry: () -> Unit,
    remoteSongs: List<SongEntity>,
    remoteArtists: List<com.gratia.music.provider.RemoteArtistRef>,
    /** When true, the Artists shelf is drawn above Songs (spec A). */
    artistFirst: Boolean,
    remoteAlbums: List<com.gratia.music.provider.RemoteAlbumSummary>,
    remotePlaylists: List<com.gratia.music.provider.RemotePlaylistSummary>,
    onDeviceMatches: List<SongEntity>,
    lyricsMatchIds: Set<String>,
    currentSongId: String?,
    isPlaying: Boolean,
    onPlaySong: (SongEntity, List<SongEntity>) -> Unit,
    onMoreClick: (SongEntity) -> Unit,
    onNavigateToRemoteArtist: (String) -> Unit,
    onNavigateToRemoteAlbum: (String) -> Unit,
    onNavigateToRemotePlaylist: (String) -> Unit
) {
    val showSongs = filter == SearchFilterType.ALL || filter == SearchFilterType.SONGS
    val showArtists = filter == SearchFilterType.ALL || filter == SearchFilterType.ARTISTS
    val showAlbums = filter == SearchFilterType.ALL || filter == SearchFilterType.ALBUMS
    val showPlaylists = filter == SearchFilterType.ALL || filter == SearchFilterType.PLAYLISTS

    val hasAny = (showSongs && remoteSongs.isNotEmpty()) ||
        (showArtists && remoteArtists.isNotEmpty()) ||
        (showAlbums && remoteAlbums.isNotEmpty()) ||
        (showPlaylists && remotePlaylists.isNotEmpty()) ||
        (filter == SearchFilterType.ALL && onDeviceMatches.isNotEmpty())

    if (!hasAny && !isSearchingRemote) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyStateView(
                icon = Icons.Default.Search,
                headline = if (remoteError != null) "Couldn't search" else "No results for \"$query\"",
                description = remoteError ?: "Try another search term.",
                actionLabel = if (remoteError != null) "Retry" else null,
                onActionClick = if (remoteError != null) onRetry else null
            )
        }
        return
    }

    val bottomInset = com.gratia.music.ui.LocalBottomPadding.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = bottomInset + GratiaTheme.spacing.heroLarge, top = 4.dp)
    ) {
        // ── Section order. Songs lead by default — an exact track match must
        // never be buried under loosely-matching artists (the "God's Plan" bug)
        // — but when the query is plainly an artist name and the catalogue
        // agreed, the Artists shelf steps above Songs (spec A). Both orders draw
        // the same two section composables, so they cannot drift apart.
        if (artistFirst) {
            if (showArtists) searchArtistsSection(remoteArtists, onNavigateToRemoteArtist, topSpacing = 0.dp)
            if (showSongs) searchSongsSection(remoteSongs, currentSongId, isPlaying, onPlaySong, onMoreClick, topSpacing = 12.dp)
        } else {
            if (showSongs) searchSongsSection(remoteSongs, currentSongId, isPlaying, onPlaySong, onMoreClick, topSpacing = 0.dp)
            if (showArtists) searchArtistsSection(remoteArtists, onNavigateToRemoteArtist, topSpacing = 12.dp)
        }

        if (showAlbums && remoteAlbums.isNotEmpty()) {
            item {
                Spacer(Modifier.height(12.dp))
                AppleSectionHeader(title = "Albums")
                Spacer(Modifier.height(6.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(remoteAlbums) { album ->
                        ArtworkCard(
                            title = album.title,
                            subtitle = album.artists.joinToString(", ") { it.name },
                            artworkUrl = album.artworkUrl,
                            onClick = { onNavigateToRemoteAlbum(album.id) }
                        )
                    }
                }
            }
        }

        if (showPlaylists && remotePlaylists.isNotEmpty()) {
            item {
                Spacer(Modifier.height(12.dp))
                AppleSectionHeader(title = "Playlists")
                Spacer(Modifier.height(6.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(remotePlaylists) { playlist ->
                        ArtworkCard(
                            title = playlist.title,
                            subtitle = playlist.author ?: "",
                            artworkUrl = playlist.artworkUrl,
                            onClick = { onNavigateToRemotePlaylist(playlist.id) }
                        )
                    }
                }
            }
        }

        if (onDeviceMatches.isNotEmpty() &&
            (filter == SearchFilterType.ALL || filter == SearchFilterType.SONGS)
        ) {
            item {
                Spacer(Modifier.height(12.dp))
                AppleSectionHeader(title = "On Device")
            }
            itemsIndexed(onDeviceMatches, key = { _, s -> "local_${s.id}" }) { index, song ->
                SongRow(
                    song = song,
                    index = index,
                    isActive = currentSongId == song.id,
                    isPlaying = currentSongId == song.id && isPlaying,
                    onClick = { onPlaySong(song, onDeviceMatches) },
                    onMoreClick = { onMoreClick(song) },
                    badge = if (song.id in lyricsMatchIds) "Lyrics match" else null,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
        }
    }
}

/**
 * The Songs section of the search results, as one reusable list-scope block so
 * the two possible orders (Songs-first, artist-first) draw identical UI.
 */
private fun LazyListScope.searchSongsSection(
    remoteSongs: List<SongEntity>,
    currentSongId: String?,
    isPlaying: Boolean,
    onPlaySong: (SongEntity, List<SongEntity>) -> Unit,
    onMoreClick: (SongEntity) -> Unit,
    topSpacing: Dp,
) {
    if (remoteSongs.isEmpty()) return
    item(key = "songs_header") {
        if (topSpacing > 0.dp) Spacer(Modifier.height(topSpacing))
        AppleSectionHeader(title = "Songs")
    }
    itemsIndexed(remoteSongs, key = { _, s -> "remote_${s.id}" }) { index, song ->
        SongRow(
            song = song,
            index = index,
            isActive = currentSongId == song.id,
            isPlaying = currentSongId == song.id && isPlaying,
            onClick = { onPlaySong(song, listOf(song)) },
            onMoreClick = { onMoreClick(song) },
            badge = "YouTube Music",
            modifier = Modifier.padding(horizontal = 8.dp)
        )
    }
}

/** The Artists shelf of the search results, as one reusable list-scope block. */
private fun LazyListScope.searchArtistsSection(
    remoteArtists: List<com.gratia.music.provider.RemoteArtistRef>,
    onNavigateToRemoteArtist: (String) -> Unit,
    topSpacing: Dp,
) {
    if (remoteArtists.isEmpty()) return
    item(key = "artists_header") {
        if (topSpacing > 0.dp) Spacer(Modifier.height(topSpacing))
        AppleSectionHeader(title = "Artists")
        Spacer(Modifier.height(6.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(remoteArtists) { artistRef ->
                ArtistCard(
                    name = artistRef.name,
                    artworkUrl = artistRef.artworkUrl,
                    onClick = { artistRef.id?.let(onNavigateToRemoteArtist) }
                )
            }
        }
    }
}

@Composable
private fun ArtistCard(
    name: String,
    artworkUrl: String?,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(90.dp)
            .bounceClick { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(GratiaTheme.colors.surface),
            contentAlignment = Alignment.Center
        ) {
            if (!artworkUrl.isNullOrBlank()) {
                AsyncImage(
                    model = rememberArtworkRequest(artworkUrl, LARGE_ARTWORK_PX, crossfadeMs = 0),
                    contentDescription = name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    text = name.take(1),
                    fontFamily = SpaceGrotesk,
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp,
                    color = GratiaTheme.colors.textSecondary
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = name,
            fontFamily = Inter,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = GratiaTheme.colors.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
private fun ArtworkCard(
    title: String,
    subtitle: String,
    artworkUrl: String?,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(130.dp)
            .bounceClick { onClick() }
    ) {
        AsyncImage(
            model = rememberArtworkRequest(artworkUrl, LARGE_ARTWORK_PX, crossfadeMs = 0),
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(130.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(GratiaTheme.colors.surface)
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = title,
            fontFamily = Inter,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            color = GratiaTheme.colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (subtitle.isNotBlank()) {
            Text(
                text = subtitle,
                fontFamily = Inter,
                fontSize = 11.sp,
                color = GratiaTheme.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun GenreCard(
    genre: String,
    onClick: () -> Unit
) {
    val hash = abs(genre.hashCode())
    val palette = listOf(
        listOf(Color(0xFF810100), Color(0xFFA65D03)),
        listOf(Color(0xFF1DB954), Color(0xFF0D7041)),
        listOf(Color(0xFF2D46B9), Color(0xFF1F3179)),
        listOf(Color(0xFF8B4513), Color(0xFFA0522D)),
        listOf(Color(0xFF6B3FA0), Color(0xFF9C27B0)),
        listOf(Color(0xFF00838F), Color(0xFF006064)),
        listOf(Color(0xFFE65100), Color(0xFFBF360C)),
        listOf(Color(0xFF283593), Color(0xFF1A237E)),
    )
    val gradientColors = palette[hash % palette.size]

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.linearGradient(gradientColors))
            .bounceClick { onClick() }
            .padding(16.dp),
        contentAlignment = Alignment.BottomStart
    ) {
        Text(
            text = genre,
            fontFamily = SpaceGrotesk,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = Color.White
        )
    }
}
