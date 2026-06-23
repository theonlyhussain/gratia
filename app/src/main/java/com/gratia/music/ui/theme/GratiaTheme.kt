package com.gratia.music.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
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

/**
 * Gratia warm premium color scheme.
 * Uses Cotton background with Noir text and Cherry/Maroon accents.
 */
private val GratiaColorScheme = lightColorScheme(
    primary = Color(0xFF810100),        // Cherry Red
    onPrimary = Color(0xFFEDEBDE),      // Cotton
    secondary = Color(0xFF630102),       // Maroon
    background = Color(0xFFEDEBDE),     // Cotton
    surface = Color(0xFFE2DFD2),        // Card surface
    onBackground = Color(0xFF1B1716),   // Noir Black
    onSurface = Color(0xFF1B1716),      // Noir Black
    error = Color(0xFFC62828),
)

private val gratiaColors = GratiaColors()

object GratiaTheme {
    val colors: GratiaColors
        @Composable
        @ReadOnlyComposable
        get() = LocalGratiaColors.current
}

@Composable
fun GratiaTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalGratiaColors provides gratiaColors
    ) {
        MaterialTheme(
            colorScheme = GratiaColorScheme,
            content = content
        )
    }
}
