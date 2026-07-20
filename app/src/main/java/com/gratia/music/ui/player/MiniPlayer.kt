package com.gratia.music.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
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

    var offsetX by remember { mutableFloatStateOf(0f) }

    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = GratiaTheme.spacing.mediumLarge, vertical = GratiaTheme.spacing.small)
            .graphicsLayer {
                translationX = offsetX
                alpha = (1f - (Math.abs(offsetX) / 1000f)).coerceIn(0f, 1f)
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (Math.abs(offsetX) > 300f) {
                            playerViewModel.clearQueue()
                        }
                        offsetX = 0f
                    },
                    onDragCancel = { offsetX = 0f },
                    onHorizontalDrag = { _, dragAmount ->
                        offsetX += dragAmount
                    }
                )
            },
        shape = GratiaTheme.shapes.extraLarge,
        backgroundColor = GratiaTheme.colors.surface.copy(alpha = 0.92f),
        glowColor = coverColors.dominant,
        elevation = 8.dp, // GDL standard? GratiaTheme.elevation.low? Let's use 8dp as base
        borderColorStart = if (GratiaTheme.colors.isDark) {
            Color.White.copy(alpha = 0.08f)
        } else {
            Color.White.copy(alpha = 0.4f)
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
                        coverArtPath = song.coverArtPath,
                        title = song.title,
                        artist = song.artist,
                        size = GratiaTheme.spacing.heroSmall, // ~48dp
                        cornerRadius = 10.dp, // Or GratiaTheme.shapes.small equivalent
                        fontSize = 12.sp
                    )
                    
                    // Semi-transparent overlay when playing
                    androidx.compose.animation.AnimatedVisibility(
                        visible = isPlaying,
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
                        text = song.title,
                        style = GratiaTheme.typography.body.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium),
                        color = GratiaTheme.colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fadeDurationMs = motion.normal,
                        isMarquee = true
                    )
                    AnimatedText(
                        text = song.artist,
                        style = GratiaTheme.typography.caption,
                        color = GratiaTheme.colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fadeDurationMs = motion.normal
                    )
                }

                // Play/Pause
                Box(
                    modifier = Modifier.size(40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedContent(
                        targetState = isPlaying,
                        transitionSpec = {
                            fadeIn(tween(motion.normal)) togetherWith fadeOut(tween(motion.fast))
                        },
                        label = "miniPlayPause"
                    ) { playing ->
                        GratiaIconButton(
                            icon = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (playing) "Pause" else "Play",
                            onClick = {
                                haptics.light(view)
                                playerViewModel.togglePlay()
                            },
                            tint = GratiaTheme.colors.textPrimary,
                            size = GratiaTheme.icons.normal
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

            Spacer(Modifier.height(2.dp))
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
