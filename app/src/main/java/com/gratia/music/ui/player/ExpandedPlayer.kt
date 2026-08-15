package com.gratia.music.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import com.gratia.music.ui.components.SongMenuSheet
import com.gratia.music.ui.components.SongInfoDialog
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gratia.music.data.CoverColorCache
import com.gratia.music.lyrics.LyricsParser
import com.gratia.music.lyrics.LrcParser

import com.gratia.music.player.PlayerViewModel
import com.gratia.music.ui.components.AnimatedText
import com.gratia.music.ui.components.GratiaText
import com.gratia.music.ui.lyrics.SyncedLyricsView
import com.gratia.music.ui.lyrics.LyricsEditorSheet
import com.gratia.music.ui.theme.GratiaTheme
import com.gratia.music.ui.theme.SpaceGrotesk
import com.gratia.music.ui.LocalSnackbarHostState
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Content mode for the expanded player.
 * The player transforms between these modes using coordinated animations.
 */
enum class PlayerContentMode {
    /** Normal expanded player — large artwork, full controls */
    Normal,
    /** Lyrics mode — compact header, synced lyrics fill the content area */
    Lyrics,
    /** Queue mode — compact header, inline queue fills the content area */
    Queue
}

/**
 * Full-screen expanded player with a unified transformation system.
 *
 * Supports three content modes (Normal, Lyrics, Queue) that share a single
 * collapsing-header animation architecture. The player feels like one
 * continuously transforming surface rather than separate screens.
 *
 * Layout in Normal mode:
 * 1. Large Artwork (dominant visual element)
 * 2. Track Info + Favorite + More
 * 3. Progress bar
 * 4. Playback controls (Prev / Play-Pause / Next)
 * 5. Action row (Lyrics / Connect / Queue)
 * 6. Scrollable: About the Artist, Credits
 *
 * Layout in Lyrics/Queue mode:
 * 1. Compact Header (small artwork + track info + favorite + more)
 * 2. Content area (Lyrics or Queue, scrollable)
 * 3. Progress bar
 * 4. Playback controls
 * 5. Action row
 *
 * Fullscreen content mode (after inactivity):
 * 1. Content fills the screen
 * 2. Tap to restore the header/controls
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

    val favoriteSongIds by playerViewModel.favoriteSongIds.collectAsState()
    val isFavorite = currentSong?.id?.let { favoriteSongIds.contains(it) } ?: false
    val artistInfos by playerViewModel.artistInfos.collectAsState()
    val trackCredits by playerViewModel.trackCredits.collectAsState()

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

    // --- Lyrics editor state ---
    var showLyricsEditor by remember { mutableStateOf(false) }

    // --- Device selector state ---
    var showDeviceSelector by remember { mutableStateOf(false) }

    // --- Credits and Bio state ---
    var showBiographySheet by remember { mutableStateOf(false) }
    var showCreditsSheet by remember { mutableStateOf(false) }
    var selectedBioArtist by remember { mutableStateOf("") }

    // --- Content mode state ---
    var contentMode by remember { mutableStateOf(PlayerContentMode.Normal) }

    // --- Fullscreen content mode (header hidden after inactivity) ---
    var isFullscreenContent by remember { mutableStateOf(false) }

    // --- Collapse progress: 0f = full header, 1f = fully collapsed ---
    val collapseProgress by animateFloatAsState(
        targetValue = when {
            contentMode == PlayerContentMode.Normal -> 0f
            isFullscreenContent -> 1f
            else -> 0f // Compact header visible
        },
        animationSpec = tween(
            durationMillis = if (isFullscreenContent) 600 else 400,
            easing = GratiaTheme.motion.standardEasing
        ),
        label = "collapseProgress"
    )

    // --- Inactivity timer for fullscreen content mode ---
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Reset interaction on any touch
    fun onUserInteraction() {
        lastInteractionTime = System.currentTimeMillis()
        if (isFullscreenContent) {
            isFullscreenContent = false
        }
    }

    // Auto-collapse after inactivity (only in Lyrics/Queue mode)
    LaunchedEffect(contentMode, lastInteractionTime) {
        if (contentMode != PlayerContentMode.Normal) {
            delay(3500L)
            if (System.currentTimeMillis() - lastInteractionTime >= 3400L) {
                isFullscreenContent = true
            }
        }
    }

    // Reset fullscreen when returning to Normal mode
    LaunchedEffect(contentMode) {
        if (contentMode == PlayerContentMode.Normal) {
            isFullscreenContent = false
        }
    }

    val isAnyOverlayOpen = showBiographySheet || showCreditsSheet || showSongMenu ||
            showLyricsEditor || showDeviceSelector || showAddToPlaylist ||
            showDeleteConfirm || showSongInfo

    // Intercept back button for ALL overlays + content modes
    androidx.activity.compose.BackHandler(enabled = isAnyOverlayOpen || contentMode != PlayerContentMode.Normal) {
        when {
            showBiographySheet -> showBiographySheet = false
            showCreditsSheet -> showCreditsSheet = false
            showSongMenu -> showSongMenu = false
            showLyricsEditor -> showLyricsEditor = false
            showDeviceSelector -> showDeviceSelector = false
            showAddToPlaylist -> showAddToPlaylist = false
            showDeleteConfirm -> showDeleteConfirm = false
            showSongInfo -> showSongInfo = false
            contentMode != PlayerContentMode.Normal -> {
                contentMode = PlayerContentMode.Normal
            }
        }
    }

    val motion = GratiaTheme.motion

    // --- Swipe-to-dismiss state (only in Normal mode) ---
    val dismissOffsetY = remember { Animatable(0f) }
    val dismissAlpha by animateFloatAsState(
        targetValue = if (dismissOffsetY.value > 0f) {
            (1f - (dismissOffsetY.value / 800f)).coerceIn(0.3f, 1f)
        } else 1f,
        animationSpec = tween(motion.instant),
        label = "dismissAlpha"
    )

    // --- Smooth visual time interpolator for silky lyrics sync ---
    var visualTimeMs by remember { mutableLongStateOf(currentTimeMs) }
    var lastUpdateTime by remember { mutableLongStateOf(android.os.SystemClock.elapsedRealtime()) }

    LaunchedEffect(currentTimeMs, isPlaying) {
        visualTimeMs = currentTimeMs
        lastUpdateTime = android.os.SystemClock.elapsedRealtime()
        if (isPlaying) {
            while (isActive) {
                androidx.compose.runtime.withFrameNanos {
                    val now = android.os.SystemClock.elapsedRealtime()
                    visualTimeMs = currentTimeMs + (now - lastUpdateTime)
                }
            }
        }
    }

    // --- Parse lyrics for the SyncedLyricsView ---
    val lyricsRaw = currentLyrics?.text ?: ""
    val parsedLines = remember(lyricsRaw) {
        if (currentLyrics?.isSynced == true && lyricsRaw.isNotBlank()) {
            LrcParser.parse(lyricsRaw)
        } else emptyList()
    }

    // Queue list state
    val queueListState = rememberLazyListState()

    // --- Controls & header visibility ---
    val controlsAlpha by animateFloatAsState(
        targetValue = if (isFullscreenContent) 0f else 1f,
        animationSpec = tween(400, easing = motion.standardEasing),
        label = "controlsAlpha"
    )

    // --- NestedScroll for content-driven collapse in fullscreen mode ---
    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: androidx.compose.ui.geometry.Offset, source: NestedScrollSource): androidx.compose.ui.geometry.Offset {
                // Scrolling up in content → don't consume, let content scroll
                // Scrolling down at top → restore header
                return androidx.compose.ui.geometry.Offset.Zero
            }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationY = dismissOffsetY.value
                alpha = dismissAlpha
            }
            .pointerInput(isAnyOverlayOpen, contentMode) {
                if (isAnyOverlayOpen) return@pointerInput
                // Only allow swipe-to-dismiss in Normal mode
                if (contentMode != PlayerContentMode.Normal) return@pointerInput

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
        val screenHeight = maxHeight

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

        // ========== MAIN CONTENT LAYOUT ==========
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // ===== NORMAL MODE =====
            if (contentMode == PlayerContentMode.Normal) {
                NormalModeContent(
                    song = song,
                    coverColors = coverColors,
                    isPlaying = isPlaying,
                    isDragging = isDragging,
                    isFavorite = isFavorite,
                    progress = progress,
                    currentTimeMs = currentTimeMs,
                    durationMs = durationMs,
                    currentLyrics = currentLyrics,
                    contentMode = contentMode,
                    artistInfos = artistInfos,
                    trackCredits = trackCredits,
                    audioFormat = playerViewModel.audioFormat.collectAsState().value,
                    screenHeight = screenHeight,
                    onSeek = { newProgress ->
                        playerViewModel.seekTo((newProgress * durationMs).toLong())
                    },
                    onDragStart = { isDragging = true },
                    onDragEnd = { isDragging = false },
                    onPlayPause = { playerViewModel.togglePlay() },
                    onPrevious = { playerViewModel.prevSong() },
                    onNext = { playerViewModel.nextSong() },
                    onToggleFavorite = {
                        playerViewModel.toggleFavorite(song)
                        val msg = if (isFavorite) "Removed from Liked Songs" else "Added to Liked Songs"
                        scope.launch { snackbarHostState.showSnackbar(msg) }
                    },
                    onMoreClick = { showSongMenu = true },
                    onClickTitle = { showSongInfo = true },
                    onClickArtist = {
                        onDismiss()
                        onNavigateToArtist(song.artist)
                    },
                    onClickAlbum = {
                        if (!song.album.isNullOrBlank()) {
                            onDismiss()
                            onDismiss()
                            onNavigateToAlbum(song.album)
                        }
                    },
                    onOpenLyrics = {
                        if (currentLyrics != null) {
                            contentMode = PlayerContentMode.Lyrics
                        }
                    },
                    onOpenDeviceSelector = { showDeviceSelector = true },
                    onOpenQueue = {
                        contentMode = PlayerContentMode.Queue
                    },
                    onArtistClick = { artistName ->
                        onDismiss()
                        onNavigateToArtist(artistName)
                    },
                    onSeeMoreArtist = { artistName ->
                        selectedBioArtist = artistName
                        showBiographySheet = true
                    },
                    onShowAllCredits = { showCreditsSheet = true },
                    playerViewModel = playerViewModel
                )
            }

            // ===== LYRICS / QUEUE MODE =====
            if (contentMode != PlayerContentMode.Normal) {
                ContentModeLayout(
                    song = song,
                    contentMode = contentMode,
                    coverColors = coverColors,
                    isPlaying = isPlaying,
                    isFavorite = isFavorite,
                    progress = progress,
                    currentTimeMs = currentTimeMs,
                    durationMs = durationMs,
                    currentLyrics = currentLyrics,
                    controlsAlpha = controlsAlpha,
                    isFullscreenContent = isFullscreenContent,
                    visualTimeMs = visualTimeMs,
                    lyricsRaw = lyricsRaw,
                    parsedLines = parsedLines,
                    queueListState = queueListState,
                    audioFormat = playerViewModel.audioFormat.collectAsState().value,
                    onUserInteraction = { onUserInteraction() },
                    onSeek = { newProgress ->
                        onUserInteraction()
                        playerViewModel.seekTo((newProgress * durationMs).toLong())
                    },
                    onSeekLyrics = { seekMs ->
                        onUserInteraction()
                        playerViewModel.seekTo(seekMs)
                    },
                    onDragStart = {
                        onUserInteraction()
                        isDragging = true
                    },
                    onDragEnd = {
                        isDragging = false
                    },
                    onPlayPause = {
                        onUserInteraction()
                        playerViewModel.togglePlay()
                    },
                    onPrevious = {
                        onUserInteraction()
                        playerViewModel.prevSong()
                    },
                    onNext = {
                        onUserInteraction()
                        playerViewModel.nextSong()
                    },
                    onToggleFavorite = {
                        onUserInteraction()
                        playerViewModel.toggleFavorite(song)
                        val msg = if (isFavorite) "Removed from Liked Songs" else "Added to Liked Songs"
                        scope.launch { snackbarHostState.showSnackbar(msg) }
                    },
                    onMoreClick = {
                        onUserInteraction()
                        showSongMenu = true
                    },
                    onOpenLyrics = {
                        onUserInteraction()
                        if (contentMode == PlayerContentMode.Lyrics) {
                            contentMode = PlayerContentMode.Normal
                        } else if (currentLyrics != null) {
                            contentMode = PlayerContentMode.Lyrics
                        }
                    },
                    onOpenDeviceSelector = {
                        onUserInteraction()
                        showDeviceSelector = true
                    },
                    onOpenQueue = {
                        onUserInteraction()
                        if (contentMode == PlayerContentMode.Queue) {
                            contentMode = PlayerContentMode.Normal
                        } else {
                            contentMode = PlayerContentMode.Queue
                        }
                    },
                    syncOffset = currentLyrics?.offsetMs ?: 0L,
                    playerViewModel = playerViewModel
                )
            }
        }

        // ========== OVERLAYS (unchanged) ==========

        // --- Song Menu (three-dot) ---
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
                    val msg = if (isFavorite) "Removed from Liked Songs" else "Added to Liked Songs"
                    scope.launch { snackbarHostState.showSnackbar(msg) }
                },
                onGoToAlbum = {
                    if (!song.album.isNullOrBlank()) {
                        onDismiss()
                        onDismiss()
                        onNavigateToAlbum(song.album)
                    }
                },
                onGoToArtist = {
                    onDismiss()
                    onNavigateToArtist(song.artist)
                },
                hasLyrics = currentLyrics != null,
                onEditLyrics = {
                    showLyricsEditor = true
                },
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

        if (showDeviceSelector) {
            com.gratia.music.ui.components.DeviceSelectorSheet(
                songTitle = song.title,
                artistName = song.artist,
                onDismissRequest = { showDeviceSelector = false }
            )
        }

        // --- Lyrics Editor Sheet (opened directly from three-dot menu) ---
        if (showLyricsEditor) {
            LyricsEditorSheet(
                song = song,
                initialLyrics = lyricsRaw,
                currentTimeMs = visualTimeMs,
                onDismiss = { showLyricsEditor = false },
                onSave = { newLyrics, isSynced ->
                    playerViewModel.saveManualLyrics(newLyrics, isSynced)
                    showLyricsEditor = false
                }
            )
        }

        // --- Artist Info Full Screen Layer ---
        androidx.compose.animation.AnimatedVisibility(
            visible = showBiographySheet,
            enter = androidx.compose.animation.slideInVertically(
                initialOffsetY = { it },
                animationSpec = GratiaTheme.motion.springStandard()
            ) + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.slideOutVertically(
                targetOffsetY = { it },
                animationSpec = androidx.compose.animation.core.tween(GratiaTheme.motion.normal, easing = GratiaTheme.motion.standardEasing)
            ) + androidx.compose.animation.fadeOut()
        ) {
            com.gratia.music.ui.components.ArtistInfoScreen(
                artistName = selectedBioArtist,
                artistInfo = artistInfos.values.firstOrNull { it?.name.equals(selectedBioArtist, ignoreCase = true) } ?: artistInfos[selectedBioArtist],
                trackCredits = trackCredits,
                onDismiss = { showBiographySheet = false }
            )
        }
    }
}

// =============================================================================
// NORMAL MODE — Full player with large artwork
// =============================================================================

@Composable
private fun NormalModeContent(
    song: com.gratia.music.data.model.SongEntity,
    coverColors: CoverColorCache.CoverColors,
    isPlaying: Boolean,
    isDragging: Boolean,
    isFavorite: Boolean,
    progress: Float,
    currentTimeMs: Long,
    durationMs: Long,
    currentLyrics: com.gratia.music.data.model.LyricsEntity?,
    contentMode: PlayerContentMode,
    artistInfos: Map<String, com.gratia.music.data.repository.ArtistInfo?>,
    trackCredits: List<com.gratia.music.data.repository.ContributorInfo>,
    audioFormat: com.gratia.music.player.AudioFormatInfo?,
    screenHeight: androidx.compose.ui.unit.Dp,
    onSeek: (Float) -> Unit,
    onDragStart: () -> Unit,
    onDragEnd: () -> Unit,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleFavorite: () -> Unit,
    onMoreClick: () -> Unit,
    onClickTitle: () -> Unit,
    onClickArtist: () -> Unit,
    onClickAlbum: () -> Unit,
    onOpenLyrics: () -> Unit,
    onOpenDeviceSelector: () -> Unit,
    onOpenQueue: () -> Unit,
    onArtistClick: (String) -> Unit,
    onSeeMoreArtist: (String) -> Unit,
    onShowAllCredits: () -> Unit,
    playerViewModel: PlayerViewModel
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
    ) {
        // Main player container — fills one screen height
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(screenHeight)
        ) {
            // --- Hero Artwork area ---
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
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
            }

            // --- Song Info + Favorite + Menu ---
            PlayerHeader(
                title = song.title,
                artist = song.artist,
                album = song.album,
                isFavorite = isFavorite,
                playingFrom = (song.album ?: "GRATIA").uppercase(),
                onClickTitle = onClickTitle,
                onClickArtist = onClickArtist,
                onClickAlbum = onClickAlbum,
                onToggleFavorite = onToggleFavorite,
                onMoreClick = onMoreClick,
                audioFormatInfo = audioFormat
            )

            Spacer(Modifier.height(GratiaTheme.spacing.mediumLarge))

            // --- Progress Bar ---
            GratiaProgressBar(
                progress = progress,
                currentTimeMs = currentTimeMs,
                durationMs = durationMs,
                onSeek = onSeek,
                onDragStart = onDragStart,
                onDragEnd = onDragEnd
            )

            Spacer(Modifier.height(GratiaTheme.spacing.small))

            // --- Primary Controls ---
            PlayerControls(
                isPlaying = isPlaying,
                onPlayPause = onPlayPause,
                onPrevious = onPrevious,
                onNext = onNext,
                glowColor = coverColors.vibrant
            )

            Spacer(Modifier.height(GratiaTheme.spacing.mediumLarge))

            // --- Secondary Actions: Lyrics, Device, Queue ---
            SecondaryActionRow(
                hasLyrics = currentLyrics != null,
                onOpenLyrics = onOpenLyrics,
                onOpenDeviceSelector = onOpenDeviceSelector,
                onOpenQueue = onOpenQueue,
                isLyricsActive = false,
                isQueueActive = false
            )
            Spacer(Modifier.height(GratiaTheme.spacing.mediumLarge))
            Spacer(Modifier.navigationBarsPadding())
        }

        // --- About the Artist (scrollable below player) ---
        if (artistInfos.isNotEmpty()) {
            com.gratia.music.ui.components.AboutTheArtistCard(
                artistInfos = artistInfos,
                onArtistClick = onArtistClick,
                onSeeMoreClick = onSeeMoreArtist,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
            )
        }

        // --- Credits (scrollable below player) ---
        val credits = remember(song, artistInfos) {
            val list = mutableListOf<com.gratia.music.ui.components.CreditPerson>()
            val mainArtists = com.gratia.music.utils.ArtistParser.parseArtists(song.artist)
            mainArtists.forEach { name ->
                list.add(com.gratia.music.ui.components.CreditPerson(name, listOf("Main Artist")))
            }
            val composers = com.gratia.music.utils.ArtistParser.parseArtists(song.composer)
            composers.forEach { name ->
                val existing = list.find { it.name.equals(name, ignoreCase = true) }
                if (existing != null) {
                    list.remove(existing)
                    list.add(existing.copy(roles = existing.roles + "Composer"))
                } else {
                    list.add(com.gratia.music.ui.components.CreditPerson(name, listOf("Composer")))
                }
            }
            list
        }

        if (credits.isNotEmpty()) {
            com.gratia.music.ui.components.CreditsCard(
                credits = credits,
                onShowAllClick = onShowAllCredits,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            Spacer(Modifier.navigationBarsPadding())
            Spacer(Modifier.height(16.dp))
        }
    }
}

// =============================================================================
// CONTENT MODE LAYOUT — Lyrics or Queue with compact header
// =============================================================================

@Composable
private fun ContentModeLayout(
    song: com.gratia.music.data.model.SongEntity,
    contentMode: PlayerContentMode,
    coverColors: CoverColorCache.CoverColors,
    isPlaying: Boolean,
    isFavorite: Boolean,
    progress: Float,
    currentTimeMs: Long,
    durationMs: Long,
    currentLyrics: com.gratia.music.data.model.LyricsEntity?,
    controlsAlpha: Float,
    isFullscreenContent: Boolean,
    visualTimeMs: Long,
    lyricsRaw: String,
    parsedLines: List<com.gratia.music.lyrics.LyricLine>,
    queueListState: androidx.compose.foundation.lazy.LazyListState,
    audioFormat: com.gratia.music.player.AudioFormatInfo?,
    onUserInteraction: () -> Unit,
    onSeek: (Float) -> Unit,
    onSeekLyrics: (Long) -> Unit,
    onDragStart: () -> Unit,
    onDragEnd: () -> Unit,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleFavorite: () -> Unit,
    onMoreClick: () -> Unit,
    onOpenLyrics: () -> Unit,
    onOpenDeviceSelector: () -> Unit,
    onOpenQueue: () -> Unit,
    syncOffset: Long,
    playerViewModel: PlayerViewModel
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Spacer(Modifier.statusBarsPadding())

        // --- Compact header (animated visibility) ---
        AnimatedVisibility(
            visible = !isFullscreenContent,
            enter = fadeIn(tween(300)) + androidx.compose.animation.expandVertically(
                animationSpec = tween(300, easing = GratiaTheme.motion.standardEasing)
            ),
            exit = fadeOut(tween(300)) + androidx.compose.animation.shrinkVertically(
                animationSpec = tween(300, easing = GratiaTheme.motion.standardEasing)
            )
        ) {
            CompactPlayerHeader(
                coverArtPath = song.coverArtPath,
                title = song.title,
                artist = song.artist,
                isFavorite = isFavorite,
                onToggleFavorite = onToggleFavorite,
                onMoreClick = onMoreClick
            )
        }

        // --- Content area (Lyrics or Queue) ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    // Tap content area to restore header in fullscreen mode
                    if (isFullscreenContent) {
                        onUserInteraction()
                    }
                }
        ) {
            AnimatedContent(
                targetState = contentMode,
                transitionSpec = {
                    (fadeIn(tween(250)) + scaleIn(tween(250), initialScale = 0.96f)) togetherWith
                            (fadeOut(tween(200)) + scaleOut(tween(200), targetScale = 0.96f))
                },
                label = "contentModeSwitch"
            ) { mode ->
                when (mode) {
                    PlayerContentMode.Lyrics -> {
                        LyricsContentArea(
                            lyricsRaw = lyricsRaw,
                            parsedLines = parsedLines,
                            visualTimeMs = visualTimeMs,
                            syncOffset = syncOffset,
                            onSeek = onSeekLyrics,
                            onInteraction = onUserInteraction
                        )
                    }
                    PlayerContentMode.Queue -> {
                        InlineQueueContent(
                            playerViewModel = playerViewModel,
                            listState = queueListState,
                            onInteraction = onUserInteraction
                        )
                    }
                    else -> {
                        // Should not happen, but safety
                        Box(Modifier.fillMaxSize())
                    }
                }
            }
        }

        // --- Bottom controls (animated visibility) ---
        AnimatedVisibility(
            visible = !isFullscreenContent,
            enter = fadeIn(tween(300)) + androidx.compose.animation.expandVertically(
                expandFrom = Alignment.Bottom,
                animationSpec = tween(300, easing = GratiaTheme.motion.standardEasing)
            ),
            exit = fadeOut(tween(300)) + androidx.compose.animation.shrinkVertically(
                shrinkTowards = Alignment.Bottom,
                animationSpec = tween(300, easing = GratiaTheme.motion.standardEasing)
            )
        ) {
            Column {
                // Progress Bar
                GratiaProgressBar(
                    progress = progress,
                    currentTimeMs = currentTimeMs,
                    durationMs = durationMs,
                    onSeek = onSeek,
                    onDragStart = onDragStart,
                    onDragEnd = onDragEnd
                )

                Spacer(Modifier.height(GratiaTheme.spacing.small))

                // Playback Controls
                PlayerControls(
                    isPlaying = isPlaying,
                    onPlayPause = onPlayPause,
                    onPrevious = onPrevious,
                    onNext = onNext,
                    glowColor = coverColors.vibrant
                )

                Spacer(Modifier.height(GratiaTheme.spacing.mediumSmall))

                // Action Row
                SecondaryActionRow(
                    hasLyrics = currentLyrics != null,
                    onOpenLyrics = onOpenLyrics,
                    onOpenDeviceSelector = onOpenDeviceSelector,
                    onOpenQueue = onOpenQueue,
                    isLyricsActive = contentMode == PlayerContentMode.Lyrics,
                    isQueueActive = contentMode == PlayerContentMode.Queue
                )

                Spacer(Modifier.height(GratiaTheme.spacing.mediumSmall))
                Spacer(Modifier.navigationBarsPadding())
            }
        }
    }
}

// =============================================================================
// LYRICS CONTENT AREA
// =============================================================================

@Composable
private fun LyricsContentArea(
    lyricsRaw: String,
    parsedLines: List<com.gratia.music.lyrics.LyricLine>,
    visualTimeMs: Long,
    syncOffset: Long,
    onSeek: (Long) -> Unit,
    onInteraction: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp)
            .graphicsLayer { alpha = 0.99f }
            .drawWithContent {
                drawContent()
                val maskBrush = Brush.verticalGradient(
                    0.0f to Color.Transparent,
                    0.06f to Color.Black,
                    0.94f to Color.Black,
                    1.0f to Color.Transparent,
                    startY = 0f,
                    endY = size.height
                )
                drawRect(brush = maskBrush, blendMode = BlendMode.DstIn)
            }
    ) {
        if (parsedLines.isNotEmpty()) {
            SyncedLyricsView(
                lyrics = lyricsRaw,
                parsedLyricsInput = parsedLines,
                currentPlaybackTime = visualTimeMs,
                onSeek = { seekMs ->
                    onInteraction()
                    onSeek(seekMs)
                },
                syncOffset = syncOffset,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // No synced lyrics — show a clean empty state
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    GratiaText(
                        text = "No synced lyrics available",
                        style = GratiaTheme.typography.body,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                    Spacer(Modifier.height(12.dp))
                    GratiaText(
                        text = "Tap Edit Lyrics from the menu to add them",
                        style = GratiaTheme.typography.caption,
                        color = Color.White.copy(alpha = 0.3f)
                    )
                }
            }
        }
    }
}
