package com.gratia.music.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import com.gratia.music.ui.components.SongMenuSheet
import com.gratia.music.ui.components.SongInfoDialog
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
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
import kotlin.math.abs

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
 * continuous transition animation. The player feels like one surface that
 * physically transforms rather than separate screens.
 *
 * Animation Architecture:
 * - `transitionProgress` (Animatable 0f→1f): drives artwork position/scale,
 *   header visibility, and content crossfade as ONE coordinated motion
 * - `dismissOffsetY` (Animatable): swipe-to-dismiss gesture tracking
 * - `peekOffsetX` (Animatable): horizontal song-peek gesture tracking
 *
 * Layout in Normal mode (transitionProgress = 0f):
 * 1. Large Artwork (dominant visual element)
 * 2. Track Info + Favorite + More
 * 3. Progress bar
 * 4. Playback controls (Prev / Play-Pause / Next)
 * 5. Action row (Lyrics / Connect / Queue)
 * 6. Scrollable: About the Artist, Credits
 *
 * Layout in Lyrics/Queue mode (transitionProgress = 1f):
 * 1. Compact Header (small artwork + track info + favorite + more)
 * 2. Content area (Lyrics or Queue, scrollable)
 * 3. Progress bar
 * 4. Playback controls
 * 5. Action row
 *
 * Lyrics fullscreen (after inactivity):
 * 1. Compact header dims but stays visible
 * 2. Lyrics fill most of the screen
 * 3. Tap to restore full controls
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
    val density = LocalDensity.current

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
    var allLyricsList by remember { mutableStateOf<List<com.gratia.music.data.model.LyricsEntity>>(emptyList()) }

    LaunchedEffect(showLyricsEditor, currentLyrics) {
        if (showLyricsEditor) {
            allLyricsList = playerViewModel.getAllLyricsForCurrentSong()
        }
    }

    // --- Estimated Timings state ---
    var enableEstimatedTimings by remember { mutableStateOf(false) }

    // --- Device selector state ---
    var showDeviceSelector by remember { mutableStateOf(false) }

    // --- Credits and Bio state ---
    var showBiographySheet by remember { mutableStateOf(false) }
    var showCreditsSheet by remember { mutableStateOf(false) }
    var selectedBioArtist by remember { mutableStateOf("") }
    
    // --- Multiple Artists state ---
    var showMultipleArtistSelector by remember { mutableStateOf(false) }
    var multipleArtistsList by remember { mutableStateOf<List<String>>(emptyList()) }

    // --- Content mode state ---
    var contentMode by remember { mutableStateOf(PlayerContentMode.Normal) }

    // --- Fullscreen content mode (header dimmed after inactivity, Lyrics only) ---
    var isFullscreenContent by remember { mutableStateOf(false) }

    // ======================================================================
    // CONTINUOUS TRANSITION PROGRESS (the core animation value)
    // 0f = Normal player, 1f = Lyrics/Queue compact mode
    // ======================================================================
    val transitionProgress = remember { Animatable(0f) }

    // Drive the transition based on contentMode
    LaunchedEffect(contentMode) {
        val target = if (contentMode == PlayerContentMode.Normal) 0f else 1f
        transitionProgress.animateTo(
            targetValue = target,
            animationSpec = spring(
                dampingRatio = 0.85f,
                stiffness = 300f
            )
        )
    }

    // ======================================================================
    // INACTIVITY TIMER — Lyrics fullscreen (NOT Queue)
    // ======================================================================
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    fun onUserInteraction() {
        lastInteractionTime = System.currentTimeMillis()
        if (isFullscreenContent) {
            isFullscreenContent = false
        }
    }

    // Auto-collapse after inactivity (only in Lyrics mode, NOT Queue)
    LaunchedEffect(contentMode, lastInteractionTime) {
        if (contentMode == PlayerContentMode.Lyrics) {
            delay(4000L)
            if (System.currentTimeMillis() - lastInteractionTime >= 3900L) {
                isFullscreenContent = true
            }
        }
    }

    // Reset fullscreen when returning to Normal mode or switching to Queue
    LaunchedEffect(contentMode) {
        if (contentMode != PlayerContentMode.Lyrics) {
            isFullscreenContent = false
        }
    }

    val isAnyOverlayOpen = showBiographySheet || showCreditsSheet || showSongMenu ||
            showLyricsEditor || showDeviceSelector || showAddToPlaylist ||
            showDeleteConfirm || showSongInfo || showMultipleArtistSelector

    // Intercept back button for ALL overlays + content modes + normal closing
    androidx.activity.compose.BackHandler(enabled = true) {
        when {
            showBiographySheet -> showBiographySheet = false
            showCreditsSheet -> showCreditsSheet = false
            showSongMenu -> showSongMenu = false
            showLyricsEditor -> showLyricsEditor = false
            showDeviceSelector -> showDeviceSelector = false
            showAddToPlaylist -> showAddToPlaylist = false
            showDeleteConfirm -> showDeleteConfirm = false
            showSongInfo -> showSongInfo = false
            showMultipleArtistSelector -> showMultipleArtistSelector = false
            contentMode != PlayerContentMode.Normal -> {
                contentMode = PlayerContentMode.Normal
            }
            else -> onDismiss()
        }
    }

    val motion = GratiaTheme.motion

    // ======================================================================
    // SWIPE-TO-DISMISS — physically follows finger
    // ======================================================================
    val dismissOffsetY = remember { Animatable(0f) }
    val dismissAlpha by animateFloatAsState(
        targetValue = if (dismissOffsetY.value > 0f) {
            (1f - (dismissOffsetY.value / 800f)).coerceIn(0.3f, 1f)
        } else 1f,
        animationSpec = tween(motion.instant),
        label = "dismissAlpha"
    )

    // ======================================================================
    // HORIZONTAL PEEK GESTURE — for song switching
    // ======================================================================
    val peekOffsetX = remember { Animatable(0f) }
    var peekVelocity by remember { mutableFloatStateOf(0f) }

    // ======================================================================
    // SMOOTH VISUAL TIME INTERPOLATOR — for silky lyrics sync
    // ======================================================================
    val visualTimeState = remember { mutableLongStateOf(currentTimeMs) }
    var lastUpdateTime by remember { mutableLongStateOf(android.os.SystemClock.elapsedRealtime()) }

    LaunchedEffect(currentTimeMs, isPlaying) {
        visualTimeState.longValue = currentTimeMs
        lastUpdateTime = android.os.SystemClock.elapsedRealtime()
        if (isPlaying) {
            while (isActive) {
                androidx.compose.runtime.withFrameNanos {
                    val now = android.os.SystemClock.elapsedRealtime()
                    visualTimeState.longValue = currentTimeMs + (now - lastUpdateTime)
                }
            }
        }
    }

    // --- Parse lyrics for the SyncedLyricsView ---
    val lyricsRaw = currentLyrics?.text ?: ""
    val parsedLines = remember(lyricsRaw, enableEstimatedTimings) {
        if (currentLyrics?.isSynced == true && lyricsRaw.isNotBlank()) {
            LrcParser.parse(lyricsRaw, enableEstimatedTimings)
        } else emptyList()
    }

    // Queue list state
    val queueListState = rememberLazyListState()

    // --- Controls & header visibility (for fullscreen lyrics dimming) ---
    val controlsAlpha by animateFloatAsState(
        targetValue = if (isFullscreenContent) 0f else 1f,
        animationSpec = tween(400, easing = motion.standardEasing),
        label = "controlsAlpha"
    )

    // Header alpha for fullscreen mode — dims but stays visible
    val headerAlpha by animateFloatAsState(
        targetValue = if (isFullscreenContent) 0.4f else 1f,
        animationSpec = tween(400, easing = motion.standardEasing),
        label = "headerAlpha"
    )

    // Nested scroll connection for smooth swipe-to-dismiss
    val nestedScrollConnection = remember(isAnyOverlayOpen, contentMode) {
        object : NestedScrollConnection {
            override fun onPreScroll(
                available: androidx.compose.ui.geometry.Offset,
                source: NestedScrollSource
            ): androidx.compose.ui.geometry.Offset {
                if (isAnyOverlayOpen) return androidx.compose.ui.geometry.Offset.Zero

                if (dismissOffsetY.value > 0f && available.y < 0) {
                    val newOffset = (dismissOffsetY.value + available.y).coerceAtLeast(0f)
                    val consumed = dismissOffsetY.value - newOffset
                    scope.launch { dismissOffsetY.snapTo(newOffset) }
                    return androidx.compose.ui.geometry.Offset(0f, -consumed)
                }
                return androidx.compose.ui.geometry.Offset.Zero
            }

            override fun onPostScroll(
                consumed: androidx.compose.ui.geometry.Offset,
                available: androidx.compose.ui.geometry.Offset,
                source: NestedScrollSource
            ): androidx.compose.ui.geometry.Offset {
                if (isAnyOverlayOpen) return androidx.compose.ui.geometry.Offset.Zero

                if (available.y > 0) {
                    val newOffset = (dismissOffsetY.value + available.y).coerceAtLeast(0f)
                    scope.launch { dismissOffsetY.snapTo(newOffset) }
                    return androidx.compose.ui.geometry.Offset(0f, available.y)
                }
                return androidx.compose.ui.geometry.Offset.Zero
            }

            override suspend fun onPreFling(available: androidx.compose.ui.unit.Velocity): androidx.compose.ui.unit.Velocity {
                if (dismissOffsetY.value > 150f || available.y > 1000f) {
                    onDismiss()
                    dismissOffsetY.snapTo(0f)
                    return available
                } else if (dismissOffsetY.value > 0f) {
                    dismissOffsetY.animateTo(
                        targetValue = 0f,
                        animationSpec = motion.springStiff()
                    )
                    return available
                }
                return androidx.compose.ui.unit.Velocity.Zero
            }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection)
            .graphicsLayer {
                translationY = dismissOffsetY.value
                alpha = dismissAlpha
            }
    ) {
        val screenHeight = maxHeight
        val screenWidthPx = with(density) { maxWidth.toPx() }

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
        // The entire player is one unified Column. The `transitionProgress`
        // controls how much the artwork shrinks and where content appears.

        val tp = transitionProgress.value // shorthand

        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // ===== NORMAL MODE (transitionProgress ≈ 0f) =====
            if (tp < 0.01f && contentMode == PlayerContentMode.Normal) {
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
                    peekOffsetX = peekOffsetX.value,
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
                        val parsed = com.gratia.music.utils.ArtistParser.parseArtists(song.artist)
                        if (parsed.size > 1) {
                            multipleArtistsList = parsed
                            showMultipleArtistSelector = true
                        } else {
                            onDismiss()
                            onNavigateToArtist(song.artist)
                        }
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
                        val parsed = com.gratia.music.utils.ArtistParser.parseArtists(artistName)
                        if (parsed.size > 1) {
                            multipleArtistsList = parsed
                            showMultipleArtistSelector = true
                        } else {
                            onDismiss()
                            onNavigateToArtist(artistName)
                        }
                    },
                    onSeeMoreArtist = { artistName ->
                        selectedBioArtist = artistName
                        showBiographySheet = true
                    },
                    onShowAllCredits = { showCreditsSheet = true },
                    onHorizontalDrag = { delta ->
                        scope.launch { peekOffsetX.snapTo(peekOffsetX.value + delta) }
                        peekVelocity = delta
                    },
                    onHorizontalDragEnd = {
                        scope.launch {
                            val threshold = screenWidthPx * 0.3f
                            val fastSwipe = abs(peekVelocity) > 8f
                            when {
                                peekOffsetX.value < -threshold || (peekOffsetX.value < -50f && fastSwipe && peekVelocity < 0) -> {
                                    // Swiped left → next song
                                    peekOffsetX.animateTo(-screenWidthPx, animationSpec = tween(200))
                                    playerViewModel.nextSong()
                                    peekOffsetX.snapTo(0f)
                                }
                                peekOffsetX.value > threshold || (peekOffsetX.value > 50f && fastSwipe && peekVelocity > 0) -> {
                                    // Swiped right → previous song
                                    peekOffsetX.animateTo(screenWidthPx, animationSpec = tween(200))
                                    playerViewModel.prevSong()
                                    peekOffsetX.snapTo(0f)
                                }
                                else -> {
                                    // Spring back
                                    peekOffsetX.animateTo(0f, animationSpec = motion.springStiff())
                                }
                            }
                        }
                    },
                    playerViewModel = playerViewModel
                )
            }

            // ===== LYRICS / QUEUE MODE (transitionProgress ≈ 1f) =====
            if (tp > 0.01f || contentMode != PlayerContentMode.Normal) {
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
                    headerAlpha = headerAlpha,
                    isFullscreenContent = isFullscreenContent,
                    visualTimeProvider = { visualTimeState.longValue },
                    lyricsRaw = lyricsRaw,
                    parsedLines = parsedLines,
                    queueListState = queueListState,
                    audioFormat = playerViewModel.audioFormat.collectAsState().value,
                    enableEstimatedTimings = enableEstimatedTimings,
                    onToggleEstimatedTimings = { enableEstimatedTimings = !enableEstimatedTimings },
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

        if (showMultipleArtistSelector) {
            com.gratia.music.ui.components.MultipleArtistSelectorSheet(
                artists = multipleArtistsList,
                onArtistClick = { artistName ->
                    showMultipleArtistSelector = false
                    onDismiss() // Dismiss expanded player
                    onNavigateToArtist(artistName) // Navigate to artist
                },
                onDismissRequest = { showMultipleArtistSelector = false }
            )
        }

        // --- Lyrics Editor Sheet (opened directly from three-dot menu) ---
        if (showLyricsEditor) {
            LyricsEditorSheet(
                song = song,
                allLyrics = allLyricsList,
                currentTimeMs = visualTimeState.longValue,
                onDismiss = { showLyricsEditor = false },
                onSave = { newLyrics, isSynced, isWordLevel ->
                    playerViewModel.saveManualLyrics(newLyrics, isSynced, isWordLevel, isActive = true)
                    showLyricsEditor = false
                },
                onDelete = { provider ->
                    playerViewModel.deleteLyrics(provider)
                    showLyricsEditor = false
                },
                onSetActive = { provider ->
                    playerViewModel.setActiveLyrics(provider)
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
// NORMAL MODE — Full player with large artwork + horizontal peek gesture
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
    peekOffsetX: Float,
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
    onHorizontalDrag: (Float) -> Unit,
    onHorizontalDragEnd: () -> Unit,
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
            // --- Hero Artwork area with horizontal peek gesture ---
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragEnd = { onHorizontalDragEnd() },
                            onDragCancel = { onHorizontalDragEnd() },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                onHorizontalDrag(dragAmount)
                            }
                        )
                    }
            ) {
                Column(
                    modifier = Modifier.graphicsLayer {
                        translationX = peekOffsetX
                    }
                ) {
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
                modifier = Modifier.padding(vertical = 16.dp)
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
    headerAlpha: Float,
    isFullscreenContent: Boolean,
    visualTimeProvider: () -> Long,
    lyricsRaw: String,
    parsedLines: List<com.gratia.music.lyrics.LyricLine>,
    queueListState: androidx.compose.foundation.lazy.LazyListState,
    audioFormat: com.gratia.music.player.AudioFormatInfo?,
    enableEstimatedTimings: Boolean,
    onToggleEstimatedTimings: () -> Unit,
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

        // --- Compact header (always visible, dims in fullscreen lyrics) ---
        // In fullscreen mode: header dims to 40% alpha but stays visible
        // so the user always knows what song is playing
        Box(
            modifier = Modifier.graphicsLayer {
                alpha = headerAlpha
            }
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
                    // Tap content area to restore controls in fullscreen mode
                    // This does NOT seek — that prevents accidental seeking
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
                            currentLyrics = currentLyrics,
                            visualTimeProvider = visualTimeProvider,
                            syncOffset = syncOffset,
                            enableEstimatedTimings = enableEstimatedTimings,
                            onToggleEstimatedTimings = onToggleEstimatedTimings,
                            isFullscreenContent = isFullscreenContent,
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

        // --- Bottom controls ---
        // In Lyrics fullscreen: controls fade out but can be revealed by tap
        // In Queue mode: controls always visible (no inactivity collapse)
        AnimatedVisibility(
            visible = !isFullscreenContent || contentMode == PlayerContentMode.Queue,
            enter = fadeIn(tween(300)) + expandVertically(
                expandFrom = Alignment.Bottom,
                animationSpec = tween(300, easing = GratiaTheme.motion.standardEasing)
            ),
            exit = fadeOut(tween(300)) + shrinkVertically(
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
    currentLyrics: com.gratia.music.data.model.LyricsEntity?,
    visualTimeProvider: () -> Long,
    syncOffset: Long,
    enableEstimatedTimings: Boolean,
    onToggleEstimatedTimings: () -> Unit,
    isFullscreenContent: Boolean,
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
                currentPlaybackTimeProvider = visualTimeProvider,
                onSeek = { seekMs ->
                    onInteraction()
                    onSeek(seekMs)
                },
                onTapLyricsView = onInteraction,
                syncOffset = syncOffset,
                modifier = Modifier.fillMaxSize()
            )
            
            // Estimated timing toggle button overlay
            if (currentLyrics?.isSynced == true && currentLyrics.isWordLevel == false) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = !isFullscreenContent,
                    enter = androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(300)),
                    exit = androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(300)),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 16.dp, bottom = 16.dp)
                ) {
                    androidx.compose.material3.FilledTonalIconToggleButton(
                        checked = enableEstimatedTimings,
                        onCheckedChange = { onToggleEstimatedTimings() },
                        modifier = Modifier.size(36.dp),
                        shape = androidx.compose.foundation.shape.CircleShape,
                        colors = androidx.compose.material3.IconButtonDefaults.filledTonalIconToggleButtonColors(
                            containerColor = Color.White.copy(alpha = 0.15f),
                            checkedContainerColor = GratiaTheme.colors.accent,
                            contentColor = Color.White.copy(alpha = 0.6f),
                            checkedContentColor = Color.White
                        )
                    ) {
                        androidx.compose.material3.Icon(
                            imageVector = androidx.compose.material.icons.Icons.Default.Timer,
                            contentDescription = "Toggle Word-by-Word Timings",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
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
