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

object GratiaTheme {
    val colors: GratiaColors
        @Composable
        @ReadOnlyComposable
        get() = LocalGratiaColors.current
}

@Composable
fun GratiaTheme(
    isDark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val targetColors = if (isDark) darkGratiaColors else lightGratiaColors

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
        accentWarm = accentWarm
    )

    CompositionLocalProvider(
        LocalGratiaColors provides animatedColors
    ) {
        // We still provide MaterialTheme for standard components that rely on it
        MaterialTheme(
            colorScheme = if (isDark) androidx.compose.material3.darkColorScheme() else androidx.compose.material3.lightColorScheme(),
            content = content
        )
    }
}