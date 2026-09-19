package com.gratia.music.ui.lyrics

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * Draws the auto-scroll's own view of the page over the lyrics.
 *
 * Three things, so the follow can be watched rather than inferred: the safe band
 * the group being sung is kept inside, the rows currently counted as being sung,
 * and the move [decideLyricScroll] would make against the layout as it stands.
 *
 * Read-only: it measures the same list the follow measures and changes nothing.
 * It does recompose every frame of playback, though — the position is read here
 * so the numbers move with the song — which is why it is behind a setting rather
 * than always on.
 *
 * Expected on screen:
 * - The amber lines are the safe band's top and bottom. A line sitting inside
 *   them is being followed; a group crossing one is what makes the page move.
 * - The green bands mark every row in the active group. More than one at once
 *   means overlapping vocals, which is the case the group exists for.
 * - `target` names the move the follow would make right now: `— (no move)` when
 *   the group already fits, otherwise the row and the distance from the top of
 *   the viewport it would be placed at.
 * - `browsing` shows the manual-scroll stand-down, which suppresses the move
 *   regardless of what `target` says.
 */
@Composable
internal fun LyricsScrollDebugOverlay(
    listState: LazyListState,
    activeGroup: ActiveLyricGroup,
    clock: State<Long>,
    isBrowsing: Boolean,
    modifier: Modifier = Modifier
) {
    // Read in composition, not in a draw lambda: `layoutInfo` is snapshot-backed
    // and swaps value on every scroll, so this keeps the boxes in step with the
    // list without the follow having to tell it anything.
    val layout = listState.layoutInfo
    val viewportHeight = layout.viewportSize.height
    if (viewportHeight <= 0) return

    val safeTop = (viewportHeight * LYRIC_SAFE_TOP_FRACTION).roundToInt()
    val safeBottom = (viewportHeight * LYRIC_SAFE_BOTTOM_FRACTION).roundToInt()
    val visible = layout.visibleItemsInfo.map {
        LyricItemBounds(index = it.index, top = it.offset, height = it.size)
    }
    val target = decideLyricScroll(activeGroup.first, activeGroup.last, visible, viewportHeight)
    val positionMs = clock.value

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(
                    color = SAFE_BAND_FILL,
                    topLeft = Offset(0f, safeTop.toFloat()),
                    size = Size(size.width, (safeBottom - safeTop).toFloat())
                )
                val topY = safeTop.toFloat()
                val bottomY = safeBottom.toFloat()
                drawLine(SAFE_EDGE, Offset(0f, topY), Offset(size.width, topY), strokeWidth = EDGE_STROKE)
                drawLine(SAFE_EDGE, Offset(0f, bottomY), Offset(size.width, bottomY), strokeWidth = EDGE_STROKE)

                visible.forEach { row ->
                    if (row.index in activeGroup) {
                        val topLeft = Offset(0f, row.top.toFloat())
                        val rowSize = Size(size.width, row.height.toFloat())
                        drawRect(ACTIVE_ROW_FILL, topLeft, rowSize)
                        drawRect(ACTIVE_ROW_EDGE, topLeft, rowSize, style = Stroke(width = EDGE_STROKE))
                    }
                }
            }
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                // Kept clear of the top of the page, where the lyrics themselves
                // fade out under the parent's mask.
                .padding(start = 12.dp, top = 80.dp)
                .background(PANEL_FILL, RoundedCornerShape(8.dp))
                .border(1.dp, SAFE_EDGE.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            val group = if (activeGroup.isEmpty) {
                "—"
            } else {
                "${activeGroup.first}..${activeGroup.last} " +
                    "(${activeGroup.last - activeGroup.first + 1})"
            }
            val rows = if (visible.isEmpty()) {
                "—"
            } else {
                "${visible.first().index}..${visible.last().index}"
            }
            val move = when (target) {
                is LyricScrollTarget.Item ->
                    "→ item ${target.index} @ ${target.topFromViewport}"
                LyricScrollTarget.None -> "— (no move)"
            }

            DebugRow("pos", "${positionMs}ms")
            DebugRow("group", group)
            DebugRow("rows", rows)
            DebugRow("viewport", "$viewportHeight px")
            DebugRow("safe", "$safeTop .. $safeBottom")
            DebugRow("target", move)
            DebugRow("browsing", isBrowsing.toString())
        }
    }
}

@Composable
private fun DebugRow(label: String, value: String) {
    Text(
        text = label.padEnd(9) + value,
        color = Color.White,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        fontFamily = FontFamily.Monospace
    )
}

/** The band the followed group is kept inside. */
private val SAFE_BAND_FILL = Color(0x1400E676)

/** Its top and bottom edges. */
private val SAFE_EDGE = Color(0xCCFFB300)

/** Rows counted as being sung. */
private val ACTIVE_ROW_FILL = Color(0x3300E676)
private val ACTIVE_ROW_EDGE = Color(0x9900E676)

/** The panel's own backing, so the numbers stay legible over the lyrics. */
private val PANEL_FILL = Color(0xC0000000)

private const val EDGE_STROKE = 2f
