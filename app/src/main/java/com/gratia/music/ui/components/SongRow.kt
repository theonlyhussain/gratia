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
    val firstMood = song.mood?.split(",")?.firstOrNull()?.trim()
    var showMenu by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    var showAddToPlaylist by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

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
        Box(modifier = Modifier
            .clickable { playerViewModel.setExpandedPlayerOpen(true) }
        ) {
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
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable { navController.navigate("artist/${song.artist}") }
                )
                if (song.album != null) {
                    Text(" · ", fontSize = 11.sp, color = GratiaTheme.colors.textSecondary)
                    Text(
                        " • ${song.album}",
                        style = GratiaTheme.typography.caption,
                        color = GratiaTheme.colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable { navController.navigate("album/${song.album}") }
                    )
                }
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

        // Duration
        Text(
            formatTime(song.durationMs),
            style = GratiaTheme.typography.caption,
            color = GratiaTheme.colors.textSecondary
        )
        
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
        SongMenuSheet(
            song = song,
            onDismiss = { showMenu = false },
            onPlayNext = { playerViewModel.playNext(song) },
            onAddToQueue = { playerViewModel.addToQueue(song) },
            onAddToPlaylist = { 
                showMenu = false
                showAddToPlaylist = true 
            },
            onToggleLike = { playerViewModel.toggleFavorite(song) },
            onGoToAlbum = { 
                if (!song.album.isNullOrBlank()) navController.navigate("album/${song.album}")
            },
            onGoToArtist = { navController.navigate("artist/${song.artist}") },
            onEditLyrics = {
                navController.navigate("fullLyrics/${song.id}")
            },
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
}


