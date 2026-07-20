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
    background = Color(0xFFFFFFFF), // Pure white
    surface = Color(0xFFFFFFFF),    // Pure white
    surfaceHover = Color(0xFFF2F2F7), // iOS System Gray 6 (Light)
    accent = Color(0xFFFA243C),     // Apple Music Red
    accentGlow = Color(0x33FA243C),
    textPrimary = Color(0xFF000000),// Pure Black
    textSecondary = Color(0xFF8E8E93), // iOS System Gray
    glassBg = Color(0xB3FFFFFF),    // Semi-transparent white
    glassBorder = Color(0x26000000), // Slight dark border
    error = Color(0xFFFF3B30),      // iOS Red
    success = Color(0xFF34C759),    // iOS Green
    warning = Color(0xFFFF9500),    // iOS Orange
    accentWarm = Color(0xFFFF9500),
    playerGlow = Color(0x1AFA243C),
    progressTrack = Color(0x26000000),
    progressActive = Color(0xFFFA243C), // Wait, Apple Music progress bar is usually white or grey, but red when active
    controlMuted = Color(0x66000000),
    playerSurface = Color(0xCCFFFFFF)
)

val darkGratiaColors = GratiaColors(
    isDark = true,
    background = Color(0xFF000000), // Pure black
    surface = Color(0xFF1C1C1E),    // iOS System Gray 6 (Dark)
    surfaceHover = Color(0xFF2C2C2E), // iOS System Gray 5 (Dark)
    accent = Color(0xFFFA243C),     // Apple Music Red
    accentGlow = Color(0x4DFA243C), // Red glow
    textPrimary = Color(0xFFFFFFFF),// Pure White
    textSecondary = Color(0xFF8E8E93), // iOS System Gray
    glassBg = Color(0xB31C1C1E),    // Semi-transparent charcoal
    glassBorder = Color(0x26FFFFFF),
    error = Color(0xFFFF453A),      // iOS Red (Dark)
    success = Color(0xFF30D158),    // iOS Green (Dark)
    warning = Color(0xFFFF9F0A),    // iOS Orange (Dark)
    accentWarm = Color(0xFFFF9F0A),
    playerGlow = Color(0x40FA243C),
    progressTrack = Color(0x26FFFFFF),
    progressActive = Color(0xFFFFFFFF),
    controlMuted = Color(0x4DFFFFFF),
    playerSurface = Color(0xB3000000)
)

val LocalGratiaColors = staticCompositionLocalOf { lightGratiaColors }
