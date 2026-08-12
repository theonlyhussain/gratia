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
            // Replicating flatlistRef.current?.scrollToIndex animated: true
            // FlatList without viewPosition scrolls to the top of the viewport.
            // But typically lyrics should be centered or slightly above. We'll use animateScrollToItem
            listState.animateScrollToItem(currentLineIndex, scrollOffset = 0)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        GradientBackground()
        
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 400.dp) // Bottom spacer
        ) {
            item {
                Spacer(modifier = Modifier.height(100.dp)) // ListHeaderComponent
            }

            itemsIndexed(parsedLyrics) { index, item ->
                val nextStartMs = parsedLyrics.getOrNull(index + 1)?.startMs
                LyricsLine(
                    line = item,
                    isActiveLine = index == currentLineIndex,
                    nextLineStartMs = nextStartMs,
                    currentPositionMs = adjustedPlaybackTime
                )
            }
        }
    }
}
