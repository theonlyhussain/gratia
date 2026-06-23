package com.gratia.music.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import java.io.File

/**
 * Reusable cover art image composable.
 * Shows the actual cover image from coverArtPath via Coil, or falls back
 * to CoverArtFallback gradient with initials.
 */
@Composable
fun CoverArtImage(
    coverArtPath: String?,
    title: String,
    artist: String = "",
    size: Dp = 48.dp,
    cornerRadius: Dp = 8.dp,
    fontSize: TextUnit = 16.sp,
    modifier: Modifier = Modifier
) {
    if (!coverArtPath.isNullOrBlank() && File(coverArtPath).exists()) {
        AsyncImage(
            model = File(coverArtPath),
            contentDescription = "$title cover art",
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(cornerRadius))
        )
    } else {
        CoverArtFallback(
            title = title,
            artist = artist,
            size = size,
            cornerRadius = cornerRadius,
            fontSize = fontSize,
            modifier = modifier
        )
    }
}
