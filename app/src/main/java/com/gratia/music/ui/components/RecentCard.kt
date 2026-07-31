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
import java.io.File
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
    val context = LocalContext.current
    val hasCover = !song.coverArtPath.isNullOrBlank() && File(song.coverArtPath).exists()

    Box(
        modifier = Modifier
            .size(140.dp)
            .clip(RoundedCornerShape(8.dp))
            .bounceClick(onClick = onClick)
    ) {
        if (hasCover) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(File(song.coverArtPath!!))
                    .crossfade(300)
                    .build(),
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
