package com.gratia.music.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gratia.music.ui.components.AnimatedText
import com.gratia.music.ui.theme.Inter
import com.gratia.music.ui.theme.SpaceGrotesk

/**
 * Song information hierarchy for the expanded player.
 *
 * Visual hierarchy (top → bottom):
 * 1. "PLAYING FROM" — tiny, tracked, muted label
 * 2. Song title — large, bold, bright
 * 3. Artist — medium, slightly muted
 * 4. Album / year — optional, very muted
 *
 * All text uses [AnimatedText] so changes crossfade — never snap.
 * Spacing is generous ("breathing") — not cramped.
 */
@Composable
fun PlayerHeader(
    title: String,
    artist: String,
    album: String?,
    playingFrom: String = "GRATIA",
    onClickTitle: () -> Unit = {},
    onClickArtist: () -> Unit = {},
    onClickAlbum: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp)
    ) {
        // "PLAYING FROM" label
        AnimatedText(
            text = "PLAYING FROM $playingFrom",
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = Inter,
            color = Color.White.copy(alpha = 0.45f),
            letterSpacing = 2.sp,
            maxLines = 1
        )

        Spacer(Modifier.height(12.dp))

        // Song title — hero text
        AnimatedText(
            text = title,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = SpaceGrotesk,
            color = Color.White,
            lineHeight = 30.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            fadeDurationMs = 400,
            modifier = Modifier.clickable { onClickTitle() }
        )

        Spacer(Modifier.height(6.dp))

        // Artist
        AnimatedText(
            text = artist,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = Inter,
            color = Color.White.copy(alpha = 0.55f),
            maxLines = 1,
            fadeDurationMs = 350,
            modifier = Modifier.clickable { onClickArtist() }
        )

        // Album / year — only if present
        if (!album.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            AnimatedText(
                text = album,
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                fontFamily = Inter,
                color = Color.White.copy(alpha = 0.3f),
                maxLines = 1,
                fadeDurationMs = 350,
                modifier = Modifier.clickable { onClickAlbum() }
            )
        }
    }
}
