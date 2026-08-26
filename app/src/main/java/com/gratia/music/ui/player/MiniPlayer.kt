package com.gratia.music.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gratia.music.data.CoverColorCache
import com.gratia.music.player.PlayerViewModel
import com.gratia.music.ui.components.AnimatedText
import com.gratia.music.ui.components.CoverArtImage
import com.gratia.music.ui.components.GlassSurface
import com.gratia.music.ui.components.GratiaIcon
import com.gratia.music.ui.components.GratiaIconButton
import com.gratia.music.ui.theme.GratiaTheme

/**
 * Redesigned mini player that feels connected to the full expanded player.
 *
 * Design details:
 * - Glass surface container matching the Gratia aesthetic
 * - Album art thumbnail (44dp) with GDL medium corners
 * - Song title + artist with crossfade on song change
 * - Thin progress line across the bottom edge
 * - Play/Pause + Next buttons
 * - Tapping expands to full player
 * - Color-tinted subtle background from cover art
 */
@Composable
fun MiniPlayer(playerViewModel: PlayerViewModel) {
    val currentSong by playerViewModel.currentSong.collectAsState()
    val isPlaying by playerViewModel.isPlaying.collectAsState()
    val currentTimeMs by playerViewModel.currentTimeMs.collectAsState()
    val durationMs by playerViewModel.durationMs.collectAsState()
    val queue by playerViewModel.queue.collectAsState()

    val song = currentSong ?: return
    val progress = if (durationMs > 0) currentTimeMs.toFloat() / durationMs.toFloat() else 0f

    val sleepTimerActive by playerViewModel.sleepTimerActive.collectAsState()
    val sleepTimerRemainingMs by playerViewModel.sleepTimerRemainingMs.collectAsState()

    val view = LocalView.current
    val haptics = GratiaTheme.haptics
    val motion = GratiaTheme.motion

    // Extract cover colors for subtle tinting
    var coverColors by remember { mutableStateOf(CoverColorCache.FALLBACK) }
    LaunchedEffect(song.id, song.coverArtPath) {
        coverColors = CoverColorCache.getColors(song.id, song.coverArtPath)
    }

    // Subtle tint from dominant color mixed into the glass surface
    val glassTint = if (GratiaTheme.colors.isDark) {
        coverColors.darkMuted.copy(alpha = 0.15f)
    } else {
        coverColors.dominant.copy(alpha = 0.08f)
    }

    val scope = rememberCoroutineScope()
    val offsetY = remember { androidx.compose.animation.core.Animatable(0f) }

    val initialPage = remember(queue, song.id) {
        val idx = playerViewModel.playerManager.currentQueueIndex
        if (idx in queue.indices && queue[idx].id == song.id) idx
        else {
            val findIdx = queue.indexOfFirst { it.id == song.id }
            if (findIdx != -1) findIdx else 0
        }
    }
    
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(
        initialPage = initialPage,
        pageCount = { queue.size.coerceAtLeast(1) }
    )

    val isMiniDragged by pagerState.interactionSource.collectIsDraggedAsState()
    var miniUserInitiatedSwipe by remember { mutableStateOf(false) }

    LaunchedEffect(isMiniDragged) {
        if (isMiniDragged) {
            miniUserInitiatedSwipe = true
        }
    }

    // Sync pager with current song when it changes externally
    val currentQueueIndex = playerViewModel.playerManager.currentQueueIndex
    LaunchedEffect(song.id, currentQueueIndex, queue.size) {
        val target = if (currentQueueIndex in queue.indices && queue[currentQueueIndex].id == song.id) {
            currentQueueIndex
        } else {
            queue.indexOfFirst { it.id == song.id }
        }
        if (target != -1 && target != pagerState.currentPage && target in queue.indices) {
            miniUserInitiatedSwipe = false
            pagerState.animateScrollToPage(target)
        }
    }

    // Play song ONLY when user commits a manual swipe gesture
    LaunchedEffect(pagerState.isScrollInProgress) {
        if (!pagerState.isScrollInProgress && miniUserInitiatedSwipe) {
            miniUserInitiatedSwipe = false
            val targetSong = queue.getOrNull(pagerState.currentPage)
            if (targetSong != null && targetSong.id != currentSong?.id) {
                haptics.light(view)
                playerViewModel.playFromQueue(pagerState.currentPage)
            }
        }
    }

    androidx.compose.foundation.pager.HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 0.dp),
        beyondViewportPageCount = 1
    ) { page ->
        val pageSong = queue.getOrNull(page) ?: return@HorizontalPager
        
        var pageCoverColors by remember { mutableStateOf(CoverColorCache.FALLBACK) }
        LaunchedEffect(pageSong.id, pageSong.coverArtPath) {
            pageCoverColors = CoverColorCache.getColors(pageSong.id, pageSong.coverArtPath)
        }

        GlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = GratiaTheme.spacing.mediumLarge, vertical = GratiaTheme.spacing.small)
                .graphicsLayer {
                    translationY = offsetY.value
                    alpha = (1f - (Math.abs(offsetY.value) / 500f)).coerceIn(0f, 1f)
                }
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                if (offsetY.value > 150f) {
                                    haptics.heavy(view)
                                    offsetY.animateTo(1000f, animationSpec = tween(300))
                                    playerViewModel.clearQueue()
                                    offsetY.snapTo(0f)
                                } else {
                                    offsetY.animateTo(0f, animationSpec = spring(stiffness = 300f))
                                }
                            }
                        },
                        onDragCancel = { 
                            scope.launch {
                                offsetY.animateTo(0f)
                            }
                        },
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            scope.launch {
                                if (dragAmount > 0 || offsetY.value > 0) {
                                    offsetY.snapTo((offsetY.value + dragAmount).coerceAtLeast(0f))
                                }
                            }
                        }
                    )
                },
        shape = androidx.compose.foundation.shape.CircleShape,
        backgroundColor = GratiaTheme.colors.surface.copy(alpha = 0.95f),
        glowColor = pageCoverColors.dominant,
        elevation = 12.dp,
        borderColorStart = if (GratiaTheme.colors.isDark) {
            Color.White.copy(alpha = 0.1f)
        } else {
            Color.Black.copy(alpha = 0.05f)
        },
        borderColorEnd = Color.Transparent
    ) {
        Column {
            // Main content row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { playerViewModel.setExpandedPlayerOpen(true) }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Album art with playing indicator overlay
                Box(contentAlignment = Alignment.Center) {
                    CoverArtImage(
                        coverArtPath = pageSong.coverArtPath,
                        title = pageSong.title,
                        artist = pageSong.artist,
                        size = 44.dp,
                        cornerRadius = 8.dp, // Rounded square
                        fontSize = 12.sp
                    )
                    
                    // Semi-transparent overlay when playing
                    androidx.compose.animation.AnimatedVisibility(
                        visible = isPlaying && pageSong.id == currentSong?.id,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(GratiaTheme.spacing.heroSmall)
                                .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            com.gratia.music.ui.components.PlayingIndicator(
                                isPaused = false,
                                color = Color.White
                            )
                        }
                    }
                }

                Spacer(Modifier.width(GratiaTheme.spacing.mediumSmall))

                // Title + Artist with crossfade
                Column(modifier = Modifier.weight(1f)) {
                    AnimatedText(
                        text = pageSong.title,
                        style = GratiaTheme.typography.body.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium),
                        color = GratiaTheme.colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fadeDurationMs = motion.normal,
                        isMarquee = true
                    )
                    AnimatedText(
                        text = pageSong.artist,
                        style = GratiaTheme.typography.caption,
                        color = GratiaTheme.colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fadeDurationMs = motion.normal
                    )
                }

                // Play/Pause - Redesigned Circular Button
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(GratiaTheme.colors.textPrimary.copy(alpha = 0.05f), androidx.compose.foundation.shape.CircleShape)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = androidx.compose.foundation.LocalIndication.current,
                            onClick = {
                                haptics.light(view)
                                playerViewModel.togglePlay()
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedContent(
                        targetState = isPlaying,
                        transitionSpec = {
                            (scaleIn(animationSpec = spring(dampingRatio = 0.6f, stiffness = 800f)) + fadeIn(tween(150))) togetherWith
                            (scaleOut(animationSpec = spring(dampingRatio = 0.6f, stiffness = 800f)) + fadeOut(tween(150)))
                        },
                        label = "miniPlayPause"
                    ) { playing ->
                        androidx.compose.material3.Icon(
                            imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (playing) "Pause" else "Play",
                            tint = GratiaTheme.colors.textPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Sleep Timer Countdown
                androidx.compose.animation.AnimatedVisibility(
                    visible = sleepTimerActive,
                    enter = fadeIn() + androidx.compose.animation.expandHorizontally(),
                    exit = fadeOut() + androidx.compose.animation.shrinkHorizontally()
                ) {
                    val totalSeconds = sleepTimerRemainingMs / 1000
                    val minutes = totalSeconds / 60
                    val seconds = totalSeconds % 60
                    val timeString = String.format("%d:%02d", minutes, seconds)

                    Row(
                        modifier = Modifier
                            .padding(start = GratiaTheme.spacing.small)
                            .background(GratiaTheme.colors.surfaceHover, RoundedCornerShape(12.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.Icon(
                            imageVector = androidx.compose.material.icons.Icons.Outlined.Bedtime,
                            contentDescription = "Sleep Timer",
                            modifier = Modifier.size(12.dp),
                            tint = GratiaTheme.colors.accent
                        )
                        Spacer(Modifier.width(4.dp))
                        com.gratia.music.ui.components.GratiaText(
                            text = timeString,
                            style = GratiaTheme.typography.caption.copy(
                                fontFamily = com.gratia.music.ui.theme.JetBrainsMono,
                                fontSize = 10.sp,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                            ),
                            color = GratiaTheme.colors.accent
                        )
                    }
                }

                // Next
                GratiaIconButton(
                    icon = Icons.Default.SkipNext,
                    contentDescription = "Next",
                    onClick = {
                        haptics.light(view)
                        playerViewModel.nextSong()
                    },
                    tint = GratiaTheme.colors.textSecondary,
                    size = GratiaTheme.icons.small,
                    modifier = Modifier.padding(start = GratiaTheme.spacing.small)
                )
            }

            val progressTrackColor = GratiaTheme.colors.progressTrack
            val accentColor = GratiaTheme.colors.accent

            // Thin progress line at bottom
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .padding(horizontal = 14.dp)
            ) {
                val trackHeight = size.height
                val cornerR = trackHeight / 2f

                // Track
                drawRoundRect(
                    color = progressTrackColor,
                    topLeft = Offset.Zero,
                    size = Size(size.width, trackHeight),
                    cornerRadius = CornerRadius(cornerR, cornerR)
                )

                // Active fill
                val activeWidth = size.width * progress
                if (activeWidth > 0f) {
                    drawRoundRect(
                        color = accentColor,
                        topLeft = Offset.Zero,
                        size = Size(activeWidth, trackHeight),
                        cornerRadius = CornerRadius(cornerR, cornerR)
                    )
                }
            }

        }
    }
}
}


/** Utility: format milliseconds as m:ss */
fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSecs = ms / 1000
    val mins = totalSecs / 60
    val secs = totalSecs % 60
    return "$mins:${secs.toString().padStart(2, '0')}"
}

/** Utility: get initials from a title string */
fun getInitials(title: String): String {
    return title.split(" ").filter { it.isNotBlank() }.take(2).map { it.first().uppercase() }.joinToString("")
}
