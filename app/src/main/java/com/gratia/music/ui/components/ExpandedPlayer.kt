package com.gratia.music.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.alpha
import coil.compose.AsyncImage
import java.io.File
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gratia.music.data.CoverColorCache
import com.gratia.music.lyrics.LyricsParser
import com.gratia.music.lyrics.LyricsDocument
import com.gratia.music.lyrics.LyricsTimingEngine
import com.gratia.music.player.PlayerViewModel
import com.gratia.music.player.RepeatMode
import com.gratia.music.ui.theme.GratiaTheme
import com.gratia.music.ui.theme.Inter
import com.gratia.music.ui.theme.SpaceGrotesk
import com.gratia.music.ui.components.liquidGlass

import kotlinx.coroutines.isActive

/**
 * Full-screen expanded player with dynamic cover-art background.
 * Apple Music-inspired premium layout with smooth transitions.
 */
@Composable
fun ExpandedPlayer(
    playerViewModel: PlayerViewModel,
    onOpenLyrics: () -> Unit = {}
) {
    val currentSong by playerViewModel.currentSong.collectAsState()
    val isPlaying by playerViewModel.isPlaying.collectAsState()
    val currentTimeMs by playerViewModel.currentTimeMs.collectAsState()
    val durationMs by playerViewModel.durationMs.collectAsState()
    val shuffleEnabled by playerViewModel.shuffleEnabled.collectAsState()
    val repeatMode by playerViewModel.repeatMode.collectAsState()

    val song = currentSong ?: return

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

    val progress = if (durationMs > 0) visualTimeMs.toFloat() / durationMs.toFloat() else 0f

    // Extract colors from cover art (cached, done on IO thread)
    var coverColors by remember { mutableStateOf(CoverColorCache.FALLBACK) }
    LaunchedEffect(song.id, song.coverArtPath) {
        coverColors = CoverColorCache.getColors(song.id, song.coverArtPath)
    }

    // Animated background colors for smooth transitions
    val animatedDominant by animateColorAsState(
        targetValue = coverColors.dominant,
        animationSpec = tween(800),
        label = "bgDominant"
    )
    val animatedDarkMuted by animateColorAsState(
        targetValue = coverColors.darkMuted,
        animationSpec = tween(800),
        label = "bgDarkMuted"
    )

    // Cached synced lyrics preview — only recalculate when song changes or active line changes
    val parsedLyrics = remember(song.lyricsSynced, song.lyricsMode) {
        if (song.lyricsMode == "synced" && !song.lyricsSynced.isNullOrBlank()) {
            LyricsParser.parse(song.lyricsSynced)
        } else null
    }

    val currentLyricPreview by remember(parsedLyrics, visualTimeMs) {
        derivedStateOf {
            val doc = parsedLyrics
            if (doc != null) {
                when (doc) {
                    is LyricsDocument.LineSynced -> {
                        val activeIndex = LyricsTimingEngine.findActiveLineIndex(doc.lines, visualTimeMs)
                        doc.lines.getOrNull(activeIndex)?.text
                    }
                    is LyricsDocument.WordSynced -> {
                        val activeIndex = LyricsTimingEngine.findActiveLineIndex(doc.lines, visualTimeMs)
                        doc.lines.getOrNull(activeIndex)?.text
                    }
                    else -> null
                }
            } else null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        animatedDominant,
                        animatedDarkMuted,
                        Color(0xFF0A0808)
                    )
                )
            )
    ) {
        // Apple-style background is handled by the gradient Brush above, which is highly performant.

        // Dark overlay for readability
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.35f))
        )

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
                    onClick = { playerViewModel.setExpandedPlayerOpen(false) },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(Icons.Default.KeyboardArrowDown, null, tint = Color.White, modifier = Modifier.size(28.dp))
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("PLAYING FROM", fontSize = 9.sp, color = Color.White.copy(alpha = 0.6f), fontFamily = Inter, letterSpacing = 2.sp)
                    Text(song.album ?: "Gratia", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White, fontFamily = Inter)
                }

                IconButton(onClick = { }, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Default.MoreVert, null, tint = Color.White, modifier = Modifier.size(22.dp))
                }
            }

            Spacer(Modifier.weight(0.25f))

            // Cover artwork with scale animation
            val coverScale by animateFloatAsState(
                targetValue = if (isPlaying) 1f else 0.85f,
                animationSpec = tween(600, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                label = "coverScale"
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 40.dp)
                    .aspectRatio(1f)
                    .scale(coverScale),
                contentAlignment = Alignment.Center
            ) {
                CoverArtImage(
                    coverArtPath = song.coverArtPath,
                    title = song.title,
                    artist = song.artist,
                    size = 300.dp,
                    cornerRadius = 16.dp,
                    fontSize = 60.sp,
                    modifier = Modifier
                        .fillMaxSize()
                        .shadow(if (isPlaying) 24.dp else 8.dp, RoundedCornerShape(16.dp))
                )
            }

            Spacer(Modifier.weight(0.12f))

            // Track info + favorite
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        song.title,
                        fontFamily = SpaceGrotesk,
                        fontWeight = FontWeight.Bold,
                        fontSize = 26.sp,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        song.artist,
                        fontFamily = Inter,
                        fontSize = 15.sp,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
                IconButton(onClick = { playerViewModel.toggleFavorite(song) }) {
                    Icon(
                        if (song.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        null,
                        tint = if (song.isFavorite) GratiaTheme.colors.accent else Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // Progress bar
            Column(modifier = Modifier.padding(horizontal = 28.dp)) {
                Slider(
                    value = progress,
                    onValueChange = { newProgress ->
                        playerViewModel.seekTo((newProgress * durationMs).toLong())
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color.White.copy(alpha = 0.15f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(formatTime(visualTimeMs), fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f), fontFamily = Inter)
                    Text(formatTime(durationMs), fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f), fontFamily = Inter)
                }
            }

            Spacer(Modifier.height(12.dp))

            // Main controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { playerViewModel.prevSong() }, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Default.SkipPrevious, null, tint = Color.White, modifier = Modifier.size(36.dp))
                }

                Spacer(Modifier.width(24.dp))

                IconButton(
                    onClick = { playerViewModel.togglePlay() },
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                ) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(40.dp)
                    )
                }

                Spacer(Modifier.width(24.dp))

                IconButton(onClick = { playerViewModel.nextSong() }, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Default.SkipNext, null, tint = Color.White, modifier = Modifier.size(36.dp))
                }
            }

            Spacer(Modifier.height(20.dp))

            // Secondary controls row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { playerViewModel.toggleShuffle() }) {
                    Icon(
                        Icons.Default.Shuffle,
                        null,
                        tint = if (shuffleEnabled) GratiaTheme.colors.accent else androidx.compose.ui.graphics.Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(24.dp)
                    )
                }
                
                Spacer(Modifier.width(48.dp))

                IconButton(onClick = { playerViewModel.cycleRepeatMode() }) {
                    Icon(
                        when (repeatMode) {
                            RepeatMode.ONE -> Icons.Default.RepeatOne
                            else -> Icons.Default.Repeat
                        },
                        null,
                        tint = if (repeatMode != RepeatMode.OFF) GratiaTheme.colors.accent else androidx.compose.ui.graphics.Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Lyrics preview card — tappable
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp)
                    .liquidGlass(
                        shape = RoundedCornerShape(16.dp),
                        backgroundColor = Color.White.copy(alpha = 0.06f),
                        borderColorStart = Color.White.copy(alpha = 0.2f),
                        borderColorEnd = Color.Transparent
                    )
                    .clickable { onOpenLyrics() },
                shape = RoundedCornerShape(16.dp),
                color = Color.Transparent,
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("LYRICS", fontSize = 9.sp, color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.6f), letterSpacing = 2.sp, fontFamily = Inter)
                        Icon(Icons.Default.OpenInFull, null, tint = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.4f), modifier = Modifier.size(14.dp))
                    }
                    Spacer(Modifier.height(8.dp))

                    // Determine what lyrics to show
                    val plainLyrics = song.lyricsPlain ?: song.lyrics
                    val hasSyncedLyrics = song.lyricsMode == "synced" && !song.lyricsSynced.isNullOrBlank()
                    val hasAnyLyrics = !plainLyrics.isNullOrBlank() || hasSyncedLyrics

                    if (!hasAnyLyrics) {
                        Text("No lyrics added yet", fontSize = 13.sp, color = Color.White.copy(alpha = 0.4f), fontFamily = Inter)
                        Text("Tap to add lyrics", fontSize = 11.sp, color = Color.White.copy(alpha = 0.25f), fontFamily = Inter)
                    } else if (hasSyncedLyrics && currentLyricPreview != null) {
                        Text(
                            currentLyricPreview!!,
                            fontSize = 15.sp,
                            fontFamily = Inter,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White.copy(alpha = 0.95f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(4.dp))
                        Text("Tap to view full lyrics", fontSize = 11.sp, color = Color.White.copy(alpha = 0.3f), fontFamily = Inter)
                    } else {
                        val previewText = plainLyrics ?: ""
                        val preview = previewText.lines().take(3)
                        preview.forEachIndexed { index, line ->
                            Text(
                                line,
                                fontSize = 13.sp,
                                fontFamily = Inter,
                                color = when (index) {
                                    0 -> Color.White.copy(alpha = 0.9f)
                                    1 -> Color.White.copy(alpha = 0.4f)
                                    else -> Color.White.copy(alpha = 0.2f)
                                },
                                fontWeight = if (index == 0) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.weight(0.08f))
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}
