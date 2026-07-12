package com.gratia.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gratia.music.GratiaApp
import com.gratia.music.data.model.PlaylistEntity
import com.gratia.music.data.model.SongEntity
import com.gratia.music.player.PlayerViewModel
import com.gratia.music.ui.components.CollageArtwork
import com.gratia.music.ui.components.EmptyStateView
import com.gratia.music.ui.components.SongRow
import com.gratia.music.ui.theme.GratiaTheme
import com.gratia.music.ui.theme.Inter
import com.gratia.music.ui.theme.SpaceGrotesk
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    playlistId: String,
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit
) {
    val playlistDao = remember { GratiaApp.instance.database.playlistDao() }
    val playlistFlow by playlistDao.getPlaylist(playlistId).collectAsState(initial = null)
    val playlistSongs by playlistDao.getSongsForPlaylist(playlistId).collectAsState(initial = emptyList())
    
    val currentSong by playerViewModel.currentSong.collectAsState()
    val isPlaying by playerViewModel.isPlaying.collectAsState()
    val scope = rememberCoroutineScope()

    var showMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val playlist = playlistFlow ?: return

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(GratiaTheme.colors.background)
    ) {
        LazyColumn(
            contentPadding = PaddingValues(bottom = 120.dp)
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 64.dp, bottom = 24.dp, start = 24.dp, end = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val paths = playlistSongs.take(4).map { it.coverArtPath }
                    Box(modifier = Modifier.shadow(24.dp, RoundedCornerShape(16.dp), spotColor = GratiaTheme.colors.accent)) {
                        CollageArtwork(
                            paths = paths,
                            size = 200.dp,
                            cornerRadius = 16.dp
                        )
                    }
                    
                    Spacer(Modifier.height(24.dp))
                    
                    Text(
                        text = playlist.name,
                        fontFamily = SpaceGrotesk,
                        fontWeight = FontWeight.Bold,
                        fontSize = 28.sp,
                        color = GratiaTheme.colors.textPrimary,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                    
                    val totalDurationMs = playlistSongs.sumOf { it.durationMs }
                    val minutes = totalDurationMs / (1000 * 60)
                    Text(
                        text = "${playlistSongs.size} songs • $minutes min",
                        fontFamily = Inter,
                        fontSize = 14.sp,
                        color = GratiaTheme.colors.textSecondary,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(Modifier.height(24.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Button(
                            onClick = { if (playlistSongs.isNotEmpty()) playerViewModel.playSong(playlistSongs.first(), playlistSongs) },
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
                                if (playlistSongs.isNotEmpty()) {
                                    playerViewModel.toggleShuffle()
                                    playerViewModel.playSong(playlistSongs.random(), playlistSongs)
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

            if (playlistSongs.isEmpty()) {
                item {
                    EmptyStateView(
                        icon = Icons.Default.QueueMusic,
                        headline = "It's a bit empty here",
                        description = "Add some songs from your library to this playlist."
                    )
                }
            } else {
                itemsIndexed(playlistSongs, key = { _, s -> s.id }) { index, song ->
                    SongRow(
                        song = song,
                        index = index,
                        isActive = currentSong?.id == song.id,
                        isPlaying = currentSong?.id == song.id && isPlaying,
                        onClick = { playerViewModel.playSong(song, playlistSongs) },
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            }
        }

        // Header Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(GratiaTheme.colors.surface.copy(alpha = 0.8f))
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = GratiaTheme.colors.textPrimary)
            }
            
            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(GratiaTheme.colors.surface.copy(alpha = 0.8f))
                ) {
                    Icon(Icons.Default.MoreVert, "More Options", tint = GratiaTheme.colors.textPrimary)
                }
                
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier.background(GratiaTheme.colors.surface)
                ) {
                    DropdownMenuItem(
                        text = { Text("Rename", color = GratiaTheme.colors.textPrimary, fontFamily = Inter) },
                        onClick = {
                            showMenu = false
                            showRenameDialog = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete Playlist", color = GratiaTheme.colors.error, fontFamily = Inter) },
                        onClick = {
                            showMenu = false
                            showDeleteConfirm = true
                        }
                    )
                }
            }
        }
    }

    if (showRenameDialog) {
        var newName by remember { mutableStateOf(playlist.name) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Playlist", fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, color = GratiaTheme.colors.textPrimary) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GratiaTheme.colors.accent,
                        unfocusedBorderColor = GratiaTheme.colors.glassBorder,
                        cursorColor = GratiaTheme.colors.accent
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newName.isNotBlank()) {
                            scope.launch {
                                playlistDao.insertPlaylist(playlist.copy(name = newName))
                                showRenameDialog = false
                            }
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = GratiaTheme.colors.accent)
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showRenameDialog = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = GratiaTheme.colors.textSecondary)
                ) {
                    Text("Cancel")
                }
            },
            containerColor = GratiaTheme.colors.surface,
            textContentColor = GratiaTheme.colors.textSecondary
        )
    }
    
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Playlist?", fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, color = GratiaTheme.colors.textPrimary) },
            text = { Text("Are you sure you want to delete '${playlist.name}'? This cannot be undone.", fontFamily = Inter, color = GratiaTheme.colors.textSecondary) },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            playlistDao.deletePlaylist(playlist)
                            showDeleteConfirm = false
                            onBack()
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = GratiaTheme.colors.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteConfirm = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = GratiaTheme.colors.textSecondary)
                ) {
                    Text("Cancel")
                }
            },
            containerColor = GratiaTheme.colors.surface
        )
    }
}
