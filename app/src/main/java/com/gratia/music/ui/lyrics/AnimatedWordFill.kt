package com.gratia.music.ui.lyrics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
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

    // Geometry MUST remain absolutely stable.
    // The inactive and active visual layers use this exact same typography.
    val textStyle = remember {
        TextStyle(
            fontSize = 28.sp,
            fontWeight = FontWeight.ExtraBold,
            lineHeight = 36.sp
        )
    }

    // Measure the word layout exactly ONCE.
    val measured = remember(word.text, textStyle) {
        textMeasurer.measure(word.text, textStyle)
    }
    val wordW = measured.size.width.toFloat()
    val wordH = measured.size.height.toFloat()

    val hasValidTiming = remember(word.startMs, word.endMs) {
        word.startMs > 0L && word.endMs > word.startMs
    }
    val wordDuration = (word.endMs - word.startMs).coerceAtLeast(1L)

    Canvas(
        modifier = Modifier
            .size(
                width = with(density) { wordW.toDp() },
                height = with(density) { wordH.toDp() }
            )
            .graphicsLayer {
                // Line-level dimming applied at the hardware layer.
                alpha = if (isLineActive) 1f else 0.2f
            }
    ) {
        // Direct read of current position during the draw phase.
        // This triggers a cheap redraw rather than a composition pass.
        val progress = if (!hasValidTiming) 0f else {
            val raw = (currentPositionProvider() - word.startMs).toFloat() / wordDuration
            raw.coerceIn(0f, 1f)
        }

        // Layer 1: Inactive Base
        // Uses 35% opacity so it's subdued but fully readable.
        val baseAlpha = if (isLineActive) 0.35f else 1f
        drawText(
            textLayoutResult = measured,
            color = Color.White.copy(alpha = baseAlpha)
        )

        // Layer 2: Active Fill
        // Clips the fully-bright text to the exact physical progress.
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
