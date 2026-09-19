package com.gratia.music.ui.lyrics

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.gratia.music.lyrics.LyricLine

/**
 * An instrumental stretch: the part of a song where nothing is sung.
 *
 * The break counts itself out rather than being marked. Three dots light left
 * to right across the interlude, each owning an equal share of it, so a long
 * instrumental reads as time running down instead of a symbol parked on screen
 * waiting for the singing to come back.
 *
 * The row itself opens as the singing stops and closes again as it returns, and
 * holds no space while it is closed — an interlude that kept its row open
 * through the verse either side of it left a hole in the list, and the page
 * scrolled past empty space to reach the next thing sung.
 */
@Composable
fun MusicLine(
    line: LyricLine,
    nextStartMs: Long?,
    isActiveLine: Boolean,
    clock: State<Long>,
    reduceAnimation: Boolean = false
) {
    val swell by animateFloatAsState(
        targetValue = if (isActiveLine) 1f else 0f,
        animationSpec = if (reduceAnimation) {
            snap()
        } else {
            tween(
                durationMillis = if (isActiveLine) 400 else 350,
                easing = LYRIC_EASING
            )
        },
        label = "GapSwell"
    )

    val until = nextStartMs ?: (line.timeMs + GAP_FALLBACK_MS)
    val span = (until - line.timeMs).coerceAtLeast(1L)
    val dotsWidth = GAP_DOT_SIZE * GAP_DOTS + GAP_DOT_GAP * (GAP_DOTS - 1)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height((GAP_ROW_HEIGHT + GAP_ROW_SPACING) * swell)
            .clipToBounds(),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.5f * swell),
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .size(width = dotsWidth, height = GAP_DOT_SIZE)
                    .graphicsLayer {
                        val grow = GAP_REST_SCALE + (1f - GAP_REST_SCALE) * swell
                        scaleX = grow
                        scaleY = grow
                        transformOrigin = TransformOrigin(0f, 0.5f)
                        alpha = swell
                    }
                    .drawBehind {
                        // Read here rather than in composition: the fill moves
                        // every frame, and this way a break costs a redraw of
                        // three circles, not a recomposition.
                        val through = ((clock.value - line.timeMs).toFloat() / span)
                            .coerceIn(0f, 1f)
                        val radius = GAP_DOT_SIZE.toPx() / 2f
                        val stride = (GAP_DOT_SIZE + GAP_DOT_GAP).toPx()
                        repeat(GAP_DOTS) { dot ->
                            // Each dot owns its share of the break and fills
                            // across it, so they light left to right.
                            val lit = (through * GAP_DOTS - dot).coerceIn(0f, 1f)
                            drawCircle(
                                color = Color.White.copy(
                                    alpha = GAP_DOT_REST + (1f - GAP_DOT_REST) * lit
                                ),
                                radius = radius,
                                center = Offset(radius + dot * stride, size.height / 2f)
                            )
                        }
                    }
            )
        }
    }
}
