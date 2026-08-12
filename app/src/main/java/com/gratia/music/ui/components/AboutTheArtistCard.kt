package com.gratia.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.gratia.music.data.repository.ArtistInfo
import com.gratia.music.ui.theme.GratiaTheme
import com.gratia.music.ui.theme.Inter
import com.gratia.music.ui.theme.SpaceGrotesk
import java.text.NumberFormat
import java.util.Locale

@Composable
fun AboutTheArtistCard(
    artistInfo: ArtistInfo?,
    modifier: Modifier = Modifier
) {
    if (artistInfo == null) return

    var expanded by remember { mutableStateOf(false) }
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(GratiaTheme.colors.surface)
            .clickable { expanded = !expanded }
    ) {
        Column {
            // Top section: Image with "About the artist" text
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
            ) {
                if (artistInfo.pictureUrl != null) {
                    AsyncImage(
                        model = artistInfo.pictureUrl,
                        contentDescription = "Artist Image",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(GratiaTheme.colors.surfaceHover)
                    )
                }

                // Top gradient for text readability
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.5f),
                                    Color.Transparent
                                )
                            )
                        )
                )

                Text(
                    text = "About the artist",
                    fontFamily = SpaceGrotesk,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = Color.White,
                    modifier = Modifier.padding(24.dp)
                )
            }

            // Bottom section: Info and Bio
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                // Name and verification
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = artistInfo.name,
                        fontFamily = SpaceGrotesk,
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp,
                        color = GratiaTheme.colors.textPrimary
                    )
                    
                    if (artistInfo.isVerified) {
                        Icon(
                            imageVector = VerifiedRosette,
                            contentDescription = "Verified Artist",
                            tint = GratiaTheme.colors.accent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Fans / Listeners
                val formattedFans = NumberFormat.getNumberInstance(Locale.getDefault()).format(artistInfo.fanCount)
                Text(
                    text = "$formattedFans fans on Deezer",
                    fontFamily = Inter,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    color = GratiaTheme.colors.textSecondary
                )

                // Bio
                if (!artistInfo.biography.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = artistInfo.biography,
                        fontFamily = Inter,
                        fontWeight = FontWeight.Normal,
                        fontSize = 15.sp,
                        color = GratiaTheme.colors.textPrimary.copy(alpha = 0.9f),
                        maxLines = if (expanded) Int.MAX_VALUE else 4,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 22.sp
                    )
                }
            }
        }
    }
}

val VerifiedRosette: ImageVector
    get() = ImageVector.Builder(
        name = "Verified",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.White), pathFillType = PathFillType.EvenOdd) {
            // Outer Scalloped Badge (Rosette)
            moveTo(12.0f, 1.0f)
            lineTo(14.8f, 3.2f)
            lineTo(18.2f, 3.2f)
            lineTo(19.2f, 6.4f)
            lineTo(22.0f, 8.2f)
            lineTo(20.8f, 11.4f)
            lineTo(22.2f, 14.4f)
            lineTo(19.6f, 16.6f)
            lineTo(19.0f, 20.0f)
            lineTo(15.6f, 20.6f)
            lineTo(13.2f, 23.0f)
            lineTo(10.0f, 22.0f)
            lineTo(6.8f, 23.0f)
            lineTo(4.4f, 20.6f)
            lineTo(1.0f, 20.0f)
            lineTo(0.4f, 16.6f)
            lineTo(2.2f, 14.4f)
            lineTo(0.8f, 11.4f)
            lineTo(2.0f, 8.2f)
            lineTo(4.8f, 6.4f)
            lineTo(5.8f, 3.2f)
            lineTo(9.2f, 3.2f)
            close()
            
            // Inner Checkmark Cutout
            moveTo(10.5f, 16.5f)
            lineTo(6.0f, 12.0f)
            lineTo(7.41f, 10.59f)
            lineTo(10.5f, 13.67f)
            lineTo(16.59f, 7.58f)
            lineTo(18.0f, 9.0f)
            close()
        }
    }.build()
