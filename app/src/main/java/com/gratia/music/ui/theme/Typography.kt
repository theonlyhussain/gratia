package com.gratia.music.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.CircleShape

// Shapes system - rounded, soft, premium feel
object Shapes {
    // Extra small - for chips, small buttons
    val ExtraSmall = RoundedCornerShape(4.dp)
    // Small - for buttons, cards with subtle rounding
    val Small = RoundedCornerShape(8.dp)
    // Medium - for cards, containers
    val Medium = RoundedCornerShape(12.dp)
    // Large - for prominent cards, modals
    val Large = RoundedCornerShape(16.dp)
    // Extra large - for full-screen dialogs, modals
    val ExtraLarge = RoundedCornerShape(24.dp)
    // Pill shape for buttons, chips
    val Pill = RoundedCornerShape(24.dp)
    // Circle for avatars, icons
    val Circle = CircleShape
}

// Typography system matching the premium, emotional feel
object Typography {
    // Using system fonts for now, can be customized later
    val FontFamily = androidx.compose.ui.text.font.FontFamily.Default

    val value = Typography(
        // Large title - bold, prominent for section headers
        titleLarge = TextStyle(
            fontFamily = FontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp
        ),
        // Medium title - for section titles
        titleMedium = TextStyle(
            fontFamily = FontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp
        ),
        // Small title - for card titles
        titleSmall = TextStyle(
            fontFamily = FontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp
        ),
        // Body large - for primary text content
        bodyLarge = TextStyle(
            fontFamily = FontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 16.sp
        ),
        // Body medium - for secondary text content
        bodyMedium = TextStyle(
            fontFamily = FontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp
        ),
        // Body small - for helper text, captions
        bodySmall = TextStyle(
            fontFamily = FontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 12.sp
        ),
        // Label large - for buttons, chips
        labelLarge = TextStyle(
            fontFamily = FontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp
        ),
        // Label medium - for small buttons, tags
        labelMedium = TextStyle(
            fontFamily = FontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp
        ),
        // Label small - for captions, icons
        labelSmall = TextStyle(
            fontFamily = FontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp
        )
    )
}