package com.gratia.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gratia.music.GratiaApp
import com.gratia.music.data.repository.SongRepository
import com.gratia.music.player.PlayerViewModel
import com.gratia.music.ui.components.ArtistFallback
import com.gratia.music.ui.components.CoverArtImage
import com.gratia.music.ui.components.EmptyStateView
import com.gratia.music.ui.components.FolderFallback
import com.gratia.music.ui.theme.GratiaTheme
import com.gratia.music.ui.theme.Inter
import com.gratia.music.ui.theme.SpaceGrotesk

@Composable
fun LibraryScreen(
    playerViewModel: PlayerViewModel,
    onNavigateToAlbum: (String) -> Unit = {},
    onNavigateToArtist: (String) -> Unit = {},
    onNavigateToFolder: (String) -> Unit = {}
) {
    val songRepo = remember { SongRepository(GratiaApp.instance.database.songDao()) }
    val allSongs by songRepo.getAllSongs().collectAsState(initial = emptyList())
    
    val tabs = listOf("Songs", "Albums", "Artists", "Folders")
    var selectedTab by remember { mutableStateOf(tabs[0]) }

    var sortOption by remember { mutableStateOf("Date added") }
    var showSortMenu by remember { mutableStateOf(false) }

    val sortedSongs = remember(allSongs, sortOption) {
        when (sortOption) {
            "Title" -> allSongs.sortedBy { it.title.lowercase() }
            "Artist" -> allSongs.sortedBy { it.artist.lowercase() }
            "Play count" -> allSongs.sortedByDescending { it.playCount }
            else -> allSongs.sortedByDescending { it.createdAt }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GratiaTheme.colors.background)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Your Library",
                fontFamily = SpaceGrotesk,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                color = GratiaTheme.colors.textPrimary
            )
            
            if (selectedTab == "Songs") {
                Box {
                    IconButton(onClick = { showSortMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.Sort,
                            contentDescription = "Sort",
                            tint = GratiaTheme.colors.textPrimary
                        )
                    }
                    
                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false },
                        modifier = Modifier.background(GratiaTheme.colors.surface)
                    ) {
                        listOf("Date added", "Title", "Artist", "Play count").forEach { option ->
                            DropdownMenuItem(
                                text = { 
                                    Text(
                                        text = option, 
                                        color = if (sortOption == option) GratiaTheme.colors.accent else GratiaTheme.colors.textPrimary,
                                        fontFamily = Inter
                                    ) 
                                },
                                onClick = {
                                    sortOption = option
                                    showSortMenu = false
                                }
                            )
                        }
                    }
                }
            }
        }

        // Tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            tabs.forEach { tab ->
                val isSelected = selectedTab == tab
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isSelected) GratiaTheme.colors.accent else GratiaTheme.colors.surface)
                        .clickable { selectedTab = tab }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = tab,
                        fontFamily = Inter,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        fontSize = 14.sp,
                        color = if (isSelected) GratiaTheme.colors.background else GratiaTheme.colors.textSecondary
                    )
                }
            }
        }

        // Content
        Box(modifier = Modifier.fillMaxSize().padding(top = 8.dp)) {
            when (selectedTab) {
                "Songs" -> {
                    if (sortedSongs.isEmpty()) {
                        EmptyStateView(
                            icon = Icons.Default.LibraryMusic,
                            headline = "Your library is empty",
                            description = "Add music to begin listening."
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(bottom = 120.dp, top = 8.dp)
                        ) {
                            item { SongList(sortedSongs, playerViewModel) }
                        }
                    }
                }
                "Albums" -> {
                    val albums = remember(allSongs) { allSongs.mapNotNull { it.album }.distinct().sorted() }
                    if (albums.isEmpty()) {
                        EmptyStateView(
                            icon = Icons.Default.Album,
                            headline = "No albums found",
                            description = "Albums will appear here automatically."
                        )
                    } else {
                        LazyColumn(contentPadding = PaddingValues(bottom = 120.dp, top = 8.dp, start = 24.dp, end = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(albums) { album ->
                                val coverArtPath = allSongs.firstOrNull { it.album == album && it.coverArtPath != null }?.coverArtPath
                                Row(
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(GratiaTheme.colors.surface).clickable { onNavigateToAlbum(album) }.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CoverArtImage(coverArtPath = coverArtPath, title = album, size = 56.dp, cornerRadius = 8.dp)
                                    Spacer(Modifier.width(16.dp))
                                    Text(text = album, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = GratiaTheme.colors.textPrimary)
                                }
                            }
                        }
                    }
                }
                "Artists" -> {
                    val artists = remember(allSongs) { allSongs.map { it.artist }.distinct().sorted() }
                    if (artists.isEmpty()) {
                        EmptyStateView(
                            icon = Icons.Default.Person,
                            headline = "No artists found",
                            description = "Artists will appear here automatically."
                        )
                    } else {
                        LazyColumn(contentPadding = PaddingValues(bottom = 120.dp, top = 8.dp, start = 24.dp, end = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(artists) { artist ->
                                val coverArtPath = allSongs.firstOrNull { it.artist == artist && it.coverArtPath != null }?.coverArtPath
                                Row(
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(GratiaTheme.colors.surface).clickable { onNavigateToArtist(artist) }.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (coverArtPath != null) {
                                        CoverArtImage(coverArtPath = coverArtPath, title = artist, size = 56.dp, cornerRadius = 28.dp)
                                    } else {
                                        ArtistFallback(artistName = artist, size = 56.dp)
                                    }
                                    Spacer(Modifier.width(16.dp))
                                    Text(text = artist, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = GratiaTheme.colors.textPrimary)
                                }
                            }
                        }
                    }
                }
                "Folders" -> {
                    val folders = remember(allSongs) { allSongs.mapNotNull { it.storagePath?.substringBeforeLast("/")?.substringAfterLast("/") }.distinct().filter { it.isNotBlank() }.sorted() }
                    if (folders.isEmpty()) {
                        EmptyStateView(
                            icon = Icons.Default.Folder,
                            headline = "No folders found",
                            description = "Your music folders will appear here."
                        )
                    } else {
                        LazyColumn(contentPadding = PaddingValues(bottom = 120.dp, top = 8.dp, start = 24.dp, end = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(folders) { folder ->
                                val coverArtPath = allSongs.firstOrNull { it.storagePath?.substringBeforeLast("/")?.substringAfterLast("/") == folder && it.coverArtPath != null }?.coverArtPath
                                Row(
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(GratiaTheme.colors.surface).clickable { onNavigateToFolder(folder) }.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (coverArtPath != null) {
                                        CoverArtImage(coverArtPath = coverArtPath, title = folder, size = 56.dp, cornerRadius = 8.dp)
                                    } else {
                                        FolderFallback(size = 56.dp)
                                    }
                                    Spacer(Modifier.width(16.dp))
                                    Text(text = folder, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = GratiaTheme.colors.textPrimary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
