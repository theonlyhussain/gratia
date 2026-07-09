package com.gratia.music.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun CollageArtwork(
    paths: List<String?>,
    size: Dp = 48.dp,
    cornerRadius: Dp = 8.dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
    ) {
        val validPaths = paths.filterNotNull()

        if (validPaths.isEmpty()) {
            PlaylistFallback(size = size, cornerRadius = cornerRadius, modifier = Modifier.size(size))
        } else if (validPaths.size == 1) {
            CoverArtImage(
                coverArtPath = validPaths[0],
                title = "Playlist",
                size = size,
                cornerRadius = 0.dp,
                modifier = Modifier.size(size)
            )
        } else if (validPaths.size in 2..3) {
            val halfSize = size / 2
            Row {
                CoverArtImage(
                    coverArtPath = validPaths[0],
                    title = "Playlist",
                    size = size,
                    cornerRadius = 0.dp,
                    modifier = Modifier.size(width = halfSize, height = size)
                )
                CoverArtImage(
                    coverArtPath = validPaths[1],
                    title = "Playlist",
                    size = size,
                    cornerRadius = 0.dp,
                    modifier = Modifier.size(width = halfSize, height = size)
                )
            }
        } else {
            val halfSize = size / 2
            Column {
                Row {
                    CoverArtImage(
                        coverArtPath = validPaths[0],
                        title = "Playlist",
                        size = halfSize,
                        cornerRadius = 0.dp,
                        modifier = Modifier.size(halfSize)
                    )
                    CoverArtImage(
                        coverArtPath = validPaths[1],
                        title = "Playlist",
                        size = halfSize,
                        cornerRadius = 0.dp,
                        modifier = Modifier.size(halfSize)
                    )
                }
                Row {
                    CoverArtImage(
                        coverArtPath = validPaths[2],
                        title = "Playlist",
                        size = halfSize,
                        cornerRadius = 0.dp,
                        modifier = Modifier.size(halfSize)
                    )
                    CoverArtImage(
                        coverArtPath = validPaths[3],
                        title = "Playlist",
                        size = halfSize,
                        cornerRadius = 0.dp,
                        modifier = Modifier.size(halfSize)
                    )
                }
            }
        }
    }
}
