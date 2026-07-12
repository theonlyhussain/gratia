package com.gratia.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.gratia.music.GratiaApp
import com.gratia.music.data.SettingsDataStore
import com.gratia.music.data.model.SongEntity
import com.gratia.music.data.repository.SongRepository
import com.gratia.music.player.PlayerViewModel
import com.gratia.music.ui.components.EmptyStateView
import com.gratia.music.ui.components.SongRow
import com.gratia.music.ui.theme.GratiaTheme
import com.gratia.music.ui.theme.Inter
import com.gratia.music.ui.theme.SpaceGrotesk
import kotlinx.coroutines.launch

@Composable
fun SearchScreen(playerViewModel: PlayerViewModel) {
    val context = LocalContext.current
    val settingsDataStore = remember { SettingsDataStore(context) }
    val searchHistory by settingsDataStore.searchHistoryFlow.collectAsState(initial = emptySet())
    
    val songRepo = remember { SongRepository(GratiaApp.instance.database.songDao()) }
    var query by remember { mutableStateOf("") }
    val results by songRepo.search(query.ifBlank { "§§NOMATCH§§" }).collectAsState(initial = emptyList())
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
        // Header
        Text(
            "Search",
            fontFamily = SpaceGrotesk,
            fontWeight = FontWeight.Bold,
            fontSize = 28.sp,
            color = GratiaTheme.colors.textPrimary,
            modifier = Modifier.padding(start = 24.dp, top = 20.dp, end = 24.dp, bottom = 12.dp)
        )

        // Search field
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Songs, artists, albums, lyrics…", fontSize = 13.sp, color = GratiaTheme.colors.textSecondary) },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = GratiaTheme.colors.textSecondary, modifier = Modifier.size(18.dp)) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = GratiaTheme.colors.accent.copy(alpha = 0.4f),
                unfocusedBorderColor = GratiaTheme.colors.surfaceHover,
                focusedContainerColor = GratiaTheme.colors.surface,
                unfocusedContainerColor = GratiaTheme.colors.surface,
                focusedTextColor = GratiaTheme.colors.textPrimary,
                unfocusedTextColor = GratiaTheme.colors.textPrimary,
                cursorColor = GratiaTheme.colors.accent,
            ),
            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, fontFamily = Inter)
        )

        Spacer(Modifier.height(12.dp))

        // Filter Chips
        androidx.compose.foundation.lazy.LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filters) { filter ->
                val isSelected = selectedFilter == filter
                Surface(
                    modifier = Modifier.clickable { selectedFilter = filter },
                    shape = RoundedCornerShape(16.dp),
                    color = if (isSelected) GratiaTheme.colors.accent else GratiaTheme.colors.surface,
                    border = if (isSelected) null else androidx.compose.foundation.BorderStroke(1.dp, GratiaTheme.colors.glassBorder)
                ) {
                    Text(
                        text = filter,
                        fontFamily = Inter,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (isSelected) GratiaTheme.colors.background else GratiaTheme.colors.textPrimary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        if (query.isBlank()) {
            if (searchHistory.isEmpty()) {
                // Empty search state
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    EmptyStateView(
                        icon = Icons.Default.Search,
                        headline = "Search your library",
                        description = "Find any song, artist, album, or matching lyric."
                    )
                }
            } else {
                // Search History
                Column(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Recent Searches",
                            fontFamily = SpaceGrotesk,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                            color = GratiaTheme.colors.textPrimary
                        )
                        Text(
                            text = "CLEAR",
                            fontFamily = Inter,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = GratiaTheme.colors.accent,
                            modifier = Modifier
                                .clickable { scope.launch { settingsDataStore.clearSearchHistory() } }
                                .padding(4.dp)
                        )
                    }

                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        searchHistory.forEach { historyQuery ->
                            InputChip(
                                selected = false,
                                onClick = { query = historyQuery },
                                label = { Text(historyQuery, fontFamily = Inter, fontSize = 13.sp) },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remove",
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clickable { scope.launch { settingsDataStore.removeSearchHistory(historyQuery) } }
                                    )
                                },
                                colors = InputChipDefaults.inputChipColors(
                                    containerColor = GratiaTheme.colors.surface,
                                    labelColor = GratiaTheme.colors.textPrimary,
                                    trailingIconColor = GratiaTheme.colors.textSecondary
                                ),
                                border = InputChipDefaults.inputChipBorder(
                                    borderColor = GratiaTheme.colors.glassBorder,
                                    enabled = true,
                                    selected = false
                                )
                            )
                        }
                    }
                }
            }
        } else if (displayResults.isEmpty()) {
            // No results
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                EmptyStateView(
                    icon = Icons.Default.Search,
                    headline = "No results for \"$query\"",
                    description = "Try a different spelling or check your active filter."
                )
            }
        } else {
            // Results
            Text(
                "${displayResults.size} result${if (displayResults.size > 1) "s" else ""}",
                fontFamily = Inter,
                fontSize = 11.sp,
                color = GratiaTheme.colors.textSecondary,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
            )
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
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            }
        }
    }
}
