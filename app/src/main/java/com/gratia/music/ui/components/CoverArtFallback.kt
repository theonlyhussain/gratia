package com.gratia.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gratia.music.ui.theme.GratiaTheme
import com.gratia.music.ui.theme.SpaceGrotesk
import kotlin.math.abs

/**
 * Gradient fallback cover art with song initial and warm Gratia accent.
 * Uses Noir Black → Maroon → Cherry Red gradient.
 */
@Composable
fun CoverArtFallback(
    title: String,
    artist: String = "",
    size: Dp = 48.dp,
    cornerRadius: Dp = 8.dp,
    fontSize: TextUnit = 16.sp,
    modifier: Modifier = Modifier
) {
    val accentColor = deriveAccentColor(title, artist)
    val initials = getInitials(title)

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        accentColor.copy(alpha = 0.4f),
                        GratiaTheme.colors.accent.copy(alpha = 0.3f),
                        GratiaTheme.colors.textPrimary.copy(alpha = 0.8f)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            initials,
            fontFamily = SpaceGrotesk,
            fontWeight = FontWeight.Bold,
            fontSize = fontSize,
            color = GratiaTheme.colors.background.copy(alpha = 0.8f)
        )
    }
}

/**
 * Derive a consistent warm accent color from title and artist.
 * Uses the Gratia warm palette instead of random rainbow colors.
 */
private fun deriveAccentColor(title: String, artist: String): Color {
    val hash = abs((title + artist).hashCode())
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
    return palette[hash % palette.size]
}
