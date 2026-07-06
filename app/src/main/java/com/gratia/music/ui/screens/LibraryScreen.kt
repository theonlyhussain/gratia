package com.gratia.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
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
import com.gratia.music.ui.theme.GratiaTheme
import com.gratia.music.ui.theme.Inter
import com.gratia.music.ui.theme.SpaceGrotesk

@Composable
fun LibraryScreen(playerViewModel: PlayerViewModel) {
    val songRepo = remember { SongRepository(GratiaApp.instance.database.songDao()) }
    val allSongs by songRepo.getAllSongs().collectAsState(initial = emptyList())
    
    val tabs = listOf("Songs", "Albums", "Artists", "Folders")
    var selectedTab by remember { mutableStateOf(tabs[0]) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GratiaTheme.colors.background)
            .statusBarsPadding()
    ) {
        Text(
            text = "Your Library",
            fontFamily = SpaceGrotesk,
            fontWeight = FontWeight.Bold,
            fontSize = 28.sp,
            color = GratiaTheme.colors.textPrimary,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp)
        )

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
                    if (allSongs.isEmpty()) {
                        EmptyLibraryMessage()
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(bottom = 120.dp, top = 8.dp)
                        ) {
                            item { SongList(allSongs, playerViewModel) }
                        }
                    }
                }
                "Albums" -> {
                    // TODO: Group by Album
                    EmptyLibraryMessage("Albums coming soon")
                }
                "Artists" -> {
                    // TODO: Group by Artist
                    EmptyLibraryMessage("Artists coming soon")
                }
                "Folders" -> {
                    // TODO: Group by Folder
                    EmptyLibraryMessage("Folders coming soon")
                }
            }
        }
    }
}

@Composable
fun EmptyLibraryMessage(message: String = "Your library is empty\nSync songs from settings.") {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            fontFamily = Inter,
            color = GratiaTheme.colors.textSecondary,
            fontSize = 16.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}
