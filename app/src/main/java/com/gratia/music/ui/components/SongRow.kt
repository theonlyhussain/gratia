package com.gratia.music.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gratia.music.data.model.SongEntity
import com.gratia.music.data.model.hasRemoteIdentity
import com.gratia.music.ui.LocalNavController
import com.gratia.music.ui.LocalPlayerViewModel
import com.gratia.music.ui.components.SongMenuSheet
import com.gratia.music.ui.components.SongInfoDialog
import com.gratia.music.ui.theme.GratiaTheme
import com.gratia.music.ui.player.formatTime
import kotlinx.coroutines.launch

/**
 * Library song row. Translates SongRow.tsx from the web prototype.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongRow(
    song: SongEntity,
    index: Int,
    isActive: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onMoreClick: (() -> Unit)? = null,
    badge: String? = null,
    modifier: Modifier = Modifier
) {
    val playerViewModel = LocalPlayerViewModel.current
    val favoriteSongIds by playerViewModel.favoriteSongIds.collectAsState()
    val isFavorite = favoriteSongIds.contains(song.id)
    
    val firstMood = song.mood?.split(",")?.firstOrNull()?.trim()
    var showMenu by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    var showAddToPlaylist by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showMultipleArtistSelector by remember { mutableStateOf(false) }
    var multipleArtistsList by remember { mutableStateOf<List<String>>(emptyList()) }

    val navController = com.gratia.music.ui.LocalNavController.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val snackbarHostState = com.gratia.music.ui.LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val haptics = GratiaTheme.haptics
    val view = androidx.compose.ui.platform.LocalView.current

    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1.0f,
        animationSpec = androidx.compose.animation.core.spring(dampingRatio = 1.0f, stiffness = 400f),
        label = "rowScale"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(8.dp))
            .then(
                if (isActive) Modifier.background(GratiaTheme.colors.glassBorder)
                else Modifier
            )
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    haptics.light(view)
                    onClick()
                },
                onLongClick = { 
                    haptics.heavy(view)
                    onLongClick?.invoke() ?: run { showMenu = true } 
                }
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Track number or playing bars
        Box(modifier = Modifier.width(24.dp), contentAlignment = Alignment.Center) {
            if (isPlaying) {
                PlayingIndicator(isPaused = false)
            } else {
                Text(
                    "${index + 1}",
                    style = GratiaTheme.typography.caption,
                    color = if (isActive) GratiaTheme.colors.accent else GratiaTheme.colors.textSecondary
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        // Cover art image
        Box {
            CoverArtImage(
                coverArtPath = song.coverArtPath,
                title = song.title,
                artist = song.artist,
                size = 48.dp,
                cornerRadius = 4.dp,
                fontSize = 12.sp
            )
        }

        Spacer(Modifier.width(12.dp))

        // Title + Artist
        Column(modifier = Modifier.weight(1f)) {
            Text(
                song.title,
                style = GratiaTheme.typography.body,
                fontWeight = FontWeight.Medium,
                color = if (isActive) GratiaTheme.colors.accent else GratiaTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row {
                Text(
                    song.artist,
                    style = GratiaTheme.typography.caption,
                    color = GratiaTheme.colors.textSecondary,
                    maxLines = 1,
                    modifier = Modifier.clickable {
                        // A remote track has a catalogue artist id to route on;
                        // a local file's artist is metadata, so it opens the
                        // local artist page and never the remote one.
                        val remoteArtistId = song.providerArtistId
                            ?.takeIf { song.hasRemoteIdentity() && it.isNotBlank() }
                        if (remoteArtistId != null) {
                            navController.navigate(
                                "remote_artist/${android.net.Uri.encode(remoteArtistId)}"
                            )
                        } else {
                            val parsed = com.gratia.music.utils.ArtistParser.parseArtists(song.artist)
                            if (parsed.size > 1) {
                                multipleArtistsList = parsed
                                showMultipleArtistSelector = true
                            } else {
                                navController.navigate("artist?name=${android.net.Uri.encode(parsed.firstOrNull() ?: song.artist)}")
                            }
                        }
                    }
                )
            }
            // Badge (e.g., "Lyrics match")
            if (badge != null) {
                Text(
                    badge,
                    style = GratiaTheme.typography.caption,
                    color = GratiaTheme.colors.accent,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Mood pill
        if (firstMood != null) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = androidx.compose.ui.graphics.Color.Transparent,
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = androidx.compose.ui.graphics.SolidColor(GratiaTheme.colors.surfaceHover)
                )
            ) {
                Text(
                    firstMood,
                    style = GratiaTheme.typography.caption,
                    color = GratiaTheme.colors.textSecondary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
            Spacer(Modifier.width(8.dp))
        }

        // Duration. A remote row that never stated one is *unknown*, not zero:
        // printing 0:00 claimed every such track was empty. Nothing is drawn
        // until a real length is known.
        if (song.durationMs > 0) {
            Text(
                formatTime(song.durationMs),
                style = GratiaTheme.typography.caption,
                color = GratiaTheme.colors.textSecondary
            )
        }
        
        if (onMoreClick != null) {
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = { showMenu = true },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = "More options",
                    tint = GratiaTheme.colors.textSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }

    if (showMenu) {
        // Source-aware targets: a remote track has catalogue ids to route on, a
        // local file does not — and an action that cannot be honoured is passed
        // as null so its tile is not drawn.
        val remoteAlbumId = song.providerAlbumId?.takeIf { song.hasRemoteIdentity() && it.isNotBlank() }
        val remoteArtistId = song.providerArtistId?.takeIf { song.hasRemoteIdentity() && it.isNotBlank() }
        SongMenuSheet(
            song = song,
            isFavorite = isFavorite,
            onDismiss = { showMenu = false },
            onPlayNext = { playerViewModel.playNext(song) },
            onAddToQueue = { playerViewModel.addToQueue(song) },
            onAddToPlaylist = { 
                showMenu = false
                showAddToPlaylist = true 
            },
            // The download tile must actually download — a row's menu offering
            // a "Download" button that does nothing is worse than not offering
            // it. State comes from the app-wide ledger, so a row reflects a
            // download started anywhere else.
            downloadState = playerViewModel.downloadStateFor(song),
            onDownload = { playerViewModel.downloadSong(song) },
            onRemoveDownload = { playerViewModel.removeDownload(song) },
            onToggleLike = { playerViewModel.toggleFavorite(song) },
            // Source-aware routing: a remote track opens the remote page keyed
            // by its provider id; a local file's album/artist is metadata, not
            // a catalogue route.
            onGoToAlbum = remoteAlbumId?.let { id ->
                {
                    // The row knows the album's display name; carrying it in
                    // the route lets the destination page show the title
                    // immediately while its details load.
                    val title = android.net.Uri.encode(song.album ?: song.title)
                    navController.navigate("remote_album/${android.net.Uri.encode(id)}?title=$title")
                }
            },
            onGoToArtist = when {
                remoteArtistId != null -> {
                    { navController.navigate("remote_artist/${android.net.Uri.encode(remoteArtistId)}") }
                }
                song.hasRemoteIdentity() -> {
                    {
                        val parsed = com.gratia.music.utils.ArtistParser.parseArtists(song.artist)
                        if (parsed.size > 1) {
                            showMenu = false
                            multipleArtistsList = parsed
                            showMultipleArtistSelector = true
                        }
                    }
                }
                else -> null
            },
            // There is no standalone lyrics-editor destination for a list row;
            // the editor lives in the player. Offering a tile that navigates to
            // an unregistered route crashes, so it is simply not offered here.
            onEditLyrics = null,
            onSongInfo = { showInfo = true },
            onDelete = { 
                showMenu = false
                showDeleteConfirm = true 
            }
        )
    }

    if (showInfo) {
        SongInfoDialog(
            song = song,
            onDismiss = { showInfo = false }
        )
    }

    if (showAddToPlaylist) {
        com.gratia.music.ui.components.AddToPlaylistSheet(
            song = song,
            onDismiss = { showAddToPlaylist = false }
        )
    }

    if (showDeleteConfirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { 
                androidx.compose.material3.Text(
                    text = "Delete Song", 
                    fontFamily = com.gratia.music.ui.theme.SpaceGrotesk, 
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, 
                    color = GratiaTheme.colors.textPrimary
                ) 
            },
            text = { 
                androidx.compose.material3.Text(
                    text = "Are you sure you want to delete '${song.title}' from your library?", 
                    fontFamily = com.gratia.music.ui.theme.Inter, 
                    color = GratiaTheme.colors.textSecondary
                ) 
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        playerViewModel.deleteSong(song) {
                            try {
                                val uri = android.net.Uri.parse(song.localUri)
                                val file = java.io.File(uri.path ?: "")
                                if (file.exists()) file.delete()
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                        scope.launch {
                            val result = snackbarHostState.showSnackbar(
                                message = "Song deleted",
                                actionLabel = "Undo",
                                duration = androidx.compose.material3.SnackbarDuration.Short
                            )
                            if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                                playerViewModel.restoreSong(song)
                            }
                        }
                    },
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = GratiaTheme.colors.error)
                ) {
                    androidx.compose.material3.Text("Delete", fontFamily = com.gratia.music.ui.theme.Inter, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = { showDeleteConfirm = false },
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = GratiaTheme.colors.textSecondary)
                ) {
                    androidx.compose.material3.Text("Cancel", fontFamily = com.gratia.music.ui.theme.Inter)
                }
            },
            containerColor = GratiaTheme.colors.surface
        )
    }

    if (showMultipleArtistSelector) {
        MultipleArtistSelectorSheet(
            artists = multipleArtistsList,
            onArtistClick = { artistName ->
                showMultipleArtistSelector = false
                navController.navigate("artist?name=${android.net.Uri.encode(artistName)}")
            },
            onDismissRequest = { showMultipleArtistSelector = false }
        )
    }
}


