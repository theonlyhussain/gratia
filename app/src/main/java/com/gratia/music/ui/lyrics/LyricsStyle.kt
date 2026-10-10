package com.gratia.music.ui.lyrics

import android.os.Build
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// =============================================================================
// LYRICS TYPOGRAPHY
// =============================================================================
//
// The lyrics page is set larger than the rest of the app, and larger again when
// the timings are known. A synced line is read at a glance while it is being
// sung, so it wants the extra size; an unsynced page is read the way a book is,
// so it takes a step back — the same split Apple Music makes between the two.

/** Synced lines. Big enough to carry the sweep without the words going loose. */
internal const val LYRIC_FONT_SIZE = 34f
internal const val LYRIC_LINE_HEIGHT = 41f

/** Plain lines: same face, a step down, tighter leading. Unreached today. */
internal const val LYRIC_UNSYNCED_FONT_SIZE = 30f
internal const val LYRIC_UNSYNCED_LINE_HEIGHT = 38f

/** The answering vocal: smaller than the lead, and a shade behind it. */
internal const val BACKING_FONT_SIZE = 23f
internal const val BACKING_LINE_HEIGHT = 29f
internal const val BACKING_ALPHA = 0.72f

/** The weight lyrics are drawn at, before the system's own bold adjustment. */
private const val LYRIC_BASE_WEIGHT = 800

/**
 * The weight to draw lyrics at, with the system's font weight adjustment folded
 * in.
 *
 * Android's accessibility settings can ask for every string on the device to be
 * drawn heavier. Text drawn through the theme picks that up on its own, but a
 * lyric line builds its own [TextStyle] — so without reading the adjustment here
 * the one surface in the app people actually read along with would be the only
 * one that ignored it. The base keeps lyrics at ExtraBold; the adjustment is
 * added on top and clamped, so "bolder" never lands somewhere the system font
 * has no weight to draw.
 */
@Composable
internal fun rememberLyricFontWeight(): FontWeight {
    val context = LocalContext.current
    val adjustment = remember {
        runCatching {
            android.provider.Settings.Secure.getInt(
                context.contentResolver,
                "font_weight_adjustment",
                0
            )
        }.getOrDefault(0)
    }
    if (adjustment == 0) return FontWeight(LYRIC_BASE_WEIGHT)
    return FontWeight((LYRIC_BASE_WEIGHT + adjustment).coerceIn(1, 1000))
}

/**
 * The style a lyric line is drawn in.
 *
 * [synced] is whether the page's lines carry timings, and reach for the larger
 * scale because of it. Line-level timings count: a line-synced page is followed
 * while it is sung, not read the way a book is. Only a plain page takes the
 * step down, and a plain document does not reach this view at all — every line
 * that arrives here is timed.
 *
 * [fontScale] is the user's own lyrics size setting; the rest of the scale is
 * fixed so that every line of a song measures the same way and the sweep's
 * character boxes stay in step with the text being clipped.
 */
@Composable
internal fun lyricTextStyle(
    synced: Boolean,
    fontScale: Float = 1f,
    textAlign: TextAlign = TextAlign.Start
): TextStyle {
    val weight = rememberLyricFontWeight()
    val size = if (synced) LYRIC_FONT_SIZE else LYRIC_UNSYNCED_FONT_SIZE
    val leading = if (synced) LYRIC_LINE_HEIGHT else LYRIC_UNSYNCED_LINE_HEIGHT
    return remember(synced, fontScale, textAlign, weight) {
        TextStyle(
            fontSize = (size * fontScale).sp,
            lineHeight = (leading * fontScale).sp,
            fontWeight = weight,
            textAlign = textAlign
        )
    }
}

// =============================================================================
// LYRICS MOTION
// =============================================================================

/**
 * How the lyrics page settles: away quickly, back in slowly.
 *
 * One curve for the whole handover — the dim, the blur, the scale and the
 * scroll — so a line handing over reads as one movement rather than four that
 * merely happen to start together.
 */
internal val LYRIC_EASING: Easing = CubicBezierEasing(0.41f, 0f, 0.12f, 0.99f)

/** How long the page takes to settle on a new line. */
internal const val LYRIC_SETTLE_MS = 400

/**
 * Where the unsung part of a line that *is* being sung is held, as a share of
 * full brightness.
 *
 * The words ahead of the sweep. They are dimmer than the words just sung, which
 * is what gives the highlight something to arrive from, but they are still the
 * line being read along with — so they stay at the bottom of the readable range
 * rather than fading out of it.
 */
internal const val UNSUNG_ALPHA = 0.6f

/** The one-line strip is read at a glance, so it gets slightly more. */
internal const val UNSUNG_ALPHA_STRIP = 0.55f

/** What the rest of the page drops to while the list is being scrolled by hand. */
internal const val BROWSING_ALPHA = 0.7f

/** The playing line sits at 1; the rest step back from it. */
internal const val INACTIVE_SCALE = 0.96f

/** A line under a finger dips, the way a button does. */
internal const val PRESSED_SCALE = 0.94f

/**
 * How bright the page falls away either side of the line being sung, indexed by
 * distance from it.
 *
 * Split by which side of the singing a row is on, because the two sides are
 * read differently and have to be told apart on sight.
 *
 * **Sung rows** have been read. They stay legible as the page moves on — the
 * line above is where an eye that lost its place goes back to, and dropping it
 * into the blur made the page look like it was deleting itself as it went.
 *
 * **Upcoming rows** have not been read, and are held a step further back, so
 * that on a song whose lines change one at a time the line being sung is
 * unmistakable.
 *
 * Both ladders are floors, not fades to nothing. These rows are on screen: they
 * *are* the page, and a reader looking ahead or back is reading them. Held at
 * what the far rungs used to be — 0.16 to 0.26 of white over dark artwork, with
 * a blur on top of it, which is most of a verse at once — the surrounding lines
 * read as missing rather than as de-emphasised, which is the opposite of what a
 * lyrics page is for. The near rows sit in the 0.6–0.75 the emphasis should
 * leave them at, and the falloff from there is a step or two of brightness
 * rather than a disappearance.
 *
 * The last entry stands for everything further out, which is most of the list.
 */
internal val SUNG_ALPHA = floatArrayOf(1f, 0.74f, 0.62f, 0.5f, 0.4f)
internal val UPCOMING_ALPHA = floatArrayOf(1f, 0.62f, 0.5f, 0.4f, 0.32f)

/**
 * The brightness a row is held at, given which side of the singing it is on and
 * how far from it. The active row is always the brightest thing on the page.
 */
internal fun lyricRowAlpha(side: LyricFocusSide, distance: Int): Float = when (side) {
    LyricFocusSide.Active -> 1f
    LyricFocusSide.Sung -> SUNG_ALPHA[distance.coerceIn(0, SUNG_ALPHA.lastIndex)]
    LyricFocusSide.Upcoming -> UPCOMING_ALPHA[distance.coerceIn(0, UPCOMING_ALPHA.lastIndex)]
}

/**
 * The blur half of the same falloff, on distance alone.
 *
 * Nothing for the line being sung or the two rows around it: those are the rows
 * people actually read along with, and softening them costs an offscreen layer
 * per row to make the words being read harder. It only starts once the stack is
 * genuinely falling away, and it stays light — a hard blur is not a fade, it is
 * an erasure, and it is what made a line that had just been sung look gone. At
 * the far end it is under a pixel and a half: enough to sit behind the reading
 * line, never enough to eat a letter of a row that is still on screen.
 */
internal val LINE_FALLOFF_BLUR = arrayOf(0.dp, 0.dp, 0.dp, 1.dp, 1.4.dp)

/**
 * How long the line being sung takes to arrive, against [LYRIC_SETTLE_MS] for
 * everything letting go.
 *
 * Asymmetric on purpose. Two rows crossfading over the same span are at the same
 * brightness in the middle of the handover, and a page where four rows are all
 * sitting at about 0.7 is a page with no line being pointed at. The line taking
 * over arrives almost at once; whatever it displaced eases away behind it.
 */
internal const val LYRIC_ACTIVATE_MS = 160

/** Whether this device can blur at all — RenderEffect landed in API 31. */
internal val CAN_BLUR: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

// =============================================================================
// THE SWEEP
// =============================================================================

/**
 * The bloom behind the word being sung, at its very strongest.
 *
 * Kept well under half strength: the halo is drawn from the same white as the
 * text, so at full alpha it stops reading as light and starts reading as a
 * second, badly printed copy of the words. It is also masked to the word being
 * held — see [SweptLyricsLine] — so it can only ever be that word's own light;
 * at anything more than this it reads as the word itself going soft.
 */
internal const val GLOW_ALPHA = 0.5f

/**
 * How far the bloom spreads off a letter. Tight, because it belongs to a word's
 * worth of light: a wide radius on something this small is a smudge behind the
 * text instead of a glow coming off it, and the word being sung has to stay the
 * crispest thing on the page.
 */
internal val GLOW_RADIUS = 5.dp

/** How far the word being sung lifts off its line. About two pixels. */
internal val WORD_RISE = 2.dp

/**
 * How far the sweep's leading edge fades instead of ending on a cut.
 *
 * A hard boundary is legible as a boundary: the eye reads a bar travelling
 * across the words rather than the words lighting up as they are sung.
 * Feathering it over roughly a character and a half turns the cut back into a
 * wavefront.
 */
internal val WIPE_FEATHER = 30.dp

// =============================================================================
// INSTRUMENTAL BREAKS
// =============================================================================

/**
 * How tall a break stands while it is playing, and the gap it opens up with.
 *
 * Nothing when it is not: an interlude that held its row open through the verse
 * either side of it left a hole in the list, and the page scrolled past empty
 * space to reach the next thing sung. It opens as the singing stops and closes
 * again as it comes back, so the list only carries a break while there is one.
 */
internal val GAP_ROW_HEIGHT = 40.dp
internal val GAP_ROW_SPACING = 16.dp

/**
 * The break counted out rather than marked: dots that light left to right
 * across the interlude, so a long one reads as time running down instead of a
 * symbol parked on screen waiting for the singing to come back.
 *
 * [GAP_DOT_REST] is what an unlit dot still shows — enough to say how many are
 * coming, not enough to be read as already counted.
 */
internal const val GAP_DOTS = 3
internal val GAP_DOT_SIZE = 13.dp
internal val GAP_DOT_GAP = 5.dp
internal const val GAP_DOT_REST = 0.25f
internal const val GAP_REST_SCALE = 0.76f

/** Used when a break has no next line to count down to. */
internal const val GAP_FALLBACK_MS = 4_000L
