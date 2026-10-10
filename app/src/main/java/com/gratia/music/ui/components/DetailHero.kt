package com.gratia.music.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp as colorLerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import coil.compose.AsyncImage
import com.gratia.music.ui.theme.ArtworkPalette
import com.gratia.music.ui.theme.Inter
import com.gratia.music.ui.theme.SpaceGrotesk

/**
 * The artist / album / playlist detail-page hero — one implementation, three
 * pages that agree (spec 68–70).
 *
 * These three pages are the same object at different scales: a sleeve, a title,
 * and the page dissolving out of the sleeve's own colours. The hero used to be
 * rebuilt per page, which is exactly how they drifted — the playlist page ended
 * up a flat gradient with a hard-edged square of artwork on it. These
 * composables are the season's single answer:
 *
 *  - [DetailPageBackground] paints the adaptive wash and its two mesh lobes,
 *    derived from the artwork by [com.gratia.music.ui.theme.rememberArtworkPalette].
 *  - [DetailHeroArtwork] draws the full-bleed artwork and dissolves its bottom
 *    edge into that wash, so there is never a hard rectangular image edge.
 *  - [DetailHero] drops the two together with the title set across the foot of
 *    the picture, which is what the artist page already did.
 *
 * The dissolve stops are identical to the artist page's, deliberately: `0.44 →
 * 0.58 → 0.70 → 0.80 → 0.89 → 0.96 → 1.00`, an eased curve rather than a linear
 * ramp, because a linear two-stop fade is visible *as* a fade — the eye reads the
 * constant rate as a straight edge laid over the picture. The last stop is an
 * opaque [ArtworkPalette.wash] so the picture and the page meet on one colour
 * with no seam left to soften.
 */

/** How far down the page the wash holds before it settles into the background. */
private const val WASH_FRACTION = 0.62f

/**
 * The adaptive page ground: the artwork's own bottom-edge colour, held under the
 * hero and eased into the page's background further down.
 *
 * A layer of its own so the fade has the join to itself — draw it
 * `matchParentSize()` behind a transparent scrolling list.
 */
@Composable
fun DetailPageBackground(
    palette: ArtworkPalette,
    modifier: Modifier = Modifier,
    /** Where the wash begins its long settle into [ArtworkPalette.background]. */
    washFraction: Float = WASH_FRACTION,
) {
    Canvas(modifier) {
        drawRect(
            Brush.verticalGradient(
                0f to palette.wash,
                washFraction to palette.wash,
                lerp(washFraction, 1f, 0.35f) to colorLerp(palette.wash, palette.background, 0.12f),
                lerp(washFraction, 1f, 0.70f) to colorLerp(palette.wash, palette.background, 0.55f),
                1f to palette.background,
            ),
        )
        detailMeshBlob(palette.accent.copy(alpha = 0.13f), Offset(0.12f, washFraction + 0.08f), 0.80f)
        detailMeshBlob(palette.elevated.copy(alpha = 0.30f), Offset(0.96f, washFraction + 0.30f), 0.95f)
    }
}

/**
 * The full-bleed artwork with its bottom edge dissolved into the page.
 *
 * Sized and positioned by the caller's [modifier] (the artist page passes a
 * scroll-linked offset; the playlist and album pages a fixed height). The
 * picture is left sharp and opaque through its top half and gives way
 * progressively below, which is the difference between "a picture fading into a
 * page" and "a picture that failed to load".
 */
@Composable
fun DetailHeroArtwork(
    artworkUrl: String?,
    contentDescription: String?,
    palette: ArtworkPalette,
    modifier: Modifier = Modifier,
    artPx: Int = LARGE_ARTWORK_PX,
) {
    val request = rememberArtworkRequest(artworkUrl, artPx, crossfadeMs = 400)

    Box(modifier.clipToBounds()) {
        if (request != null) {
            AsyncImage(
                model = request,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        } else {
            // No sleeve yet: the page's own elevated colour, never a simulated
            // cover. The dissolve below still runs, so the surface reads as the
            // page rather than as a missing image (spec 45/46).
            Box(Modifier.matchParentSize().background(palette.elevated))
        }

        // A short, faint scrim under the back arrow, in the page's own tint
        // rather than black so the themed arrow keeps contrast in light mode.
        // Deliberately shallow: it must not become a dark band across the top
        // of a picture that is meant to be clean there.
        Box(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.20f)
                .background(
                    Brush.verticalGradient(
                        listOf(palette.background.copy(alpha = 0.42f), Color.Transparent),
                    ),
                ),
        )

        // The dissolve. See the class doc for why these stops and not a linear
        // ramp; the last stop is opaque wash so the join is invisible.
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0.00f to Color.Transparent,
                        0.44f to Color.Transparent,
                        0.58f to palette.wash.copy(alpha = 0.08f),
                        0.70f to palette.wash.copy(alpha = 0.28f),
                        0.80f to palette.wash.copy(alpha = 0.55f),
                        0.89f to palette.wash.copy(alpha = 0.80f),
                        0.96f to palette.wash.copy(alpha = 0.95f),
                        1.00f to palette.wash,
                    ),
                ),
        )
    }
}

/**
 * Artwork + dissolve + the title set across the foot of the picture: everything
 * a detail page's opening item needs, in the artist page's proportions.
 *
 * The title lands in the dissolve's opaque region, so it is legible without a
 * plate behind it on any sleeve. [subtitle] (a creator or artist credit) and
 * [meta] (a track count, a year) are optional and carry the same hierarchy every
 * detail page uses.
 */
@Composable
fun DetailHero(
    artworkUrl: String?,
    palette: ArtworkPalette,
    title: String,
    height: Dp,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    meta: String? = null,
    titleSize: TextUnit = 34.sp,
    onSubtitleClick: (() -> Unit)? = null,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(height),
    ) {
        DetailHeroArtwork(
            artworkUrl = artworkUrl,
            contentDescription = title,
            palette = palette,
            modifier = Modifier.matchParentSize(),
        )

        // The dissolve already ends in [ArtworkPalette.wash], but a long title
        // or a short hero can push the text up into the picture's mid-tones.
        // This continues the fade up to meet the title, so it is always set on
        // the page's own colour rather than on whatever the sleeve happens to be
        // there. It reinforces the shared fade rather than replacing it.
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0.00f to Color.Transparent,
                        0.55f to Color.Transparent,
                        1.00f to palette.wash,
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom,
        ) {
            Text(
                text = title,
                fontFamily = SpaceGrotesk,
                fontWeight = FontWeight.Bold,
                fontSize = titleSize,
                lineHeight = titleSize * 1.12f,
                letterSpacing = (-0.5).sp,
                color = palette.onBackground,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = subtitle,
                    fontFamily = Inter,
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp,
                    color = palette.accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = if (onSubtitleClick != null) {
                        Modifier.clickable(onClick = onSubtitleClick)
                    } else {
                        Modifier
                    },
                )
            }
            if (!meta.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = meta,
                    fontFamily = Inter,
                    fontSize = 13.sp,
                    color = palette.onBackgroundVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * The detail pages' Play button: a pill in the page's own elevated glass, with
 * the accent on its glyph. Same shape, border and type as the artist page's, so
 * a Play on a playlist looks like a Play on an artist (spec 65/69).
 */
@Composable
fun DetailPlayPill(
    palette: ArtworkPalette,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(50.dp)
            .clip(CircleShape)
            .background(palette.elevated.copy(alpha = 0.75f))
            .border(0.5.dp, palette.divider, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 32.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.PlayArrow,
            contentDescription = null,
            tint = palette.accent,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "Play",
            fontFamily = SpaceGrotesk,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            color = palette.onBackground,
        )
    }
}

/** The detail pages' round glass button — Shuffle, Save, and their kin. */
@Composable
fun DetailCircleButton(
    icon: ImageVector,
    contentDescription: String,
    palette: ArtworkPalette,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 50.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(palette.elevated.copy(alpha = 0.75f))
            .border(0.5.dp, palette.divider, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = palette.onBackground,
            modifier = Modifier.size(size * 0.44f),
        )
    }
}

/** One mesh lobe: a colour at its centre, gone by [radiusFraction] of the width. */
internal fun DrawScope.detailMeshBlob(color: Color, at: Offset, radiusFraction: Float) {
    val center = Offset(at.x * size.width, at.y * size.height)
    val radius = size.width * radiusFraction
    if (radius <= 0f) return
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color, color.copy(alpha = 0f)),
            center = center,
            radius = radius,
        ),
        radius = radius,
        center = center,
    )
}
