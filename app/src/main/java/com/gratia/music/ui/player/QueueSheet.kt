package com.gratia.music.ui.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gratia.music.data.model.SongEntity
import com.gratia.music.player.PlayerViewModel
import com.gratia.music.ui.components.CoverArtImage
import com.gratia.music.ui.theme.GratiaTheme
import com.gratia.music.ui.theme.Inter
import com.gratia.music.ui.theme.SpaceGrotesk
import org.burnoutcrew.reorderable.rememberReorderableLazyListState
import org.burnoutcrew.reorderable.reorderable
import org.burnoutcrew.reorderable.ReorderableItem
import org.burnoutcrew.reorderable.detectReorderAfterLongPress

/**
 * Queue bottom sheet showing the current playback queue.
 *
 * Design:
 * - Current song pinned at top with accent highlight
 * - Upcoming songs listed below
 * - Tap any song to play it
 * - Drag handle for reorder (visual, functional reorder via buttons)
 * - Swipe to remove (via close button per row)
 * - Beautiful empty state when queue is empty
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

    val current = currentSong
    val upcomingStartIndex = if (current != null) {
        val idx = queue.indexOfFirst { it.id == current.id }
        if (idx >= 0) idx + 1 else 0
    } else 0

    Column(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight(0.75f)
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(GratiaTheme.colors.surface)
            .padding(top = 12.dp)
    ) {
        // Drag indicator
        Box(
            modifier = Modifier
                .width(36.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(GratiaTheme.colors.textSecondary.copy(alpha = 0.3f))
                .align(Alignment.CenterHorizontally)
        )

        Spacer(Modifier.height(16.dp))

        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "QUEUE",
                fontFamily = Inter,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                letterSpacing = 2.sp,
                color = GratiaTheme.colors.textSecondary
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${queue.size} songs",
                    fontFamily = Inter,
                    fontSize = 12.sp,
                    color = GratiaTheme.colors.textSecondary.copy(alpha = 0.6f)
                )
                if (queue.isNotEmpty()) {
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "CLEAR",
                        fontFamily = Inter,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                        color = GratiaTheme.colors.accent,
                        modifier = Modifier.clickable { 
                            playerViewModel.clearQueue()
                            onDismiss()
                        }.padding(4.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        if (queue.isEmpty()) {
            // Empty state
            QueueEmptyState()
        } else {
            val listState = rememberReorderableLazyListState(
                onMove = { from, to ->
                    // Reorder the queue via viewmodel
                    // Note: from.index and to.index are based on LazyColumn items
                    // We need to adjust by the number of headers
                    val headerCount = if (current != null) 2 else 0
                    val fromAdjusted = from.index - headerCount + upcomingStartIndex
                    val toAdjusted = to.index - headerCount + upcomingStartIndex
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
                // Current song — pinned with highlight
                if (current != null) {
                    item(key = "now_playing_${current.id}") {
                        NowPlayingRow(
                            song = current,
                            modifier = Modifier.animateItem()
                        )
                    }

                    item {
                        // "Up Next" divider
                        Text(
                            "UP NEXT",
                            fontFamily = Inter,
                            fontWeight = FontWeight.Medium,
                            fontSize = 10.sp,
                            letterSpacing = 2.sp,
                            color = GratiaTheme.colors.textSecondary.copy(alpha = 0.5f),
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                        )
                    }
                }

                // Upcoming songs
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
 * Now Playing row — highlighted with accent color.
 */
@Composable
private fun NowPlayingRow(
    song: SongEntity,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(GratiaTheme.colors.accent.copy(alpha = 0.08f))
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CoverArtImage(
            coverArtPath = song.coverArtPath,
            title = song.title,
            artist = song.artist,
            size = 44.dp,
            cornerRadius = 10.dp,
            fontSize = 12.sp
        )

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                song.title,
                fontFamily = SpaceGrotesk,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = GratiaTheme.colors.accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                song.artist,
                fontFamily = Inter,
                fontSize = 12.sp,
                color = GratiaTheme.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Icon(
            Icons.Default.PlayArrow,
            contentDescription = "Now playing",
            tint = GratiaTheme.colors.accent,
            modifier = Modifier.size(20.dp)
        )
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
            size = 40.dp,
            cornerRadius = 8.dp,
            fontSize = 11.sp
        )

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                song.title,
                fontFamily = SpaceGrotesk,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                color = GratiaTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                song.artist,
                fontFamily = Inter,
                fontSize = 11.sp,
                color = GratiaTheme.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Remove button
        IconButton(
            onClick = {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                onRemove()
            },
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Remove from queue",
                tint = GratiaTheme.colors.textSecondary.copy(alpha = 0.4f),
                modifier = Modifier.size(16.dp)
            )
        }

        // Drag handle (visual cue)
        Icon(
            Icons.Default.DragHandle,
            contentDescription = "Reorder",
            tint = GratiaTheme.colors.textSecondary.copy(alpha = 0.3f),
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
                contentDescription = "Drag handle",
                tint = GratiaTheme.colors.textSecondary.copy(alpha = 0.2f),
                modifier = Modifier.size(64.dp)
            )

            Spacer(Modifier.height(20.dp))

            Text(
                "Queue is Empty",
                fontFamily = SpaceGrotesk,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = GratiaTheme.colors.textPrimary
            )

            Spacer(Modifier.height(8.dp))

            Text(
                "Play a song and it will appear here\nwith the rest of your queue",
                fontFamily = Inter,
                fontSize = 13.sp,
                color = GratiaTheme.colors.textSecondary.copy(alpha = 0.6f),
                lineHeight = 20.sp,
                modifier = Modifier.padding(horizontal = 32.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}
