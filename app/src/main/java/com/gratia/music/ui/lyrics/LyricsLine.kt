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
import androidx.compose.foundation.layout.Box
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
 * Visual behaviour, all of it driven off where the line sits relative to the one
 * being sung ([focusSide]) and how far from it ([focusDistance]), rather than
 * from a binary active/not-active flag:
 *
 * - **The line being sung:** the brightest thing on the page, arriving almost
 *   at once (see [LYRIC_ACTIVATE_MS]), never blurred, sitting fractionally
 *   forward of the stack.
 * - **Lines already sung:** still legible, and only softly behind the playing
 *   one. They are where an eye that lost its place goes back to, so they are
 *   never dimmed out of reading range.
 * - **Lines not yet reached:** held well back, and the further out the further
 *   back. Nothing near the playing line shares its brightness, which is what
 *   makes the line being sung readable as *the* line being sung on a song whose
 *   lyrics change a line at a time.
 * - **While the list is being scrolled by hand** ([isBrowsing]): the rest of the
 *   page flattens to one brightness, because reading by hand is not following
 *   along — but the playing line keeps its own. Losing your place the moment you
 *   touch the page is the one thing a lyrics view must not do.
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
internal fun LyricsLine(
    line: LyricLine,
    isActiveLine: Boolean,
    nextLineStartMs: Long?,
    clock: State<Long>,
    onSeek: ((Long) -> Unit)? = null,
    focusDistance: Int = 0,
    focusSide: LyricFocusSide,
    isBrowsing: Boolean = false,
    animateWordFill: Boolean = true,
    reduceAnimation: Boolean = false,
    /** Whether lyrics away from the line being sung soften as they fall away. */
    blurUnfocused: Boolean = true,
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

    val step = focusDistance.coerceIn(0, LINE_FALLOFF_BLUR.lastIndex)

    // The line being sung is the brightest thing on the page, always — including
    // while the list is being read by hand, when the rest of the page flattens
    // but the pointer stays put.
    val alpha by animateFloatAsState(
        targetValue = when {
            isActiveLine -> 1f
            isBrowsing -> BROWSING_ALPHA
            else -> lyricRowAlpha(focusSide, focusDistance)
        },
        animationSpec = if (reduceAnimation) {
            snap()
        } else {
            // Arrive almost at once, let go slowly. See [LYRIC_ACTIVATE_MS].
            tween(
                durationMillis = if (isActiveLine || isBrowsing) {
                    LYRIC_ACTIVATE_MS
                } else {
                    LYRIC_SETTLE_MS
                },
                easing = LYRIC_EASING
            )
        },
        label = "LineAlpha"
    )

    // Blur follows the same ladder, but only where it can be drawn at all and
    // only for the rows genuinely out of focus: the playing line is never
    // blurred, and neither are the two rows around it or a page being read by
    // hand.
    val blurRadius by animateDpAsState(
        targetValue = when {
            !CAN_BLUR || reduceAnimation || isBrowsing || isActiveLine || !blurUnfocused -> 0.dp
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

    // The larger scale belongs to a page that is followed rather than read —
    // which is this one, whatever the provider stamped. Keying it off word-level
    // timings instead set every line-synced song a step down, and with it the
    // answering vocal measured against it.
    val style = lyricTextStyle(
        synced = true,
        fontScale = fontScale,
        textAlign = textAlign
    )

    // The lead line and its transient overlays share a box. The overlays are
    // drawn inside it rather than as rows, so they change nothing's height
    // (spec 19).
    Box(modifier = Modifier.fillMaxWidth()) {
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
        if (animateWordFill && line.isWordSynced) {
            SweptLyricsLine(
                line = line,
                clock = clock,
                style = style,
                // The unsung words of the line being *sung* are held back so the
                // sweep has somewhere to arrive from. A row that is not being
                // sung yet is not halfway through anything, so it draws whole
                // and lets the focus falloff be the only thing dimming it —
                // otherwise an upcoming line carried the sweep's own dimming
                // *and* the falloff's, and came out at a fraction of the
                // brightness of an equivalent line-synced row.
                dimAlpha = if (isActiveLine) UNSUNG_ALPHA else 1f,
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

        // The answering vocal, as a transient overlay rather than a second row.
        // It appears only inside its own timing window, highlights across its
        // own words, and gets out of the way (spec 2/8/22). Drawn inside the
        // lead line's box — over the padding at its foot — so it adds no height,
        // no list item and no scroll, and steals no gestures (spec 10/19/20).
        val backing = line.background
        if (backing != null && backing.text.isNotBlank()) {
            TransientLyricOverlay(
                line = backing,
                clock = clock,
                style = style,
                reduceAnimation = reduceAnimation,
                textAlign = textAlign,
                modifier = Modifier.matchParentSize(),
            )
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
