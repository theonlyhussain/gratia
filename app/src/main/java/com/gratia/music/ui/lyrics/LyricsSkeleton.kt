package com.gratia.music.ui.lyrics

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * The shape of the lyrics page, before there are any lyrics to put on it.
 *
 * A lookup takes a moment — several providers are tried in turn — and what is
 * on screen for that moment decides whether the page feels like it is working
 * or like it is empty. A spinner says neither; it says only that something is
 * happening somewhere. This says what is happening: a page of lyrics is being
 * laid out, at the same size, in the same place, with the same rhythm the real
 * lines will have when they arrive.
 *
 * The block shapes are the part that matters. One entry per line of the song,
 * and one fraction per row that line wraps to — because at 34sp a line of a
 * song is rarely one row, so the rows that wrap run nearly the full column and
 * only the last one of each is short. A ladder of evenly spaced bars of
 * assorted lengths is what a loading list looks like; ragged bottoms is what
 * text looks like.
 *
 * Set to the panel's own metrics: a bar stands the cap height of the type the
 * lines are drawn in, the rows of one line sit a line-height apart, and lines
 * are a row's own padding further apart again than that. Laid out this way
 * nothing moves when the real lyrics land — the bars are simply replaced.
 */
@Composable
fun LyricsSkeleton(
    modifier: Modifier = Modifier,
    reduceAnimation: Boolean = false
) {
    val brush = if (reduceAnimation) staticBrush() else shimmeringBrush()

    Column(
        modifier = modifier
            .fillMaxSize()
            // There are deliberately more blocks than fit: a page of lyrics
            // carries on past the fold, and bars running off the bottom say so.
            // Clipped so the ones that overflow are simply cut off rather than
            // drawn over the controls underneath.
            .clipToBounds()
            // Matched to the lyrics list's own content padding, so the
            // skeleton occupies exactly the space the lines will.
            .padding(start = 32.dp, end = 32.dp, top = 60.dp),
        verticalArrangement = Arrangement.spacedBy(SKELETON_BLOCK_GAP)
    ) {
        repeat(SKELETON_LINE_COUNT) { index ->
            val rows = SKELETON_BLOCKS[index % SKELETON_BLOCKS.size]
            Column(verticalArrangement = Arrangement.spacedBy(SKELETON_LEADING)) {
                rows.forEach { fraction ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction)
                            .height(SKELETON_BAR)
                            .clip(RoundedCornerShape(9.dp))
                            .background(brush)
                    )
                }
            }
        }
    }
}

/**
 * The sweep across the placeholder bars.
 *
 * Drawn from white at low alpha rather than the theme's greys: this page always
 * sits over the player's blurred artwork, in every theme, and a grey bar on a
 * dark blur is a bar nobody can see.
 */
@Composable
private fun shimmeringBrush(): Brush {
    val base = Color.White.copy(alpha = 0.06f)
    val highlight = Color.White.copy(alpha = 0.15f)

    val transition = rememberInfiniteTransition(label = "lyricsSkeleton")
    val shift by transition.animateFloat(
        initialValue = -SHIMMER_WIDTH,
        targetValue = SHIMMER_TRAVEL,
        animationSpec = infiniteRepeatable(
            animation = tween(SKELETON_PERIOD_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "lyricsSkeletonShift"
    )

    return Brush.linearGradient(
        colors = listOf(base, highlight, base),
        start = Offset(shift, 0f),
        end = Offset(shift + SHIMMER_WIDTH, 0f)
    )
}

/** Held still, for when the animation is switched off. */
private fun staticBrush(): Brush = Brush.linearGradient(
    colors = listOf(
        Color.White.copy(alpha = 0.08f),
        Color.White.copy(alpha = 0.08f)
    )
)

/** A placeholder line short enough to read as one, long enough to wrap. */
private val SKELETON_BLOCKS = listOf(
    floatArrayOf(0.97f, 0.54f),
    floatArrayOf(0.92f, 0.99f, 0.41f),
    floatArrayOf(0.68f),
    floatArrayOf(0.95f, 0.73f),
    floatArrayOf(0.89f, 0.96f, 0.37f)
)

/** How many lyric lines to stand in for. Enough to fill the page. */
private const val SKELETON_LINE_COUNT = 7

/** The cap height of the 34sp the lyrics are set in. */
private val SKELETON_BAR = 26.dp

/** The rows of one wrapped line, a line-height apart. */
private val SKELETON_LEADING = 15.dp

/** Lines are a row's own padding further apart again than their own rows. */
private val SKELETON_BLOCK_GAP = 35.dp

private const val SKELETON_PERIOD_MS = 1_400
private const val SHIMMER_WIDTH = 420f
private const val SHIMMER_TRAVEL = 1_600f
