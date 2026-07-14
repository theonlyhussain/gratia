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
import com.gratia.music.ui.components.GratiaText
import com.gratia.music.ui.components.GratiaIcon
import com.gratia.music.ui.components.GratiaIconButton
import com.gratia.music.ui.components.GratiaButton
import com.gratia.music.ui.theme.GratiaTheme
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
            contentPadding = PaddingValues(bottom = GratiaTheme.spacing.heroLarge)
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(
                        top = 64.dp, // extra for status bar + top bar
                        bottom = GratiaTheme.spacing.large,
                        start = GratiaTheme.spacing.large,
                        end = GratiaTheme.spacing.large
                    ),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val paths = playlistSongs.take(4).map { it.coverArtPath }
                    Box(modifier = Modifier.shadow(24.dp, GratiaTheme.shapes.extraLarge, spotColor = GratiaTheme.colors.accent)) {
                        CollageArtwork(
                            paths = paths,
                            size = 200.dp,
                            cornerRadius = 24.dp
                        )
                    }
                    
                    Spacer(Modifier.height(GratiaTheme.spacing.large))
                    
                    GratiaText(
                        text = playlist.name,
                        style = GratiaTheme.typography.largeTitle,
                        color = GratiaTheme.colors.textPrimary,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                    
                    val totalDurationMs = playlistSongs.sumOf { it.durationMs }
                    val minutes = totalDurationMs / (1000 * 60)
                    GratiaText(
                        text = "${playlistSongs.size} songs • $minutes min",
                        style = GratiaTheme.typography.caption,
                        color = GratiaTheme.colors.textSecondary,
                        modifier = Modifier.fillMaxWidth().padding(top = GratiaTheme.spacing.small),
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(Modifier.height(GratiaTheme.spacing.large))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(GratiaTheme.spacing.medium)
                    ) {
                        GratiaButton(
                            text = "Play",
                            icon = Icons.Default.PlayArrow,
                            onClick = { if (playlistSongs.isNotEmpty()) playerViewModel.playSong(playlistSongs.first(), playlistSongs) },
                            modifier = Modifier.weight(1f),
                            backgroundColor = GratiaTheme.colors.surface,
                            contentColor = GratiaTheme.colors.textPrimary
                        )
                        
                        GratiaButton(
                            text = "Shuffle",
                            icon = Icons.Default.Shuffle,
                            onClick = { 
                                if (playlistSongs.isNotEmpty()) {
                                    playerViewModel.toggleShuffle()
                                    playerViewModel.playSong(playlistSongs.random(), playlistSongs)
                                }
                            },
                            modifier = Modifier.weight(1f),
                            backgroundColor = GratiaTheme.colors.surface,
                            contentColor = GratiaTheme.colors.textPrimary
                        )
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
                        modifier = Modifier.padding(horizontal = GratiaTheme.spacing.large)
                    )
                }
            }
        }

        // Header Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = GratiaTheme.spacing.medium, vertical = GratiaTheme.spacing.medium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            GratiaIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                onClick = onBack,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(GratiaTheme.colors.surface.copy(alpha = 0.8f)),
                tint = GratiaTheme.colors.textPrimary
            )
            
            Box {
                GratiaIconButton(
                    icon = Icons.Default.MoreVert,
                    contentDescription = "More Options",
                    onClick = { showMenu = true },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(GratiaTheme.colors.surface.copy(alpha = 0.8f)),
                    tint = GratiaTheme.colors.textPrimary
                )
                
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier.background(GratiaTheme.colors.surface)
                ) {
                    DropdownMenuItem(
                        text = { GratiaText("Rename", style = GratiaTheme.typography.body, color = GratiaTheme.colors.textPrimary) },
                        onClick = {
                            showMenu = false
                            showRenameDialog = true
                        }
                    )
                    DropdownMenuItem(
                        text = { GratiaText("Delete Playlist", style = GratiaTheme.typography.body, color = GratiaTheme.colors.error) },
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
            title = { GratiaText("Rename Playlist", style = GratiaTheme.typography.title, color = GratiaTheme.colors.textPrimary) },
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
            title = { GratiaText("Delete Playlist?", style = GratiaTheme.typography.title, color = GratiaTheme.colors.textPrimary) },
            text = { GratiaText("Are you sure you want to delete '${playlist.name}'? This cannot be undone.", style = GratiaTheme.typography.body, color = GratiaTheme.colors.textSecondary) },
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
