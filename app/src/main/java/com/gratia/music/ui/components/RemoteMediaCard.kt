package com.gratia.music.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gratia.music.ui.theme.GratiaTheme

/**
 * A remote catalogue card: an album, playlist, mix or artist from YouTube Music.
 *
 * Remote shelves and Explore category pages are full of these, and they all
 * look the same — square art (round for an artist), a title, a credit line —
 * so the shape lives in one place rather than being re-drawn per screen.
 * The artwork is an http URL, which [CoverArtImage] already knows how to fetch
 * and resize.
 */
@Composable
fun RemoteMediaCard(
    title: String,
    subtitle: String?,
    artworkUrl: String?,
    modifier: Modifier = Modifier,
    circular: Boolean = false,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .width(148.dp)
            .clip(RoundedCornerShape(8.dp))
            .bounceClick(onClick = onClick)
    ) {
        CoverArtImage(
            coverArtPath = artworkUrl,
            title = title,
            artist = subtitle ?: "",
            size = 148.dp,
            // A circle is a corner radius large enough to round the whole square,
            // which is how CoverArtImage already clips.
            cornerRadius = if (circular) 74.dp else 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = title,
            style = GratiaTheme.typography.body,
            fontWeight = FontWeight.Medium,
            color = GratiaTheme.colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = GratiaTheme.typography.caption,
                color = GratiaTheme.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
