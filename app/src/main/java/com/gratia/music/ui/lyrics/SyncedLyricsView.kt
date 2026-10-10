package com.gratia.music.ui.lyrics

import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.platform.LocalDensity
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
 * The lyrics panel's height to anchor against when it measures as unbounded.
 *
 * The page is always laid out inside a bounded panel, so this is a guard rather
 * than a layout decision: it keeps an unconstrained measure from turning the
 * anchor and the scroll space below the last line into infinities.
 */
private val FALLBACK_LYRICS_HEIGHT = 460.dp

/** The breathing room either side of the text. */
private val LYRIC_SIDE_PADDING = 32.dp

/**
 * Premium synced lyrics view with word-level animation.
 *
 * Renders the scrolling lyrics list, holding the line being sung at a reading
 * anchor in the upper part of the page and lighting up its words as they are.
 *
 * The scroll is driven by the row being sung, not by the playback position. The
 * page moves when the line the voice is on changes, and never on a tick, a word,
 * or a scroll of its own making: it is asked once per line where that row
 * belongs — see [decideLyricScroll] and [ActiveLyricGroup] — and the answer is
 * the same anchor every time, so the document travels upward under it and the
 * line just sung leaves through the top rather than the page drifting down the
 * screen.
 *
 * The anchor is expressed as the list's own leading content padding. Nothing
 * has to carry a pixel offset around: the first row of the list rests at the
 * anchor, and every later row is put in the same place by scrolling it to the
 * top of the content area, which is a position a lazy list can always reach.
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
    val blurUnfocused by settingsDataStore.blurUnfocusedLyricsFlow.collectAsState(initial = true)

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

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {

        // ── The reading anchor ─────────────────────────────────────────────
        // The page's own height, measured, and not the screen: this list is
        // whatever the player left between the track header and the transport
        // controls. Just under a quarter of the way down it is where the line
        // being sung is read from.
        val panelHeight = if (maxHeight.value.isFinite()) maxHeight else FALLBACK_LYRICS_HEIGHT
        val anchorDp = panelHeight * LYRIC_ANCHOR_FRACTION
        // Everything below the anchor, so the last line of a song can rise to it
        // exactly as every other line does. Without this the end of the song
        // could only be read from the foot of the page — see [lastRowTopAtEnd].
        val trailingDp = panelHeight - anchorDp
        val anchorPx = with(LocalDensity.current) { anchorDp.roundToPx() }

        // ── Following the singing ────────────────────────────────────────
        // One controller and one scroll. It runs when the row being *sung*
        // changes — not on a playback tick, not on a word, not on a scroll — so
        // a line takes the reading position once and then holds it while its
        // words sweep underneath. Keying on that row also means a second voice
        // joining the line already being sung changes nothing on screen, since
        // the page follows the oldest row of the group.
        var parkedLyrics by remember { mutableStateOf<String?>(null) }
        // Whether anything has been sung since that park. A run-in is only
        // protected from being re-parked while it is still the same run-in: once
        // lines have been sung, an empty group again means the song has gone
        // back before its first line — a rewind, or the same track on repeat —
        // and the page belongs at the top of the document again.
        var sungSincePark by remember(lyrics) { mutableStateOf(false) }
        val anchorIndex = activeGroup.first
        LaunchedEffect(lyrics, anchorIndex, parsedLyrics.size, isBrowsing, reduceAnimation, anchorPx) {
            if (isBrowsing || parsedLyrics.isEmpty()) return@LaunchedEffect

            if (anchorIndex < 0) {
                // Nothing has been reached yet — the run-in before the first
                // line. Park the page on the first row once per run-in, so the
                // line the singing will start on is already waiting at the
                // anchor — and only once, because a page read by hand through a
                // long intro must not be pulled back to the top of the list
                // again while there is still nothing being sung.
                if (parkedLyrics == lyrics && !sungSincePark) return@LaunchedEffect
                listState.scrollToItem(0)
                parkedLyrics = lyrics
                sungSincePark = false
                return@LaunchedEffect
            }
            sungSincePark = true

            // Lyrics can land while the song is already playing. Wait for the
            // list to be measured rather than dropping the follow — nothing
            // would ask again until the next line.
            if (listState.layoutInfo.viewportSize.height <= 0) {
                snapshotFlow { listState.layoutInfo.viewportSize.height }.first { it > 0 }
            }
            val layout = listState.layoutInfo
            val visible = layout.visibleItemsInfo.map {
                LyricItemBounds(index = it.index, top = it.offset, height = it.size)
            }
            // The leading scroll space, read from the list rather than assumed:
            // a lazy list reports the top of its viewport as a negative offset
            // once the content is padded, and that is what the padding is worth
            // in the same space the rows are measured in. Taking it from the
            // layout keeps the anchor and the padding from ever drifting apart.
            val beforeContentPadding = (-layout.viewportStartOffset).coerceAtLeast(0)
            val target = decideLyricScroll(
                anchorIndex = anchorIndex,
                visible = visible,
                viewportHeight = layout.viewportSize.height,
                beforeContentPadding = beforeContentPadding
            )
            if (target !is LyricScrollTarget.Item) return@LaunchedEffect
            // A row on screen is a handover and is animated; one that is not is a
            // seek, and lands at once instead of showing every line in between.
            val onScreen = visible.any { it.index == target.index }
            // A positive offset scrolls forward, so the target is negated.
            val scrollOffset = -target.anchorOffset
            if (animateLyricScroll(onScreen, reduceAnimation)) {
                listState.animateScrollToItem(target.index, scrollOffset)
            } else {
                listState.scrollToItem(target.index, scrollOffset)
            }
        }

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
            contentPadding = PaddingValues(
                start = LYRIC_SIDE_PADDING,
                end = LYRIC_SIDE_PADDING,
                // The reading anchor, and the room the end of the song needs to
                // reach it. See the anchor block above.
                top = anchorDp,
                bottom = trailingDp
            )
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
                    // Which side of the singing this row is on. The brightness
                    // falls away differently above the playing line than below
                    // it — see [LyricFocusSide] — because a row already sung
                    // has been read and a row not yet reached has not.
                    focusSide = activeGroup.focusSide(index),
                    isBrowsing = isBrowsing,
                    animateWordFill = animateWordFill,
                    reduceAnimation = reduceAnimation,
                    blurUnfocused = blurUnfocused,
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
                beforeContentPadding = anchorPx,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
