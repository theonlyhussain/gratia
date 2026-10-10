package com.gratia.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.blur
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import com.gratia.music.data.model.ArtistListenSummary
import com.gratia.music.data.model.SongEntity
import com.gratia.music.ui.theme.GratiaTheme
import com.gratia.music.ui.theme.Inter
import com.gratia.music.ui.theme.SpaceGrotesk

/** "1h 30m", "8m", "2h 5m" — human time, not raw minutes (spec 103). */
fun formatListeningDuration(totalSeconds: Long): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return when {
        hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
        hours > 0 -> "${hours}h"
        minutes > 0 -> "${minutes}m"
        totalSeconds > 0 -> "${totalSeconds}s"
        else -> "0m"
    }
}

/**
 * The Home Listening Stats card — a summary, a door, and honest.
 *
 * The background is the period's #1 song's artwork, softened under a scrim;
 * the values on it are the same aggregates the Replay screen shows, from the
 * same repository (spec 54–58, 99). With no listening history the card shows
 * an invitation instead of zeros pretending to be measurements (spec 56).
 * Palette work happens in Coil's pipeline off the main thread; Home never
 * waits on it (spec 59).
 */
@Composable
fun ListeningStatsCard(
    totalListeningSeconds: Long,
    topArtists: List<ArtistListenSummary>,
    topSong: SongEntity?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    // The period's #1 song's artwork — sampled for its palette, not drawn as a
    // literal background (spec 101). Coil's pipeline extracts the dominant
    // colours off the main thread and the gradient is built from those; the
    // card's story is the abstract colour of what was listened to most.
    val artRequest = rememberArtworkRequest(topSong?.artworkUrl ?: topSong?.coverArtPath, 128, crossfadeMs = 400)

    // Foreground readability (stats spec 13): with artwork behind it the card
    // keeps its white-on-scrim treatment, but the scrim is theme-aware — light
    // mode's backdrop is far brighter, so it needs a deeper wash for the same
    // contrast. Without artwork there is nothing to scrim: the card falls back
    // to plain theme colours on its surface, never white-on-white.
    val hasArtwork = artRequest != null
    val isDark = GratiaTheme.colors.isDark
    val onCardPrimary = if (hasArtwork) Color.White else GratiaTheme.colors.textPrimary
    val onCardSecondary = if (hasArtwork) Color.White.copy(alpha = 0.72f) else GratiaTheme.colors.textSecondary

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(148.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(GratiaTheme.colors.surface)
            .bounceClick(onClick)
    ) {
        // Palette layer: the #1 artwork, softened into a colour field — the
        // cover itself stays recognizable only as colour, never as a picture
        // pretending to be a background.
        if (artRequest != null) {
            AsyncImage(
                model = artRequest,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = 0.45f }
                    .blur(24.dp)
            )
            // Scrim keeps text legible over any artwork.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = if (isDark) 0.30f else 0.52f),
                                Color.Black.copy(alpha = if (isDark) 0.55f else 0.78f),
                            )
                        )
                    )
            )
        } else {
            // No artwork to soften: a faint accent wash gives the card its
            // atmosphere without ever putting light text on a light ground.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                GratiaTheme.colors.accent.copy(alpha = 0.05f),
                                GratiaTheme.colors.accent.copy(alpha = 0.12f),
                            )
                        )
                    )
            )
        }

        Column(Modifier.fillMaxSize().padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        imageVector = Icons.Default.Insights,
                        contentDescription = null,
                        tint = onCardSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        "Listening Stats",
                        fontFamily = SpaceGrotesk,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = onCardPrimary
                    )
                }
                Text(
                    "Today",
                    fontFamily = Inter,
                    fontSize = 12.sp,
                    color = onCardSecondary
                )
            }

            Spacer(Modifier.weight(1f))

            if (totalListeningSeconds == 0L && topArtists.isEmpty()) {
                // Zero data: an invitation, never "0m / Unknown" dressed up as
                // a measurement (spec 56/135/136).
                Text(
                    "Start listening to build your stats.",
                    fontFamily = Inter,
                    fontSize = 14.sp,
                    color = onCardSecondary
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column {
                        Text(
                            formatListeningDuration(totalListeningSeconds),
                            fontFamily = SpaceGrotesk,
                            fontWeight = FontWeight.Bold,
                            fontSize = 28.sp,
                            color = onCardPrimary
                        )
                        Text(
                            "listened today",
                            fontFamily = Inter,
                            fontSize = 12.sp,
                            color = onCardSecondary
                        )
                    }
                    if (topArtists.isNotEmpty()) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                "Top Artist",
                                fontFamily = Inter,
                                fontSize = 12.sp,
                                color = onCardSecondary
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                topArtists.first().artist,
                                fontFamily = Inter,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = onCardPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 160.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * An On Device shelf tile: the management-space card at the end of the Home
 * shelf — real counts, icon, no fake artwork (spec 52/53).
 */
@Composable
fun OnDeviceTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(140.dp)
            .height(140.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(GratiaTheme.colors.surface)
            .bounceClick(onClick)
            .padding(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(GratiaTheme.colors.accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = GratiaTheme.colors.accent,
                modifier = Modifier.size(26.dp)
            )
        }
        Spacer(Modifier.weight(1f))
        Text(
            label,
            fontFamily = SpaceGrotesk,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = GratiaTheme.colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            "${count} song${if (count == 1) "" else "s"}",
            fontFamily = Inter,
            fontSize = 12.sp,
            color = GratiaTheme.colors.textSecondary
        )
    }
}
