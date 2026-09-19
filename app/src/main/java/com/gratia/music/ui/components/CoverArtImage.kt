package com.gratia.music.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize

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
    // The row's own size, in pixels, is the decode size — the request used to
    // pin nothing and left Coil to size the decode off whatever the layout
    // reported at load time.
    val sizePx = with(LocalDensity.current) { size.roundToPx() }
    val artRequest = rememberArtworkRequest(coverArtPath, sizePx)

    if (artRequest != null) {
        SubcomposeAsyncImage(
            model = artRequest,
            contentDescription = "$title cover art",
            contentScale = ContentScale.Crop,
            loading = {
                Box(modifier = Modifier.fillMaxSize().background(shimmerBrush()))
            },
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
