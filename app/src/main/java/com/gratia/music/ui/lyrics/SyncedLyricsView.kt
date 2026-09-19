package com.gratia.music.ui.lyrics

import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gratia.music.data.SettingsDataStore
import com.gratia.music.lyrics.LyricLine
import com.gratia.music.ui.components.IosBounce
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** How long the page waits after a manual scroll before following along again. */
private const val BROWSE_RESUME_MS = 3_000L

/**
 * Premium synced lyrics view with word-level animation.
 *
 * Renders the scrolling lyrics list, following what is being sung and lighting
 * up its words as they are.
 *
 * The scroll is driven by *viewport occupancy*, not by the active line's index.
 * Each change to the set of lines being sung — usually one line, sometimes two
 * or more vocals at once — is measured against the safe band of the viewport,
 * and the page moves only when that group would otherwise run out of room. See
 * [decideLyricScroll] for the policy and [ActiveLyricGroup] for what "being
 * sung" means.
 *
 * Performance considerations:
 * - `activeGroup` uses `derivedStateOf` so the LazyColumn only recomposes when
 *   the lines being sung change, not on every playback tick.
 * - The playback position is reconciled once a frame by [rememberLyricClock]
 *   and shared by every line, so the sweep, the bloom and the break counters
 *   all move off one value instead of a poll apart.
 * - Each line reads that clock in its draw phase, so a frame of animation costs
 *   a redraw rather than a recomposition.
 * - Stable keys prevent unnecessary item recreation.
 * - The background is intentionally transparent so it composites properly over
 *   the player's existing blurred album art background.
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
    textAlignment: TextAlign = TextAlign.Center,
    onTapLyricsView: (() -> Unit)? = null,
    animateWordFill: Boolean = true
) {
    val context = LocalContext.current
    val settingsDataStore = remember(context) { SettingsDataStore(context) }
    val reduceAnimation by settingsDataStore.reduceAnimationFlow.collectAsState(initial = false)
    val scrollDebug by settingsDataStore.lyricsScrollDebugFlow.collectAsState(initial = false)

    val adjustedPlaybackTimeProvider = remember(syncOffset) {
        { currentPlaybackTimeProvider() + syncOffset }
    }

    // One clock for the whole page, sampled once a frame. See [rememberLyricClock].
    val clock = rememberLyricClock(adjustedPlaybackTimeProvider)

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

    // Read the clock INSIDE derivedStateOf, so this only triggers a
    // recomposition when the group being sung changes rather than on every
    // frame of playback. `ActiveLyricGroup` is a value type, so two frames of
    // the same line compare equal and nothing downstream re-runs.
    val activeGroup by remember(parsedLyrics, clock) {
        derivedStateOf { activeLyricGroup(parsedLyrics, clock.value) }
    }

    // ── Reading by hand ────────────────────────────────────────────────
    // While a finger is on the list the page flattens to one brightness — no row
    // is being followed, so no row should be pointed at — and the auto-scroll
    // stands down so it doesn't fight the gesture. Both come back a few seconds
    // after the list is let go.
    //
    // The finger, not `isScrollInProgress`, is what marks the page as being
    // read by hand: the follow itself animates the list, and treating its own
    // movement as a gesture would cancel it a frame in and leave the page
    // flattening every time it followed a line.
    var isBrowsing by remember { mutableStateOf(false) }
    val isDragged by listState.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(isDragged) {
        if (isDragged) {
            isBrowsing = true
            return@LaunchedEffect
        }
        // The finger has lifted, but a fling may still be running. Wait for the
        // list to come to rest, then a beat, before following along again.
        snapshotFlow { listState.isScrollInProgress }.first { !it }
        delay(BROWSE_RESUME_MS)
        isBrowsing = false
    }

    // Follow what is being sung. The group is measured against the safe band of
    // the viewport and the page moves only when the group no longer fits inside
    // it — by the smallest amount that puts it back. A line index changing is
    // not, on its own, a reason to scroll; a line that is already comfortably
    // visible does not move the page at all.
    LaunchedEffect(activeGroup, isBrowsing, parsedLyrics.size, reduceAnimation) {
        if (isBrowsing || activeGroup.isEmpty || parsedLyrics.isEmpty()) return@LaunchedEffect
        val layout = listState.layoutInfo
        val viewportHeight = layout.viewportSize.height
        if (viewportHeight <= 0) return@LaunchedEffect
        val visible = layout.visibleItemsInfo.map {
            LyricItemBounds(index = it.index, top = it.offset, height = it.size)
        }
        val target = decideLyricScroll(
            groupFirst = activeGroup.first,
            groupLast = activeGroup.last,
            visible = visible,
            viewportHeight = viewportHeight
        )
        if (target is LyricScrollTarget.Item) {
            // A positive offset scrolls forward, so the distance below the top
            // of the viewport is negated.
            val scrollOffset = -target.topFromViewport
            if (reduceAnimation) {
                listState.scrollToItem(target.index, scrollOffset)
            } else {
                listState.animateScrollToItem(target.index, scrollOffset)
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {

        // Detect manual scrolling to wake up the screen and show controls.
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

        // The whole page gives when it is pulled past its end — see [IosBounce].
        IosBounce(
            state = listState,
            modifier = Modifier.fillMaxSize()
        ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(nestedScrollConnection),
            // Generous padding for breathing room — prevents long lines from
            // feeling cramped, and gives the resting line somewhere to sit.
            contentPadding = PaddingValues(start = 32.dp, end = 32.dp, top = 60.dp, bottom = 200.dp)
        ) {
            itemsIndexed(
                parsedLyrics,
                key = { index, item -> "lyric_${item.startMs}_$index" }
            ) { index, item ->
                val nextStartMs = parsedLyrics.getOrNull(index + 1)?.startMs
                LyricsLine(
                    line = item,
                    // Every line in the group is being sung, so every line in
                    // the group is lit — two overlapping vocals read as two
                    // bright lines, not one bright and one dimmed away.
                    isActiveLine = index in activeGroup,
                    nextLineStartMs = nextStartMs,
                    clock = clock,
                    onSeek = onSeek,
                    focusDistance = activeGroup.distanceTo(index),
                    isBrowsing = isBrowsing,
                    animateWordFill = animateWordFill,
                    reduceAnimation = reduceAnimation,
                    fontScale = textSizeMultiplier,
                    textAlign = textAlignment
                )
            }

            if (!lyricsSource.isNullOrBlank()) {
                item {
                    Text(
                        text = "Provided by $lyricsSource",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 48.dp, bottom = 16.dp),
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                            textAlign = textAlignment
                        )
                    )
                }
            }
        }
        }

        // Drawn over the page, and only when asked for: it recomposes on every
        // frame of playback, since the numbers are read off the clock as it
        // moves.
        if (scrollDebug) {
            LyricsScrollDebugOverlay(
                listState = listState,
                activeGroup = activeGroup,
                clock = clock,
                isBrowsing = isBrowsing,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
