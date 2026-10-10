package com.gratia.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gratia.music.ui.theme.GratiaTheme
import com.gratia.music.ui.theme.SpaceGrotesk

/**
 * The loading state for a remote album or playlist page.
 *
 * A center spinner was the old answer, and it threw away the one thing the
 * page already knows: the title the listener tapped on. Navigation args carry
 * at least the id; for albums and playlists opened from a shelf or search the
 * display title is usually known too — so [knownTitle] is rendered in the
 * real header style immediately, and only the genuinely unknown parts
 * (artwork, metadata, track rows) are skeletoned.
 *
 * Geometry matches the finished page — a centered square sleeve area, the
 * title line beneath it, a metadata strip, two action buttons, then track
 * rows — so the page does not jump when the data lands (spec 25/26/27).
 */
@Composable
fun DetailPageSkeleton(
    knownTitle: String? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(GratiaTheme.colors.background)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))

        // Sleeve area: the geometry of the finished header image, shimmering.
        Box(
            modifier = Modifier
                .size(200.dp)
                .clip(RoundedCornerShape(16.dp))
                .shimmer()
        )

        Spacer(Modifier.height(20.dp))

        // Known title renders as real text; unknown stays a skeleton line of
        // the same height so the layout is identical either way.
        if (knownTitle.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.65f)
                    .height(30.sp.value.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .shimmer()
            )
        } else {
            androidx.compose.material3.Text(
                text = knownTitle,
                fontFamily = SpaceGrotesk,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                color = GratiaTheme.colors.textPrimary,
            )
        }

        Spacer(Modifier.height(10.dp))

        // Metadata line (artist · year · track count)
        Box(
            modifier = Modifier
                .fillMaxWidth(0.45f)
                .height(14.dp)
                .clip(RoundedCornerShape(4.dp))
                .shimmer()
        )

        Spacer(Modifier.height(24.dp))

        // Action row: two button-shaped blocks
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier
                    .size(width = 130.dp, height = 44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .shimmer()
            )
            Box(
                modifier = Modifier
                    .size(width = 130.dp, height = 44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .shimmer()
            )
        }

        Spacer(Modifier.height(32.dp))

        // Track rows
        repeat(6) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .shimmer()
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.62f)
                            .height(14.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .shimmer()
                    )
                    Spacer(Modifier.height(7.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.38f)
                            .height(12.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .shimmer()
                    )
                }
            }
        }
    }
}
