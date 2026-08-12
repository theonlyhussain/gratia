package com.gratia.music.ui.lyrics

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

    val currentLineIndex by remember(adjustedPlaybackTime, parsedLyrics) {
        derivedStateOf {
            parsedLyrics.indexOfLast { it.startMs <= adjustedPlaybackTime }
        }
    }

    LaunchedEffect(currentLineIndex) {
        if (currentLineIndex >= 0 && parsedLyrics.isNotEmpty()) {
            listState.animateScrollToItem(currentLineIndex, scrollOffset = -100)
        }
    }

    // Transparent background — lets the player's blurred album art show through
    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 60.dp, bottom = 200.dp)
        ) {
            itemsIndexed(parsedLyrics) { index, item ->
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
