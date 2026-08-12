package com.gratia.music.ui.lyrics

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.gratia.music.lyrics.LyricLine
import kotlin.math.pow

val QuadInOutEasing = Easing { fraction ->
    if (fraction < 0.5f) {
        2f * fraction * fraction
    } else {
        1f - (-2.0 * fraction + 2.0).pow(2.0).toFloat() / 2f
    }
}

@Composable
fun LyricsLine(
    line: LyricLine,
    isActiveLine: Boolean,
    nextLineStartMs: Long?,
    currentPositionMs: Long
) {
    val opacity by animateFloatAsState(
        targetValue = if (isActiveLine) 1f else 0.1f,
        animationSpec = tween(
            durationMillis = 100,
            easing = QuadInOutEasing
        ),
        label = "LineOpacity"
    )

    // Handle instrumental gap logic (if content is empty or explicitly marked)
    // The lyrics-animation repo used line.content === " "
    val isInstrumental = line.words.isEmpty() || (line.words.size == 1 && line.words[0].text.trim().isEmpty())

    if (isInstrumental) {
        val duration = (nextLineStartMs ?: (line.startMs + 2000)) - line.startMs
        MusicLine(isActiveLine = isActiveLine, durationMs = duration.toInt())
        return
    }

    // WrapLayout to wrap words (Row with Modifier.wrapContentWidth or FlowRow)
    @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(opacity)
            .padding(bottom = 35.dp),
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
            
            // Add a spacer to represent space between words if word text doesn't contain a space
            if (wordIndex < line.words.size - 1 && !word.text.endsWith(" ")) {
                 Spacer(modifier = Modifier.width(8.dp)) // Approximation of space width
            }
        }
    }
}
