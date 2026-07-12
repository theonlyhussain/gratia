package com.gratia.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gratia.music.GratiaApp
import com.gratia.music.data.model.PlaylistEntity
import com.gratia.music.ui.components.CollageArtwork
import com.gratia.music.ui.components.EmptyStateView
import com.gratia.music.ui.theme.GratiaTheme
import com.gratia.music.ui.theme.Inter
import com.gratia.music.ui.theme.SpaceGrotesk
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun PlaylistsScreen(onNavigateToPlaylist: (String) -> Unit) {
    val playlistDao = remember { GratiaApp.instance.database.playlistDao() }
    val playlists by playlistDao.getAllPlaylists().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var showCreateDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(GratiaTheme.colors.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                "Playlists",
                fontFamily = SpaceGrotesk,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                color = GratiaTheme.colors.textPrimary,
                modifier = Modifier.padding(start = 24.dp, top = 20.dp, end = 24.dp, bottom = 8.dp)
            )

            if (playlists.isEmpty()) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    EmptyStateView(
                        icon = Icons.AutoMirrored.Filled.QueueMusic,
                        headline = "No playlists yet",
                        description = "Create your first playlist to organize your favorite songs",
                        actionLabel = "Create Playlist",
                        onActionClick = { showCreateDialog = true }
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 120.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(playlists) { playlist ->
                        PlaylistRow(
                            playlist = playlist,
                            onClick = { onNavigateToPlaylist(playlist.id) }
                        )
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showCreateDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 100.dp, end = 24.dp),
            containerColor = GratiaTheme.colors.accent,
            contentColor = GratiaTheme.colors.background,
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Create Playlist")
        }

        if (showCreateDialog) {
            CreatePlaylistDialog(
                onDismiss = { showCreateDialog = false },
                onCreate = { name ->
                    scope.launch {
                        playlistDao.insertPlaylist(
                            PlaylistEntity(id = UUID.randomUUID().toString(), name = name, createdAt = System.currentTimeMillis())
                        )
                        showCreateDialog = false
                    }
                }
            )
        }
    }
}

@Composable
fun PlaylistRow(playlist: PlaylistEntity, onClick: () -> Unit) {
    val playlistDao = remember { GratiaApp.instance.database.playlistDao() }
    val songs by playlistDao.getSongsForPlaylist(playlist.id).collectAsState(initial = emptyList())
    val paths = songs.take(4).map { it.coverArtPath }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = GratiaTheme.colors.surface
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
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
                    fontWeight = FontWeight.SemiBold,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatePlaylistDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Playlist", fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, color = GratiaTheme.colors.textPrimary) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Playlist Name") },
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
                onClick = { if (text.isNotBlank()) onCreate(text) },
                colors = ButtonDefaults.textButtonColors(contentColor = GratiaTheme.colors.accent)
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = GratiaTheme.colors.textSecondary)
            ) {
                Text("Cancel")
            }
        },
        containerColor = GratiaTheme.colors.surface,
        textContentColor = GratiaTheme.colors.textSecondary
    )
}
