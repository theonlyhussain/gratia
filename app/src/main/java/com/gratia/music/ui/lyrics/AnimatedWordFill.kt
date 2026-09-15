package com.gratia.music.ui.lyrics

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gratia.music.lyrics.LyricWord

@Composable
fun AnimatedWordFill(
    word: LyricWord,
    currentPositionProvider: () -> Long,
    isLineActive: Boolean
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()

    val textStyle = remember {
        TextStyle(
            fontSize = 28.sp,
            fontWeight = FontWeight.ExtraBold,
            lineHeight = 36.sp
        )
    }

    val measured = remember(word.text, textStyle) {
        textMeasurer.measure(word.text, textStyle)
    }
    val wordW = measured.size.width.toFloat()
    val wordH = measured.size.height.toFloat()

    val hasValidTiming = remember(word.startMs, word.endMs) {
        word.startMs > 0L && word.endMs > word.startMs
    }
    val wordDuration = (word.endMs - word.startMs).coerceAtLeast(1L)

    val progress by remember(word.startMs, word.endMs, hasValidTiming) {
        derivedStateOf {
            if (!hasValidTiming) 0f
            else {
                val raw = (currentPositionProvider() - word.startMs).toFloat() / wordDuration
                (raw.coerceIn(0f, 1f) * 60f).toInt() / 60f
            }
        }
    }

    val isActive by remember(word.startMs, word.endMs, hasValidTiming) {
        derivedStateOf {
            hasValidTiming && currentPositionProvider() in word.startMs..word.endMs
        }
    }

    val lengthFactor = remember(word.text) {
        (word.text.length / 8f).coerceIn(0.25f, 1f)
    }
    val microY by animateFloatAsState(
        targetValue = if (isActive && isLineActive) -1.2f * lengthFactor else 0f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow),
        label = "WordMicroY"
    )

    val microYPx = with(density) { microY.dp.toPx() }

    Canvas(
        modifier = Modifier
            .size(
                width = with(density) { wordW.toDp() },
                height = with(density) { wordH.toDp() }
            )
            .graphicsLayer {
                translationY = microYPx
                alpha = if (isLineActive) 1f else 0.2f
            }
    ) {
        val baseAlpha = if (isLineActive) 0.35f else 1f
        drawText(
            textLayoutResult = measured,
            color = Color.White.copy(alpha = baseAlpha)
        )

        val clipRight = wordW * progress
        if (clipRight > 0.5f) {
            clipRect(left = 0f, top = 0f, right = clipRight, bottom = wordH) {
                drawText(
                    textLayoutResult = measured,
                    color = Color.White
                )
            }
        }
    }
}
