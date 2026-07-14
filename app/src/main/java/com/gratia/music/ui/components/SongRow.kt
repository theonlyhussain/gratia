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
import com.gratia.music.ui.theme.Inter
import com.gratia.music.ui.theme.SpaceGrotesk
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

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .then(
                if (isActive) Modifier.background(GratiaTheme.colors.glassBorder)
                else Modifier
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = { showMenu = true }
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Track number or playing bars
        Box(modifier = Modifier.width(24.dp), contentAlignment = Alignment.Center) {
            if (isPlaying) {
                PlayingBars()
            } else {
                Text(
                    "${index + 1}",
                    fontFamily = Inter,
                    fontSize = 11.sp,
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
                size = 40.dp,
                cornerRadius = 6.dp,
                fontSize = 10.sp
            )
        }

        Spacer(Modifier.width(12.dp))

        // Title + Artist
        Column(modifier = Modifier.weight(1f)) {
            Text(
                song.title,
                fontFamily = Inter,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                color = if (isActive) GratiaTheme.colors.accent else GratiaTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row {
                Text(
                    song.artist,
                    fontFamily = Inter,
                    fontSize = 11.sp,
                    color = GratiaTheme.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable { navController.navigate("artist/${song.artist}") }
                )
                if (song.album != null) {
                    Text(" · ", fontSize = 11.sp, color = GratiaTheme.colors.textSecondary)
                    Text(
                        song.album!!,
                        fontFamily = Inter,
                        fontSize = 11.sp,
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
                    fontFamily = Inter,
                    fontSize = 9.sp,
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
                    fontFamily = Inter,
                    fontSize = 9.sp,
                    color = GratiaTheme.colors.textSecondary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
            Spacer(Modifier.width(8.dp))
        }

        // Duration
        Text(
            formatTime(song.durationMs),
            fontFamily = Inter,
            fontSize = 11.sp,
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
            onShare = {
                showMenu = false
                val sendIntent: android.content.Intent = android.content.Intent().apply {
                    action = android.content.Intent.ACTION_SEND
                    putExtra(android.content.Intent.EXTRA_TEXT, "Check out this song: ${song.title} by ${song.artist}")
                    type = "text/plain"
                }
                val shareIntent = android.content.Intent.createChooser(sendIntent, null)
                context.startActivity(shareIntent)
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

/**
 * Animated playing bars (equalizer animation).
 */
@Composable
fun PlayingBars() {
    val infiniteTransition = rememberInfiniteTransition(label = "playingBars")

    val bar1Height by infiniteTransition.animateFloat(
        initialValue = 4f, targetValue = 14f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ), label = "bar1"
    )
    val bar2Height by infiniteTransition.animateFloat(
        initialValue = 8f, targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = EaseInOut, delayMillis = 150),
            repeatMode = RepeatMode.Reverse
        ), label = "bar2"
    )
    val bar3Height by infiniteTransition.animateFloat(
        initialValue = 6f, targetValue = 12f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = EaseInOut, delayMillis = 300),
            repeatMode = RepeatMode.Reverse
        ), label = "bar3"
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier.height(14.dp)
    ) {
        listOf(bar1Height, bar2Height, bar3Height).forEach { height ->
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(height.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(GratiaTheme.colors.accent)
            )
        }
    }
}

private val EaseInOut = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)
