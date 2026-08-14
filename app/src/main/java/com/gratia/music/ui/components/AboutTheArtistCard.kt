package com.gratia.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
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
    artistInfos: Map<String, ArtistInfo?>,
    onArtistClick: (String) -> Unit,
    onSeeMoreClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (artistInfos.isEmpty()) return

    var selectedArtistName by remember(artistInfos) {
        mutableStateOf(artistInfos.keys.firstOrNull())
    }

    val selectedInfo = selectedArtistName?.let { artistInfos[it] }
    // If we only have the name but no loaded info yet, we still show the card with just the name
    val displayName = selectedInfo?.name ?: selectedArtistName ?: return

    Column(modifier = modifier.fillMaxWidth()) {
        // Multi-artist selector
        if (artistInfos.size > 1) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = GratiaTheme.spacing.large)
            ) {
                items(artistInfos.keys.toList()) { artistName ->
                    val isSelected = artistName == selectedArtistName
                    val info = artistInfos[artistName]
                    
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(24.dp))
                            .background(if (isSelected) GratiaTheme.colors.accent else GratiaTheme.colors.surface)
                            .clickable { selectedArtistName = artistName }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (info?.pictureUrl != null) {
                            AsyncImage(
                                model = info.pictureUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.2f))
                            )
                        }
                        Text(
                            text = artistName,
                            fontFamily = Inter,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 13.sp,
                            color = if (isSelected) GratiaTheme.colors.background else GratiaTheme.colors.textPrimary
                        )
                    }
                }
            }
        }

        // Main Artist Card
        Box(
            modifier = Modifier
                .padding(horizontal = GratiaTheme.spacing.large)
                .fillMaxWidth()
                .clip(RoundedCornerShape(32.dp))
                .background(GratiaTheme.colors.surface)
        ) {
            Column {
                // Top section: Image with "About the artist" text
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .clickable { onArtistClick(displayName) } // Tapping image -> Artist Page
                ) {
                    if (selectedInfo?.pictureUrl != null) {
                        AsyncImage(
                            model = selectedInfo.pictureUrl,
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
                        modifier = Modifier.clickable { onArtistClick(displayName) }, // Tapping name -> Artist Page
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = displayName,
                            fontFamily = SpaceGrotesk,
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp,
                            color = GratiaTheme.colors.textPrimary
                        )
                        
                        if (selectedInfo?.isVerified == true) {
                            Icon(
                                imageVector = VerifiedRosette,
                                contentDescription = "Verified Artist",
                                tint = GratiaTheme.colors.accent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    if (selectedInfo != null) {
                        Spacer(modifier = Modifier.height(4.dp))

                        // Fans / Listeners
                        val formattedFans = NumberFormat.getNumberInstance(Locale.getDefault()).format(selectedInfo.fanCount)
                        Text(
                            text = "$formattedFans fans on Deezer",
                            fontFamily = Inter,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                            color = GratiaTheme.colors.textSecondary
                        )

                        // Bio
                        if (!selectedInfo.biography.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSeeMoreClick(displayName) } // Tapping bio -> Bio Sheet
                            ) {
                                Text(
                                    text = selectedInfo.biography,
                                    fontFamily = Inter,
                                    fontWeight = FontWeight.Normal,
                                    fontSize = 15.sp,
                                    color = GratiaTheme.colors.textPrimary.copy(alpha = 0.9f),
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                    lineHeight = 22.sp
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = "See more",
                                    fontFamily = Inter,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = GratiaTheme.colors.textPrimary
                                )
                            }
                        }
                    }
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
