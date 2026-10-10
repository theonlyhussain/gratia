package com.gratia.music.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.gratia.music.data.model.SongEntity
import com.gratia.music.ui.theme.GratiaTheme
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import kotlin.math.abs
import androidx.compose.ui.graphics.Color
import com.gratia.music.ui.components.bounceClick

@Composable
fun RecentCard(
    song: SongEntity,
    onClick: () -> Unit
) {
    // The shared artwork request at the card's drawn size. The old gate —
    // `File(coverArtPath).exists()` — is a *local-file* check: for a liked
    // remote track the path is an http URL, `File.exists()` is false for
    // those, and every liked song therefore fell to the colour-gradient
    // placeholder even though its artwork URL was in hand. rememberArtworkRequest
    // handles files, http URLs and sizing in one place, so the shelf no
    // longer needs to know which kind of path it is holding.
    val artRequest = rememberArtworkRequest(song.coverArtPath, sizePx = 480)

    Box(
        modifier = Modifier
            .size(140.dp)
            .clip(RoundedCornerShape(8.dp))
            .bounceClick(onClick = onClick)
    ) {
        if (artRequest != null) {
            AsyncImage(
                model = artRequest,
                contentDescription = "${song.title} cover art",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            val hash = abs((song.title + song.artist).hashCode())
            val palette = listOf(
                Color(0xFF810100), // Cherry Red
                Color(0xFF630102), // Maroon
                Color(0xFFA65D03), // Warm amber
                Color(0xFF8B4513), // Saddle brown
                Color(0xFF6B3A2A), // Warm brown
                Color(0xFF4A2020), // Dark wine
                Color(0xFF7A3B1E), // Copper
                Color(0xFF5C3317), // Dark sienna
            )
            val accentColor = palette[hash % palette.size]
            
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                accentColor.copy(alpha = 0.6f),
                                GratiaTheme.colors.surface
                            )
                        )
                    )
            ) {
                Text(
                    text = song.title,
                    style = GratiaTheme.typography.title,
                    fontWeight = FontWeight.Bold,
                    color = GratiaTheme.colors.textPrimary.copy(alpha = 0.9f),
                    modifier = Modifier.padding(12.dp),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
