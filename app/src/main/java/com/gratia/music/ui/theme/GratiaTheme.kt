package com.gratia.music.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont

val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = com.gratia.music.R.array.com_google_android_gms_fonts_certs
)

val SpaceGrotesk = FontFamily(
    Font(googleFont = GoogleFont("Space Grotesk"), fontProvider = provider, weight = FontWeight.Normal),
    Font(googleFont = GoogleFont("Space Grotesk"), fontProvider = provider, weight = FontWeight.Medium),
    Font(googleFont = GoogleFont("Space Grotesk"), fontProvider = provider, weight = FontWeight.SemiBold),
    Font(googleFont = GoogleFont("Space Grotesk"), fontProvider = provider, weight = FontWeight.Bold),
    Font(googleFont = GoogleFont("Space Grotesk"), fontProvider = provider, weight = FontWeight.ExtraBold),
)

val Inter = FontFamily(
    Font(googleFont = GoogleFont("Inter"), fontProvider = provider, weight = FontWeight.Light),
    Font(googleFont = GoogleFont("Inter"), fontProvider = provider, weight = FontWeight.Normal),
    Font(googleFont = GoogleFont("Inter"), fontProvider = provider, weight = FontWeight.Medium),
    Font(googleFont = GoogleFont("Inter"), fontProvider = provider, weight = FontWeight.SemiBold),
)

/** Monospaced font for time labels, progress indicators, and metadata. */
val JetBrainsMono = FontFamily(
    Font(googleFont = GoogleFont("JetBrains Mono"), fontProvider = provider, weight = FontWeight.Normal),
    Font(googleFont = GoogleFont("JetBrains Mono"), fontProvider = provider, weight = FontWeight.Medium),
)

object GratiaTheme {
    val colors: GratiaColors
        @Composable
        @ReadOnlyComposable
        get() = LocalGratiaColors.current

    val typography: GratiaTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalGratiaTypography.current

    val spacing: GratiaSpacing
        @Composable
        @ReadOnlyComposable
        get() = LocalGratiaSpacing.current

    val shapes: GratiaShapes
        @Composable
        @ReadOnlyComposable
        get() = LocalGratiaShapes.current

    val motion: GratiaMotion
        @Composable
        @ReadOnlyComposable
        get() = LocalGratiaMotion.current

    val elevation: GratiaElevation
        @Composable
        @ReadOnlyComposable
        get() = LocalGratiaElevation.current

    val glass: GratiaGlass
        @Composable
        @ReadOnlyComposable
        get() = LocalGratiaGlass.current

    val icons: GratiaIcons
        @Composable
        @ReadOnlyComposable
        get() = LocalGratiaIcons.current

    val haptics: GratiaHaptics
        @Composable
        @ReadOnlyComposable
        get() = LocalGratiaHaptics.current
}

@Composable
fun GratiaTheme(
    themeOption: com.gratia.music.data.ThemeOption = com.gratia.music.data.ThemeOption.SYSTEM,
    accentOption: com.gratia.music.data.AccentColorOption = com.gratia.music.data.AccentColorOption.DEFAULT,
    isOledThemeEnabled: Boolean = false,
    content: @Composable () -> Unit
) {
    val isSystemDark = isSystemInDarkTheme()
    val isDark = when (themeOption) {
        com.gratia.music.data.ThemeOption.LIGHT -> false
        com.gratia.music.data.ThemeOption.DARK -> true
        com.gratia.music.data.ThemeOption.SYSTEM -> isSystemDark
    }

    var targetColors = when (themeOption) {
        com.gratia.music.data.ThemeOption.LIGHT -> lightGratiaColors
        com.gratia.music.data.ThemeOption.DARK -> darkGratiaColors
        com.gratia.music.data.ThemeOption.SYSTEM -> if (isSystemDark) darkGratiaColors else lightGratiaColors
    }

    if (isDark && isOledThemeEnabled) {
        targetColors = targetColors.copy(
            background = androidx.compose.ui.graphics.Color.Black,
            surface = androidx.compose.ui.graphics.Color.Black,
            surfaceHover = androidx.compose.ui.graphics.Color(0xFF0A0A0A)
        )
    }

    // Apply dynamic global accent
    if (accentOption != com.gratia.music.data.AccentColorOption.DEFAULT) {
        val accentColor = androidx.compose.ui.graphics.Color(accentOption.hex)
        targetColors = targetColors.copy(
            accent = accentColor,
            accentGlow = accentColor.copy(alpha = if (isDark) 0.3f else 0.2f),
            playerGlow = accentColor.copy(alpha = if (isDark) 0.25f else 0.1f),
            progressActive = accentColor
        )
    }

    val background by androidx.compose.animation.animateColorAsState(targetColors.background, label = "background")
    val surface by androidx.compose.animation.animateColorAsState(targetColors.surface, label = "surface")
    val surfaceHover by androidx.compose.animation.animateColorAsState(targetColors.surfaceHover, label = "surfaceHover")
    val accent by androidx.compose.animation.animateColorAsState(targetColors.accent, label = "accent")
    val accentGlow by androidx.compose.animation.animateColorAsState(targetColors.accentGlow, label = "accentGlow")
    val textPrimary by androidx.compose.animation.animateColorAsState(targetColors.textPrimary, label = "textPrimary")
    val textSecondary by androidx.compose.animation.animateColorAsState(targetColors.textSecondary, label = "textSecondary")
    val glassBg by androidx.compose.animation.animateColorAsState(targetColors.glassBg, label = "glassBg")
    val glassBorder by androidx.compose.animation.animateColorAsState(targetColors.glassBorder, label = "glassBorder")
    val error by androidx.compose.animation.animateColorAsState(targetColors.error, label = "error")
    val success by androidx.compose.animation.animateColorAsState(targetColors.success, label = "success")
    val warning by androidx.compose.animation.animateColorAsState(targetColors.warning, label = "warning")
    val accentWarm by androidx.compose.animation.animateColorAsState(targetColors.accentWarm, label = "accentWarm")
    val playerGlow by androidx.compose.animation.animateColorAsState(targetColors.playerGlow, label = "playerGlow")
    val progressTrack by androidx.compose.animation.animateColorAsState(targetColors.progressTrack, label = "progressTrack")
    val progressActive by androidx.compose.animation.animateColorAsState(targetColors.progressActive, label = "progressActive")
    val controlMuted by androidx.compose.animation.animateColorAsState(targetColors.controlMuted, label = "controlMuted")
    val playerSurface by androidx.compose.animation.animateColorAsState(targetColors.playerSurface, label = "playerSurface")

    val animatedColors = GratiaColors(
        isDark = isDark,
        background = background,
        surface = surface,
        surfaceHover = surfaceHover,
        accent = accent,
        accentGlow = accentGlow,
        textPrimary = textPrimary,
        textSecondary = textSecondary,
        glassBg = glassBg,
        glassBorder = glassBorder,
        error = error,
        success = success,
        warning = warning,
        accentWarm = accentWarm,
        playerGlow = playerGlow,
        progressTrack = progressTrack,
        progressActive = progressActive,
        controlMuted = controlMuted,
        playerSurface = playerSurface
    )

    CompositionLocalProvider(
        LocalGratiaColors provides animatedColors,
        LocalGratiaTypography provides GratiaTypography(),
        LocalGratiaSpacing provides GratiaSpacing(),
        LocalGratiaShapes provides GratiaShapes(),
        LocalGratiaMotion provides GratiaMotion(),
        LocalGratiaElevation provides GratiaElevation(),
        LocalGratiaGlass provides GratiaGlass(),
        LocalGratiaIcons provides GratiaIcons(),
        LocalGratiaHaptics provides GratiaHaptics()
    ) {
        // Inject GratiaColors into the standard MaterialTheme so native M3 components (like FAB, Switch, Slider) 
        // automatically inherit the dynamic global accent and background colors without manual overrides.
        val m3ColorScheme = if (isDark) {
            androidx.compose.material3.darkColorScheme(
                primary = animatedColors.accent,
                primaryContainer = animatedColors.accentGlow,
                onPrimary = androidx.compose.ui.graphics.Color.White,
                background = animatedColors.background,
                surface = animatedColors.surface,
                onBackground = animatedColors.textPrimary,
                onSurface = animatedColors.textPrimary,
                onSurfaceVariant = animatedColors.textSecondary,
                surfaceVariant = animatedColors.surfaceHover,
                error = animatedColors.error
            )
        } else {
            androidx.compose.material3.lightColorScheme(
                primary = animatedColors.accent,
                primaryContainer = animatedColors.accentGlow,
                onPrimary = androidx.compose.ui.graphics.Color.White,
                background = animatedColors.background,
                surface = animatedColors.surface,
                onBackground = animatedColors.textPrimary,
                onSurface = animatedColors.textPrimary,
                onSurfaceVariant = animatedColors.textSecondary,
                surfaceVariant = animatedColors.surfaceHover,
                error = animatedColors.error
            )
        }

        MaterialTheme(
            colorScheme = m3ColorScheme,
            content = content
        )
    }
}