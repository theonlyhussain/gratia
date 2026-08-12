package com.gratia.music.ui.lyrics

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import com.gratia.music.lyrics.LyricLine

/**
 * A single lyrics line within the synced scroll list.
 *
 * Visual behaviour:
 * - **Active line:** full opacity + slight scale-up (1.0 → 1.02) to subtly
 *   draw attention without looking jarring.
 * - **Inactive line:** 20 % opacity, normal scale.
 * - Transition uses a 250 ms ease-out, matching Apple Music's smooth fade.
 *
 * Instrumental gaps are delegated to [MusicLine].
 */
@Composable
fun LyricsLine(
    line: LyricLine,
    isActiveLine: Boolean,
    nextLineStartMs: Long?,
    currentPositionMs: Long,
    onSeek: ((Long) -> Unit)? = null
) {
    val opacity by animateFloatAsState(
        targetValue = if (isActiveLine) 1f else 0.2f,
        animationSpec = tween(
            durationMillis = 250,
            easing = androidx.compose.animation.core.FastOutSlowInEasing
        ),
        label = "LineOpacity"
    )

    // Subtle scale effect on the active line for visual emphasis
    val scale by animateFloatAsState(
        targetValue = if (isActiveLine) 1.02f else 1f,
        animationSpec = tween(
            durationMillis = 300,
            easing = androidx.compose.animation.core.FastOutSlowInEasing
        ),
        label = "LineScale"
    )

    // Handle instrumental gap logic
    val isInstrumental = line.words.isEmpty() ||
            (line.words.size == 1 && line.words[0].text.trim().isEmpty())

    if (isInstrumental) {
        val duration = (nextLineStartMs ?: (line.startMs + 2000)) - line.startMs
        MusicLine(isActiveLine = isActiveLine, durationMs = duration.toInt())
        return
    }

    // Word-synced content
    @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(opacity)
            .scale(scale)
            .clickable(enabled = onSeek != null) { onSeek?.invoke(line.startMs) }
            .padding(bottom = 28.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        line.words.forEachIndexed { wordIndex, word ->
            val durationMs = if (wordIndex < line.words.size - 1) {
                line.words[wordIndex + 1].startMs - word.startMs
            } else if (nextLineStartMs != null) {
                nextLineStartMs - word.startMs
            } else {
                500L
            }

            AnimatedWord(
                word = word,
                durationMs = durationMs.toInt(),
                currentPositionMs = currentPositionMs
            )

            // Space between words
            if (wordIndex < line.words.size - 1 && !word.text.endsWith(" ")) {
                Spacer(modifier = Modifier.width(7.dp))
            }
        }
    }
}
