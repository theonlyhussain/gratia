package com.gratia.music.ui.lyrics

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gratia.music.lyrics.LyricLine

/**
 * An Apple Music–style lyric line: one line of text drawn three times over, so
 * that the words light up as they are sung instead of switching on in whole
 * words.
 *
 * The three copies, bottom to top:
 *
 * 1. **The bloom**, a copy masked down to the word being held and then blurred.
 *    Masking *before* the blur is what makes the halo bleed out past the
 *    letters it belongs to, which is the part that reads as light coming off a
 *    carried note rather than a drop shadow under the line. It sits at the
 *    bottom so the light is only ever what escapes around the letters: drawn on
 *    top it would haze the word being sung instead of glowing off it.
 * 2. **The unsung line**, held at [dimAlpha]. This is the whole of the line
 *    before the singing reaches it, and the tail of it afterwards.
 * 3. **The lit line**, clipped to the character the voice has reached, and so
 *    the copy that is actually read. All three lay out identically, so the
 *    bright one lands exactly on top of the dim one and the boundary is a wipe
 *    rather than a step. Colouring an `AnnotatedString` word by word can only
 *    change a whole word at a time, which turns the sweep into a flicker.
 *
 * The word being sung also lifts a hair ([WORD_RISE]) and eases back down, on
 * the same envelope as its bloom — see [glowIntensity][LyricLine.glowIntensity].
 *
 * Everything is recomputed in the draw phase from [clock], so a frame costs a
 * clip and a redraw of already-measured text rather than a recomposition.
 */
@Composable
fun SweptLyricsLine(
    line: LyricLine,
    clock: State<Long>,
    style: TextStyle,
    modifier: Modifier = Modifier,
    dimAlpha: Float = UNSUNG_ALPHA,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    glowAlpha: Float = GLOW_ALPHA,
    glowRadius: Dp = GLOW_RADIUS,
    rise: Boolean = true,
    textAlign: TextAlign = TextAlign.Start
) {
    var layout by remember(line) { mutableStateOf<TextLayoutResult?>(null) }

    val density = LocalDensity.current
    val risePx = with(density) { WORD_RISE.toPx() }
    val featherPx = with(density) { WIPE_FEATHER.toPx() }

    // Both text copies carry this, and so does the bloom — the same arithmetic
    // in all three, which is what keeps them stacked on top of each other.
    val lift: Modifier = if (!rise || risePx <= 0f) {
        Modifier
    } else {
        Modifier.drawWithContent {
            val position = clock.value
            val span = line.heldWordSpan(position)
            val amount = line.glowIntensity(position) * risePx
            val measured = layout
            if (span == null || amount <= 0.05f || measured == null) {
                drawContent()
            } else {
                drawWithWordLift(measured, span, amount) { this@drawWithContent.drawContent() }
            }
        }
    }

    // Only the line actually being sung has a boundary to soften; everywhere
    // else the wipe is at one end of the text or the other and there is nothing
    // to draw.
    val sweep = Modifier.drawWithContent {
        val position = clock.value
        when {
            // Sung and done with. Checked first so the lines above and below the
            // playing one — which sit in this state for minutes at a time — cost
            // a comparison per frame rather than a walk of their words.
            position >= line.computedEndMs -> drawContent()
            // Not started: nothing lit, the dim copy is the whole of it.
            position <= line.timeMs -> Unit
            else -> layout?.let { sweepTo(it, line.revealedChars(position), featherPx) }
        }
    }

    Box(
        modifier = modifier,
        contentAlignment = if (textAlign == TextAlign.End) Alignment.TopEnd else Alignment.TopStart
    ) {
        // 1. The bloom, underneath both text copies. Left off entirely on
        //    devices that cannot blur, on any line whose words are never held
        //    long enough to earn one, and on a line carrying no word timings —
        //    nothing there is ever held, so the copy could only ever be an
        //    invisible blurred layer on every row of a line-synced page.
        if (CAN_BLUR && glowAlpha > 0.01f && line.isWordSynced) {
            Text(
                text = line.text,
                style = style,
                color = Color.White,
                maxLines = maxLines,
                overflow = overflow,
                textAlign = textAlign,
                modifier = lift
                    .graphicsLayer {
                        alpha = glowAlpha * line.glowIntensity(clock.value)
                    }
                    .blur(glowRadius, BlurredEdgeTreatment.Unbounded)
                    // Each letter is masked to its own brightness with DstIn,
                    // which needs a layer of its own to erase into — against the
                    // backdrop it would take the artwork with it.
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        // Deliberately not the shared sweep: that lights
                        // everything sung so far, and this lights only the word
                        // being held. Most lines draw nothing here at all, which
                        // is the whole difference between this and a halo
                        // travelling along under the highlight.
                        //
                        // Nothing is drawn unless that word could actually be
                        // placed in the line's own text. The word cannot be
                        // placed between words, before the line's first word,
                        // while the copy the sweep measures is still unmeasured
                        // (which is every line for a frame after a handover),
                        // or when a provider's word timing does not spell itself
                        // out in the text it belongs to. Drawing the copy whole
                        // in any of those cases is a blur laid over the entire
                        // line — which is what made the line being sung the
                        // blurriest thing on the page.
                        val measured = layout
                        val word = measured?.let { line.heldWordSpan(clock.value) }
                        if (measured != null && word != null) {
                            drawContent()
                            eraseOutsideWord(measured, word)
                        }
                    }
            )
        }

        // 2. The unsung line. Its layout is the one the other two are measured
        //    against, so it is always the copy that reports it.
        Text(
            text = line.text,
            style = style,
            color = Color.White.copy(alpha = dimAlpha),
            maxLines = maxLines,
            overflow = overflow,
            textAlign = textAlign,
            onTextLayout = { layout = it },
            modifier = lift
        )

        // 3. The lit line, revealed up to wherever the voice has got to. Last,
        //    so the sung words are drawn from the sharp copy rather than from
        //    underneath a blurred one.
        Text(
            text = line.text,
            style = style,
            color = Color.White,
            maxLines = maxLines,
            overflow = overflow,
            textAlign = textAlign,
            modifier = lift.then(sweep)
        )
    }
}

/**
 * The character range of the word being sung at [positionMs], or `null` between
 * words.
 *
 * A word is only ever looked for by walking the line's own text, because a word
 * repeated in a line still has to line up with the copy the sweep is measuring.
 */
private fun LyricLine.heldWordSpan(positionMs: Long): IntRange? {
    val word = words.firstOrNull { positionMs < it.endMs } ?: return null
    if (positionMs < word.startMs) return null
    var offset = 0
    words.forEach { candidate ->
        val start = text.indexOf(candidate.text, offset)
        if (start < 0) return@forEach
        if (candidate === word) {
            val end = (start + candidate.text.length).coerceAtMost(text.length)
            return start..(end - 1)
        }
        offset = start + candidate.text.length
    }
    return null
}

/**
 * Draws [content] with the characters in [span] lifted by [amount] and the rest
 * left where they are.
 *
 * Done a visual line at a time, and within each line as at most three runs —
 * what comes before the held word, the held word itself, and what comes after
 * it — because the lift has to move the letters of one word without moving the
 * line they sit in. The band the lift happens inside keeps its own top and
 * bottom, so a lifted word cannot draw over the line above.
 */
private fun ContentDrawScope.drawWithWordLift(
    layout: TextLayoutResult,
    span: IntRange,
    amount: Float,
    content: () -> Unit
) {
    for (visualLine in 0 until layout.lineCount) {
        val start = layout.getLineStart(visualLine)
        val end = layout.getLineEnd(visualLine, visibleEnd = true)
        if (end <= start) continue
        val top = layout.getLineTop(visualLine)
        val bottom = layout.getLineBottom(visualLine)
        val left = layout.getLineLeft(visualLine)
        val right = layout.getLineRight(visualLine)

        // Nothing of the held word is on this visual line: it draws whole.
        if (span.last < start || span.first >= end) {
            clipRect(left, top, right, bottom) { content() }
            continue
        }

        val heldStart = maxOf(span.first, start)
        val heldEnd = minOf(span.last, end - 1)
        val heldLeft = layout.getHorizontalPosition(heldStart, true)
        val heldRight = layout.getHorizontalPosition(heldEnd, false)

        if (heldLeft > left) clipRect(left, top, heldLeft, bottom) { content() }
        if (heldRight < right) clipRect(heldRight, top, right, bottom) { content() }
        clipRect(heldLeft, top, heldRight, bottom) {
            translate(top = -amount) { content() }
        }
    }
}

/**
 * Erases everything the bloom has drawn except the letters of [span], so the
 * halo belongs to the word being held rather than to the sweep's leading edge.
 *
 * Done by erasing the word's *surroundings*, not by painting the word. A blend
 * mode only applies where the drawing itself lands, so painting the word's boxes
 * with `DstIn` keeps the word — and leaves everything around it untouched, which
 * is the whole line still sitting in the layer. Since this runs inside the
 * layer the blur is applied to, the bloom that came out was the entire line
 * hazed, not one word lit: the line being sung was the blurriest thing on the
 * page. Turning it around — a rect either side of the word and the bands above
 * and below its own line, cleared out — leaves the word alone in the layer, and
 * the blur then bleeds off its letters the way it is meant to.
 *
 * Rectangles rather than one box around the word, so a short word does not drag
 * a slab of light along with it, and so a word that wraps keeps both halves.
 */
private fun ContentDrawScope.eraseOutsideWord(layout: TextLayoutResult, span: IntRange) {
    val erase = { left: Float, top: Float, right: Float, bottom: Float ->
        if (right > left && bottom > top) {
            clipRect(left, top, right, bottom) {
                // Opaque, so `DstOut` takes the layer to nothing wherever this
                // lands: the source is discarded and only what it covers goes.
                drawRect(color = Color.White, blendMode = BlendMode.DstOut)
            }
        }
    }

    var keptTop = Float.MAX_VALUE
    var keptBottom = 0f
    for (visualLine in 0 until layout.lineCount) {
        val start = layout.getLineStart(visualLine)
        val end = layout.getLineEnd(visualLine, visibleEnd = true)
        if (end <= start) continue
        val top = layout.getLineTop(visualLine)
        val bottom = layout.getLineBottom(visualLine)
        val left = layout.getLineLeft(visualLine)
        val right = layout.getLineRight(visualLine)

        val heldStart = maxOf(span.first, start)
        val heldEnd = minOf(span.last, end - 1)
        // None of the held word is on this line: all of it goes.
        if (heldStart > heldEnd) {
            erase(left, top, right, bottom)
            continue
        }

        val heldLeft = layout.getHorizontalPosition(heldStart, true)
        val heldRight = layout.getHorizontalPosition(heldEnd, false)
        erase(left, top, heldLeft, bottom)
        erase(heldRight, top, right, bottom)
        keptTop = minOf(keptTop, top)
        keptBottom = maxOf(keptBottom, bottom)
    }

    // Nothing kept — the word could not be placed in the line at all — so there
    // is nothing for a bloom to come off, and the layer goes rather than
    // brightening the whole line.
    if (keptTop > keptBottom) {
        erase(0f, 0f, size.width, size.height)
        return
    }
    erase(0f, 0f, size.width, keptTop)
    erase(0f, keptBottom, size.width, size.height)
}

/**
 * Reveals [revealedChars] worth of a laid-out line.
 *
 * Handled a visual line at a time: the ones already passed are drawn whole, the
 * one holding the boundary is cut at it, and the rest are left to the dim copy.
 * Within a word the cut sits between two character positions, so the edge
 * advances smoothly instead of jumping a letter at a time.
 *
 * The boundary is then feathered over [featherPx] rather than left as the cut —
 * which needs the caller to have given this an offscreen layer to erase into.
 */
private fun ContentDrawScope.sweepTo(
    layout: TextLayoutResult,
    revealedChars: Float,
    featherPx: Float
) {
    val chars = layout.layoutInput.text.length
    if (revealedChars <= 0f) return
    if (revealedChars >= chars) {
        drawContent()
        return
    }

    for (visualLine in 0 until layout.lineCount) {
        val start = layout.getLineStart(visualLine)
        // Lines beyond the boundary have nothing lit on them, and neither has
        // anything after them.
        if (revealedChars <= start) return
        val end = layout.getLineEnd(visualLine, visibleEnd = true)
        val cut = revealedChars < end
        val right = if (cut) {
            horizontalAt(layout, revealedChars, chars)
        } else {
            layout.getLineRight(visualLine)
        }
        val top = layout.getLineTop(visualLine)
        val bottom = layout.getLineBottom(visualLine)
        val left = layout.getLineLeft(visualLine)

        clipRect(left, top, right, bottom) { this@sweepTo.drawContent() }

        // Only the visual line holding the boundary has an edge to soften; a
        // line revealed to its end runs into the wrap, which is not an edge.
        if (!cut || featherPx <= 0f) continue
        // Scoped to this line's band so the mask cannot reach the lines above
        // and below it: DstIn erases whatever the source does not cover, and
        // outside the clip there is no source at all, so they are left alone.
        clipRect(top = top, bottom = bottom) {
            drawRect(
                brush = Brush.horizontalGradient(
                    0f to Color.White,
                    1f to Color.Transparent,
                    startX = (right - featherPx).coerceAtLeast(left),
                    endX = right
                ),
                blendMode = BlendMode.DstIn
            )
        }
    }
}

/** The x the boundary sits at, interpolated between two character positions. */
private fun horizontalAt(layout: TextLayoutResult, index: Float, chars: Int): Float {
    val whole = index.toInt()
    val fraction = (index - whole).coerceIn(0f, 1f)
    val safe = whole.coerceIn(0, (chars - 1).coerceAtLeast(0))
    val box = layout.getBoundingBox(safe)
    return box.left + box.width * fraction
}
