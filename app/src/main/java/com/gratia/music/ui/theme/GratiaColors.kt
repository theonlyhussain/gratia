package com.gratia.music.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Gratia Design System — Warm Premium Color Palette
 *
 * Cotton:      #EDEBDE  — main background for normal screens
 * Cherry Red:  #810100  — important accents (active tab, play button, progress)
 * Maroon:      #630102  — strong surfaces (bottom nav, mini-player, buttons)
 * Noir Black:  #1B1716  — main text and deep dark surfaces
 *
 * Design: warm, premium, vintage, music-first, soft but bold.
 */
@Immutable
data class GratiaColors(
    // Main backgrounds
    val cotton: Color = Color(0xFFEDEBDE),       // Light warm background
    val noirBlack: Color = Color(0xFF1B1716),     // Deep dark text / surfaces
    val cherryRed: Color = Color(0xFF810100),     // Primary accent
    val maroon: Color = Color(0xFF630102),        // Strong accent / surfaces

    // Derived — backwards-compatible property names
    val baseDark: Color = cotton,                 // Main screen background
    val baseBlack: Color = noirBlack,             // Deepest dark

    // Surfaces
    val surfaceCard: Color = Color(0xFFE2DFD2),   // Card surface (warm off-white)
    val surfaceHover: Color = Color(0xFFD8D5C8),  // Hover/pressed surface

    // Accents
    val accentPrimary: Color = cherryRed,          // Primary accent
    val accentSecondary: Color = maroon,           // Secondary accent
    val accentWarm: Color = Color(0xFFA65D03),     // Warm accent for special elements

    // Text — on Cotton background
    val textPrimary: Color = noirBlack,            // Main text
    val textSecondary: Color = Color(0xFF4A4440),  // Secondary text
    val textMuted: Color = Color(0xFF8A8478),      // Muted/hint text
    val textInactive: Color = Color(0xFFB5AFA6),   // Inactive/disabled text

    // Text — on dark/player surfaces
    val textOnDark: Color = Color(0xFFF5F3EB),     // Text on dark backgrounds
    val textOnDarkSecondary: Color = Color(0xFFBDB8AB),

    // Glass — for overlays on dark surfaces (player/lyrics)
    val glassBg: Color = Color(0xB31B1716),        // 70% Noir
    val glassBorder: Color = Color(0x26EDEBDE),    // 15% Cotton
    val glassLight: Color = Color(0x0DEDEBDE),     // 5% Cotton

    // Bottom nav / mini-player surface
    val navSurface: Color = Color(0xFF231E1C),     // Noir + Maroon mix
    val miniPlayerSurface: Color = Color(0xFF2D1E1D), // Slightly warmer dark

    // Player fallback gradient colors
    val playerGradientStart: Color = noirBlack,
    val playerGradientMid: Color = maroon,
    val playerGradientEnd: Color = cherryRed,

    // Semantic
    val success: Color = Color(0xFF2E7D32),        // Green for success
    val error: Color = Color(0xFFC62828),           // Red for errors
    val warning: Color = Color(0xFFE65100),         // Orange for warnings
)

val LocalGratiaColors = staticCompositionLocalOf { GratiaColors() }
