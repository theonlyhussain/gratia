package com.gratia.music.ui.lyrics

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gratia.music.lyrics.LyricLine
import com.gratia.music.lyrics.LyricsTimingEngine
import kotlinx.coroutines.delay

/**
 * Kinetic scrolling container for synced lyrics.
 *
 * Centers the active line smoothly within the viewport.
 * User-scroll aware: when the user manually scrolls, auto-scroll
 * pauses for 5 seconds of inactivity, then smoothly reconnects.
 */
@Composable
fun KineticLyricsColumn(
    lines: List<LyricLine>,
    currentTimeMs: Long,
    onLineClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeIndex by remember(lines) {
        derivedStateOf {
            LyricsTimingEngine.findActiveLineIndex(lines, currentTimeMs)
        }
    }

    val listState = rememberLazyListState()

    // User-scroll detection: pause auto-scroll when user is interacting
    var autoScrollEnabled by remember { mutableStateOf(true) }
    var lastUserScrollTime by remember { mutableLongStateOf(0L) }

    // Detect user-initiated scrolls
    val isUserScrolling = listState.isScrollInProgress
    LaunchedEffect(isUserScrolling) {
        if (isUserScrolling) {
            // User started scrolling — disable auto-scroll
            autoScrollEnabled = false
            lastUserScrollTime = System.currentTimeMillis()
        }
    }

    // Re-enable auto-scroll after 5s of inactivity
    LaunchedEffect(lastUserScrollTime) {
        if (!autoScrollEnabled && lastUserScrollTime > 0) {
            delay(5000)
            autoScrollEnabled = true
        }
    }

    // Smooth kinetic centering animation — only when auto-scroll is active
    LaunchedEffect(activeIndex, autoScrollEnabled) {
        if (!autoScrollEnabled) return@LaunchedEffect
        if (activeIndex >= 0 && lines.isNotEmpty()) {
            val layoutInfo = listState.layoutInfo
            val viewportHeight = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
            if (viewportHeight > 0) {
                val activeItem = layoutInfo.visibleItemsInfo.find { it.index == activeIndex }
                val itemSize = activeItem?.size ?: 120
                val targetOffset = -(viewportHeight / 2 - itemSize / 2)
                listState.animateScrollToItem(activeIndex, targetOffset)
            } else {
                listState.animateScrollToItem(activeIndex, -220)
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = 280.dp,
            bottom = 280.dp,
            start = 28.dp,
            end = 28.dp
        ),
        verticalArrangement = Arrangement.spacedBy(35.dp)
    ) {
        itemsIndexed(lines, key = { index, line -> "${line.startMs}_$index" }) { index, line ->
            LyricLineView(
                line = line,
                isActive = index == activeIndex,
                isPast = index < activeIndex,
                currentTimeMs = currentTimeMs,
                onClick = {
                    onLineClick(line.startMs)
                    // When user taps a lyric line, re-enable auto-scroll
                    autoScrollEnabled = true
                }
            )
        }
    }
}

