package com.gratia.music.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gratia.music.data.model.SongEntity
import com.gratia.music.ui.theme.GratiaTheme
import com.gratia.music.ui.theme.Inter
import com.gratia.music.ui.theme.SpaceGrotesk

/**
 * Library song row. Translates SongRow.tsx from the web prototype.
 */
@Composable
fun SongRow(
    song: SongEntity,
    index: Int,
    isActive: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    badge: String? = null,
    modifier: Modifier = Modifier
) {
    val firstMood = song.mood?.split(",")?.firstOrNull()?.trim()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .then(
                if (isActive) Modifier.background(GratiaTheme.colors.glassBorder)
                else Modifier
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Track number or playing bars
        Box(modifier = Modifier.width(24.dp), contentAlignment = Alignment.Center) {
            if (isPlaying) {
                PlayingBars()
            } else {
                Text(
                    "${index + 1}",
                    fontFamily = Inter,
                    fontSize = 11.sp,
                    color = if (isActive) GratiaTheme.colors.accent else GratiaTheme.colors.textSecondary
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        // Cover art image
        CoverArtImage(
            coverArtPath = song.coverArtPath,
            title = song.title,
            artist = song.artist,
            size = 40.dp,
            cornerRadius = 6.dp,
            fontSize = 10.sp
        )

        Spacer(Modifier.width(12.dp))

        // Title + Artist
        Column(modifier = Modifier.weight(1f)) {
            Text(
                song.title,
                fontFamily = Inter,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                color = if (isActive) GratiaTheme.colors.accent else GratiaTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row {
                Text(
                    song.artist,
                    fontFamily = Inter,
                    fontSize = 11.sp,
                    color = GratiaTheme.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (song.album != null) {
                    Text(" · ", fontSize = 11.sp, color = GratiaTheme.colors.textSecondary)
                    Text(
                        song.album!!,
                        fontFamily = Inter,
                        fontSize = 11.sp,
                        color = GratiaTheme.colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            // Badge (e.g., "Lyrics match")
            if (badge != null) {
                Text(
                    badge,
                    fontFamily = Inter,
                    fontSize = 9.sp,
                    color = GratiaTheme.colors.accent,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Mood pill
        if (firstMood != null) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = androidx.compose.ui.graphics.Color.Transparent,
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = androidx.compose.ui.graphics.SolidColor(GratiaTheme.colors.surfaceHover)
                )
            ) {
                Text(
                    firstMood,
                    fontFamily = Inter,
                    fontSize = 9.sp,
                    color = GratiaTheme.colors.textSecondary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
            Spacer(Modifier.width(8.dp))
        }

        // Duration
        Text(
            formatTime(song.durationMs),
            fontFamily = Inter,
            fontSize = 11.sp,
            color = GratiaTheme.colors.textSecondary
        )
    }
}

/**
 * Animated playing bars (equalizer animation).
 */
@Composable
fun PlayingBars() {
    val infiniteTransition = rememberInfiniteTransition(label = "playingBars")

    val bar1Height by infiniteTransition.animateFloat(
        initialValue = 4f, targetValue = 14f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ), label = "bar1"
    )
    val bar2Height by infiniteTransition.animateFloat(
        initialValue = 8f, targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = EaseInOut, delayMillis = 150),
            repeatMode = RepeatMode.Reverse
        ), label = "bar2"
    )
    val bar3Height by infiniteTransition.animateFloat(
        initialValue = 6f, targetValue = 12f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = EaseInOut, delayMillis = 300),
            repeatMode = RepeatMode.Reverse
        ), label = "bar3"
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier.height(14.dp)
    ) {
        listOf(bar1Height, bar2Height, bar3Height).forEach { height ->
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(height.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(GratiaTheme.colors.accent)
            )
        }
    }
}

private val EaseInOut = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)
