package com.gratia.music.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import com.gratia.music.ui.components.SongMenuSheet
import com.gratia.music.ui.components.SongInfoDialog
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gratia.music.data.CoverColorCache
import com.gratia.music.lyrics.LyricsDocument
import com.gratia.music.lyrics.LyricsParser

import com.gratia.music.player.PlayerViewModel
import com.gratia.music.ui.components.AnimatedText
import com.gratia.music.ui.components.GratiaText
import com.gratia.music.ui.theme.GratiaTheme
import com.gratia.music.ui.theme.SpaceGrotesk
import com.gratia.music.ui.LocalSnackbarHostState
import kotlinx.coroutines.launch

/**
 * Full-screen cinematic expanded player.
 *
 * Layout (top → bottom):
 * 1. Album art — full-width, edge-to-edge with gradient fade
 * 2. Song title + artist + favorite star + three-dots
 * 3. Progress/seek bar
 * 4. Play/Pause/Skip controls
 * 5. Bottom bar: Lyrics + Queue (2 buttons only)
 *
 * Lyrics mode: tapping Lyrics toggles an overlay that replaces the artwork area
 * with scrolling synced lyrics. Tap any line to seek.
 */
@Composable
fun ExpandedPlayer(
    playerViewModel: PlayerViewModel,
    onOpenLyrics: () -> Unit = {},
    onOpenQueue: () -> Unit = {},
    onOpenSleepTimer: () -> Unit = {},
    onNavigateToAlbum: (String) -> Unit = {},
    onNavigateToArtist: (String) -> Unit = {},
    onDismiss: () -> Unit = { playerViewModel.setExpandedPlayerOpen(false) }
) {
    val currentSong by playerViewModel.currentSong.collectAsState()
    val currentLyrics by playerViewModel.currentLyrics.collectAsState()
    val isPlaying by playerViewModel.isPlaying.collectAsState()
    val currentTimeMs by playerViewModel.currentTimeMs.collectAsState()
    val durationMs by playerViewModel.durationMs.collectAsState()

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

    // --- Lyrics overlay state ---
    var showLyricsOverlay by remember { mutableStateOf(false) }

    val motion = GratiaTheme.motion

    // --- Swipe-to-dismiss state ---
    val dismissOffsetY = remember { androidx.compose.animation.core.Animatable(0f) }
    val dismissAlpha by animateFloatAsState(
        targetValue = if (dismissOffsetY.value > 0f) {
            (1f - (dismissOffsetY.value / 800f)).coerceIn(0.3f, 1f)
        } else 1f,
        animationSpec = tween(motion.instant),
        label = "dismissAlpha"
    )

    // --- Synced lyrics parsing ---
    val parsedLyrics = remember(currentLyrics?.text) {
        if (currentLyrics?.isSynced == true && currentLyrics?.text?.isNotBlank() == true) {
            LyricsParser.parse(currentLyrics!!.text)
        } else null
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationY = dismissOffsetY.value
                alpha = dismissAlpha
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        scope.launch {
                            if (dismissOffsetY.value > 300f) {
                                onDismiss()
                                dismissOffsetY.snapTo(0f)
                            } else {
                                dismissOffsetY.animateTo(
                                    targetValue = 0f,
                                    animationSpec = motion.springStiff()
                                )
                            }
                        }
                    },
                    onDragCancel = {
                        scope.launch {
                            dismissOffsetY.animateTo(
                                targetValue = 0f,
                                animationSpec = motion.springStiff()
                            )
                        }
                    },
                    onVerticalDrag = { _, dragAmount ->
                        scope.launch {
                            if (dragAmount > 0 || dismissOffsetY.value > 0) {
                                val newValue = (dismissOffsetY.value + dragAmount).coerceAtLeast(0f)
                                dismissOffsetY.snapTo(newValue)
                            }
                        }
                    }
                )
            }
    ) {
        // --- Background ---
        PlayerBackground(
            coverArtPath = song.coverArtPath,
            dominantColor = coverColors.dominant
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
            // --- Hero Artwork / Lyrics Overlay area ---
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f) // Takes remaining space above controls
            ) {
                androidx.compose.animation.Crossfade(
                    targetState = showLyricsOverlay,
                    label = "lyricsOverlayCrossfade",
                    animationSpec = tween(300)
                ) { isLyricsOverlayOpen ->
                    if (!isLyricsOverlayOpen) {
                    Column {
                        Spacer(Modifier.statusBarsPadding())
                        ArtworkView(
                            coverArtPath = song.coverArtPath,
                            title = song.title,
                            artist = song.artist,
                            isPlaying = isPlaying,
                            glowColor = coverColors.dominant,
                            isDragging = isDragging
                        )
                    }
                    } else {
                        // Lyrics overlay — visible when lyrics button is active
                        Column {
                            Spacer(Modifier.statusBarsPadding())
                            Spacer(Modifier.height(16.dp))
                            InlinePlayerLyrics(
                                parsedLyrics = parsedLyrics,
                                currentTimeMs = currentTimeMs,
                                onSeekToLine = { timeMs -> playerViewModel.seekTo(timeMs) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .padding(horizontal = 24.dp)
                            )
                        }
                    }
                }
            }

            // --- Song Info + Favorite + Menu ---
            PlayerHeader(
                title = song.title,
                artist = song.artist,
                album = song.album,
                isFavorite = song.isFavorite,
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
                },
                onToggleFavorite = {
                    playerViewModel.toggleFavorite(song)
                    val msg = if (song.isFavorite) "Removed from Liked Songs" else "Added to Liked Songs"
                    scope.launch { snackbarHostState.showSnackbar(msg) }
                },
                onMoreClick = { showSongMenu = true }
            )

            Spacer(Modifier.height(GratiaTheme.spacing.mediumLarge))

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

            Spacer(Modifier.height(GratiaTheme.spacing.small))

            // --- Primary Controls ---
            PlayerControls(
                isPlaying = isPlaying,
                onPlayPause = { playerViewModel.togglePlay() },
                onPrevious = { playerViewModel.prevSong() },
                onNext = { playerViewModel.nextSong() },
                glowColor = coverColors.vibrant
            )

            Spacer(Modifier.height(GratiaTheme.spacing.mediumLarge))

            // --- Secondary Actions: Lyrics + Queue only ---
            SecondaryActionRow(
                hasLyrics = currentLyrics != null,
                onOpenLyrics = {
                    if (currentLyrics != null) {
                        showLyricsOverlay = !showLyricsOverlay
                    }
                },
                onOpenQueue = onOpenQueue,
                isLyricsActive = showLyricsOverlay
            )

            Spacer(Modifier.height(GratiaTheme.spacing.mediumLarge))

            // --- Volume Slider ---
            com.gratia.music.ui.components.VolumeSlider()

            Spacer(Modifier.height(GratiaTheme.spacing.medium))
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
                hasLyrics = currentLyrics != null,
                onEditLyrics = { onOpenLyrics() },
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
                        fontWeight = FontWeight.Bold,
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
                        androidx.compose.material3.Text("Delete", fontFamily = com.gratia.music.ui.theme.Inter, fontWeight = FontWeight.SemiBold)
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
 * Inline lyrics view displayed over the artwork area.
 * Shows synced lyrics with the current line highlighted.
 * Tapping a line seeks to that timestamp.
 */
@Composable
private fun InlinePlayerLyrics(
    parsedLyrics: LyricsDocument?,
    currentTimeMs: Long,
    onSeekToLine: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    if (parsedLyrics == null) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            GratiaText(
                text = "No synced lyrics available",
                style = GratiaTheme.typography.body,
                color = Color.White.copy(alpha = 0.5f)
            )
        }
        return
    }

    val lines: List<Pair<Long, String>> = when (parsedLyrics) {
        is LyricsDocument.LineSynced -> parsedLyrics.lines.map { it.startMs to it.text }
        is LyricsDocument.WordSynced -> parsedLyrics.lines.map { it.startMs to it.text }
        is LyricsDocument.Plain -> parsedLyrics.text.split("\n").mapIndexed { i, text -> (i * 5000L) to text }
    }

    val currentLineIndex = lines.indexOfLast { it.first <= currentTimeMs }
    val listState = rememberLazyListState()

    // Auto-scroll to current line
    LaunchedEffect(currentLineIndex) {
        if (currentLineIndex >= 0) {
            listState.animateScrollToItem(
                index = currentLineIndex,
                scrollOffset = -200
            )
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 40.dp)
    ) {
        itemsIndexed(lines) { index, (startMs, text) ->
            val isCurrent = index == currentLineIndex
            val isPast = index < currentLineIndex

            val alpha by animateFloatAsState(
                targetValue = when {
                    isCurrent -> 1f
                    isPast -> 0.3f
                    else -> 0.5f
                },
                animationSpec = tween(300),
                label = "lyricAlpha"
            )

            val textSize = if (isCurrent) 28.sp else 22.sp

            Text(
                text = text,
                fontFamily = SpaceGrotesk,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                fontSize = textSize,
                color = Color.White.copy(alpha = alpha),
                lineHeight = textSize * 1.2f,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (parsedLyrics !is LyricsDocument.Plain) {
                            onSeekToLine(startMs)
                        }
                    }
            )
        }
    }
}
