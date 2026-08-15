package com.gratia.music.ui.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gratia.music.ui.components.AnimatedText
import com.gratia.music.ui.components.GratiaIcon
import com.gratia.music.ui.components.GratiaIconButton
import com.gratia.music.ui.theme.GratiaTheme

/**
 * Player header with song info, favorite star button, and more options.
 * Matches the industry standard's layout: title + artist on left, star + dots on right.
 */
@Composable
fun PlayerHeader(
    title: String,
    artist: String,
    album: String?,
    isFavorite: Boolean = false,
    playingFrom: String = "GRATIA",
    onClickTitle: () -> Unit = {},
    onClickArtist: () -> Unit = {},
    onClickAlbum: () -> Unit = {},
    onToggleFavorite: () -> Unit = {},
    onMoreClick: () -> Unit = {},
    audioFormatInfo: com.gratia.music.player.AudioFormatInfo? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = GratiaTheme.spacing.large),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Song info — left side
        Column(
            modifier = Modifier.weight(1f)
        ) {
            // Song title — hero text
            AnimatedText(
                text = title,
                style = GratiaTheme.typography.largeTitle,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fadeDurationMs = GratiaTheme.motion.slow,
                isMarquee = true,
                modifier = Modifier.clickable { onClickTitle() }
            )

            Spacer(Modifier.height(GratiaTheme.spacing.micro)) // 2dp

            // Artist
            AnimatedText(
                text = artist,
                style = GratiaTheme.typography.section,
                color = Color.White.copy(alpha = 0.55f),
                maxLines = 1,
                fadeDurationMs = GratiaTheme.motion.slow,
                isMarquee = true,
                modifier = Modifier.clickable { onClickArtist() }
            )

            // Audio Format Badge
            if (audioFormatInfo != null) {
                Spacer(Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val isLossless = when (audioFormatInfo.mimeType) {
                        "audio/flac", "audio/alac", "audio/x-wav", "audio/raw", "audio/x-aiff" -> true
                        else -> false
                    }
                    val formatName = when (audioFormatInfo.mimeType) {
                        "audio/flac" -> "FLAC"
                        "audio/alac" -> "ALAC"
                        "audio/mp4a-latm" -> "AAC"
                        "audio/mpeg" -> "MP3"
                        "audio/ogg", "audio/vorbis" -> "OGG"
                        "audio/x-wav" -> "WAV"
                        else -> audioFormatInfo.mimeType?.substringAfter("audio/")?.uppercase() ?: "AUDIO"
                    }
                    val sampleRateKhz = audioFormatInfo.sampleRate / 1000f
                    val sampleRateStr = if (sampleRateKhz % 1 == 0f) "${sampleRateKhz.toInt()}" else "$sampleRateKhz"
                    
                    val text = buildString {
                        append(formatName)
                        if (audioFormatInfo.bitDepth > 0 && isLossless) {
                            append(" • ${audioFormatInfo.bitDepth}-bit")
                        }
                        if (audioFormatInfo.sampleRate > 0) {
                            append(" / $sampleRateStr kHz")
                        }
                    }

                    androidx.compose.material3.Text(
                        text = text,
                        style = GratiaTheme.typography.caption.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium),
                        color = Color.White.copy(alpha = 0.4f)
                    )

                    if (isLossless) {
                        Box(
                            modifier = Modifier
                                .background(Color.White.copy(alpha = 0.15f), androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            val hiRes = audioFormatInfo.bitDepth > 16 || audioFormatInfo.sampleRate > 48000
                            androidx.compose.material3.Text(
                                text = if (hiRes) "Hi-Res Lossless" else "Lossless",
                                style = GratiaTheme.typography.caption.copy(fontSize = androidx.compose.ui.unit.TextUnit(9f, androidx.compose.ui.unit.TextUnitType.Sp), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.width(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // Favorite star button
            FavoriteStarButton(
                isFavorite = isFavorite,
                onToggle = onToggleFavorite
            )

            // More options button
            MoreButton(onClick = onMoreClick)
        }
    }
}

/**
 * Animated favorite star button with translucent circle background matching inspo.
 */
@Composable
private fun FavoriteStarButton(
    isFavorite: Boolean,
    onToggle: () -> Unit
) {
    val view = LocalView.current
    val haptics = GratiaTheme.haptics
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Spring pop animation on state change
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.85f else 1.0f,
        animationSpec = spring(
            dampingRatio = 0.5f,
            stiffness = 600f
        ),
        label = "favScale"
    )

    // Star icon color
    val starColor by animateColorAsState(
        targetValue = if (isFavorite) Color.White else Color.White.copy(alpha = 0.8f),
        animationSpec = if (isFavorite) tween(0) else tween(200),
        label = "starColor"
    )

    Box(
        modifier = Modifier
            .size(36.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.15f))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    haptics.medium(view)
                    onToggle()
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        GratiaIcon(
            imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
            contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
            tint = starColor,
            size = 20.dp
        )
    }
}

/**
 * More options button with translucent circle background.
 */
@Composable
private fun MoreButton(
    onClick: () -> Unit
) {
    val view = LocalView.current
    val haptics = GratiaTheme.haptics
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.85f else 1.0f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 600f),
        label = "moreScale"
    )

    Box(
        modifier = Modifier
            .size(36.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.15f))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    haptics.medium(view)
                    onClick()
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        GratiaIcon(
            imageVector = Icons.Default.MoreHoriz,
            contentDescription = "More options",
            tint = Color.White.copy(alpha = 0.8f),
            size = 20.dp
        )
    }
}
