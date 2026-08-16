package com.gratia.music.ui.lyrics

import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gratia.music.lyrics.LyricLine
import com.gratia.music.lyrics.LrcParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Premium synced lyrics view with word-level animations.
 *
 * This composable renders the scrolling lyrics list, auto-scrolling to
 * the active line and displaying word-level highlights when data is available.
 *
 * Performance considerations:
 * - `currentLineIndex` uses `derivedStateOf` so the LazyColumn only
 *   recomposes when the ACTIVE LINE changes, not every playback tick.
 * - Individual `LyricsLine` items use `graphicsLayer` for alpha/scale
 *   so word animations don't cause layout passes.
 * - Stable keys prevent unnecessary item recreation.
 * - Generous padding gives text breathing room and prevents clipping.
 *
 * The background is intentionally transparent so it composites properly
 * over the player's existing blurred album art background.
 */
@Composable
fun SyncedLyricsView(
    lyrics: String,
    currentPlaybackTime: Long,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    parsedLyricsInput: List<LyricLine>? = null,
    onSeek: ((Long) -> Unit)? = null,
    syncOffset: Long = 0L,
    showTranslation: Boolean = true,
    showRomanization: Boolean = true,
    lyricsSource: String? = null,
    textSizeMultiplier: Float = 1.0f,
    textAlignment: androidx.compose.ui.text.style.TextAlign = androidx.compose.ui.text.style.TextAlign.Center,
    onTapLyricsView: (() -> Unit)? = null
) {
    val adjustedPlaybackTime = currentPlaybackTime + syncOffset

    val parsedLyrics by produceState(
        initialValue = parsedLyricsInput ?: emptyList(),
        key1 = lyrics,
        key2 = parsedLyricsInput
    ) {
        value = if (parsedLyricsInput != null) {
            parsedLyricsInput
        } else {
            withContext(Dispatchers.Default) {
                LrcParser.parse(lyrics)
            }
        }
    }

    LaunchedEffect(lyrics) {
        if (parsedLyrics.isNotEmpty()) {
            listState.scrollToItem(0)
        }
    }

    // Calculate the active line index. We don't use derivedStateOf here because
    // adjustedPlaybackTime is a raw Long, not a State, so it wouldn't trigger updates.
    val currentLineIndex = parsedLyrics.indexOfLast { it.startMs <= adjustedPlaybackTime }

    // Auto-scroll to the active line with a smooth animation
    LaunchedEffect(currentLineIndex) {
        if (currentLineIndex >= 0 && parsedLyrics.isNotEmpty()) {
            listState.animateScrollToItem(currentLineIndex, scrollOffset = -100)
        }
    }

    // Transparent background — lets the player's blurred album art show through
    Box(modifier = modifier.fillMaxSize()) {
        
        // Detect manual scrolling to wake up the screen and show controls
        val nestedScrollConnection = remember {
            object : NestedScrollConnection {
                override fun onPreScroll(
                    available: Offset,
                    source: NestedScrollSource
                ): Offset {
                    if (source == NestedScrollSource.Drag) {
                        onTapLyricsView?.invoke()
                    }
                    return Offset.Zero
                }
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(nestedScrollConnection),
            // Generous padding for breathing room — prevents long lines from feeling cramped
            contentPadding = PaddingValues(start = 32.dp, end = 32.dp, top = 60.dp, bottom = 200.dp)
        ) {
            itemsIndexed(
                parsedLyrics,
                key = { index, item -> "lyric_${item.startMs}_$index" }
            ) { index, item ->
                val nextStartMs = parsedLyrics.getOrNull(index + 1)?.startMs
                LyricsLine(
                    line = item,
                    isActiveLine = index == currentLineIndex,
                    nextLineStartMs = nextStartMs,
                    currentPositionMs = adjustedPlaybackTime,
                    onSeek = onSeek
                )
            }
        }
    }
}
