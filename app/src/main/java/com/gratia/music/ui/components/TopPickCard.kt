package com.gratia.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.gratia.music.data.model.SongEntity
import com.gratia.music.ui.theme.GratiaTheme
import java.io.File

@Composable
fun TopPickCard(
    song: SongEntity,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val hasCover = !song.coverArtPath.isNullOrBlank() && File(song.coverArtPath).exists()

    Box(
        modifier = Modifier
            .width(200.dp)
            .height(280.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
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
            // Full bleed fallback
            CoverArtFallback(
                title = song.title,
                artist = song.artist,
                size = 300.dp, // large enough to cover the box
                cornerRadius = 0.dp,
                fontSize = 64.sp,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Dark gradient overlay at bottom
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.7f),
                            Color.Black.copy(alpha = 0.95f)
                        )
                    )
                )
        )

        // Text overlaid
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
        ) {
            Text(
                text = "Top Pick",
                style = GratiaTheme.typography.caption,
                color = Color.White.copy(alpha = 0.75f),
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = song.title,
                style = GratiaTheme.typography.body,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontSize = 18.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = song.artist,
                style = GratiaTheme.typography.caption,
                color = Color.White.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
