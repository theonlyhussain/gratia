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
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalContext
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
import com.gratia.music.ui.components.bounceClick
import androidx.compose.ui.draw.alpha
import org.burnoutcrew.reorderable.rememberReorderableLazyListState
import org.burnoutcrew.reorderable.reorderable
import org.burnoutcrew.reorderable.ReorderableItem
import org.burnoutcrew.reorderable.detectReorderAfterLongPress

/**
 * Redesigned queue bottom sheet matching the the industry standard reference.
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
    val history by playerViewModel.history.collectAsState()
    val favoriteSongIds by playerViewModel.favoriteSongIds.collectAsState()

    val context = LocalContext.current
    val shuffleEnabled by playerViewModel.shuffleEnabled.collectAsState()
    val repeatMode by playerViewModel.repeatMode.collectAsState()
    val autoplayEnabled by playerViewModel.autoplayEnabled.collectAsState()

    val current = currentSong
    val upcomingStartIndex = if (current != null) {
        val idx = queue.indexOfFirst { it.id == current.id }
        if (idx >= 0) idx + 1 else 0
    } else 0

    Column(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxSize()
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(GratiaTheme.colors.surface)
    ) {
        // Drag handle
        Box(
            modifier = Modifier
                .padding(top = 16.dp, bottom = 8.dp)
                .width(36.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(GratiaTheme.colors.textSecondary.copy(alpha = 0.3f))
                .align(Alignment.CenterHorizontally)
        )

        val listState = rememberReorderableLazyListState(
            onMove = { from, to ->
                // The first item (index 0) is the header.
                // Reorderable list indices are offset by 1 because of the header.
                val fromAdjusted = (from.index - 1) + upcomingStartIndex
                val toAdjusted = (to.index - 1) + upcomingStartIndex
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
            item {
                Column {
                    // --- Current Song ---
                    if (current != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(GratiaTheme.colors.surface)
                                .padding(8.dp),
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
                                    color = GratiaTheme.colors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    current.artist,
                                    fontFamily = Inter,
                                    fontSize = 12.sp,
                                    color = GratiaTheme.colors.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // Favorite button
                            IconButton(onClick = { playerViewModel.toggleFavorite(current) }) {
                                val isFav = favoriteSongIds.contains(current.id)
                                Icon(
                                    if (isFav) Icons.Default.Star else Icons.Default.StarBorder,
                                    contentDescription = "Favorite",
                                    tint = if (isFav) GratiaTheme.colors.accent else GratiaTheme.colors.textSecondary,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        // --- Shuffle / Repeat / Autoplay pill buttons ---
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ShufflePill(
                                isActive = shuffleEnabled,
                                isAlbum = current.album != null,
                                onClick = { playerViewModel.toggleShuffle() },
                                modifier = Modifier.weight(1f)
                            )

                            RepeatPill(
                                repeatMode = repeatMode,
                                onClick = { playerViewModel.cycleRepeatMode() },
                                modifier = Modifier.weight(1f)
                            )

                            AutoplayPill(
                                isActive = autoplayEnabled,
                                onClick = { playerViewModel.toggleAutoplay() },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(Modifier.height(16.dp))

                        // --- History Section ---
                        if (history.isNotEmpty()) {
                            Spacer(Modifier.height(24.dp))
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
                                    fontSize = 18.sp,
                                    color = GratiaTheme.colors.textPrimary
                                )
                                Text(
                                    "Clear",
                                    fontFamily = Inter,
                                    fontSize = 14.sp,
                                    color = GratiaTheme.colors.textSecondary,
                                    modifier = Modifier.bounceClick { playerViewModel.clearHistory() }
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            
                            // Show last 3 history items just as a preview, to save space
                            Column {
                                history.take(3).forEach { song ->
                                    QueueRow(
                                        song = song,
                                        index = -1,
                                        isCurrentSong = false,
                                        onPlay = { playerViewModel.playSong(song, history) },
                                        onRemove = {}, // History items aren't removed individually here
                                        modifier = Modifier.alpha(0.6f),
                                        showDragHandle = false
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(24.dp))

                        // --- "Continue Playing" header ---
                        Text(
                            "Continue Playing",
                            fontFamily = SpaceGrotesk,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = GratiaTheme.colors.textPrimary,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                        Text(
                            "From ${current.album ?: "your library"}",
                            fontFamily = Inter,
                            fontSize = 13.sp,
                            color = GratiaTheme.colors.textSecondary,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    if (queue.isEmpty()) {
                        QueueEmptyState()
                    }
                }
            }

            if (queue.isNotEmpty()) {
                val upcoming = if (upcomingStartIndex < queue.size) {
                    queue.subList(upcomingStartIndex, queue.size)
                } else emptyList()

                itemsIndexed(
                    upcoming,
                    key = { index, song -> "queue_${song.id}_$index" }
                ) { index, song ->
                    ReorderableItem(listState, key = "queue_${song.id}_$index") { isDragging ->
                        QueueRow(
                            song = song,
                            index = upcomingStartIndex + index,
                            isCurrentSong = false,
                            onPlay = { playerViewModel.playFromQueue(upcomingStartIndex + index) },
                            onRemove = { playerViewModel.removeFromQueue(song.id) },
                            modifier = Modifier
                                .animateItem()
                                .background(if (isDragging) GratiaTheme.colors.surfaceHover else Color.Transparent)
                                .detectReorderAfterLongPress(listState)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Shuffle/Mix pill button.
 * Acts as Mix when an album is playing, standard Shuffle otherwise.
 */
@Composable
private fun ShufflePill(
    isActive: Boolean,
    isAlbum: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hapticFeedback = LocalHapticFeedback.current
    val bgColor by animateColorAsState(
        targetValue = if (isActive) GratiaTheme.colors.textPrimary.copy(alpha = 0.15f) else GratiaTheme.colors.textPrimary.copy(alpha = 0.05f),
        animationSpec = tween(200),
        label = "shuffleBg"
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
                Icons.Default.Shuffle,
                contentDescription = if (isAlbum) "Mix" else "Shuffle",
                tint = if (isActive) GratiaTheme.colors.textPrimary else GratiaTheme.colors.textSecondary,
                modifier = Modifier.size(18.dp)
            )
            if (isAlbum) {
                Spacer(Modifier.width(6.dp))
                Text(
                    "Mix",
                    fontFamily = SpaceGrotesk,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    color = if (isActive) GratiaTheme.colors.textPrimary else GratiaTheme.colors.textSecondary
                )
            }
        }
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
        targetValue = if (isActive) GratiaTheme.colors.textPrimary.copy(alpha = 0.15f) else GratiaTheme.colors.textPrimary.copy(alpha = 0.05f),
        animationSpec = tween(200),
        label = "repeatBg"
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
                tint = if (isActive) GratiaTheme.colors.textPrimary else GratiaTheme.colors.textSecondary,
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
                    color = GratiaTheme.colors.textPrimary
                )
            }
        }
    }
}

/**
 * Autoplay (Infinite Mix) pill button.
 */
@Composable
private fun AutoplayPill(
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hapticFeedback = LocalHapticFeedback.current
    val bgColor by animateColorAsState(
        targetValue = if (isActive) GratiaTheme.colors.textPrimary.copy(alpha = 0.15f) else GratiaTheme.colors.textPrimary.copy(alpha = 0.05f),
        animationSpec = tween(200),
        label = "autoplayBg"
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
            tint = if (isActive) GratiaTheme.colors.textPrimary else GratiaTheme.colors.textSecondary,
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
                color = GratiaTheme.colors.textPrimary,
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

        // Drag handle or placeholder
        if (showDragHandle) {
            Icon(
                Icons.Default.DragHandle,
                contentDescription = "Reorder",
                tint = GratiaTheme.colors.textSecondary,
                modifier = Modifier.size(20.dp)
            )
        } else {
            Spacer(Modifier.width(20.dp))
        }
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
                tint = GratiaTheme.colors.textSecondary.copy(alpha = 0.5f),
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
                color = GratiaTheme.colors.textSecondary,
                lineHeight = 20.sp,
                modifier = Modifier.padding(horizontal = 32.dp),
                textAlign = TextAlign.Center
            )
        }
    }
}
