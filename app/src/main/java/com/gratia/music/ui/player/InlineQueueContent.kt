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
import androidx.compose.foundation.layout.heightIn
import android.util.Log
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.gratia.music.ui.components.IosBounce
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gratia.music.data.model.SongEntity
import com.gratia.music.player.PlayerViewModel
import com.gratia.music.player.RepeatMode
import com.gratia.music.player.playbackContextLabel
import com.gratia.music.ui.components.CoverArtImage
import com.gratia.music.ui.components.bounceClick
import com.gratia.music.ui.theme.GratiaTheme
import com.gratia.music.ui.theme.Inter
import com.gratia.music.ui.theme.SpaceGrotesk
import org.burnoutcrew.reorderable.ReorderableItem
import org.burnoutcrew.reorderable.detectReorderAfterLongPress
import org.burnoutcrew.reorderable.rememberReorderableLazyListState
import org.burnoutcrew.reorderable.reorderable

data class StableQueueItem(val uniqueId: String, val song: SongEntity)

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
    val currentQueueIndex by playerViewModel.currentQueueIndex.collectAsState()
    val history by playerViewModel.history.collectAsState()
    val favoriteSongIds by playerViewModel.favoriteSongIds.collectAsState()
    val shuffleEnabled by playerViewModel.shuffleEnabled.collectAsState()
    val repeatMode by playerViewModel.repeatMode.collectAsState()
    val autoplayEnabled by playerViewModel.autoplayEnabled.collectAsState()
    val autoplayStatus by playerViewModel.autoplayStatus.collectAsState()
    val playbackOrigin by playerViewModel.playbackOrigin.collectAsState()

    var showClearHistoryDialog by remember { mutableStateOf(false) }

    val current = currentSong
    val upcomingStartIndex = if (currentQueueIndex in queue.indices) currentQueueIndex + 1 else 0

    // The queue's tiers, split the way they are presented: what the listener
    // queued by hand first, then what the radio generated (spec 13/68). The id
    // set only ever changes with the queue, so keying on it keeps the pair
    // consistent.
    val (userUpcoming, autoplayUpcoming) = remember(queue, currentQueueIndex) {
        com.gratia.music.player.RadioQueue.upcomingByTier(
            queue,
            currentQueueIndex,
            playerViewModel.playerManager.autoplayIdsSnapshot(),
        )
    }

    // Use a wrapper to provide stable, unique keys for ReorderableItem
    var upcomingLocal by remember { mutableStateOf(emptyList<StableQueueItem>()) }

    val reorderableState = rememberReorderableLazyListState(
        listState = listState,
        onMove = { from, to ->
            val fromIndex = from.index - 1
            val toIndex = to.index - 1
            if (fromIndex in upcomingLocal.indices && toIndex in upcomingLocal.indices) {
                upcomingLocal = upcomingLocal.toMutableList().apply {
                    add(toIndex, removeAt(fromIndex))
                }
            }
        },
        onDragEnd = { _, _ ->
            // Tier-aware commit: only the user's rows are rebuilt; the AutoPlay
            // region stays attached behind them instead of being cut off.
            playerViewModel.updateUserQueueUpcoming(upcomingLocal.map { it.song }, upcomingStartIndex)
        }
    )

    val isDragging = reorderableState.draggingItemKey != null
    LaunchedEffect(userUpcoming, isDragging) {
        if (!isDragging) {
            upcomingLocal = userUpcoming.mapIndexed { i, song ->
                StableQueueItem("${song.id}_${System.identityHashCode(song)}_$i", song)
            }
        }
    }

    // The page gives when it is pulled past its end — see [IosBounce]. Dragging
    // a row to reorder is a press-and-hold gesture rather than a scroll, so it
    // never reaches this connection.
    IosBounce(
        state = listState,
        modifier = modifier.fillMaxSize()
    ) {
    LazyColumn(
        state = reorderableState.listState,
        modifier = Modifier
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
                            Log.d("InlineQueueContent", "AUTOPLAY_TOGGLE tapped -> ${!autoplayEnabled}")
                            playerViewModel.toggleAutoplay()
                        },
                        modifier = Modifier.weight(1f)
                    )
                }

                // AutoPlay is a mode, not a row: when it is on, say so in the
                // same words the feature is described with, so tapping the pill
                // is answered by the queue itself and not only by the icon.
                if (autoplayEnabled) {
                    Text(
                        "AutoPlay · Similar music, selected to play next",
                        fontFamily = Inter,
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.55f),
                        modifier = Modifier.padding(horizontal = 24.dp).padding(top = 10.dp)
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
                    // Where this session actually came from. The album name
                    // was never the answer — for a remote track it is usually
                    // absent, and the old fallback claimed "your library" for
                    // a song chosen out of Search. When the origin is genuinely
                    // unknown the line is omitted rather than guessed at.
                    playbackContextLabel(playbackOrigin)?.let { context ->
                        Text(
                            "Playing from $context",
                            fontFamily = Inter,
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(Modifier.height(GratiaTheme.spacing.mediumSmall))

                if (queue.isEmpty()) {
                    InlineQueueEmptyState()
                }
            }
        }

        // --- User queue rows (what the listener queued by hand) ---
        if (upcomingLocal.isNotEmpty()) {
            item(key = "user_queue_header") {
                Text(
                    "Up Next",
                    fontFamily = SpaceGrotesk,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp)
                )
            }
            itemsIndexed(
                upcomingLocal,
                key = { _, item -> "iq_${item.uniqueId}" }
            ) { index, item ->
                val song = item.song
                ReorderableItem(reorderableState, key = "iq_${item.uniqueId}") { isDragging ->
                    val scope = rememberCoroutineScope()
                    val dismissed = remember { mutableStateOf(false) }
                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = { dismissValue ->
                            if ((dismissValue == SwipeToDismissBoxValue.EndToStart || dismissValue == SwipeToDismissBoxValue.StartToEnd) && !dismissed.value) {
                                // Logically removed at the moment the gesture
                                // commits — the animation only draws the exit
                                // the model has already decided (spec 10). No
                                // delay: a delayed removal is a window where a
                                // dead row could still be played.
                                dismissed.value = true
                                playerViewModel.removeQueueItemAt(upcomingStartIndex + index)
                                true
                            } else false
                        }
                    )
                    
                    SwipeToDismissBox(
                        state = dismissState,
                        modifier = Modifier.animateItem(),
                        backgroundContent = {
                            val direction = dismissState.dismissDirection
                            
                            val color by animateColorAsState(
                                targetValue = when (dismissState.targetValue) {
                                    SwipeToDismissBoxValue.Settled -> Color.Transparent
                                    else -> Color.Red.copy(alpha = 0.8f)
                                },
                                animationSpec = tween(300),
                                label = "dismissColor"
                            )
                            
                            val alignment = when (direction) {
                                SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                                SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                                else -> Alignment.CenterEnd
                            }
                            
                            val scale by animateFloatAsState(
                                targetValue = if (dismissState.targetValue == SwipeToDismissBoxValue.Settled) 0.5f else 1f,
                                animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
                                label = "iconScale"
                            )
                            
                            val iconAlpha by animateFloatAsState(
                                targetValue = if (dismissState.targetValue == SwipeToDismissBoxValue.Settled) 0f else 1f,
                                animationSpec = tween(300),
                                label = "iconAlpha"
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(color)
                                    .padding(horizontal = 32.dp),
                                contentAlignment = alignment
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Remove from Queue",
                                    tint = Color.White.copy(alpha = iconAlpha),
                                    modifier = Modifier.scale(scale)
                                )
                            }
                        }
                    ) {
                        val actualIndex = upcomingLocal.indexOf(item)
                        InlineQueueRow(
                            song = song,
                            onPlay = {
                                onInteraction()
                                val realIndex = upcomingStartIndex + actualIndex
                                if (realIndex in playerViewModel.queue.value.indices) {
                                    playerViewModel.playFromQueue(realIndex)
                                } else {
                                    val findIdx = playerViewModel.queue.value.indexOfFirst { it.id == song.id }
                                    if (findIdx >= 0) playerViewModel.playFromQueue(findIdx)
                                }
                            },
                            modifier = Modifier
                                .background(
                                    if (isDragging) Color.White.copy(alpha = 0.08f)
                                    else Color.Transparent
                                ),
                            dragModifier = Modifier.detectReorderAfterLongPress(reorderableState)
                        )
                    }
                }
            }
        }

        // --- AutoPlay section: only when the feature is on for this session ---
        if (autoplayEnabled) {
            item(key = "autoplay_header") {
                Column(Modifier.padding(horizontal = 24.dp).padding(top = 18.dp)) {
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.AllInclusive,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "AutoPlay",
                            fontFamily = SpaceGrotesk,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                    Text(
                        "Similar music, selected to play next",
                        fontFamily = Inter,
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.45f),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            when (val status = autoplayStatus) {
                is com.gratia.music.player.PlayerManager.AutoPlayStatus.Loading -> item(key = "autoplay_loading") {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Finding similar music…",
                            fontFamily = Inter,
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.45f)
                        )
                    }
                }
                is com.gratia.music.player.PlayerManager.AutoPlayStatus.Failed -> item(key = "autoplay_error") {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            status.message ?: "Couldn't load AutoPlay",
                            fontFamily = Inter,
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.45f),
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "Retry",
                            fontFamily = Inter,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = Color.White,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.12f))
                                .bounceClick {
                                    onInteraction()
                                    playerViewModel.retryAutoplay()
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
                else -> {
                    // Ready (or idle-but-counted): render the suggestions the
                    // radio actually queued. Deliberately no placeholder row.
                    if (autoplayUpcoming.isNotEmpty()) {
                        itemsIndexed(
                            autoplayUpcoming,
                            key = { _, song -> "ap_${song.id}" }
                        ) { index, song ->
                            // A suggestion can be dismissed (spec 141): the
                            // removal goes through the same removeFromQueue
                            // path as any queue row, which records it in the
                            // session's exclusion set — the radio will not
                            // offer this track again this session (spec 34).
                            val dismissState = rememberSwipeToDismissBoxState(
                                confirmValueChange = { dismissValue ->
                                    if (dismissValue == SwipeToDismissBoxValue.EndToStart ||
                                        dismissValue == SwipeToDismissBoxValue.StartToEnd
                                    ) {
                                        onInteraction()
                                        playerViewModel.removeFromQueue(song.id)
                                        true
                                    } else false
                                }
                            )
                            SwipeToDismissBox(
                                state = dismissState,
                                backgroundContent = {
                                    val direction = dismissState.dismissDirection

                                    val color by animateColorAsState(
                                        targetValue = when (dismissState.targetValue) {
                                            SwipeToDismissBoxValue.Settled -> Color.Transparent
                                            else -> Color.Red.copy(alpha = 0.7f)
                                        },
                                        animationSpec = tween(300),
                                        label = "autoDismissColor"
                                    )

                                    val alignment = when (direction) {
                                        SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                                        SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                                        else -> Alignment.CenterEnd
                                    }

                                    val scale by animateFloatAsState(
                                        targetValue = if (dismissState.targetValue == SwipeToDismissBoxValue.Settled) 0.5f else 1f,
                                        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
                                        label = "autoIconScale"
                                    )

                                    val iconAlpha by animateFloatAsState(
                                        targetValue = if (dismissState.targetValue == SwipeToDismissBoxValue.Settled) 0f else 1f,
                                        animationSpec = tween(300),
                                        label = "autoIconAlpha"
                                    )

                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(color)
                                            .padding(horizontal = 32.dp),
                                        contentAlignment = alignment
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Remove from AutoPlay",
                                            tint = Color.White.copy(alpha = iconAlpha),
                                            modifier = Modifier.scale(scale)
                                        )
                                    }
                                }
                            ) {
                                InlineQueueRow(
                                    song = song,
                                    onPlay = {
                                        onInteraction()
                                        val findIdx = playerViewModel.queue.value.indexOfFirst { it.id == song.id }
                                        if (findIdx >= 0) playerViewModel.playFromQueue(findIdx)
                                    },
                                    modifier = Modifier.alpha(0.75f).background(Color.Transparent),
                                    showDragHandle = false
                                )
                            }
                        }
                    }
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
    dragModifier: Modifier = Modifier,
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
            Box(
                modifier = dragModifier
                    .size(48.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.DragHandle,
                    contentDescription = "Reorder",
                    tint = Color.White.copy(alpha = 0.4f),
                    modifier = Modifier.size(20.dp)
                )
            }
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

    // A bounce on every state change: the pill grows briefly as the listener
    // taps it, so the state change is felt and not only coloured.
    val scale by animateFloatAsState(
        targetValue = if (isActive) 1.06f else 1f,
        animationSpec = spring(dampingRatio = 0.4f, stiffness = 380f),
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
            }
            .semantics { stateDescription = if (isActive) "Shuffle on" else "Shuffle off" },
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
            }
            .semantics {
                stateDescription = when (repeatMode) {
                    RepeatMode.OFF -> "Repeat off"
                    RepeatMode.ALL -> "Repeat all"
                    RepeatMode.ONE -> "Repeat one"
                }
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
 * Autoplay (Infinity) pill. The state must never be in doubt after a tap: the
 * pill scales into its active size, the colour crosses over, and TalkBack hears
 * which state it is in (spec 15/22/25).
 */
@Composable
private fun AnimatedAutoplayPill(
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hapticFeedback = LocalHapticFeedback.current

    val scale by animateFloatAsState(
        targetValue = if (isActive) 1.06f else 1f,
        animationSpec = spring(dampingRatio = 0.4f, stiffness = 380f),
        label = "autoplayScale"
    )

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

    // 48dp minimum, not 36: a control the spec calls untappable was a
    // thumb-sized target sandwiched between a drag-detector and a swipe
    // layer. Height grows invisibly (padding, not the pill's visual box)
    // so the hit area is comfortable without moving anything around it.
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .padding(vertical = 6.dp)
            .scale(scale)
            .clip(RoundedCornerShape(18.dp))
            .background(bgColor)
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = androidx.compose.foundation.LocalIndication.current,
            ) {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
            .semantics { stateDescription = if (isActive) "AutoPlay on" else "AutoPlay off" },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.AllInclusive,
            contentDescription = "AutoPlay",
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
