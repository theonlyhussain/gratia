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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gratia.music.data.CoverColorCache
import com.gratia.music.lyrics.LyricsDocument
import com.gratia.music.lyrics.LyricsParser
import com.gratia.music.lyrics.LyricsTimingEngine
import com.gratia.music.player.PlayerViewModel
import com.gratia.music.player.RepeatMode
import com.gratia.music.ui.components.AnimatedText
import com.gratia.music.ui.components.GlassSurface
import com.gratia.music.ui.theme.GratiaTheme
import com.gratia.music.ui.theme.Inter
import com.gratia.music.ui.theme.SpaceGrotesk

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
    onDismiss: () -> Unit = { playerViewModel.setExpandedPlayerOpen(false) }
) {
    val currentSong by playerViewModel.currentSong.collectAsState()
    val isPlaying by playerViewModel.isPlaying.collectAsState()
    val currentTimeMs by playerViewModel.currentTimeMs.collectAsState()
    val durationMs by playerViewModel.durationMs.collectAsState()
    val shuffleEnabled by playerViewModel.shuffleEnabled.collectAsState()
    val repeatMode by playerViewModel.repeatMode.collectAsState()

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

    // --- Swipe-to-dismiss state ---
    var dismissOffsetY by remember { mutableFloatStateOf(0f) }
    val dismissAlpha by animateFloatAsState(
        targetValue = if (dismissOffsetY > 0f) {
            (1f - (dismissOffsetY / 800f)).coerceIn(0.3f, 1f)
        } else 1f,
        animationSpec = tween(100),
        label = "dismissAlpha"
    )

    // --- Synced lyrics preview ---
    val parsedLyrics = remember(song.lyricsSynced) {
        if (song.lyricsSynced?.isNotBlank() == true) {
            LyricsParser.parse(song.lyricsSynced)
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
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = "Collapse",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }

                // "PLAYING FROM" in top bar
                AnimatedText(
                    text = song.album ?: "Gratia",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = Inter,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 1
                )

                IconButton(
                    onClick = { showSongMenu = true },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "More",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(22.dp)
                    )
                }
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
                playingFrom = (song.album ?: "GRATIA").uppercase()
            )

            Spacer(Modifier.height(16.dp))

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

            Spacer(Modifier.height(8.dp))

            // --- Primary Controls ---
            PlayerControls(
                isPlaying = isPlaying,
                onPlayPause = { playerViewModel.togglePlay() },
                onPrevious = { playerViewModel.prevSong() },
                onNext = { playerViewModel.nextSong() },
                glowColor = coverColors.vibrant
            )

            Spacer(Modifier.height(16.dp))

            // --- Secondary Actions ---
            SecondaryActionRow(
                shuffleEnabled = shuffleEnabled,
                repeatMode = repeatMode,
                isFavorite = song.isFavorite,
                onToggleShuffle = { playerViewModel.toggleShuffle() },
                onCycleRepeat = { playerViewModel.cycleRepeatMode() },
                onToggleFavorite = { playerViewModel.toggleFavorite(song) },
                onOpenQueue = onOpenQueue,
                onOpenLyrics = onOpenLyrics,
                accentColor = GratiaTheme.colors.accent
            )

            Spacer(Modifier.height(12.dp))

            // --- Lyrics Preview Card ---
            LyricsPreviewCard(
                currentLyricPreview = currentLyricPreview,
                hasLyrics = !song.lyricsPlain.isNullOrBlank() ||
                    !song.lyricsSynced.isNullOrBlank() ||
                    !song.lyrics.isNullOrBlank(),
                plainLyrics = song.lyricsPlain ?: song.lyrics,
                onClick = onOpenLyrics
            )

            Spacer(Modifier.weight(0.05f))
            Spacer(Modifier.navigationBarsPadding())
        }
        
        if (showSongMenu) {
            SongMenuSheet(
                song = song,
                onDismiss = { showSongMenu = false },
                onPlayNext = { },
                onAddToQueue = { },
                onAddToPlaylist = { },
                onToggleLike = { playerViewModel.toggleFavorite(song) },
                onGoToAlbum = { },
                onGoToArtist = { },
                onEditLyrics = { },
                onShare = { },
                onSongInfo = { showSongInfo = true },
                onDelete = { }
            )
        }
        
        if (showSongInfo) {
            SongInfoDialog(
                song = song,
                onDismiss = { showSongInfo = false }
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
            .padding(horizontal = 28.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        backgroundColor = Color.White.copy(alpha = 0.06f),
        borderColorStart = Color.White.copy(alpha = 0.15f),
        borderColorEnd = Color.Transparent
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "LYRICS",
                    fontSize = 9.sp,
                    color = Color.White.copy(alpha = 0.5f),
                    letterSpacing = 2.sp,
                    fontFamily = Inter,
                    fontWeight = FontWeight.Medium
                )
                Icon(
                    Icons.Default.OpenInFull,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.3f),
                    modifier = Modifier.size(12.dp)
                )
            }

            Spacer(Modifier.height(8.dp))

            if (!hasLyrics) {
                Text(
                    "No lyrics added yet",
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.35f),
                    fontFamily = Inter
                )
                Text(
                    "Tap to add lyrics",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.2f),
                    fontFamily = Inter
                )
            } else if (currentLyricPreview != null) {
                AnimatedText(
                    text = currentLyricPreview,
                    fontSize = 15.sp,
                    fontFamily = Inter,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.9f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fadeDurationMs = 400
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Tap to view full lyrics",
                    fontSize = 10.sp,
                    color = Color.White.copy(alpha = 0.25f),
                    fontFamily = Inter
                )
            } else {
                // Show plain lyrics preview
                val previewText = plainLyrics ?: ""
                val preview = previewText.lines().take(3)
                preview.forEachIndexed { index, line ->
                    Text(
                        line,
                        fontSize = 13.sp,
                        fontFamily = Inter,
                        color = when (index) {
                            0 -> Color.White.copy(alpha = 0.85f)
                            1 -> Color.White.copy(alpha = 0.35f)
                            else -> Color.White.copy(alpha = 0.15f)
                        },
                        fontWeight = if (index == 0) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }
    }
}
