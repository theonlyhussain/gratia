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
 * - User-scroll detection pauses auto-scroll for 3 seconds to avoid
 *   fighting with manual gestures.
 *
 * The background is intentionally transparent so it composites properly
 * over the player's existing blurred album art background.
 */
@Composable
fun SyncedLyricsView(
    lyrics: String,
    currentPlaybackTimeProvider: () -> Long,
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
    onTapLyricsView: (() -> Unit)? = null,
    animateWordFill: Boolean = true
) {
    val adjustedPlaybackTimeProvider = remember(syncOffset) {
        { currentPlaybackTimeProvider() + syncOffset }
    }

    val parsedLyrics by produceState(
        initialValue = parsedLyricsInput ?: emptyList(),
        key1 = lyrics,
        key2 = parsedLyricsInput
    ) {
        value = if (parsedLyricsInput != null) {
            parsedLyricsInput
        } else {
            withContext(Dispatchers.Default) {
                val document = com.gratia.music.lyrics.LyricsParser.parse(lyrics)
                when (document) {
                    is com.gratia.music.lyrics.LyricsDocument.WordSynced -> document.lines
                    is com.gratia.music.lyrics.LyricsDocument.LineSynced -> document.lines
                    is com.gratia.music.lyrics.LyricsDocument.Plain -> emptyList()
                }
            }
        }
    }

    LaunchedEffect(lyrics) {
        if (parsedLyrics.isNotEmpty()) {
            listState.scrollToItem(0)
        }
    }

    // Calculate the active line index using derivedStateOf.
    // Because we read the playback time from the provider INSIDE derivedStateOf,
    // this will only trigger a recomposition when the line index actually changes,
    // saving us from recomposing 120 times a second!
    val currentLineIndex by remember(parsedLyrics) {
        derivedStateOf {
            val adjustedTime = adjustedPlaybackTimeProvider()
            parsedLyrics.indexOfLast { it.startMs <= adjustedTime }
        }
    }

    // ── User-scroll guard ──────────────────────────────────────────────
    // When the user manually scrolls, pause auto-scroll for 3 seconds so
    // the viewport doesn't fight their finger.
    var userScrolling by remember { mutableStateOf(false) }
    var lastUserScrollTime by remember { mutableLongStateOf(0L) }

    // Detect when the user is manually dragging
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            userScrolling = true
            lastUserScrollTime = System.currentTimeMillis()
        }
    }

    // Resume auto-scroll 3 seconds after the user stops scrolling
    LaunchedEffect(lastUserScrollTime) {
        if (userScrolling) {
            kotlinx.coroutines.delay(3000L)
            userScrolling = false
        }
    }

    // Auto-scroll to the active line with a smooth animation.
    // Uses spring animation for a premium feel, and respects user scroll guard.
    LaunchedEffect(currentLineIndex) {
        if (currentLineIndex >= 0 && parsedLyrics.isNotEmpty() && !userScrolling) {
            // Check if the target line is already reasonably visible
            val visibleItems = listState.layoutInfo.visibleItemsInfo
            val isAlreadyCentered = visibleItems.any { it.index == currentLineIndex }

            if (!isAlreadyCentered || visibleItems.firstOrNull { it.index == currentLineIndex }?.let {
                    val viewportCenter = listState.layoutInfo.viewportSize.height / 3
                    it.offset !in -50..viewportCenter
                } == true) {
                listState.animateScrollToItem(
                    index = currentLineIndex,
                    scrollOffset = -100
                )
            }
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
                    if (source == NestedScrollSource.UserInput) {
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
                    currentPositionProvider = adjustedPlaybackTimeProvider,
                    onSeek = onSeek,
                    animateWordFill = animateWordFill
                )
            }
            
            if (!lyricsSource.isNullOrBlank()) {
                item {
                    androidx.compose.material3.Text(
                        text = "Provided by $lyricsSource",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 48.dp, bottom = 16.dp),
                        style = androidx.compose.material3.MaterialTheme.typography.labelMedium.copy(
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                            textAlign = textAlignment
                        )
                    )
                }
            }
        }
    }
}
