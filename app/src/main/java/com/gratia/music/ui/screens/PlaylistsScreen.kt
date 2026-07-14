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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gratia.music.GratiaApp
import com.gratia.music.data.model.PlaylistEntity
import com.gratia.music.ui.components.CollageArtwork
import com.gratia.music.ui.components.EmptyStateView
import com.gratia.music.ui.components.GratiaText
import com.gratia.music.ui.components.GratiaIcon
import com.gratia.music.ui.components.GratiaCard
import com.gratia.music.ui.components.GratiaCardStatic
import com.gratia.music.ui.components.clickableWithScale
import com.gratia.music.ui.theme.GratiaTheme
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
            GratiaText(
                text = "Playlists",
                style = GratiaTheme.typography.largeTitle,
                color = GratiaTheme.colors.textPrimary,
                modifier = Modifier.padding(
                    start = GratiaTheme.spacing.large,
                    top = GratiaTheme.spacing.mediumLarge,
                    end = GratiaTheme.spacing.large,
                    bottom = GratiaTheme.spacing.small
                )
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
                    contentPadding = PaddingValues(
                        start = GratiaTheme.spacing.large,
                        end = GratiaTheme.spacing.large,
                        top = GratiaTheme.spacing.medium,
                        bottom = GratiaTheme.spacing.heroLarge
                    ),
                    verticalArrangement = Arrangement.spacedBy(GratiaTheme.spacing.mediumSmall)
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

        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 100.dp, end = GratiaTheme.spacing.large)
                .size(56.dp)
                .clip(GratiaTheme.shapes.extraLarge)
                .background(GratiaTheme.colors.accent)
                .clickableWithScale(onClick = { showCreateDialog = true }),
            contentAlignment = Alignment.Center
        ) {
            GratiaIcon(
                imageVector = Icons.Default.Add,
                contentDescription = "Create Playlist",
                tint = GratiaTheme.colors.background
            )
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

    GratiaCardStatic(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .clickableWithScale(onClick = onClick)
                .padding(GratiaTheme.spacing.mediumSmall),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CollageArtwork(
                paths = paths,
                size = 56.dp,
                cornerRadius = 12.dp
            )
            Spacer(Modifier.width(GratiaTheme.spacing.mediumLarge))
            Column {
                GratiaText(
                    text = playlist.name,
                    style = GratiaTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
                    color = GratiaTheme.colors.textPrimary
                )
                GratiaText(
                    text = "${songs.size} songs",
                    style = GratiaTheme.typography.caption,
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
        title = {
            GratiaText(
                text = "New Playlist",
                style = GratiaTheme.typography.title,
                color = GratiaTheme.colors.textPrimary
            )
        },
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
