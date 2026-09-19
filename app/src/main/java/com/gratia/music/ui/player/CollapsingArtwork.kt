package com.gratia.music.ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import coil.compose.AsyncImage
import com.gratia.music.ui.components.CoverArtFallback
import com.gratia.music.ui.components.PLAYER_ARTWORK_PX
import com.gratia.music.ui.components.rememberArtworkRequest
import com.gratia.music.ui.theme.GratiaTheme

/**
 * The now-playing artwork, and the one animation that moves it.
 *
 * The player has two shapes: a full sleeve with the credits beneath it, and a
 * panel — the lyrics or the queue — over a small square with the credits beside
 * it. This draws the first, and drives it into the second.
 *
 * A single fraction [collapse] carries the whole thing, 0 at the sleeve and 1 at
 * the header. Every quantity that moves reads off it: the square's size, where
 * it sits, its corner radius, and how much of the credits belonging to the
 * header have arrived. There is deliberately no second animation for any of
 * them — one value, one easing, so the parts cannot drift out of step with each
 * other. What that costs is a `lerp` per property; what it buys is that the
 * artwork and its name visibly travel together, which is the entire effect.
 *
 * The target geometry is the compact header's own — a 48dp square against the
 * header's padding, title and artist stacked to its right at the same sizes
 * [CompactPlayerHeader] draws them. So at `collapse == 1` this is not merely
 * *near* the header it hands over to, it is the same picture, and the swap is
 * invisible. That is what makes the handover in [ExpandedPlayer] safe to do at
 * the ends of the travel rather than mid-flight.
 *
 * The sleeve's resting geometry is the one the plain expanded sleeve has always
 * had: the largest square that fits the box after a 32dp/16dp padding, centred,
 * at 16dp corners behind a 24dp shadow. Kept exactly, because at `collapse == 0`
 * this has to be that sleeve — the reverse handover is invisible for the same
 * reason the forward one is.
 */
@Composable
fun CollapsingArtwork(
    coverArtPath: String?,
    title: String,
    artist: String,
    isPlaying: Boolean,
    isDragging: Boolean,
    collapse: Float,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val artRequest = rememberArtworkRequest(coverArtPath, PLAYER_ARTWORK_PX)

    val headerPadH = GratiaTheme.spacing.mediumLarge
    val headerPadV = GratiaTheme.spacing.small
    // The header's own gap between the square and the credits, so the row lands
    // here exactly where it already sits next door.
    val creditsGap = GratiaTheme.spacing.mediumSmall

    // Above roughly the halfway mark the credits are on their way; before that
    // they are not this block's business at all, since the expanded player draws
    // them itself underneath the sleeve. Kept as a share of the travel rather
    // than a threshold on it, so the two never appear and disappear on the same
    // frame they cross.
    val creditsProgress = ((collapse - CREDITS_START) / (1f - CREDITS_START)).coerceIn(0f, 1f)

    // Signature Apple Music touch: the sleeve shrinks back while paused, and
    // while a finger is on the pager. Only ever a property of the *sleeve*, so
    // it is lerped away by the time the artwork is a header.
    val restingScale = when {
        isDragging -> 0.97f
        isPlaying -> 1.0f
        else -> 0.98f
    }
    val scale = restingScale + (1f - restingScale) * collapse

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            // The credits are positioned by offset rather than laid out, so at
            // the start of the travel they begin off to the right of the sleeve.
            // Clipped so that half-formed row cannot paint over the rest of the
            // screen as the artwork passes it.
            .clipToBounds()
    ) {
        val fullSize = minOf(
            maxWidth - HERO_PAD_H * 2,
            maxHeight - HERO_PAD_V * 2
        ).coerceAtLeast(HEADER_THUMB_SIZE)

        val artSize = lerp(fullSize, HEADER_THUMB_SIZE, collapse)
        val artLeft = lerp((maxWidth - fullSize) / 2, headerPadH, collapse)
        val artTop = lerp((maxHeight - fullSize) / 2, headerPadV, collapse)
        val corner = lerp(HERO_CORNER, HEADER_CORNER, collapse)

        Box(
            modifier = Modifier
                .offset(x = artLeft, y = artTop)
                .size(artSize)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .shadow(
                    elevation = (SHADOW_ELEVATION * (1f - collapse)).dp,
                    shape = RoundedCornerShape(corner),
                    spotColor = Color.Black.copy(alpha = 0.5f),
                    ambientColor = Color.Black.copy(alpha = 0.25f)
                )
                .clip(RoundedCornerShape(corner))
        ) {
            if (artRequest != null) {
                AsyncImage(
                    model = artRequest,
                    contentDescription = "$title cover art",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                CoverArtFallback(
                    title = title,
                    artist = artist,
                    size = artSize,
                    cornerRadius = 0.dp,
                    fontSize = GratiaTheme.typography.display.fontSize,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // The credits, arriving beside the square as it settles.
        //
        // Width is worked out from the square's own right edge rather than left
        // to fill and pushed across, because a row that starts beyond the box is
        // still measured against the box: the title would run off the screen and
        // only then be ellipsised, at the point it had already been clipped.
        //
        // Not composed at all until the fade has started, which matters because
        // the row is a marquee: measured at the width it would have while the
        // sleeve is still a sleeve — near zero, since the square is centred and
        // this sits to the right of it — it has nothing to lay out into, and
        // Compose would be measuring a title nobody is going to see.
        if (creditsProgress > 0f) {
            val creditsLeft = artLeft + artSize + creditsGap
            val creditsWidth = (maxWidth - creditsLeft - headerPadH).coerceAtLeast(0.dp)

            Row(
                modifier = Modifier
                    .offset(x = creditsLeft, y = artTop)
                    .width(creditsWidth)
                    .height(HEADER_THUMB_SIZE)
                    .graphicsLayer { alpha = creditsProgress },
                verticalAlignment = Alignment.CenterVertically
            ) {
                CompactHeaderCredits(
                    title = title,
                    artist = artist,
                    isFavorite = isFavorite,
                    onToggleFavorite = onToggleFavorite,
                    onMoreClick = onMoreClick
                )
            }
        }
    }
}

/** The sleeve's resting padding, and so also its resting geometry. */
private val HERO_PAD_H = 32.dp
private val HERO_PAD_V = 16.dp
private val HERO_CORNER = 16.dp
private const val SHADOW_ELEVATION = 24f

/** The compact header's square, matching [CompactPlayerHeader]'s own. */
private val HEADER_THUMB_SIZE = 48.dp
private val HEADER_CORNER = 10.dp

/**
 * How far into the collapse the credits beside the square begin to arrive.
 *
 * Late on purpose. Below this the sleeve still has the player's own credits
 * beneath it, and two copies of the same title on screen at once — one below the
 * artwork, one beside it — is worse than either alone.
 */
private const val CREDITS_START = 0.45f
