package com.gratia.music.ui.screens

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.gratia.music.GratiaApp
import com.gratia.music.data.SettingsDataStore
import com.gratia.music.data.model.SongEntity
import com.gratia.music.data.repository.SongRepository
import com.gratia.music.player.PlayerViewModel
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

enum class SearchSource(val label: String) {
    LIBRARY("Library"),
    YOUTUBE_MUSIC("YouTube Music"),
    ALL("All")
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
    val results by songRepo.search(query.ifBlank { "§§NOMATCH§§" }).collectAsState(initial = emptyList())
    val genres by songRepo.getDistinctGenres().collectAsState(initial = null)
    val currentSong by playerViewModel.currentSong.collectAsState()
    val isPlaying by playerViewModel.isPlaying.collectAsState()
    val favoriteSongIds by playerViewModel.favoriteSongIds.collectAsState()
    val scope = rememberCoroutineScope()
    var lyricsMatchIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    var selectedSource by remember { mutableStateOf(SearchSource.ALL) }
    var remoteSearchResponse by remember { mutableStateOf<RemoteSearchResponse?>(null) }
    var isSearchingRemote by remember { mutableStateOf(false) }
    var selectedSongForMenu by remember { mutableStateOf<SongEntity?>(null) }

    // Check lyrics matches for local library
    LaunchedEffect(query) {
        if (query.length >= 2) {
            scope.launch {
                val lyricsMatches = songRepo.searchByLyrics(query)
                lyricsMatchIds = lyricsMatches.map { it.id }.toSet()
            }
        } else {
            lyricsMatchIds = emptySet()
        }
    }

    val libraryFilters = listOf("All", "Songs", "Artists", "Albums", "Lyrics")
    val ytmFilters = listOf("All", "Songs", "Artists", "Albums", "Playlists")
    val currentFilters = when (selectedSource) {
        SearchSource.LIBRARY -> libraryFilters
        SearchSource.YOUTUBE_MUSIC, SearchSource.ALL -> ytmFilters
    }

    var selectedFilter by remember { mutableStateOf("All") }

    // Reset selected filter if not in current filters
    LaunchedEffect(selectedSource) {
        if (selectedFilter !in currentFilters) {
            selectedFilter = "All"
        }
    }

    // Debounced remote search
    LaunchedEffect(query, selectedSource, selectedFilter) {
        if (query.length >= 2 && (selectedSource == SearchSource.YOUTUBE_MUSIC || selectedSource == SearchSource.ALL)) {
            isSearchingRemote = true
            delay(350) // Debounce typing
            val filterParam = when (selectedFilter.lowercase()) {
                "songs" -> "songs"
                "artists" -> "artists"
                "albums" -> "albums"
                "playlists" -> "playlists"
                else -> null
            }
            try {
                val res = withContext(Dispatchers.IO) {
                    GratiaApp.instance.providerManager.youtubeMusicProvider.search(query, filterParam, limit = 25)
                }
                remoteSearchResponse = res
            } catch (e: Exception) {
                Log.e("SearchScreen", "Remote search error: ${e.message}")
            } finally {
                isSearchingRemote = false
            }
        } else if (query.isBlank()) {
            remoteSearchResponse = null
            isSearchingRemote = false
        }
    }

    // Local results filtering
    val baseResults = if (query.isBlank()) emptyList() else results
    val displayResults = remember(baseResults, lyricsMatchIds, selectedFilter, query) {
        if (query.isBlank()) return@remember emptyList<SongEntity>()
        when (selectedFilter) {
            "All" -> baseResults + baseResults.filter { it.id in lyricsMatchIds }.filter { it !in baseResults }
            "Songs" -> baseResults.filter { it.title.contains(query, ignoreCase = true) }
            "Artists" -> baseResults.filter { it.artist.contains(query, ignoreCase = true) }
            "Albums" -> baseResults.filter { it.album?.contains(query, ignoreCase = true) == true }
            "Lyrics" -> baseResults.filter { it.id in lyricsMatchIds }
            else -> baseResults
        }
    }

    val remoteSongEntities = remember(remoteSearchResponse) {
        remoteSearchResponse?.tracks?.map { RemoteTrackMapper.toSongEntity(it) } ?: emptyList()
    }

    // Song menu bottom sheet
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GratiaTheme.colors.background)
            .statusBarsPadding()
    ) {
        AppleLargeTitleHeader(title = "Search")

        // Source Switcher (Library | YouTube Music | All)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SearchSource.entries.forEach { source ->
                val isSelected = selectedSource == source
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .bounceClick { selectedSource = source },
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) GratiaTheme.colors.surface else Color.Transparent,
                    border = if (isSelected) null else BorderStroke(1.dp, GratiaTheme.colors.surface)
                ) {
                    Box(
                        modifier = Modifier.padding(vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = source.label,
                            fontFamily = Inter,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (isSelected) GratiaTheme.colors.accent else GratiaTheme.colors.textSecondary
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(4.dp))

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
                    val placeholder = when (selectedSource) {
                        SearchSource.LIBRARY -> "Search your local library"
                        SearchSource.YOUTUBE_MUSIC -> "Search YouTube Music catalogue"
                        SearchSource.ALL -> "Search library & YouTube Music"
                    }
                    GratiaText(
                        text = placeholder,
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

        // Search progress bar for remote searches
        if (isSearchingRemote) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                color = GratiaTheme.colors.accent,
                trackColor = Color.Transparent
            )
        } else {
            Spacer(Modifier.height(2.dp))
        }

        // Filter Pills
        if (query.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(currentFilters) { filter ->
                    val isSelected = selectedFilter == filter
                    Surface(
                        modifier = Modifier.clickable { selectedFilter = filter },
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) GratiaTheme.colors.textPrimary else GratiaTheme.colors.surface,
                    ) {
                        GratiaText(
                            text = filter,
                            style = GratiaTheme.typography.caption.copy(fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium),
                            color = if (isSelected) GratiaTheme.colors.background else GratiaTheme.colors.textPrimary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        // Content Area
        if (query.isBlank()) {
            // Initial view: Search history and browse categories
            if (searchHistory.isEmpty() && genres?.isEmpty() == true) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    EmptyStateView(
                        icon = Icons.Default.Search,
                        headline = "Search music",
                        description = "Find songs, artists, albums, or playlists."
                    )
                }
            } else {
                val bottomInset = com.gratia.music.ui.LocalBottomPadding.current
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
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
                                        modifier = Modifier.clickable { 
                                            scope.launch { 
                                                settingsDataStore.clearSearchHistory()
                                            } 
                                        }.padding(4.dp)
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
                                            .clickable { 
                                                scope.launch { 
                                                    settingsDataStore.removeSearchHistory(historyQuery)
                                                } 
                                            }
                                            .padding(2.dp)
                                    )
                                },
                                onClick = { query = historyQuery }
                            )
                        }
                    }

                    if (!genres.isNullOrEmpty()) {
                        item {
                            Spacer(Modifier.height(8.dp))
                            AppleSectionHeader(title = "Browse Categories")
                            Spacer(Modifier.height(8.dp))
                        }
                        
                        items(genres!!.chunked(2)) { rowGenres ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                for (genre in rowGenres) {
                                    Box(modifier = Modifier.weight(1f)) {
                                        GenreCard(
                                            genre = genre,
                                            onClick = { onNavigateToGenre(genre) }
                                        )
                                    }
                                }
                                if (rowGenres.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Results View
            val bottomInset = com.gratia.music.ui.LocalBottomPadding.current

            val hasLocalResults = displayResults.isNotEmpty()
            val hasRemoteTracks = remoteSongEntities.isNotEmpty()
            val hasRemoteArtists = remoteSearchResponse?.artists?.isNotEmpty() == true
            val hasRemoteAlbums = remoteSearchResponse?.albums?.isNotEmpty() == true
            val hasRemotePlaylists = remoteSearchResponse?.playlists?.isNotEmpty() == true

            val hasAnyResults = when (selectedSource) {
                SearchSource.LIBRARY -> hasLocalResults
                SearchSource.YOUTUBE_MUSIC -> hasRemoteTracks || hasRemoteArtists || hasRemoteAlbums || hasRemotePlaylists
                SearchSource.ALL -> hasLocalResults || hasRemoteTracks || hasRemoteArtists || hasRemoteAlbums || hasRemotePlaylists
            }

            if (!hasAnyResults && !isSearchingRemote) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    EmptyStateView(
                        icon = Icons.Default.Search,
                        headline = "No results for \"$query\"",
                        description = "Try another search term or switch source."
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = bottomInset + GratiaTheme.spacing.heroLarge, top = 4.dp)
                ) {
                    // 1. LOCAL RESULTS SECTION (in LIBRARY or ALL mode)
                    if ((selectedSource == SearchSource.LIBRARY || selectedSource == SearchSource.ALL) && hasLocalResults) {
                        if (selectedSource == SearchSource.ALL) {
                            item {
                                AppleSectionHeader(title = "From Your Library")
                            }
                        }

                        itemsIndexed(displayResults.distinctBy { it.id }, key = { _, s -> "local_${s.id}" }) { index, song ->
                            SongRow(
                                song = song,
                                index = index,
                                isActive = currentSong?.id == song.id,
                                isPlaying = currentSong?.id == song.id && isPlaying,
                                onClick = { 
                                    scope.launch { settingsDataStore.addSearchHistory(query) }
                                    playerViewModel.playSong(song, displayResults.distinctBy { it.id }) 
                                },
                                onMoreClick = { selectedSongForMenu = song },
                                badge = if (song.id in lyricsMatchIds) "Lyrics match" else null,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                        }
                    }

                    // 2. REMOTE RESULTS SECTIONS (in YOUTUBE_MUSIC or ALL mode)
                    if (selectedSource == SearchSource.YOUTUBE_MUSIC || selectedSource == SearchSource.ALL) {
                        // Remote Artists Carousel
                        if (hasRemoteArtists && (selectedFilter == "All" || selectedFilter == "Artists")) {
                            item {
                                Spacer(Modifier.height(12.dp))
                                AppleSectionHeader(title = "Artists")
                                Spacer(Modifier.height(6.dp))

                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 20.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    items(remoteSearchResponse!!.artists) { artistRef ->
                                        Column(
                                            modifier = Modifier
                                                .width(90.dp)
                                                .bounceClick {
                                                    scope.launch { settingsDataStore.addSearchHistory(query) }
                                                    if (!artistRef.id.isNullOrBlank()) {
                                                        onNavigateToRemoteArtist(artistRef.id)
                                                    }
                                                },
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(76.dp)
                                                    .clip(CircleShape)
                                                    .background(GratiaTheme.colors.surface),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = artistRef.name.take(1),
                                                    fontFamily = SpaceGrotesk,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 24.sp,
                                                    color = GratiaTheme.colors.textSecondary
                                                )
                                            }
                                            Spacer(Modifier.height(6.dp))
                                            Text(
                                                text = artistRef.name,
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
                                }
                            }
                        }

                        // Remote Albums Carousel
                        if (hasRemoteAlbums && (selectedFilter == "All" || selectedFilter == "Albums")) {
                            item {
                                Spacer(Modifier.height(12.dp))
                                AppleSectionHeader(title = "Albums")
                                Spacer(Modifier.height(6.dp))

                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 20.dp),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    items(remoteSearchResponse!!.albums) { album ->
                                        Column(
                                            modifier = Modifier
                                                .width(130.dp)
                                                .bounceClick {
                                                    scope.launch { settingsDataStore.addSearchHistory(query) }
                                                    onNavigateToRemoteAlbum(album.id)
                                                }
                                        ) {
                                            AsyncImage(
                                                model = ImageRequest.Builder(context)
                                                    .data(album.artworkUrl)
                                                    .crossfade(true)
                                                    .build(),
                                                contentDescription = album.title,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .size(130.dp)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(GratiaTheme.colors.surface)
                                            )
                                            Spacer(Modifier.height(6.dp))
                                            Text(
                                                text = album.title,
                                                fontFamily = Inter,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 13.sp,
                                                color = GratiaTheme.colors.textPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            val artistStr = album.artists.joinToString(", ") { it.name }
                                            if (artistStr.isNotBlank()) {
                                                Text(
                                                    text = artistStr,
                                                    fontFamily = Inter,
                                                    fontSize = 11.sp,
                                                    color = GratiaTheme.colors.textSecondary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Remote Playlists Carousel
                        if (hasRemotePlaylists && (selectedFilter == "All" || selectedFilter == "Playlists")) {
                            item {
                                Spacer(Modifier.height(12.dp))
                                AppleSectionHeader(title = "Playlists")
                                Spacer(Modifier.height(6.dp))

                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 20.dp),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    items(remoteSearchResponse!!.playlists) { playlist ->
                                        Column(
                                            modifier = Modifier
                                                .width(130.dp)
                                                .bounceClick {
                                                    scope.launch { settingsDataStore.addSearchHistory(query) }
                                                    onNavigateToRemotePlaylist(playlist.id)
                                                }
                                        ) {
                                            AsyncImage(
                                                model = ImageRequest.Builder(context)
                                                    .data(playlist.artworkUrl)
                                                    .crossfade(true)
                                                    .build(),
                                                contentDescription = playlist.title,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .size(130.dp)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(GratiaTheme.colors.surface)
                                            )
                                            Spacer(Modifier.height(6.dp))
                                            Text(
                                                text = playlist.title,
                                                fontFamily = Inter,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 13.sp,
                                                color = GratiaTheme.colors.textPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (!playlist.author.isNullOrBlank()) {
                                                Text(
                                                    text = playlist.author,
                                                    fontFamily = Inter,
                                                    fontSize = 11.sp,
                                                    color = GratiaTheme.colors.textSecondary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Remote Songs
                        if (hasRemoteTracks && (selectedFilter == "All" || selectedFilter == "Songs" || selectedFilter == "Videos")) {
                            item {
                                Spacer(Modifier.height(12.dp))
                                val headerTitle = if (selectedSource == SearchSource.ALL) "From YouTube Music" else "Songs"
                                AppleSectionHeader(title = headerTitle)
                            }

                            itemsIndexed(remoteSongEntities, key = { _, s -> s.id }) { index, song ->
                                SongRow(
                                    song = song,
                                    index = index,
                                    isActive = currentSong?.id == song.id,
                                    isPlaying = currentSong?.id == song.id && isPlaying,
                                    onClick = { 
                                        scope.launch { settingsDataStore.addSearchHistory(query) }
                                        val playQueue = if (selectedSource == SearchSource.ALL && hasLocalResults) {
                                            // Mixed queue of local and remote songs!
                                            (displayResults + remoteSongEntities).distinctBy { it.id }
                                        } else {
                                            remoteSongEntities
                                        }
                                        playerViewModel.playSong(song, playQueue) 
                                    },
                                    onMoreClick = { selectedSongForMenu = song },
                                    badge = "YouTube Music",
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
