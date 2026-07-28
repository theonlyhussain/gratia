package com.gratia.music.ui.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gratia.music.data.CoverColorCache
import com.gratia.music.data.model.SongEntity
import com.gratia.music.player.PlayerViewModel
import com.gratia.music.player.RepeatMode
import com.gratia.music.ui.components.CoverArtImage
import com.gratia.music.ui.theme.GratiaTheme
import com.gratia.music.ui.theme.Inter
import com.gratia.music.ui.theme.SpaceGrotesk
import org.burnoutcrew.reorderable.rememberReorderableLazyListState
import org.burnoutcrew.reorderable.reorderable
import org.burnoutcrew.reorderable.ReorderableItem
import org.burnoutcrew.reorderable.detectReorderAfterLongPress

/**
 * Redesigned queue bottom sheet matching the Apple Music reference.
 *
 * Layout:
 * - Current song info at top
 * - Shuffle / Repeat pill buttons
 * - "Continue Playing" section with song list
 * - Drag handles for reorder
 * - Tinted background from current song's dominant color
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(
    playerViewModel: PlayerViewModel,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentSong by playerViewModel.currentSong.collectAsState()
    val queue by playerViewModel.queue.collectAsState()
    val shuffleEnabled by playerViewModel.shuffleEnabled.collectAsState()
    val repeatMode by playerViewModel.repeatMode.collectAsState()

    val current = currentSong
    val upcomingStartIndex = if (current != null) {
        val idx = queue.indexOfFirst { it.id == current.id }
        if (idx >= 0) idx + 1 else 0
    } else 0

    // Extract dominant color for tinted background
    var coverColors by remember { mutableStateOf(CoverColorCache.FALLBACK) }
    LaunchedEffect(current?.id, current?.coverArtPath) {
        if (current != null) {
            coverColors = CoverColorCache.getColors(current.id, current.coverArtPath)
        }
    }

    val bgTop by animateColorAsState(
        targetValue = coverColors.dominant,
        animationSpec = tween(500),
        label = "queueBgTop"
    )
    val bgBottom by animateColorAsState(
        targetValue = coverColors.darkMuted,
        animationSpec = tween(500),
        label = "queueBgBottom"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight(0.75f)
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(bgTop, bgBottom)
                )
            )
            .padding(top = 12.dp)
    ) {
        // Drag indicator
        Box(
            modifier = Modifier
                .width(36.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(alpha = 0.3f))
                .align(Alignment.CenterHorizontally)
        )

        Spacer(Modifier.height(16.dp))

        // --- Current song info header ---
        if (current != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CoverArtImage(
                    coverArtPath = current.coverArtPath,
                    title = current.title,
                    artist = current.artist,
                    size = 48.dp,
                    cornerRadius = 10.dp,
                    fontSize = 14.sp
                )

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        current.title,
                        fontFamily = SpaceGrotesk,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        current.artist,
                        fontFamily = Inter,
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Three-dots menu
                IconButton(onClick = { /* Could open song menu */ }) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "Now playing",
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // --- Shuffle / Repeat pill buttons ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Shuffle pill
                ShufflePill(
                    isActive = shuffleEnabled,
                    onClick = { playerViewModel.toggleShuffle() },
                    modifier = Modifier.weight(1f)
                )

                // Repeat pill
                RepeatPill(
                    repeatMode = repeatMode,
                    onClick = { playerViewModel.cycleRepeatMode() },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(16.dp))

            // --- "Continue Playing" header ---
            Text(
                "Continue Playing",
                fontFamily = SpaceGrotesk,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Text(
                "From ${current.album ?: "your library"}",
                fontFamily = Inter,
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
            )
        }

        Spacer(Modifier.height(8.dp))

        if (queue.isEmpty()) {
            QueueEmptyState()
        } else {
            val listState = rememberReorderableLazyListState(
                onMove = { from, to ->
                    val fromAdjusted = from.index + upcomingStartIndex
                    val toAdjusted = to.index + upcomingStartIndex
                    if (fromAdjusted >= upcomingStartIndex && toAdjusted >= upcomingStartIndex && fromAdjusted < queue.size && toAdjusted < queue.size) {
                        playerViewModel.moveInQueue(fromAdjusted, toAdjusted)
                    }
                }
            )

            LazyColumn(
                state = listState.listState,
                modifier = Modifier
                    .fillMaxSize()
                    .reorderable(listState),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                val upcoming = if (upcomingStartIndex < queue.size) {
                    queue.subList(upcomingStartIndex, queue.size)
                } else emptyList()

                itemsIndexed(
                    upcoming,
                    key = { index, song -> "queue_${song.id}_$index" }
                ) { index, song ->
                    ReorderableItem(listState, key = "queue_${song.id}_$index") { isDragging ->
                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { dismissValue ->
                                if (dismissValue != SwipeToDismissBoxValue.Settled) {
                                    playerViewModel.removeFromQueue(song.id)
                                    true
                                } else {
                                    false
                                }
                            }
                        )

                        SwipeToDismissBox(
                            state = dismissState,
                            modifier = Modifier
                                .animateItem()
                                .background(if (isDragging) GratiaTheme.colors.surfaceHover else Color.Transparent),
                            enableDismissFromStartToEnd = true,
                            enableDismissFromEndToStart = true,
                            backgroundContent = {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(GratiaTheme.colors.error)
                                        .padding(horizontal = 24.dp),
                                    contentAlignment = if (dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Remove",
                                        tint = GratiaTheme.colors.background
                                    )
                                }
                            },
                            content = {
                                QueueRow(
                                    song = song,
                                    index = upcomingStartIndex + index,
                                    isCurrentSong = false,
                                    onPlay = { playerViewModel.playFromQueue(upcomingStartIndex + index) },
                                    onRemove = { playerViewModel.removeFromQueue(song.id) },
                                    modifier = Modifier.detectReorderAfterLongPress(listState)
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Shuffle pill button — rounded, filled when active.
 */
@Composable
private fun ShufflePill(
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hapticFeedback = LocalHapticFeedback.current
    val bgColor by animateColorAsState(
        targetValue = if (isActive) Color.White.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.1f),
        animationSpec = tween(200),
        label = "shuffleBg"
    )

    Box(
        modifier = modifier
            .height(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(bgColor)
            .clickable {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.Shuffle,
            contentDescription = "Shuffle",
            tint = if (isActive) Color.White else Color.White.copy(alpha = 0.5f),
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * Repeat pill button with badge.
 * OFF: dimmed icon, no badge
 * ALL: highlighted icon
 * ONE: highlighted icon + "1" badge
 */
@Composable
private fun RepeatPill(
    repeatMode: RepeatMode,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hapticFeedback = LocalHapticFeedback.current
    val isActive = repeatMode != RepeatMode.OFF
    val bgColor by animateColorAsState(
        targetValue = if (isActive) Color.White.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.1f),
        animationSpec = tween(200),
        label = "repeatBg"
    )

    Box(
        modifier = modifier
            .height(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(bgColor)
            .clickable {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.Repeat,
                contentDescription = "Repeat",
                tint = if (isActive) Color.White else Color.White.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp)
            )

            // Show badge for repeat one
            if (repeatMode == RepeatMode.ONE) {
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "1",
                    fontFamily = Inter,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = Color.White
                )
            }
        }
    }
}

/**
 * Queue row for upcoming songs.
 */
@Composable
private fun QueueRow(
    song: SongEntity,
    index: Int,
    isCurrentSong: Boolean,
    onPlay: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hapticFeedback = LocalHapticFeedback.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                onPlay()
            }
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CoverArtImage(
            coverArtPath = song.coverArtPath,
            title = song.title,
            artist = song.artist,
            size = 44.dp,
            cornerRadius = 8.dp,
            fontSize = 11.sp
        )

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                song.title,
                fontFamily = SpaceGrotesk,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                song.artist,
                fontFamily = Inter,
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Drag handle
        Icon(
            Icons.Default.DragHandle,
            contentDescription = "Reorder",
            tint = Color.White.copy(alpha = 0.3f),
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * Beautiful empty state for the queue.
 */
@Composable
private fun QueueEmptyState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                Icons.Default.MusicNote,
                contentDescription = "Empty queue",
                tint = Color.White.copy(alpha = 0.2f),
                modifier = Modifier.size(64.dp)
            )

            Spacer(Modifier.height(20.dp))

            Text(
                "Queue is Empty",
                fontFamily = SpaceGrotesk,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = Color.White
            )

            Spacer(Modifier.height(8.dp))

            Text(
                "Play a song and it will appear here\nwith the rest of your queue",
                fontFamily = Inter,
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.5f),
                lineHeight = 20.sp,
                modifier = Modifier.padding(horizontal = 32.dp),
                textAlign = TextAlign.Center
            )
        }
    }
}
