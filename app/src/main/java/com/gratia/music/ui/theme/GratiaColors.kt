package com.gratia.music.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Gratia Design System - Light (Cloudy) and Dark (Charcoal/Purple)
 *
 * Player-specific tokens provide dedicated colors for the cinematic
 * playback experience without polluting the main theme palette.
 */
@Immutable
data class GratiaColors(
    val isDark: Boolean = false,
    
    // Main backgrounds
    val background: Color,
    val surface: Color,
    val surfaceHover: Color,
    
    // Accents
    val accent: Color,
    val accentGlow: Color,
    
    // Text
    val textPrimary: Color,
    val textSecondary: Color,
    
    // Glass
    val glassBg: Color,
    val glassBorder: Color,
    
    // Semantic
    val error: Color,
    val success: Color,
    val warning: Color,
    val accentWarm: Color,

    // Player-specific tokens
    val playerGlow: Color,          // Ambient glow behind artwork & play button
    val progressTrack: Color,       // Inactive progress bar track
    val progressActive: Color,      // Active progress bar fill
    val controlMuted: Color,        // Disabled / inactive control tint
    val playerSurface: Color        // Glass surface tint inside the player
)

val lightGratiaColors = GratiaColors(
    isDark = false,
    background = Color(0xFFE6F7FF), // Cloudy blue
    surface = Color(0xFFFFFFFF),    // Frosted glass white
    surfaceHover = Color(0xFFF0F9FF),
    accent = Color(0xFF4A90E2),     // Soft blue
    accentGlow = Color(0x334A90E2),
    textPrimary = Color(0xFF1A2D50),// Dark navy
    textSecondary = Color(0xFF5A6D90),
    glassBg = Color(0xB3FFFFFF),    // Semi-transparent white
    glassBorder = Color(0x261A2D50),
    error = Color(0xFFB00020),
    success = Color(0xFF2E7D32),
    warning = Color(0xFFE65100),
    accentWarm = Color(0xFFA65D03),
    playerGlow = Color(0x264A90E2),
    progressTrack = Color(0x261A2D50),
    progressActive = Color(0xFF4A90E2),
    controlMuted = Color(0x661A2D50),
    playerSurface = Color(0xCCFFFFFF)
)

val darkGratiaColors = GratiaColors(
    isDark = true,
    background = Color(0xFF121212), // Deep black
    surface = Color(0xFF1E1E1E),    // Charcoal
    surfaceHover = Color(0xFF2C2C2C),
    accent = Color(0xFFBB86FC),     // Purple accent
    accentGlow = Color(0x66BB86FC), // Purple glow
    textPrimary = Color(0xFFFFFFFF),// White
    textSecondary = Color(0xFFBDB8AB),
    glassBg = Color(0xB31E1E1E),    // Semi-transparent charcoal
    glassBorder = Color(0x26FFFFFF),
    error = Color(0xFFCF6679),
    success = Color(0xFF2E7D32),
    warning = Color(0xFFE65100),
    accentWarm = Color(0xFFA65D03),
    playerGlow = Color(0x40BB86FC),
    progressTrack = Color(0x26FFFFFF),
    progressActive = Color(0xFFFFFFFF),
    controlMuted = Color(0x4DFFFFFF),
    playerSurface = Color(0xB3161616)
)

val LocalGratiaColors = staticCompositionLocalOf { lightGratiaColors }
