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
import androidx.compose.ui.text.style.TextOverflow
import com.gratia.music.ui.components.AnimatedText
import com.gratia.music.ui.theme.GratiaTheme

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
            .padding(horizontal = GratiaTheme.spacing.large) // 32dp instead of 28dp to align with tokens
    ) {
        // "PLAYING FROM" label
        AnimatedText(
            text = "PLAYING FROM $playingFrom",
            style = GratiaTheme.typography.caption.copy(letterSpacing = androidx.compose.ui.unit.TextUnit(2f, androidx.compose.ui.unit.TextUnitType.Sp)),
            color = Color.White.copy(alpha = 0.45f),
            maxLines = 1
        )

        Spacer(Modifier.height(GratiaTheme.spacing.mediumSmall)) // 12dp

        // Song title — hero text
        AnimatedText(
            text = title,
            style = GratiaTheme.typography.largeTitle,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            fadeDurationMs = GratiaTheme.motion.slow,
            modifier = Modifier.clickable { onClickTitle() }
        )

        Spacer(Modifier.height(GratiaTheme.spacing.extraSmall)) // 4dp

        // Artist
        AnimatedText(
            text = artist,
            style = GratiaTheme.typography.section,
            color = Color.White.copy(alpha = 0.55f),
            maxLines = 1,
            fadeDurationMs = GratiaTheme.motion.slow,
            modifier = Modifier.clickable { onClickArtist() }
        )

        // Album / year — only if present
        if (!album.isNullOrBlank()) {
            Spacer(Modifier.height(GratiaTheme.spacing.micro)) // 2dp
            AnimatedText(
                text = album,
                style = GratiaTheme.typography.body,
                color = Color.White.copy(alpha = 0.3f),
                maxLines = 1,
                fadeDurationMs = GratiaTheme.motion.slow,
                modifier = Modifier.clickable { onClickAlbum() }
            )
        }
    }
}
