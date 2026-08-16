package com.gratia.music.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gratia.music.data.model.SongEntity
import com.gratia.music.player.PlayerViewModel
import com.gratia.music.player.RepeatMode
import com.gratia.music.ui.components.CoverArtImage
import com.gratia.music.ui.components.bounceClick
import com.gratia.music.ui.theme.GratiaTheme
import com.gratia.music.ui.theme.Inter
import com.gratia.music.ui.theme.SpaceGrotesk
import org.burnoutcrew.reorderable.ReorderableItem
import org.burnoutcrew.reorderable.detectReorderAfterLongPress
import org.burnoutcrew.reorderable.rememberReorderableLazyListState
import org.burnoutcrew.reorderable.reorderable

/**
 * Inline queue content embedded directly inside the expanded player.
 *
 * This replaces the old ModalBottomSheet-based QueueSheet for the expanded
 * player context. The same queue data (playerViewModel.queue, history, etc.)
 * is used, but rendered inline as part of the player's content area.
 *
 * Layout:
 * - Shuffle / Repeat / Autoplay pill buttons (with animated reactions)
 * - "Continue Playing" section header
 * - Current song row
 * - "Up Next" header
 * - Upcoming song rows with drag-to-reorder
 * - History section (collapsible)
 */
@Composable
fun InlineQueueContent(
    playerViewModel: PlayerViewModel,
    listState: LazyListState = rememberLazyListState(),
    modifier: Modifier = Modifier,
    onInteraction: () -> Unit = {}
) {
    val currentSong by playerViewModel.currentSong.collectAsState()
    val queue by playerViewModel.queue.collectAsState()
    val history by playerViewModel.history.collectAsState()
    val favoriteSongIds by playerViewModel.favoriteSongIds.collectAsState()
    val shuffleEnabled by playerViewModel.shuffleEnabled.collectAsState()
    val repeatMode by playerViewModel.repeatMode.collectAsState()
    val autoplayEnabled by playerViewModel.autoplayEnabled.collectAsState()

    var showClearHistoryDialog by remember { mutableStateOf(false) }

    val current = currentSong
    val upcomingStartIndex = if (current != null) {
        val idx = queue.indexOfFirst { it.id == current.id }
        if (idx >= 0) idx + 1 else 0
    } else 0

    val reorderableState = rememberReorderableLazyListState(
        listState = listState,
        onMove = { from, to ->
            val fromAdjusted = (from.index - 1) + upcomingStartIndex
            val toAdjusted = (to.index - 1) + upcomingStartIndex
            if (fromAdjusted >= upcomingStartIndex && toAdjusted >= upcomingStartIndex &&
                fromAdjusted < queue.size && toAdjusted < queue.size
            ) {
                playerViewModel.moveInQueue(fromAdjusted, toAdjusted)
            }
        }
    )

    LazyColumn(
        state = reorderableState.listState,
        modifier = modifier
            .fillMaxSize()
            .reorderable(reorderableState),
        contentPadding = PaddingValues(bottom = 200.dp)
    ) {
        item {
            Column {
                Spacer(Modifier.height(GratiaTheme.spacing.base))

                // --- Shuffle / Repeat / Autoplay pill buttons ---
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AnimatedShufflePill(
                        isActive = shuffleEnabled,
                        onClick = {
                            onInteraction()
                            playerViewModel.toggleShuffle()
                        },
                        modifier = Modifier.weight(1f)
                    )

                    AnimatedRepeatPill(
                        repeatMode = repeatMode,
                        onClick = {
                            onInteraction()
                            playerViewModel.cycleRepeatMode()
                        },
                        modifier = Modifier.weight(1f)
                    )

                    AnimatedAutoplayPill(
                        isActive = autoplayEnabled,
                        onClick = {
                            onInteraction()
                            playerViewModel.toggleAutoplay()
                        },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(GratiaTheme.spacing.mediumLarge))

                // --- "Continue Playing" section ---
                if (current != null) {
                    Text(
                        "Continue Playing",
                        fontFamily = SpaceGrotesk,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
                    )
                    Text(
                        "From ${current.album ?: "your library"}",
                        fontFamily = Inter,
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 2.dp)
                    )
                }

                Spacer(Modifier.height(GratiaTheme.spacing.mediumSmall))

                if (queue.isEmpty()) {
                    InlineQueueEmptyState()
                }
            }
        }

        if (queue.isNotEmpty()) {
            val upcoming = if (upcomingStartIndex < queue.size) {
                queue.subList(upcomingStartIndex, queue.size)
            } else emptyList()

            itemsIndexed(
                upcoming,
                key = { index, song -> "iq_${song.id}_$index" }
            ) { index, song ->
                ReorderableItem(reorderableState, key = "iq_${song.id}_$index") { isDragging ->
                    InlineQueueRow(
                        song = song,
                        onPlay = {
                            onInteraction()
                            playerViewModel.playFromQueue(upcomingStartIndex + index)
                        },
                        modifier = Modifier
                            .animateItem()
                            .background(
                                if (isDragging) Color.White.copy(alpha = 0.08f)
                                else Color.Transparent
                            )
                            .detectReorderAfterLongPress(reorderableState)
                    )
                }
            }
        }

        // --- History section ---
        if (history.isNotEmpty()) {
            item {
                Spacer(Modifier.height(32.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "History",
                        fontFamily = SpaceGrotesk,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = Color.White
                    )
                    Text(
                        "Clear",
                        fontFamily = Inter,
                        fontSize = 15.sp,
                        color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.bounceClick {
                            onInteraction()
                            showClearHistoryDialog = true
                        }
                    )
                }
                Spacer(Modifier.height(8.dp))
            }

            itemsIndexed(
                history.reversed(),
                key = { index, song -> "ih_${song.id}_$index" }
            ) { index, song ->
                InlineQueueRow(
                    song = song,
                    onPlay = {
                        onInteraction()
                        playerViewModel.playSong(song, history)
                    },
                    modifier = Modifier.alpha(0.6f),
                    showDragHandle = false
                )
            }
        }
    }

    // Clear history dialog
    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            title = {
                Text(
                    "Clear listening history?",
                    fontFamily = SpaceGrotesk,
                    fontWeight = FontWeight.Bold,
                    color = GratiaTheme.colors.textPrimary
                )
            },
            text = {
                Text(
                    "Clearing your history will allow previously played songs to become eligible for playback again.",
                    fontFamily = Inter,
                    color = GratiaTheme.colors.textSecondary
                )
            },
            containerColor = GratiaTheme.colors.surface,
            confirmButton = {
                TextButton(onClick = {
                    playerViewModel.clearHistory()
                    showClearHistoryDialog = false
                }) {
                    Text("Clear History", color = GratiaTheme.colors.accent, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) {
                    Text("Cancel", color = GratiaTheme.colors.textSecondary)
                }
            }
        )
    }
}

/**
 * Queue row adapted for the inline player context.
 * Uses white text on the player's blurred background.
 */
@Composable
private fun InlineQueueRow(
    song: SongEntity,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
    showDragHandle: Boolean = true
) {
    val hapticFeedback = LocalHapticFeedback.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .bounceClick {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                onPlay()
            }
            .padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CoverArtImage(
            coverArtPath = song.coverArtPath,
            title = song.title,
            artist = song.artist,
            size = 48.dp,
            cornerRadius = 8.dp,
            fontSize = 14.sp
        )

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                song.title,
                fontFamily = SpaceGrotesk,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                song.artist,
                fontFamily = Inter,
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.5f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (showDragHandle) {
            Icon(
                Icons.Default.DragHandle,
                contentDescription = "Reorder",
                tint = Color.White.copy(alpha = 0.4f),
                modifier = Modifier.size(20.dp)
            )
        } else {
            Spacer(Modifier.width(20.dp))
        }
    }
}

/**
 * Shuffle pill with animated scale reaction on toggle.
 */
@Composable
private fun AnimatedShufflePill(
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hapticFeedback = LocalHapticFeedback.current

    // Animate the scale on state change for a satisfying "pop"
    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
        label = "shuffleScale"
    )

    val bgColor by animateColorAsState(
        targetValue = if (isActive) Color.White.copy(alpha = 0.2f)
        else Color.White.copy(alpha = 0.08f),
        animationSpec = tween(250),
        label = "shuffleBg"
    )

    val contentColor by animateColorAsState(
        targetValue = if (isActive) Color.White else Color.White.copy(alpha = 0.5f),
        animationSpec = tween(250),
        label = "shuffleContent"
    )

    Box(
        modifier = modifier
            .height(36.dp)
            .scale(scale)
            .clip(RoundedCornerShape(18.dp))
            .background(bgColor)
            .bounceClick {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.Shuffle,
            contentDescription = "Shuffle",
            tint = contentColor,
            modifier = Modifier.size(18.dp)
        )
    }
}

/**
 * Repeat pill with animated state transitions and badge for repeat-one.
 */
@Composable
private fun AnimatedRepeatPill(
    repeatMode: RepeatMode,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hapticFeedback = LocalHapticFeedback.current
    val isActive = repeatMode != RepeatMode.OFF

    val bgColor by animateColorAsState(
        targetValue = if (isActive) Color.White.copy(alpha = 0.2f)
        else Color.White.copy(alpha = 0.08f),
        animationSpec = tween(250),
        label = "repeatBg"
    )

    val contentColor by animateColorAsState(
        targetValue = if (isActive) Color.White else Color.White.copy(alpha = 0.5f),
        animationSpec = tween(250),
        label = "repeatContent"
    )

    Box(
        modifier = modifier
            .height(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(bgColor)
            .bounceClick {
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
                tint = contentColor,
                modifier = Modifier.size(20.dp)
            )

            // Badge for repeat-one with animated entrance
            AnimatedVisibility(
                visible = repeatMode == RepeatMode.ONE,
                enter = fadeIn(tween(150)) + scaleIn(tween(150)),
                exit = fadeOut(tween(100)) + scaleOut(tween(100))
            ) {
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
 * Autoplay (Infinite Mix) pill with animated toggle.
 */
@Composable
private fun AnimatedAutoplayPill(
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hapticFeedback = LocalHapticFeedback.current

    val bgColor by animateColorAsState(
        targetValue = if (isActive) Color.White.copy(alpha = 0.2f)
        else Color.White.copy(alpha = 0.08f),
        animationSpec = tween(250),
        label = "autoplayBg"
    )

    val contentColor by animateColorAsState(
        targetValue = if (isActive) Color.White else Color.White.copy(alpha = 0.5f),
        animationSpec = tween(250),
        label = "autoplayContent"
    )

    Box(
        modifier = modifier
            .height(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(bgColor)
            .bounceClick {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.AllInclusive,
            contentDescription = "Autoplay",
            tint = contentColor,
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * Empty state for the inline queue.
 */
@Composable
private fun InlineQueueEmptyState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.MusicNote,
                contentDescription = "Empty queue",
                tint = Color.White.copy(alpha = 0.3f),
                modifier = Modifier.size(48.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Queue is Empty",
                fontFamily = SpaceGrotesk,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = Color.White.copy(alpha = 0.7f)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Play a song and it will appear here",
                fontFamily = Inter,
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.4f),
                textAlign = TextAlign.Center
            )
        }
    }
}
