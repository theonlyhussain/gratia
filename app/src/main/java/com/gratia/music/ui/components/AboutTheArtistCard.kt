package com.gratia.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.gratia.music.data.repository.ArtistInfo
import com.gratia.music.ui.theme.GratiaTheme
import com.gratia.music.ui.theme.Inter
import com.gratia.music.ui.theme.SpaceGrotesk
import java.text.NumberFormat
import java.util.Locale

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun AboutTheArtistCard(
    artistInfos: Map<String, ArtistInfo?>,
    onArtistClick: (String) -> Unit,
    onSeeMoreClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (artistInfos.isEmpty()) return

    val artistList = remember(artistInfos) { artistInfos.keys.toList() }
    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { artistList.size }
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "About the artist",
            fontFamily = SpaceGrotesk,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
        )

        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 24.dp),
            pageSpacing = 16.dp,
            modifier = Modifier.fillMaxWidth()
        ) { page ->
            val artistName = artistList[page]
            val info = artistInfos[artistName]

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp) // wide rectangular, but tall enough for info
                    .clip(RoundedCornerShape(32.dp))
                    .background(GratiaTheme.colors.surface)
                    .clickable { onSeeMoreClick(artistName) }
            ) {
                // Background Image
                if (info?.pictureUrl != null) {
                    AsyncImage(
                        model = info.pictureUrl,
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

                // Gradient Overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)),
                                startY = Float.POSITIVE_INFINITY / 2f
                            )
                        )
                )

                // Info Section
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomStart)
                        .padding(24.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = artistName,
                            fontFamily = SpaceGrotesk,
                            fontWeight = FontWeight.Bold,
                            fontSize = 28.sp,
                            color = Color.White
                        )
                        if (info?.isVerified == true) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(24.dp)) {
                                Icon(
                                    imageVector = VerifiedRosette,
                                    contentDescription = "Verified Artist",
                                    tint = GratiaTheme.colors.accent,
                                    modifier = Modifier.matchParentSize()
                                )
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    if (info != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        val formattedFans = NumberFormat.getNumberInstance(Locale.getDefault()).format(info.fanCount)
                        Text(
                            text = "$formattedFans listeners",
                            fontFamily = Inter,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                        
                        // Bio snippit removed to fit horizontal card style and rely on clicking to see more.
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
        path(fill = SolidColor(Color.White)) {
            moveTo(23.0f, 11.99f)
            lineTo(20.56f, 9.22f)
            lineTo(20.9f, 5.54f)
            lineTo(17.29f, 4.72f)
            lineTo(15.4f, 1.54f)
            lineTo(12.0f, 2.9f)
            lineTo(8.6f, 1.54f)
            lineTo(6.71f, 4.72f)
            lineTo(3.1f, 5.53f)
            lineTo(3.44f, 9.21f)
            lineTo(1.0f, 11.99f)
            lineTo(3.44f, 14.76f)
            lineTo(3.1f, 18.47f)
            lineTo(6.71f, 19.29f)
            lineTo(8.6f, 22.47f)
            lineTo(12.0f, 21.1f)
            lineTo(15.4f, 22.46f)
            lineTo(17.29f, 19.28f)
            lineTo(20.9f, 18.46f)
            lineTo(20.56f, 14.78f)
            lineTo(23.0f, 11.99f)
            close()
        }
    }.build()
