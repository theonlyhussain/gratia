package com.gratia.music.ui.lyrics

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gratia.music.lyrics.LyricWord
import kotlin.math.pow

// Easing ported from React Native's Easing.out(Easing.exp)
val ExpOutEasing = Easing { fraction ->
    if (fraction == 1f) 1f else 1f - 2.0.pow(-10.0 * fraction).toFloat()
}

@Composable
fun AnimatedLetter(
    letter: Char,
    delayMs: Long,
    durationMs: Int,
    currentPositionMs: Long
) {
    val hasStarted = currentPositionMs >= delayMs
    
    val opacity by animateFloatAsState(
        targetValue = if (hasStarted) 1f else 0.5f,
        animationSpec = tween(
            durationMillis = durationMs,
            easing = ExpOutEasing
        ),
        label = "LetterOpacity"
    )

    val translateY by animateFloatAsState(
        targetValue = if (hasStarted) -2f else 0f,
        animationSpec = tween(
            durationMillis = durationMs,
            easing = androidx.compose.animation.core.LinearEasing
        ),
        label = "LetterTranslateY"
    )

    Text(
        text = letter.toString(),
        fontSize = 28.sp,
        fontWeight = FontWeight.ExtraBold,
        color = Color.White,
        modifier = Modifier
            .alpha(opacity)
            .offset(y = translateY.dp)
    )
}

@Composable
fun AnimatedWord(
    word: LyricWord,
    durationMs: Int,
    currentPositionMs: Long
) {
    val letters = word.text.toCharArray()
    val letterDuration = if (letters.isNotEmpty()) durationMs / letters.size else durationMs
    
    Row {
        letters.forEachIndexed { index, letter ->
            val delayMs = word.startMs + (index * letterDuration)
            AnimatedLetter(
                letter = letter,
                delayMs = delayMs,
                durationMs = letterDuration,
                currentPositionMs = currentPositionMs
            )
        }
        // Add a space after the word if needed (or handle spaces at the line level)
        // Usually LRC words might have trailing spaces. We'll render exactly what's in text.
    }
}
