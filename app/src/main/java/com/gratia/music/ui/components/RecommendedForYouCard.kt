package com.gratia.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.gratia.music.data.model.SongEntity
import com.gratia.music.ui.theme.GratiaTheme
import com.gratia.music.ui.theme.Inter
import com.gratia.music.ui.theme.SpaceGrotesk
import com.gratia.music.ui.components.bounceClick

@Composable
fun RecommendedForYouCard(
    artistName: String,
    artistImageUrl: String?,
    songs: List<SongEntity>,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(GratiaTheme.colors.surface)
            .bounceClick(onClick = onPlay)
            .padding(24.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Top: Artist Name
            Text(
                text = artistName,
                fontFamily = SpaceGrotesk,
                fontWeight = FontWeight.Bold,
                fontSize = 34.sp,
                color = GratiaTheme.colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 40.sp
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Middle: Artist Image
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                if (artistImageUrl != null) {
                    SubcomposeAsyncImage(
                        model = artistImageUrl,
                        contentDescription = "Artist Image",
                        contentScale = ContentScale.Crop,
                        loading = {
                            Box(modifier = Modifier.fillMaxSize().background(com.gratia.music.ui.components.shimmerBrush()))
                        },
                        modifier = Modifier
                            .size(180.dp)
                            .clip(PixelatedCircleShape(gridSize = 20))
                    )
                } else {
                    val fallbackPath = songs.firstOrNull { it.coverArtPath != null }?.coverArtPath
                    if (fallbackPath != null) {
                        CoverArtImage(
                            coverArtPath = fallbackPath,
                            title = artistName,
                            size = 180.dp,
                            cornerRadius = 90.dp,
                            modifier = Modifier.clip(PixelatedCircleShape(gridSize = 20))
                        )
                    } else {
                        // Fallback to a plain grey pixelated circle if no image and no cover
                        Box(
                            modifier = Modifier
                                .size(180.dp)
                                .clip(PixelatedCircleShape(gridSize = 20))
                                .background(GratiaTheme.colors.surfaceHover)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Bottom: Overlapping songs and Play button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Overlapping Song Covers
                Box(
                    modifier = Modifier.width(100.dp).height(40.dp)
                ) {
                    val displaySongs = songs.take(4)
                    displaySongs.forEachIndexed { index, song ->
                        val offset = (index * 20).dp
                        CoverArtImage(
                            coverArtPath = song.coverArtPath,
                            title = song.title,
                            size = 40.dp,
                            cornerRadius = 12.dp,
                            modifier = Modifier
                                .offset(x = offset)
                                .shadow(4.dp, RoundedCornerShape(12.dp))
                        )
                    }
                }
                
                // Play Button
                Button(
                    onClick = onPlay,
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GratiaTheme.colors.accent,
                        contentColor = androidx.compose.ui.graphics.Color.White
                    ),
                    modifier = Modifier.height(44.dp).padding(start = 16.dp)
                ) {
                    Text(
                        text = "Play",
                        fontFamily = Inter,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}
