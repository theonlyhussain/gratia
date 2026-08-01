package com.gratia.music.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gratia.music.GratiaApp
import com.gratia.music.data.model.PlaylistSongCrossRef
import com.gratia.music.ui.LocalSnackbarHostState
import com.gratia.music.ui.theme.GratiaTheme
import com.gratia.music.ui.theme.Inter
import com.gratia.music.ui.theme.SpaceGrotesk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMusicSheet(
    playlistId: String,
    onDismiss: () -> Unit
) {
    val songDao = remember { GratiaApp.instance.database.songDao() }
    val playlistDao = remember { GratiaApp.instance.database.playlistDao() }
    val allSongs by songDao.getAllSongs().collectAsState(initial = emptyList())
    val playlistSongs by playlistDao.getSongsForPlaylist(playlistId).collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val snackbarHostState = LocalSnackbarHostState.current
    
    // Set of currently selected IDs
    val selectedIds = remember { mutableStateListOf<String>() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = GratiaTheme.colors.surface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = GratiaTheme.colors.glassBorder) },
        modifier = Modifier.fillMaxHeight(0.9f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Add Music",
                    fontFamily = SpaceGrotesk,
                    fontSize = 20.sp,
                    color = GratiaTheme.colors.textPrimary
                )
                Text(
                    text = "Done",
                    fontFamily = Inter,
                    fontSize = 16.sp,
                    color = GratiaTheme.colors.accent,
                    modifier = Modifier.clickable {
                        scope.launch {
                            val currentCount = playlistSongs.size
                            selectedIds.forEachIndexed { index, songId ->
                                playlistDao.addSongToPlaylist(
                                    PlaylistSongCrossRef(
                                        playlistId = playlistId,
                                        songId = songId,
                                        addedAt = System.currentTimeMillis(),
                                        sortOrder = currentCount + index
                                    )
                                )
                            }
                            val playlist = playlistDao.getPlaylist(playlistId).first()
                            if (playlist != null) {
                                playlistDao.insertPlaylist(playlist.copy(updatedAt = System.currentTimeMillis()))
                            }
                            if (selectedIds.isNotEmpty()) {
                                snackbarHostState.showSnackbar("Added ${selectedIds.size} songs to playlist")
                            }
                            onDismiss()
                        }
                    }.padding(8.dp)
                )
            }
            
            HorizontalDivider(color = GratiaTheme.colors.glassBorder)

            // Available songs (excluding already added)
            val availableSongs = allSongs.filter { s -> playlistSongs.none { it.id == s.id } }
            
            if (availableSongs.isEmpty()) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text("No more songs available to add.", color = GratiaTheme.colors.textSecondary, fontFamily = Inter)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(availableSongs, key = { it.id }) { song ->
                        val isSelected = selectedIds.contains(song.id)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (isSelected) selectedIds.remove(song.id)
                                    else selectedIds.add(song.id)
                                }
                                .padding(horizontal = 24.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CoverArtImage(
                                coverArtPath = song.coverArtPath,
                                title = song.title,
                                artist = song.artist,
                                size = 48.dp,
                                cornerRadius = 8.dp
                            )
                            Spacer(Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = song.title,
                                    fontFamily = Inter,
                                    fontSize = 16.sp,
                                    color = GratiaTheme.colors.textPrimary,
                                    maxLines = 1
                                )
                                Text(
                                    text = song.artist,
                                    fontFamily = Inter,
                                    fontSize = 13.sp,
                                    color = GratiaTheme.colors.textSecondary,
                                    maxLines = 1
                                )
                            }
                            // Checkbox/Selection indicator
                            if (isSelected) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = GratiaTheme.colors.accent
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
