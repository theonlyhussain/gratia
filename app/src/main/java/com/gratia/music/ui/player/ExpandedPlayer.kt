package com.gratia.music.ui.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import com.gratia.music.ui.components.SongMenuSheet
import com.gratia.music.ui.components.SongInfoDialog
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gratia.music.data.CoverColorCache
import com.gratia.music.lyrics.LyricsDocument
import com.gratia.music.lyrics.LyricsParser
import com.gratia.music.lyrics.LyricsTimingEngine
import com.gratia.music.player.PlayerViewModel
import com.gratia.music.ui.components.AnimatedText
import com.gratia.music.ui.components.GlassSurface
import com.gratia.music.ui.components.GratiaIcon
import com.gratia.music.ui.components.GratiaIconButton
import com.gratia.music.ui.components.GratiaText
import com.gratia.music.ui.theme.GratiaTheme
import com.gratia.music.ui.LocalSnackbarHostState
import kotlinx.coroutines.launch

/**
 * Full-screen cinematic expanded player.
 *
 * This is the heart of Gratia's playback experience. Everything is
 * orchestrated as one continuous, breathing composition:
 *
 * - [PlayerBackground]: animated radial gradient blobs from cover colors
 * - [ArtworkView]: hero artwork with glow, shadow, scale
 * - [PlayerHeader]: song info hierarchy with crossfade text
 * - [GratiaProgressBar]: custom thin bar with drag-only thumb
 * - [PlayerControls]: primary prev/play/next with icon morph
 * - [SecondaryActionRow]: organized secondary actions
 * - Lyrics preview card: tappable glass card
 *
 * Gestures:
 * - Swipe down → dismiss
 * - Drag progress → artwork scales, background pauses
 */
@Composable
fun ExpandedPlayer(
    playerViewModel: PlayerViewModel,
    onOpenLyrics: () -> Unit = {},
    onOpenQueue: () -> Unit = {},
    onNavigateToAlbum: (String) -> Unit = {},
    onNavigateToArtist: (String) -> Unit = {},
    onDismiss: () -> Unit = { playerViewModel.setExpandedPlayerOpen(false) }
) {
    val currentSong by playerViewModel.currentSong.collectAsState()
    val currentLyrics by playerViewModel.currentLyrics.collectAsState()
    val isPlaying by playerViewModel.isPlaying.collectAsState()
    val currentTimeMs by playerViewModel.currentTimeMs.collectAsState()
    val durationMs by playerViewModel.durationMs.collectAsState()
    val shuffleEnabled by playerViewModel.shuffleEnabled.collectAsState()
    val repeatMode by playerViewModel.repeatMode.collectAsState()

    val snackbarHostState = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()

    val song = currentSong ?: return

    val progress = if (durationMs > 0) {
        (currentTimeMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    // --- Color extraction (cached on IO thread) ---
    var coverColors by remember { mutableStateOf(CoverColorCache.FALLBACK) }
    LaunchedEffect(song.id, song.coverArtPath) {
        coverColors = CoverColorCache.getColors(song.id, song.coverArtPath)
    }

    // --- State for progress bar drag interaction ---
    var isDragging by remember { mutableStateOf(false) }
    
    // --- Menu state ---
    var showSongMenu by remember { mutableStateOf(false) }
    var showSongInfo by remember { mutableStateOf(false) }
    var showAddToPlaylist by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val motion = GratiaTheme.motion

    // --- Swipe-to-dismiss state ---
    var dismissOffsetY by remember { mutableFloatStateOf(0f) }
    val dismissAlpha by animateFloatAsState(
        targetValue = if (dismissOffsetY > 0f) {
            (1f - (dismissOffsetY / 800f)).coerceIn(0.3f, 1f)
        } else 1f,
        animationSpec = tween(motion.instant),
        label = "dismissAlpha"
    )

    // --- Synced lyrics preview ---
    val parsedLyrics = remember(currentLyrics?.text) {
        if (currentLyrics?.isSynced == true && currentLyrics?.text?.isNotBlank() == true) {
            LyricsParser.parse(currentLyrics!!.text)
        } else null
    }

    val currentLyricPreview by remember(parsedLyrics, currentTimeMs) {
        derivedStateOf {
            val doc = parsedLyrics
            if (doc != null) {
                when (doc) {
                    is LyricsDocument.LineSynced -> {
                        val idx = LyricsTimingEngine.findActiveLineIndex(doc.lines, currentTimeMs)
                        doc.lines.getOrNull(idx)?.text
                    }
                    is LyricsDocument.WordSynced -> {
                        val idx = LyricsTimingEngine.findActiveLineIndex(doc.lines, currentTimeMs)
                        doc.lines.getOrNull(idx)?.text
                    }
                    else -> null
                }
            } else null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationY = dismissOffsetY
                alpha = dismissAlpha
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (dismissOffsetY > 300f) {
                            onDismiss()
                        }
                        dismissOffsetY = 0f
                    },
                    onDragCancel = {
                        dismissOffsetY = 0f
                    },
                    onVerticalDrag = { _, dragAmount ->
                        // Only allow downward drag
                        if (dragAmount > 0 || dismissOffsetY > 0) {
                            dismissOffsetY = (dismissOffsetY + dragAmount).coerceAtLeast(0f)
                        }
                    }
                )
            }
    ) {
        // --- Background ---
        PlayerBackground(
            dominantColor = coverColors.dominant,
            darkMutedColor = coverColors.darkMuted,
            vibrantColor = coverColors.vibrant,
            isPaused = isDragging
        )

        // Dark overlay for text readability
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = 0.3f }
                .then(
                    Modifier.drawBehind {
                        drawRect(Color.Black)
                    }
                )
        )

        // --- Content ---
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = GratiaTheme.spacing.medium, vertical = GratiaTheme.spacing.small),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                GratiaIconButton(
                    icon = Icons.Default.KeyboardArrowDown,
                    onClick = onDismiss,
                    contentDescription = "Collapse",
                    tint = Color.White,
                    size = GratiaTheme.icons.large
                )

                // "PLAYING FROM" in top bar
                AnimatedText(
                    text = song.album ?: "Gratia",
                    style = GratiaTheme.typography.body.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 1
                )

                GratiaIconButton(
                    icon = Icons.Default.MoreVert,
                    onClick = { showSongMenu = true },
                    contentDescription = "More",
                    tint = Color.White.copy(alpha = 0.7f),
                    size = GratiaTheme.icons.normal
                )
            }

            Spacer(Modifier.weight(0.15f))

            // --- Hero Artwork ---
            ArtworkView(
                coverArtPath = song.coverArtPath,
                title = song.title,
                artist = song.artist,
                isPlaying = isPlaying,
                glowColor = coverColors.dominant,
                isDragging = isDragging
            )

            Spacer(Modifier.weight(0.08f))

            // --- Song Info ---
            PlayerHeader(
                title = song.title,
                artist = song.artist,
                album = song.album,
                playingFrom = (song.album ?: "GRATIA").uppercase(),
                onClickTitle = { showSongInfo = true },
                onClickArtist = {
                    onDismiss()
                    onNavigateToArtist(song.artist)
                },
                onClickAlbum = {
                    if (!song.album.isNullOrBlank()) {
                        onDismiss()
                        onNavigateToAlbum(song.album)
                    }
                }
            )

            Spacer(Modifier.height(GratiaTheme.spacing.mediumLarge)) // 16dp

            // --- Progress Bar ---
            GratiaProgressBar(
                progress = progress,
                currentTimeMs = currentTimeMs,
                durationMs = durationMs,
                onSeek = { newProgress ->
                    playerViewModel.seekTo((newProgress * durationMs).toLong())
                },
                onDragStart = { isDragging = true },
                onDragEnd = { isDragging = false }
            )

            Spacer(Modifier.height(GratiaTheme.spacing.small)) // 8dp

            // --- Primary Controls ---
            PlayerControls(
                isPlaying = isPlaying,
                onPlayPause = { playerViewModel.togglePlay() },
                onPrevious = { playerViewModel.prevSong() },
                onNext = { playerViewModel.nextSong() },
                glowColor = coverColors.vibrant
            )

            Spacer(Modifier.height(GratiaTheme.spacing.mediumLarge)) // 16dp

            // --- Secondary Actions ---
            SecondaryActionRow(
                shuffleEnabled = shuffleEnabled,
                repeatMode = repeatMode,
                isFavorite = song.isFavorite,
                onToggleShuffle = { playerViewModel.toggleShuffle() },
                onCycleRepeat = { playerViewModel.cycleRepeatMode() },
                onToggleFavorite = { 
                    playerViewModel.toggleFavorite(song)
                    val msg = if (song.isFavorite) "Removed from Liked Songs" else "Added to Liked Songs"
                    scope.launch { snackbarHostState.showSnackbar(msg) }
                },
                onOpenQueue = onOpenQueue,
                onOpenLyrics = onOpenLyrics,
                accentColor = GratiaTheme.colors.accent
            )

            Spacer(Modifier.height(GratiaTheme.spacing.mediumSmall)) // 12dp

            // --- Lyrics Preview Card ---
            LyricsPreviewCard(
                currentLyricPreview = currentLyricPreview,
                hasLyrics = currentLyrics != null,
                plainLyrics = currentLyrics?.text,
                onClick = onOpenLyrics
            )

            Spacer(Modifier.weight(0.05f))
            Spacer(Modifier.navigationBarsPadding())
        }
        
        if (showSongMenu) {
            SongMenuSheet(
                song = song,
                onDismiss = { showSongMenu = false },
                onPlayNext = { playerViewModel.playNext(song) },
                onAddToQueue = { playerViewModel.addToQueue(song) },
                onAddToPlaylist = { 
                    showSongMenu = false
                    showAddToPlaylist = true 
                },
                onToggleLike = { 
                    playerViewModel.toggleFavorite(song)
                    val msg = if (song.isFavorite) "Removed from Liked Songs" else "Added to Liked Songs"
                    scope.launch { snackbarHostState.showSnackbar(msg) }
                },
                onGoToAlbum = {
                    if (!song.album.isNullOrBlank()) {
                        onDismiss()
                        onNavigateToAlbum(song.album)
                    }
                },
                onGoToArtist = {
                    onDismiss()
                    onNavigateToArtist(song.artist)
                },
                onEditLyrics = { onOpenLyrics() },
                onShare = { /* TODO Context sharing */ },
                onSongInfo = { showSongInfo = true },
                onDelete = { 
                    showSongMenu = false
                    showDeleteConfirm = true 
                }
            )
        }
        
        if (showSongInfo) {
            SongInfoDialog(
                song = song,
                onDismiss = { showSongInfo = false }
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
                                // Delete from storage if possible
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
}

/**
 * Glass-styled lyrics preview card at the bottom of the expanded player.
 */
@Composable
private fun LyricsPreviewCard(
    currentLyricPreview: String?,
    hasLyrics: Boolean,
    plainLyrics: String?,
    onClick: () -> Unit
) {
    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = GratiaTheme.spacing.large)
            .clickable { onClick() },
        shape = GratiaTheme.shapes.large,
        backgroundColor = Color.White.copy(alpha = 0.06f),
        borderColorStart = Color.White.copy(alpha = 0.15f),
        borderColorEnd = Color.Transparent
    ) {
        Column(modifier = Modifier.padding(GratiaTheme.spacing.mediumLarge)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                GratiaText(
                    text = "LYRICS",
                    style = GratiaTheme.typography.caption.copy(
                        letterSpacing = androidx.compose.ui.unit.TextUnit(2f, androidx.compose.ui.unit.TextUnitType.Sp),
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                    ),
                    color = Color.White.copy(alpha = 0.5f)
                )
                GratiaIcon(
                    imageVector = Icons.Default.OpenInFull,
                    contentDescription = "Expand volume slider",
                    tint = Color.White.copy(alpha = 0.3f),
                    size = GratiaTheme.icons.small
                )
            }

            Spacer(Modifier.height(GratiaTheme.spacing.small))

            if (!hasLyrics) {
                GratiaText(
                    text = "No lyrics added yet",
                    style = GratiaTheme.typography.body,
                    color = Color.White.copy(alpha = 0.35f)
                )
                GratiaText(
                    text = "Tap to add lyrics",
                    style = GratiaTheme.typography.caption,
                    color = Color.White.copy(alpha = 0.2f)
                )
            } else if (currentLyricPreview != null) {
                AnimatedText(
                    text = currentLyricPreview,
                    style = GratiaTheme.typography.section.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                    color = Color.White.copy(alpha = 0.9f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fadeDurationMs = GratiaTheme.motion.slow
                )
                Spacer(Modifier.height(GratiaTheme.spacing.extraSmall))
                GratiaText(
                    text = "Tap to view full lyrics",
                    style = GratiaTheme.typography.caption,
                    color = Color.White.copy(alpha = 0.25f)
                )
            } else {
                // Show plain lyrics preview
                val previewText = plainLyrics ?: ""
                val preview = previewText.lines().take(3)
                preview.forEachIndexed { index, line ->
                    GratiaText(
                        text = line,
                        style = GratiaTheme.typography.body.copy(fontWeight = if (index == 0) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Normal),
                        color = when (index) {
                            0 -> Color.White.copy(alpha = 0.85f)
                            1 -> Color.White.copy(alpha = 0.35f)
                            else -> Color.White.copy(alpha = 0.15f)
                        }
                    )
                }
            }
        }
    }
}
