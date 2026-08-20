package com.gratia.music.ui.lyrics

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
 * Performance:
 * - Uses `graphicsLayer` for opacity/scale so changes are GPU-composited
 *   without triggering Compose layout passes — critical for 120Hz smoothness.
 * - Stable layout dimensions regardless of active/inactive state to prevent jumps.
 *
 * Instrumental gaps are delegated to [MusicLine].
 */
@Composable
fun LyricsLine(
    line: LyricLine,
    isActiveLine: Boolean,
    nextLineStartMs: Long?,
    currentPositionProvider: () -> Long,
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
    // A line is ONLY instrumental if it actually contains no sung text.
    val isInstrumental = line.text.trim().isEmpty() &&
            (line.words.isEmpty() || (line.words.size == 1 && line.words[0].text.trim().isEmpty()))

    if (isInstrumental) {
        val duration = (nextLineStartMs ?: (line.startMs + 2000)) - line.startMs
        MusicLine(isActiveLine = isActiveLine, durationMs = duration.toInt())
        return
    }

    // Word-synced content
    // Using graphicsLayer for alpha/scale to avoid layout invalidation on every frame.
    // The clickable modifier wraps the entire line so tapping seeks to this line's start.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                this.alpha = opacity
                this.scaleX = scale
                this.scaleY = scale
                this.transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
            }
            .clickable(enabled = onSeek != null) { onSeek?.invoke(line.startMs) }
            .padding(bottom = 32.dp)
    ) {
        @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start
        ) {
            if (line.words.isNotEmpty()) {
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
                        currentPositionProvider = currentPositionProvider
                    )

                    // Space between words
                    if (wordIndex < line.words.size - 1 && !word.text.endsWith(" ")) {
                        Spacer(modifier = Modifier.width(7.dp))
                    }
                }
            } else {
                androidx.compose.material3.Text(
                    text = line.text,
                    fontSize = 28.sp,
                    fontWeight = if (isActiveLine) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.ExtraBold,
                    color = androidx.compose.ui.graphics.Color.White,
                    lineHeight = 36.sp
                )
            }
        }

        if (!line.romanization.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            androidx.compose.material3.Text(
                text = line.romanization!!,
                fontFamily = com.gratia.music.ui.theme.Inter,
                fontSize = 18.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.7f),
                lineHeight = 24.sp
            )
        }

        if (!line.translation.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            androidx.compose.material3.Text(
                text = line.translation!!,
                fontFamily = com.gratia.music.ui.theme.Inter,
                fontSize = 16.sp,
                color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.5f),
                lineHeight = 22.sp
            )
        }
    }
}
