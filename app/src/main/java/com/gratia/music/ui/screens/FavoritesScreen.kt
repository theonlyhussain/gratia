package com.gratia.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gratia.music.GratiaApp
import com.gratia.music.data.repository.SongRepository
import com.gratia.music.player.PlayerViewModel
import com.gratia.music.ui.components.EmptyStateView
import com.gratia.music.ui.components.SongRow
import com.gratia.music.ui.theme.GratiaTheme
import com.gratia.music.ui.theme.Inter
import com.gratia.music.ui.theme.SpaceGrotesk

@Composable
fun FavoritesScreen(playerViewModel: PlayerViewModel) {
    val songRepo = remember { SongRepository(GratiaApp.instance.database.songDao()) }
    val favorites by songRepo.getFavorites().collectAsState(initial = emptyList())
    val currentSong by playerViewModel.currentSong.collectAsState()
    val isPlaying by playerViewModel.isPlaying.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GratiaTheme.colors.background)
    ) {
        if (favorites.isEmpty()) {
            EmptyStateView(
                icon = Icons.Default.Favorite,
                headline = "No Liked Songs",
                description = "Songs you've liked will appear here. Tap the heart icon on any song to add it.",
                actionLabel = "Explore Library",
                onActionClick = { /* Navigate to Library ideally, but we can just leave it informative */ }
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 120.dp)
            ) {
                item {
                    // Smart Playlist Header
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 40.dp, bottom = 24.dp, start = 24.dp, end = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Artwork
                        Box(
                            modifier = Modifier
                                .size(180.dp)
                                .shadow(24.dp, RoundedCornerShape(16.dp), spotColor = GratiaTheme.colors.accent)
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(GratiaTheme.colors.accent, GratiaTheme.colors.accent.copy(alpha = 0.5f))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = null,
                                modifier = Modifier.size(80.dp),
                                tint = GratiaTheme.colors.background
                            )
                        }
                        
                        Spacer(Modifier.height(24.dp))
                        
                        Text(
                            text = "Liked Songs",
                            fontFamily = SpaceGrotesk,
                            fontWeight = FontWeight.Bold,
                            fontSize = 28.sp,
                            color = GratiaTheme.colors.textPrimary
                        )
                        
                        Text(
                            text = "${favorites.size} song${if (favorites.size > 1) "s" else ""}",
                            fontFamily = Inter,
                            fontSize = 14.sp,
                            color = GratiaTheme.colors.textSecondary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        
                        Spacer(Modifier.height(24.dp))
                        
                        // Play & Shuffle Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Button(
                                onClick = { playerViewModel.playSong(favorites.first(), favorites) },
                                modifier = Modifier.weight(1f).height(50.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = GratiaTheme.colors.surface,
                                    contentColor = GratiaTheme.colors.textPrimary
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Play")
                                Spacer(Modifier.width(8.dp))
                                Text("Play", fontFamily = Inter, fontWeight = FontWeight.SemiBold)
                            }
                            
                            Button(
                                onClick = { 
                                    if (favorites.isNotEmpty()) {
                                        playerViewModel.toggleShuffle()
                                        playerViewModel.playSong(favorites.random(), favorites)
                                    }
                                },
                                modifier = Modifier.weight(1f).height(50.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = GratiaTheme.colors.surface,
                                    contentColor = GratiaTheme.colors.textPrimary
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Shuffle, contentDescription = "Shuffle")
                                Spacer(Modifier.width(8.dp))
                                Text("Shuffle", fontFamily = Inter, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                itemsIndexed(favorites, key = { _, s -> s.id }) { index, song ->
                    SongRow(
                        song = song,
                        index = index,
                        isActive = currentSong?.id == song.id,
                        isPlaying = currentSong?.id == song.id && isPlaying,
                        onClick = { playerViewModel.playSong(song, favorites) },
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            }
        }
    }
}
