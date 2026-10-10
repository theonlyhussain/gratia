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

/**
 * Draws the auto-scroll's own view of the page over the lyrics.
 *
 * Three things, so the follow can be watched rather than inferred: the reading
 * anchor the row being sung is held at, the rows currently counted as being
 * sung, and the move [decideLyricScroll] would make against the layout as it
 * stands.
 *
 * Read-only: it measures the same list the follow measures and changes nothing.
 * It does recompose every frame of playback, though — the position is read here
 * so the numbers move with the song — which is why it is behind a setting rather
 * than always on.
 *
 * Expected on screen:
 * - The amber line is the reading anchor: the row being sung should have its top
 *   on it, in the upper part of the page. It is drawn at the same place the
 *   list's leading content padding puts the first row of the list.
 * - The blue band is where the row being sung actually is, measured from the
 *   live layout. The gap between the amber line and the blue band is the follow's
 *   error, and should be nothing at all while a song plays.
 * - The green bands mark every row in the active group. More than one at once
 *   means overlapping vocals, which is the case the group exists for.
 * - `target` names the move the follow would make right now: `— (no move)` when
 *   the row is already at the anchor, otherwise the row and the offset in the
 *   list's own layout space it would be put at.
 * - `browsing` shows the manual-scroll stand-down, which suppresses the move
 *   regardless of what `target` says.
 */
@Composable
internal fun LyricsScrollDebugOverlay(
    listState: LazyListState,
    activeGroup: ActiveLyricGroup,
    clock: State<Long>,
    isBrowsing: Boolean,
    beforeContentPadding: Int,
    modifier: Modifier = Modifier
) {
    // Read in composition, not in a draw lambda: `layoutInfo` is snapshot-backed
    // and swaps value on every scroll, so this keeps the boxes in step with the
    // list without the follow having to tell it anything.
    val layout = listState.layoutInfo
    val viewportHeight = layout.viewportSize.height
    if (viewportHeight <= 0) return

    // The anchor in the list's own space, and the same line on screen: that
    // space begins *below* the leading content padding, so a row at
    // [anchorOffset] is drawn this far down the lyrics viewport.
    val anchorOffset = lyricAnchorOffset(viewportHeight, beforeContentPadding)
    val anchorOnScreen = anchorOffset + beforeContentPadding
    val visible = layout.visibleItemsInfo.map {
        LyricItemBounds(index = it.index, top = it.offset, height = it.size)
    }
    val target = decideLyricScroll(
        anchorIndex = activeGroup.first,
        visible = visible,
        viewportHeight = viewportHeight,
        beforeContentPadding = beforeContentPadding
    )
    val anchorRow = visible.firstOrNull { it.index == activeGroup.first }
    val positionMs = clock.value

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                val anchorY = anchorOnScreen.toFloat()
                drawLine(
                    ANCHOR_EDGE,
                    Offset(0f, anchorY),
                    Offset(size.width, anchorY),
                    strokeWidth = EDGE_STROKE
                )

                visible.forEach { row ->
                    // Rows are reported in the list's own space, which starts
                    // below the leading padding; on screen they sit that much
                    // further down.
                    val top = (row.top + beforeContentPadding).toFloat()
                    val topLeft = Offset(0f, top)
                    val rowSize = Size(size.width, row.height.toFloat())
                    if (row.index == activeGroup.first) {
                        drawRect(ANCHOR_ROW_FILL, topLeft, rowSize)
                        drawRect(ANCHOR_ROW_EDGE, topLeft, rowSize, style = Stroke(width = EDGE_STROKE))
                    }
                    if (row.index in activeGroup) {
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
                .border(1.dp, ANCHOR_EDGE.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
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
                is LyricScrollTarget.Item -> "→ item ${target.index} @ ${target.anchorOffset}"
                LyricScrollTarget.None -> "— (no move)"
            }
            val where = if (anchorRow == null) "—" else "${anchorRow.top}"

            DebugRow("pos", "${positionMs}ms")
            DebugRow("group", group)
            DebugRow("rows", rows)
            DebugRow("viewport", "$viewportHeight px")
            DebugRow("padding", "$beforeContentPadding px")
            DebugRow("anchor", "$anchorOffset (on screen $anchorOnScreen)")
            DebugRow("active", where)
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

/** The reading anchor the row being sung is held at. */
private val ANCHOR_EDGE = Color(0xCCFFB300)

/** Where that row actually sits, measured from the live layout. */
private val ANCHOR_ROW_FILL = Color(0x223D7BFF)
private val ANCHOR_ROW_EDGE = Color(0x993D7BFF)

/** Rows counted as being sung. */
private val ACTIVE_ROW_FILL = Color(0x3300E676)
private val ACTIVE_ROW_EDGE = Color(0x9900E676)

/** The panel's own backing, so the numbers stay legible over the lyrics. */
private val PANEL_FILL = Color(0xC0000000)

private const val EDGE_STROKE = 2f
