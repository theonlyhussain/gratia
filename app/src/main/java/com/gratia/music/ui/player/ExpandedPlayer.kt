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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gratia.music.data.CoverColorCache
import com.gratia.music.lyrics.LyricsDocument
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Full-screen cinematic expanded player.
 *
 * Layout (top → bottom):
 * 1. Album art — full-width, edge-to-edge with gradient fade (or synced lyrics overlay)
 * 2. Song title + artist + favorite star + three-dots
 * 3. Progress/seek bar
 * 4. Play/Pause/Skip controls
 * 5. Bottom bar: Lyrics + Device + Queue
 *
 * Lyrics mode: tapping Lyrics toggles an overlay that replaces the artwork area
 * with the full SyncedLyricsView showing word-level animations. Player controls
 * remain visible and functional throughout.
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

    // --- Lyrics overlay state ---
    var showLyricsOverlay by remember { mutableStateOf(false) }

    // --- Lyrics editor state ---
    var showLyricsEditor by remember { mutableStateOf(false) }

    // --- Device selector state ---
    var showDeviceSelector by remember { mutableStateOf(false) }

    // --- Credits and Bio state ---
    var showBiographySheet by remember { mutableStateOf(false) }
    var showCreditsSheet by remember { mutableStateOf(false) }
    var selectedBioArtist by remember { mutableStateOf("") }

    val isAnyOverlayOpen = showBiographySheet || showCreditsSheet || showSongMenu || showLyricsEditor || showDeviceSelector || showLyricsOverlay || showAddToPlaylist || showDeleteConfirm || showSongInfo

    // Intercept back button for ALL overlays to guarantee strict hierarchy
    androidx.activity.compose.BackHandler(enabled = isAnyOverlayOpen) {
        if (showBiographySheet) showBiographySheet = false
        else if (showCreditsSheet) showCreditsSheet = false
        else if (showSongMenu) showSongMenu = false
        else if (showLyricsEditor) showLyricsEditor = false
        else if (showDeviceSelector) showDeviceSelector = false
        else if (showAddToPlaylist) showAddToPlaylist = false
        else if (showDeleteConfirm) showDeleteConfirm = false
        else if (showSongInfo) showSongInfo = false
        else if (showLyricsOverlay) showLyricsOverlay = false
    }



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

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationY = dismissOffsetY.value
                alpha = dismissAlpha
            }
            .pointerInput(isAnyOverlayOpen) {
                if (isAnyOverlayOpen) return@pointerInput // Disable swipe-to-dismiss when overlays are active

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
        val scrollState = rememberScrollState()

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
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            // Main player container
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(screenHeight)
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
                        // Independent swipe-down for Lyrics overlay
                        val lyricsDismissOffsetY = remember { androidx.compose.animation.core.Animatable(0f) }
                        
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 24.dp)
                                .graphicsLayer {
                                    translationY = lyricsDismissOffsetY.value
                                    alpha = (1f - (lyricsDismissOffsetY.value / 600f)).coerceIn(0f, 1f)
                                }
                                .pointerInput(Unit) {
                                    detectVerticalDragGestures(
                                        onDragEnd = {
                                            scope.launch {
                                                if (lyricsDismissOffsetY.value > 200f) {
                                                    showLyricsOverlay = false
                                                    lyricsDismissOffsetY.snapTo(0f)
                                                } else {
                                                    lyricsDismissOffsetY.animateTo(0f, animationSpec = motion.springStiff())
                                                }
                                            }
                                        },
                                        onDragCancel = {
                                            scope.launch { lyricsDismissOffsetY.animateTo(0f, animationSpec = motion.springStiff()) }
                                        },
                                        onVerticalDrag = { _, dragAmount ->
                                            scope.launch {
                                                if (dragAmount > 0 || lyricsDismissOffsetY.value > 0) {
                                                    lyricsDismissOffsetY.snapTo((lyricsDismissOffsetY.value + dragAmount).coerceAtLeast(0f))
                                                }
                                            }
                                        }
                                    )
                                }
                        ) {
                            Box(
                                modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { alpha = 0.99f }
                                .drawWithContent {
                                    drawContent()
                                    val maskBrush = Brush.verticalGradient(
                                        0.0f to Color.Transparent,
                                        0.08f to Color.Black,
                                        0.92f to Color.Black,
                                        1.0f to Color.Transparent,
                                        startY = 0f,
                                        endY = size.height
                                    )
                                    drawRect(brush = maskBrush, blendMode = BlendMode.DstIn)
                                }
                        ) {
                            if (parsedLines.isNotEmpty()) {
                                com.gratia.music.ui.lyrics.SyncedLyricsView(
                                    lyrics = lyricsRaw,
                                    parsedLyricsInput = parsedLines,
                                    currentPlaybackTime = visualTimeMs,
                                    onSeek = { seekMs -> playerViewModel.seekTo(seekMs) },
                                    syncOffset = currentLyrics?.offsetMs ?: 0L,
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
                }
            }

            val audioFormat by playerViewModel.audioFormat.collectAsState()

            // --- Song Info + Favorite + Menu ---
            PlayerHeader(
                title = song.title,
                artist = song.artist,
                album = song.album,
                isFavorite = isFavorite,
                playingFrom = (song.album ?: "GRATIA").uppercase(),
                onClickTitle = { showSongInfo = true },
                onClickArtist = {
                    onDismiss()
                    onNavigateToArtist(song.artist)
                },
                onClickAlbum = {
                    if (!song.album.isNullOrBlank()) {
                        onDismiss()
                        onDismiss() // Close the player first
                        onNavigateToAlbum(song.album)
                    }
                },
                onToggleFavorite = {
                    playerViewModel.toggleFavorite(song)
                    val msg = if (isFavorite) "Removed from Liked Songs" else "Added to Liked Songs"
                    scope.launch { snackbarHostState.showSnackbar(msg) }
                },
                onMoreClick = { showSongMenu = true },
                audioFormatInfo = audioFormat
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

            // --- Secondary Actions: Lyrics, Device, Queue ---
            SecondaryActionRow(
                hasLyrics = currentLyrics != null,
                onOpenLyrics = {
                    if (currentLyrics != null) {
                        showLyricsOverlay = !showLyricsOverlay
                    }
                },
                onOpenDeviceSelector = { showDeviceSelector = true },
                onOpenQueue = onOpenQueue,
                isLyricsActive = showLyricsOverlay
            )

            Spacer(Modifier.height(GratiaTheme.spacing.mediumLarge))

            // --- Volume Slider ---
            com.gratia.music.ui.components.VolumeSlider()

            Spacer(Modifier.height(GratiaTheme.spacing.medium))
            Spacer(Modifier.navigationBarsPadding())
            }

            // --- About the Artist ---
            
            if (artistInfos.isNotEmpty()) {
                com.gratia.music.ui.components.AboutTheArtistCard(
                    artistInfos = artistInfos,
                    onArtistClick = { artistName ->
                        onDismiss()
                        onNavigateToArtist(artistName)
                    },
                    onSeeMoreClick = { artistName ->
                        selectedBioArtist = artistName
                        showBiographySheet = true
                    },
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
                )
            }
            
            // --- Credits ---
            val credits = remember(song, artistInfos) {
                val list = mutableListOf<com.gratia.music.ui.components.CreditPerson>()
                
                // 1. Add Main Artists
                val mainArtists = com.gratia.music.utils.ArtistParser.parseArtists(song.artist)
                mainArtists.forEach { name ->
                    list.add(com.gratia.music.ui.components.CreditPerson(name, listOf("Main Artist")))
                }
                
                // 2. Add Composers
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
                    onShowAllClick = { showCreditsSheet = true },
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
                Spacer(Modifier.navigationBarsPadding())
                Spacer(Modifier.height(16.dp))
            }
        }

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
                        onDismiss() // Close the player first
                        onNavigateToAlbum(song.album)
                    }
                },
                onGoToArtist = {
                    onDismiss()
                    onNavigateToArtist(song.artist)
                },
                hasLyrics = currentLyrics != null,
                onEditLyrics = {
                    // Open the lyrics editor sheet directly from the player
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
}
