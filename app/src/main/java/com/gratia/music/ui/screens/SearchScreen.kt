package com.gratia.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gratia.music.GratiaApp
import com.gratia.music.data.model.SongEntity
import com.gratia.music.data.repository.SongRepository
import com.gratia.music.player.PlayerViewModel
import com.gratia.music.ui.components.SongRow
import com.gratia.music.ui.theme.GratiaTheme
import com.gratia.music.ui.theme.Inter
import com.gratia.music.ui.theme.SpaceGrotesk
import kotlinx.coroutines.launch

@Composable
fun SearchScreen(playerViewModel: PlayerViewModel) {
    val songRepo = remember { SongRepository(GratiaApp.instance.database.songDao()) }
    var query by remember { mutableStateOf("") }
    val results by songRepo.search(query.ifBlank { "§§NOMATCH§§" }).collectAsState(initial = emptyList())
    val allSongs by songRepo.getAllSongs().collectAsState(initial = emptyList())
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

    val displayResults = if (query.isBlank()) emptyList() else results

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GratiaTheme.colors.cotton)
    ) {
        // Header
        Text(
            "Search",
            fontFamily = SpaceGrotesk,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            color = GratiaTheme.colors.textPrimary,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp)
        )

        // Search field
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Songs, artists, albums, lyrics…", fontSize = 13.sp, color = GratiaTheme.colors.textMuted) },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = GratiaTheme.colors.textMuted, modifier = Modifier.size(18.dp)) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = GratiaTheme.colors.cherryRed.copy(alpha = 0.4f),
                unfocusedBorderColor = GratiaTheme.colors.surfaceHover,
                focusedContainerColor = GratiaTheme.colors.surfaceCard,
                unfocusedContainerColor = GratiaTheme.colors.surfaceCard,
                focusedTextColor = GratiaTheme.colors.textPrimary,
                unfocusedTextColor = GratiaTheme.colors.textPrimary,
                cursorColor = GratiaTheme.colors.cherryRed,
            ),
            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, fontFamily = Inter)
        )

        Spacer(Modifier.height(16.dp))

        if (query.isBlank()) {
            // Empty search state
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 80.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.Search, null, tint = GratiaTheme.colors.textMuted, modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(16.dp))
                Text("Search your private library", fontFamily = Inter, fontSize = 14.sp, color = GratiaTheme.colors.textSecondary)
                Text("Try a song, artist, album, mood, or lyric.", fontFamily = Inter, fontSize = 11.sp, color = GratiaTheme.colors.textMuted)
            }
        } else if (displayResults.isEmpty()) {
            // No results
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 80.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("No results for \"$query\"", fontFamily = Inter, fontSize = 14.sp, color = GratiaTheme.colors.textSecondary)
                Text("Try a different spelling or search term", fontFamily = Inter, fontSize = 11.sp, color = GratiaTheme.colors.textMuted)
            }
        } else {
            // Results
            Text(
                "${displayResults.size} result${if (displayResults.size > 1) "s" else ""}",
                fontFamily = Inter,
                fontSize = 11.sp,
                color = GratiaTheme.colors.textMuted,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )
            LazyColumn {
                itemsIndexed(displayResults, key = { _, s -> s.id }) { index, song ->
                    SongRow(
                        song = song,
                        index = index,
                        isActive = currentSong?.id == song.id,
                        isPlaying = currentSong?.id == song.id && isPlaying,
                        onClick = { playerViewModel.playSong(song, displayResults) },
                        badge = if (song.id in lyricsMatchIds) "Lyrics match" else null
                    )
                }
            }
        }
    }
}
