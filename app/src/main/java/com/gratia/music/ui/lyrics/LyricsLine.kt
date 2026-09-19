package com.gratia.music.ui.lyrics

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gratia.music.lyrics.LyricLine
import com.gratia.music.ui.theme.Inter

/** How long a translation or romanization takes to dissolve into the next one. */
private const val SUB_LINE_SWAP_MS = 380

/**
 * A single lyrics line within the synced scroll list.
 *
 * Visual behaviour, all of it driven off the line's distance from the one being
 * sung ([focusDistance]) rather than from a binary active/not-active flag:
 *
 * - **The line being sung:** full brightness, no blur, sitting fractionally
 *   forward of the stack.
 * - **The line either side of it:** still legible, so you can read ahead and
 *   behind without the page appearing to switch off.
 * - **Everything past that:** dimmed and very slightly blurred, on a ladder that
 *   flattens out rather than running to zero.
 * - **While the list is being scrolled by hand** ([isBrowsing]): the stack
 *   flattens to one brightness, because reading the page by hand is not
 *   following along and no row should be pointed at.
 *
 * When [animateWordFill] is on and the line carries word-level timing, the text
 * is drawn by [SweptLyricsLine] — the progressive sweep, the bloom on a carried
 * note, and the small lift of the word being sung. Lines with no word timings,
 * and the whole list when the animation is switched off, draw as plain text.
 *
 * Performance:
 * - Opacity, scale and blur all live on `graphicsLayer` / `Modifier.blur`, so
 *   changes are GPU-composited without a layout pass.
 * - Layout dimensions are stable regardless of active state, so nothing jumps as
 *   the handover moves down the song.
 *
 * Instrumental breaks are delegated to [MusicLine].
 */
@Composable
fun LyricsLine(
    line: LyricLine,
    isActiveLine: Boolean,
    nextLineStartMs: Long?,
    clock: State<Long>,
    onSeek: ((Long) -> Unit)? = null,
    focusDistance: Int = 0,
    isBrowsing: Boolean = false,
    animateWordFill: Boolean = true,
    reduceAnimation: Boolean = false,
    fontScale: Float = 1f,
    textAlign: TextAlign = TextAlign.Start
) {
    // A break has no words to sweep and no rows to dim — it counts itself out
    // instead, so it is handled before any of the focus work below.
    if (line.isGap) {
        MusicLine(
            line = line,
            nextStartMs = nextLineStartMs,
            isActiveLine = isActiveLine,
            clock = clock,
            reduceAnimation = reduceAnimation
        )
        return
    }

    val step = focusDistance.coerceIn(0, LINE_FALLOFF_ALPHA.lastIndex)

    val alpha by animateFloatAsState(
        targetValue = when {
            isBrowsing -> BROWSING_ALPHA
            isActiveLine -> LINE_FALLOFF_ALPHA[0]
            else -> LINE_FALLOFF_ALPHA[step]
        },
        animationSpec = if (reduceAnimation) snap() else tween(LYRIC_SETTLE_MS, easing = LYRIC_EASING),
        label = "LineAlpha"
    )

    // Blur follows the same ladder, but only where it can be drawn at all and
    // only for the rows genuinely out of focus: the playing line is never
    // blurred, and neither is a page being read by hand.
    val blurRadius by animateDpAsState(
        targetValue = when {
            !CAN_BLUR || reduceAnimation || isBrowsing || isActiveLine -> 0.dp
            else -> LINE_FALLOFF_BLUR[step]
        },
        animationSpec = if (reduceAnimation) snap() else tween(LYRIC_SETTLE_MS, easing = LYRIC_EASING),
        label = "LineBlur"
    )

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = when {
            pressed -> PRESSED_SCALE
            isActiveLine -> 1f
            else -> INACTIVE_SCALE
        },
        animationSpec = if (reduceAnimation) snap() else tween(LYRIC_SETTLE_MS, easing = LYRIC_EASING),
        label = "LineScale"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                // Anchored to the left edge so the words don't slide sideways
                // under the highlight; scaling about the centre would fight the
                // sweep.
                this.alpha = alpha
                this.scaleX = scale
                this.scaleY = scale
                this.transformOrigin = TransformOrigin(0f, 0.5f)
            }
            .blur(blurRadius, BlurredEdgeTreatment.Unbounded)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = onSeek != null
            ) { onSeek?.invoke(line.startMs) }
            .padding(bottom = 32.dp)
    ) {
        val style = lyricTextStyle(
            synced = line.isWordSynced,
            fontScale = fontScale,
            textAlign = textAlign
        )

        if (animateWordFill && line.isWordSynced) {
            SweptLyricsLine(
                line = line,
                clock = clock,
                style = style,
                dimAlpha = UNSUNG_ALPHA,
                textAlign = textAlign
            )
        } else {
            Text(
                text = line.text,
                style = style,
                color = Color.White,
                textAlign = textAlign
            )
        }

        // The answering vocal, drawn underneath the lead and a shade behind it,
        // the way Apple Music hangs a backing line under the one it answers.
        val backing = line.background
        if (backing != null && backing.text.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            SweptLyricsLine(
                line = backing,
                clock = clock,
                style = style.copy(
                    fontSize = (style.fontSize.value * (BACKING_FONT_SIZE / LYRIC_FONT_SIZE)).sp,
                    lineHeight = (style.lineHeight.value * (BACKING_LINE_HEIGHT / LYRIC_LINE_HEIGHT)).sp
                ),
                modifier = Modifier.graphicsLayer { this.alpha = BACKING_ALPHA },
                dimAlpha = UNSUNG_ALPHA,
                rise = false,
                textAlign = textAlign
            )
        }

        // Romanization and translation arrive after the line is already on
        // screen — a translation is fetched while the song plays, a romanization
        // is toggled on. Both swap the text under the words the reader is
        // already looking at, so they dissolve rather than cutting.
        val subLines = remember(line.romanization, line.translation) {
            SubLines.of(line.romanization, line.translation)
        }
        if (subLines.hasContent) {
            AnimatedContent(
                targetState = subLines,
                transitionSpec = {
                    val spec = if (reduceAnimation) {
                        snap<Float>()
                    } else {
                        tween<Float>(SUB_LINE_SWAP_MS, easing = LYRIC_EASING)
                    }
                    (fadeIn(spec) togetherWith fadeOut(spec)).using(SizeTransform(clip = false))
                },
                label = "lyricSubLineSwap"
            ) { rendered ->
                SubLinesBlock(rendered, textAlign)
            }
        }
    }
}

@Composable
private fun SubLinesBlock(subLines: SubLines, textAlign: TextAlign) {
    val alignment = when (textAlign) {
        TextAlign.Center -> Alignment.CenterHorizontally
        TextAlign.End -> Alignment.End
        else -> Alignment.Start
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        subLines.romanization?.let { romanization ->
            Spacer(Modifier.height(4.dp))
            Text(
                text = romanization,
                fontFamily = Inter,
                fontSize = 18.sp,
                lineHeight = 24.sp,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = textAlign
            )
        }
        subLines.translation?.let { translation ->
            Spacer(Modifier.height(4.dp))
            Text(
                text = translation,
                fontFamily = Inter,
                fontSize = 16.sp,
                lineHeight = 22.sp,
                color = Color.White.copy(alpha = 0.5f),
                textAlign = textAlign
            )
        }
    }
}

/** The pair of secondary lines under a lyric, as one animatable value. */
private data class SubLines(val romanization: String?, val translation: String?) {
    val hasContent: Boolean get() = romanization != null || translation != null

    companion object {
        /** Blank strings read as absent, so the block collapses instead of holding a gap. */
        fun of(romanization: String?, translation: String?): SubLines = SubLines(
            romanization = romanization?.takeIf { it.isNotBlank() },
            translation = translation?.takeIf { it.isNotBlank() }
        )
    }
}
