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

/**
 * Kinetic scrolling container for synced lyrics.
 * Centers the active line smoothly within the viewport.
 * Triggers list movement only when the active line transitions.
 */
@Composable
fun KineticLyricsColumn(
    lines: List<LyricLine>,
    currentTimeMs: Long,
    onLineClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    // Derived state prevents recomposition of the list layout on every playback frame tick.
    val activeIndex by remember(lines) {
        derivedStateOf {
            LyricsTimingEngine.findActiveLineIndex(lines, currentTimeMs)
        }
    }

    val listState = rememberLazyListState()

    // Smooth kinetic centering animation triggered when active line updates
    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0 && lines.isNotEmpty()) {
            val layoutInfo = listState.layoutInfo
            val viewportHeight = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
            if (viewportHeight > 0) {
                val activeItem = layoutInfo.visibleItemsInfo.find { it.index == activeIndex }
                val itemSize = activeItem?.size ?: 120 // Estimated height in pixels
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
            top = 280.dp,  // Top padding allows the first line to be centered
            bottom = 280.dp, // Bottom padding allows the last line to be centered
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
                onClick = { onLineClick(line.startMs) }
            )
        }
    }
}
