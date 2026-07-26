package com.gratia.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gratia.music.GratiaApp
import com.gratia.music.data.SettingsDataStore
import com.gratia.music.data.model.SongEntity
import com.gratia.music.data.repository.SongRepository
import com.gratia.music.player.PlayerViewModel
import com.gratia.music.ui.components.*
import com.gratia.music.ui.theme.GratiaTheme
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.launch
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    playerViewModel: PlayerViewModel,
    onNavigateToGenre: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val settingsDataStore = remember { SettingsDataStore(context) }
    val searchHistory by settingsDataStore.searchHistoryFlow.collectAsState(initial = emptySet())
    
    val songRepo = remember { SongRepository(GratiaApp.instance.database.songDao()) }
    var query by remember { mutableStateOf("") }
    val results by songRepo.search(query.ifBlank { "§§NOMATCH§§" }).collectAsState(initial = emptyList())
    val genres by songRepo.getDistinctGenres().collectAsState(initial = emptyList())
    val currentSong by playerViewModel.currentSong.collectAsState()
    val isPlaying by playerViewModel.isPlaying.collectAsState()
    val scope = rememberCoroutineScope()
    var lyricsMatchIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    // Check lyrics matches
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

    val filters = listOf("All", "Songs", "Artists", "Albums", "Lyrics")
    var selectedFilter by remember { mutableStateOf(filters[0]) }

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GratiaTheme.colors.background)
    ) {
        AppleLargeTitleHeader(title = "Search")

        // Apple style search bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .height(36.dp)
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
                        text = "Artists, Songs, Lyrics, and More",
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
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(GratiaTheme.colors.textSecondary.copy(alpha = 0.2f))
                        .clickable { query = "" }
                        .padding(2.dp)
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        if (query.isNotEmpty()) {
            // Filter Pills Apple Style (small rounded rectangles)
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filters) { filter ->
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

        if (query.isBlank()) {
            if (searchHistory.isEmpty()) {
                // Show genre cards grid
                if (genres.isNotEmpty()) {
                    AppleSectionHeader(title = "Browse Categories")
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 120.dp)
                    ) {
                        items(genres) { genre ->
                            GenreCard(
                                genre = genre,
                                onClick = { onNavigateToGenre(genre) }
                            )
                        }
                    }
                } else {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        EmptyStateView(
                            icon = Icons.Default.Search,
                            headline = "Search your library",
                            description = "Find any song, artist, album, or matching lyric."
                        )
                    }
                }
            } else {
                // Search History Apple Style
                AppleSectionHeader(
                    title = "Recent Searches",
                    action = {
                        GratiaText(
                            text = "Clear",
                            style = GratiaTheme.typography.body.copy(fontWeight = FontWeight.Medium),
                            color = GratiaTheme.colors.accent,
                            modifier = Modifier.clickable { scope.launch { settingsDataStore.clearSearchHistory() } }.padding(4.dp)
                        )
                    }
                )
                
                LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
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
                                        .clickable { scope.launch { settingsDataStore.removeSearchHistory(historyQuery) } }
                                        .padding(2.dp)
                                )
                            },
                            onClick = { query = historyQuery }
                        )
                    }
                }
            }
        } else if (displayResults.isEmpty()) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                EmptyStateView(
                    icon = Icons.Default.Search,
                    headline = "No results for \"$query\"",
                    description = "Try a different spelling or check your active filter."
                )
            }
        } else {
            // Results
            LazyColumn(
                contentPadding = PaddingValues(bottom = 120.dp, top = 8.dp)
            ) {
                itemsIndexed(displayResults.distinctBy { it.id }, key = { _, s -> s.id }) { index, song ->
                    SongRow(
                        song = song,
                        index = index,
                        isActive = currentSong?.id == song.id,
                        isPlaying = currentSong?.id == song.id && isPlaying,
                        onClick = { 
                            scope.launch { settingsDataStore.addSearchHistory(query) }
                            playerViewModel.playSong(song, displayResults.distinctBy { it.id }) 
                        },
                        badge = if (song.id in lyricsMatchIds) "Lyrics match" else null,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
            }
        }
    }
}

/**
 * Genre card with gradient background for the browse categories grid.
 * Uses a deterministic color based on the genre name hash.
 */
@Composable
private fun GenreCard(
    genre: String,
    onClick: () -> Unit
) {
    val hash = abs(genre.hashCode())
    val palette = listOf(
        listOf(Color(0xFF810100), Color(0xFFA65D03)), // Red → Amber
        listOf(Color(0xFF1DB954), Color(0xFF0D7041)), // Green tones
        listOf(Color(0xFF2D46B9), Color(0xFF1F3179)), // Blue
        listOf(Color(0xFF8B4513), Color(0xFFA0522D)), // Brown → Sienna
        listOf(Color(0xFF6B3FA0), Color(0xFF9C27B0)), // Purple
        listOf(Color(0xFF00838F), Color(0xFF006064)), // Teal
        listOf(Color(0xFFE65100), Color(0xFFBF360C)), // Deep Orange
        listOf(Color(0xFF283593), Color(0xFF1A237E)), // Indigo
    )
    val gradientColors = palette[hash % palette.size]

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.linearGradient(gradientColors)
            )
            .clickable { onClick() }
            .padding(16.dp),
        contentAlignment = Alignment.BottomStart
    ) {
        Text(
            text = genre,
            fontFamily = com.gratia.music.ui.theme.SpaceGrotesk,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = Color.White
        )
    }
}
