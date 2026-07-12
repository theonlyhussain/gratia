package com.gratia.music.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gratia.music.GratiaApp
import com.gratia.music.data.model.PlaylistEntity
import com.gratia.music.data.model.PlaylistSongCrossRef
import com.gratia.music.data.model.SongEntity
import com.gratia.music.ui.LocalSnackbarHostState
import com.gratia.music.ui.screens.CreatePlaylistDialog
import com.gratia.music.ui.theme.GratiaTheme
import com.gratia.music.ui.theme.Inter
import com.gratia.music.ui.theme.SpaceGrotesk
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPlaylistSheet(
    song: SongEntity,
    onDismiss: () -> Unit
) {
    val playlistDao = remember { GratiaApp.instance.database.playlistDao() }
    val playlists by playlistDao.getAllPlaylists().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val snackbarHostState = LocalSnackbarHostState.current
    
    var showCreateDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = GratiaTheme.colors.surface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = GratiaTheme.colors.glassBorder) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Add to Playlist",
                fontFamily = SpaceGrotesk,
                fontSize = 20.sp,
                color = GratiaTheme.colors.textPrimary,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            
            Spacer(Modifier.height(8.dp))

            // Create New Playlist Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showCreateDialog = true }
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = GratiaTheme.colors.surfaceHover
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "New Playlist",
                            tint = GratiaTheme.colors.accent
                        )
                    }
                }
                Spacer(Modifier.width(16.dp))
                Text(
                    text = "New Playlist",
                    fontFamily = Inter,
                    fontSize = 16.sp,
                    color = GratiaTheme.colors.textPrimary
                )
            }
            
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                color = GratiaTheme.colors.glassBorder
            )

            // Existing Playlists
            LazyColumn {
                items(playlists) { playlist ->
                    val songs by playlistDao.getSongsForPlaylist(playlist.id).collectAsState(initial = emptyList())
                    val paths = songs.take(4).map { it.coverArtPath }
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                scope.launch {
                                    val count = songs.size
                                    playlistDao.addSongToPlaylist(
                                        PlaylistSongCrossRef(
                                            playlistId = playlist.id,
                                            songId = song.id,
                                            addedAt = System.currentTimeMillis(),
                                            sortOrder = count
                                        )
                                    )
                                    // Update timestamp
                                    playlistDao.insertPlaylist(playlist.copy(updatedAt = System.currentTimeMillis()))
                                    
                                    snackbarHostState.showSnackbar("Added to ${playlist.name}")
                                    onDismiss()
                                }
                            }
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CollageArtwork(
                            paths = paths,
                            size = 56.dp,
                            cornerRadius = 12.dp
                        )
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(
                                text = playlist.name,
                                fontFamily = Inter,
                                fontSize = 16.sp,
                                color = GratiaTheme.colors.textPrimary
                            )
                            Text(
                                text = "${songs.size} songs",
                                fontFamily = Inter,
                                fontSize = 13.sp,
                                color = GratiaTheme.colors.textSecondary
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name ->
                scope.launch {
                    val newId = UUID.randomUUID().toString()
                    val now = System.currentTimeMillis()
                    val newPlaylist = PlaylistEntity(id = newId, name = name, createdAt = now, updatedAt = now)
                    playlistDao.insertPlaylist(newPlaylist)
                    
                    // Add song immediately to new playlist
                    playlistDao.addSongToPlaylist(
                        PlaylistSongCrossRef(
                            playlistId = newId,
                            songId = song.id,
                            addedAt = now,
                            sortOrder = 0
                        )
                    )
                    
                    showCreateDialog = false
                    snackbarHostState.showSnackbar("Added to $name")
                    onDismiss()
                }
            }
        )
    }
}
