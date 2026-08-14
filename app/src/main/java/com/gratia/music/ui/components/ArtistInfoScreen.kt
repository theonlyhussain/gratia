package com.gratia.music.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.gratia.music.data.repository.ArtistInfo
import com.gratia.music.ui.theme.GratiaTheme
import com.gratia.music.ui.theme.Inter
import com.gratia.music.ui.theme.SpaceGrotesk
import kotlinx.coroutines.launch

/**
 * Full page Artist Information Screen displayed over the ExpandedPlayer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistInfoScreen(
    artistName: String,
    artistInfo: ArtistInfo?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val motion = GratiaTheme.motion
    val scope = rememberCoroutineScope()
    
    // Independent swipe-down-to-dismiss gesture for this full-screen layer
    val dismissOffsetY = remember { Animatable(0f) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(GratiaTheme.colors.background)
            .graphicsLayer {
                translationY = dismissOffsetY.value
                // Optional slight fade out on drag
                alpha = if (dismissOffsetY.value > 0f) {
                    (1f - (dismissOffsetY.value / 1000f)).coerceAtLeast(0.5f)
                } else 1f
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        scope.launch {
                            if (dismissOffsetY.value > 300f) {
                                onDismiss()
                            } else {
                                dismissOffsetY.animateTo(0f, animationSpec = motion.springStiff())
                            }
                        }
                    },
                    onDragCancel = {
                        scope.launch {
                            dismissOffsetY.animateTo(0f, animationSpec = motion.springStiff())
                        }
                    },
                    onVerticalDrag = { _, dragAmount ->
                        scope.launch {
                            if (dragAmount > 0 || dismissOffsetY.value > 0) {
                                dismissOffsetY.snapTo((dismissOffsetY.value + dragAmount).coerceAtLeast(0f))
                            }
                        }
                    }
                )
            }
    ) {
        val scrollState = rememberScrollState()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(bottom = 64.dp)
        ) {
            // Header: Back button + Large Image + Name
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f) // Square hero image
            ) {
                // Background image
                if (artistInfo?.pictureUrl != null) {
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

                // Dark gradient overlay for text readability at the bottom
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)),
                                startY = 300f
                            )
                        )
                )

                // Back Button (Top Left)
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .padding(top = 48.dp, start = 16.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.4f))
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }

                // Name and Verification (Bottom Left)
                Column(
                    modifier = Modifier
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
                            fontSize = 32.sp,
                            color = Color.White
                        )
                        if (artistInfo?.isVerified == true) {
                            Icon(
                                imageVector = VerifiedRosette,
                                contentDescription = "Verified Artist",
                                tint = GratiaTheme.colors.accent,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    if (artistInfo != null) {
                        val formattedFans = java.text.NumberFormat.getNumberInstance(java.util.Locale.getDefault()).format(artistInfo.fanCount)
                        Text(
                            text = "$formattedFans Monthly Listeners",
                            fontFamily = Inter,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            // Body: Bio and Info
            Column(modifier = Modifier.padding(24.dp)) {
                val bio = artistInfo?.biography
                if (!bio.isNullOrBlank()) {
                    Text(
                        text = "About",
                        fontFamily = SpaceGrotesk,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = GratiaTheme.colors.textPrimary,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    Text(
                        text = bio,
                        fontFamily = Inter,
                        fontWeight = FontWeight.Normal,
                        fontSize = 16.sp,
                        color = GratiaTheme.colors.textSecondary,
                        lineHeight = 24.sp
                    )
                } else {
                    Text(
                        text = "No biography available.",
                        fontFamily = Inter,
                        fontWeight = FontWeight.Normal,
                        fontSize = 16.sp,
                        color = GratiaTheme.colors.textSecondary
                    )
                }
            }
        }
    }
}
