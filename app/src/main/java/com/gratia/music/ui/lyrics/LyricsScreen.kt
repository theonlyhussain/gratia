package com.gratia.music.ui.lyrics

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gratia.music.data.CoverColorCache
import com.gratia.music.lyrics.LyricsDocument
import com.gratia.music.lyrics.LyricsParser
import com.gratia.music.player.PlayerViewModel
import com.gratia.music.ui.components.CoverArtImage
import com.gratia.music.ui.player.formatTime
import com.gratia.music.ui.theme.GratiaTheme
import com.gratia.music.ui.theme.Inter
import com.gratia.music.ui.theme.SpaceGrotesk
import kotlinx.coroutines.isActive

/**
 * Premium Apple Music-style lyrics screen.
 * Orchestrates models parsing, custom kinetic scroll layout, top/bottom fade shader masks,
 * animated background, and player control bindings.
 */
@Composable
fun LyricsScreen(
    playerViewModel: PlayerViewModel,
    songId: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentSong by playerViewModel.currentSong.collectAsState()
    val isPlaying by playerViewModel.isPlaying.collectAsState()
    val currentTimeMs by playerViewModel.currentTimeMs.collectAsState()
    val durationMs by playerViewModel.durationMs.collectAsState()

    // Smooth interpolator for time tracking to avoid layout jumps between ticks
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

    val song = currentSong
    if (song == null) {
        EmptyLyricsState(onBack = onBack, message = "No song playing", detail = "Play a song to see lyrics")
        return
    }

    // Dynamic background color extractor
    var coverColors by remember { mutableStateOf(CoverColorCache.FALLBACK) }
    LaunchedEffect(song.id, song.coverArtPath) {
        coverColors = CoverColorCache.getColors(song.id, song.coverArtPath)
    }

    val animatedDominant by animateColorAsState(
        targetValue = coverColors.dominant,
        animationSpec = tween(800),
        label = "dominantColor"
    )
    val animatedDarkMuted by animateColorAsState(
        targetValue = coverColors.darkMuted,
        animationSpec = tween(800),
        label = "darkMutedColor"
    )

    // Parser integration
    val lyricsRaw = when {
        song.lyricsSynced?.isNotBlank() == true -> song.lyricsSynced
        song.lyricsPlain?.isNotBlank() == true -> song.lyricsPlain
        else -> ""
    }

    val parsedDocument = remember(lyricsRaw) {
        LyricsParser.parse(lyricsRaw)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
    ) {
        // Animated gradient background
        LyricsBackground(
            customDominantColor = animatedDominant,
            customDarkMutedColor = animatedDarkMuted
        )

        // Subtle dark mask for contrast
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.35f))
        )

        // Contents layout
        Column(modifier = Modifier.fillMaxSize()) {
            // Header Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f))
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Collapse",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(Modifier.width(12.dp))

                CoverArtImage(
                    coverArtPath = song.coverArtPath,
                    title = song.title,
                    artist = song.artist,
                    size = 36.dp,
                    cornerRadius = 8.dp,
                    fontSize = 10.sp
                )

                Spacer(Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        fontFamily = SpaceGrotesk,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = song.artist,
                        fontFamily = Inter,
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.5f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(onClick = { playerViewModel.toggleFavorite(song) }) {
                    Icon(
                        imageVector = if (song.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (song.isFavorite) GratiaTheme.colors.accent else Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Lyrics Main View Container
            Box(
                modifier = Modifier
                    .weight(1f)
                    // Hardware-accelerated offscreen composition layer for dissolve shader mask
                    .graphicsLayer { alpha = 0.99f }
                    .drawWithContent {
                        drawContent()
                        val maskBrush = Brush.verticalGradient(
                            0.0f to Color.Transparent,
                            0.12f to Color.Black,
                            0.88f to Color.Black,
                            1.0f to Color.Transparent,
                            startY = 0f,
                            endY = size.height
                        )
                        drawRect(brush = maskBrush, blendMode = BlendMode.DstIn)
                    }
            ) {
                when (parsedDocument) {
                    is LyricsDocument.Plain -> {
                        PlainLyricsView(
                            text = parsedDocument.text,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    is LyricsDocument.LineSynced -> {
                        KineticLyricsColumn(
                            lines = parsedDocument.lines,
                            currentTimeMs = visualTimeMs,
                            onLineClick = { seekMs -> playerViewModel.seekTo(seekMs) },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    is LyricsDocument.WordSynced -> {
                        KineticLyricsColumn(
                            lines = parsedDocument.lines,
                            currentTimeMs = visualTimeMs,
                            onLineClick = { seekMs -> playerViewModel.seekTo(seekMs) },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            // Bottom Player Control Panel
            CompactControlsPanel(
                playerViewModel = playerViewModel,
                isPlaying = isPlaying,
                currentTimeMs = visualTimeMs,
                durationMs = durationMs
            )
        }
    }
}

@Composable
private fun CompactControlsPanel(
    playerViewModel: PlayerViewModel,
    isPlaying: Boolean,
    currentTimeMs: Long,
    durationMs: Long
) {
    val progress = if (durationMs > 0) currentTimeMs.toFloat() / durationMs.toFloat() else 0f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.5f))
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Slider(
            value = progress,
            onValueChange = { playerViewModel.seekTo((it * durationMs).toLong()) },
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = GratiaTheme.colors.accent,
                inactiveTrackColor = Color.White.copy(alpha = 0.15f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatTime(currentTimeMs),
                fontSize = 10.sp,
                color = Color.White.copy(alpha = 0.4f),
                fontFamily = Inter
            )
            Text(
                text = formatTime(durationMs),
                fontSize = 10.sp,
                color = Color.White.copy(alpha = 0.4f),
                fontFamily = Inter
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { playerViewModel.prevSong() },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SkipPrevious,
                    contentDescription = "Previous",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(Modifier.width(16.dp))
            IconButton(
                onClick = { playerViewModel.togglePlay() },
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color.White)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "Play/Pause",
                    tint = Color.Black,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(Modifier.width(16.dp))
            IconButton(
                onClick = { playerViewModel.nextSong() },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = "Next",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptyLyricsState(
    onBack: () -> Unit,
    message: String,
    detail: String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A))
            .statusBarsPadding()
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .padding(12.dp)
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.08f))
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "Collapse",
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 40.dp)
            ) {
                // Large icon with ambient glow
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(120.dp)
                ) {
                    // Glow circle behind icon
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        GratiaTheme.colors.accent.copy(alpha = 0.15f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )
                    Icon(
                        imageVector = Icons.Default.Lyrics,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = Color.White.copy(alpha = 0.4f)
                    )
                }

                Spacer(Modifier.height(24.dp))

                // Headline
                Text(
                    text = message,
                    fontFamily = SpaceGrotesk,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    color = Color.White.copy(alpha = 0.85f),
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(10.dp))

                // Description
                Text(
                    text = detail,
                    fontFamily = Inter,
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.4f),
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp
                )
            }
        }
    }
}

