package com.gratia.music.ui.lyrics

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gratia.music.lyrics.LyricLine
import com.gratia.music.ui.theme.Inter

/**
 * Animated letter composable that handles opacity, translationY, and glowing effects.
 * Driven smoothly by the playback progress.
 */
@Composable
fun AnimatedLetter(
    letter: String,
    startMs: Long,
    durationMs: Long,
    currentTimeMs: Long,
    modifier: Modifier = Modifier
) {
    val isLetterActive = currentTimeMs >= startMs
    val targetAlpha = if (isLetterActive) 1.0f else 0.5f
    val targetTranslateY = if (isLetterActive) -2f else 0f // in dp

    val animDuration = durationMs.coerceAtLeast(30L).toInt()

    val alpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(
            durationMillis = animDuration,
            easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f) // EaseOutExpo
        ),
        label = "letterAlpha"
    )

    val translateY by animateFloatAsState(
        targetValue = targetTranslateY,
        animationSpec = tween(
            durationMillis = animDuration,
            easing = LinearEasing
        ),
        label = "letterTranslateY"
    )

    val density = LocalDensity.current.density
    val shadow = if (alpha > 0.5f) {
        val progress = (alpha - 0.5f) * 2f // 0f to 1.0f
        Shadow(
            color = Color.White.copy(alpha = progress * 0.4f),
            blurRadius = progress * 16f * density
        )
    } else null

    Text(
        text = letter,
        fontFamily = Inter,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 28.sp,
        color = Color.White,
        style = TextStyle(shadow = shadow),
        modifier = modifier
            .graphicsLayer {
                this.alpha = alpha
                this.translationY = translateY * density
            }
    )
}

/**
 * Instrumental / music break animation rendering 3 pulsing dots.
 * Matches Shopify Skia drawing behavior.
 */
@Composable
fun MusicLine(
    isActiveLine: Boolean,
    durationMs: Long,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "musicLinePulse")
    val radiusMultiplier by if (isActiveLine) {
        infiniteTransition.animateFloat(
            initialValue = 8f,
            targetValue = 12f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = LinearOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "radius"
        )
    } else {
        remember { mutableStateOf(8f) }
    }

    val targetOpacity = if (isActiveLine) 1.0f else 0.1f
    val opacityDuration = if (isActiveLine) durationMs.coerceAtLeast(100L).toInt() else 100
    val opacity by animateFloatAsState(
        targetValue = targetOpacity,
        animationSpec = tween(durationMillis = opacityDuration, easing = FastOutSlowInEasing),
        label = "musicLineOpacity"
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(63.dp)
    ) {
        val cY = size.height / 2f
        val baseR = radiusMultiplier * density
        val spacing = 12f * density

        drawCircle(
            color = Color.White,
            radius = baseR,
            center = Offset(spacing, cY),
            alpha = opacity
        )
        drawCircle(
            color = Color.White,
            radius = baseR,
            center = Offset(spacing * 4f, cY),
            alpha = opacity
        )
        drawCircle(
            color = Color.White,
            radius = baseR,
            center = Offset(spacing * 7f, cY),
            alpha = opacity
        )
    }
}

/**
 * Renders a single lyric line.
 * Implements transitions for opacity, scale, and blur.
 * Animates individual words/letters if word synced timestamps are present.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LyricLineView(
    line: LyricLine,
    isActive: Boolean,
    isPast: Boolean,
    currentTimeMs: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }

    // Target States
    val targetScale = when {
        isActive -> 1.05f
        isPast -> 0.96f
        else -> 0.94f
    }

    val targetAlpha = when {
        isActive -> 1.0f
        isPast -> 0.15f
        else -> 0.25f
    }

    val targetBlur = when {
        isActive -> 0.dp
        isPast -> 1.5.dp
        else -> 2.5.dp
    }

    // Cubic Bezier Easing
    val floatEasing = CubicBezierEasing(0.25f, 1f, 0.4f, 1f)
    val scaleSpec = tween<Float>(durationMillis = 750, easing = floatEasing)
    val alphaSpec = tween<Float>(durationMillis = 750, easing = floatEasing)
    val blurSpec = tween<Dp>(durationMillis = 750, easing = floatEasing)

    val scale by animateFloatAsState(targetValue = targetScale, animationSpec = scaleSpec, label = "lineScale")
    val alpha by animateFloatAsState(targetValue = targetAlpha, animationSpec = alphaSpec, label = "lineAlpha")
    val blurRadius by animateDpAsState(targetValue = targetBlur, animationSpec = blurSpec, label = "lineBlur")

    val cottonColor = Color(0xFFEDEBDE)

    val lineModifier = modifier
        .fillMaxWidth()
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .alpha(alpha)
        .let {
            if (blurRadius > 0.dp) it.blur(blurRadius) else it
        }
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )

    if (line.text.isBlank() || line.text == " ") {
        val currentLineDuration = line.startMs
        val nextLineDuration = line.endMs ?: (line.startMs + 2000L)
        MusicLine(
            isActiveLine = isActive,
            durationMs = nextLineDuration - currentLineDuration,
            modifier = lineModifier
        )
    } else if (isActive && line.words.isNotEmpty()) {
        FlowRow(
            modifier = lineModifier,
            horizontalArrangement = Arrangement.Start,
            verticalArrangement = Arrangement.Center
        ) {
            line.words.forEachIndexed { wordIndex, word ->
                val wordDuration = if (wordIndex < line.words.size - 1) {
                    line.words[wordIndex + 1].startMs - word.startMs
                } else {
                    (line.endMs ?: (word.startMs + 500L)) - word.startMs
                }

                val letters = word.text.map { it.toString() } + if (wordIndex == line.words.size - 1) emptyList() else listOf(" ")
                val letterDuration = wordDuration / letters.size.coerceAtLeast(1)

                Row {
                    letters.forEachIndexed { letterIndex, letter ->
                        val letterStartMs = word.startMs + letterIndex * letterDuration
                        AnimatedLetter(
                            letter = letter,
                            startMs = letterStartMs,
                            durationMs = letterDuration,
                            currentTimeMs = currentTimeMs
                        )
                    }
                }
            }
        }
    } else {
        Text(
            text = line.text,
            fontFamily = Inter,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 28.sp,
            color = cottonColor,
            lineHeight = 38.sp,
            modifier = lineModifier.padding(vertical = 4.dp)
        )
    }
}
