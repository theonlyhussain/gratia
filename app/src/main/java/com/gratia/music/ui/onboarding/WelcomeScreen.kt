package com.gratia.music.ui.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gratia.music.data.SettingsDataStore
import com.gratia.music.data.ThemeOption
import com.gratia.music.ui.theme.GratiaTheme
import com.gratia.music.ui.theme.Inter
import com.gratia.music.ui.theme.SpaceGrotesk
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Welcome screen — the first thing a new user sees.
 *
 * Shows the Gratia brand, a brief description, and a theme picker
 * so the user can immediately see their preferred look.
 * "Get Started" transitions to the permission screen.
 */
@Composable
fun WelcomeScreen(
    selectedTheme: ThemeOption,
    settingsDataStore: SettingsDataStore,
    onContinue: () -> Unit
) {
    val scope = rememberCoroutineScope()

    // Staggered entrance animation
    var showTitle by remember { mutableStateOf(false) }
    var showSubtitle by remember { mutableStateOf(false) }
    var showThemePicker by remember { mutableStateOf(false) }
    var showButton by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(200)
        showTitle = true
        delay(300)
        showSubtitle = true
        delay(200)
        showThemePicker = true
        delay(200)
        showButton = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(GratiaTheme.colors.background)
            .systemBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(Modifier.weight(1f))

            // --- Title ---
            AnimatedVisibility(
                visible = showTitle,
                enter = fadeIn(tween(600)) + slideInVertically(
                    initialOffsetY = { 40 },
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                )
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Welcome to",
                        fontFamily = Inter,
                        fontWeight = FontWeight.Normal,
                        fontSize = 18.sp,
                        color = GratiaTheme.colors.textSecondary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Gratia",
                        fontFamily = SpaceGrotesk,
                        fontWeight = FontWeight.Bold,
                        fontSize = 48.sp,
                        color = GratiaTheme.colors.textPrimary,
                        textAlign = TextAlign.Center,
                        letterSpacing = (-1.5).sp
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // --- Subtitle ---
            AnimatedVisibility(
                visible = showSubtitle,
                enter = fadeIn(tween(500)) + slideInVertically(
                    initialOffsetY = { 30 },
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                )
            ) {
                Text(
                    text = "Your premium local music player.\nBeautiful, fast, and completely offline.",
                    fontFamily = Inter,
                    fontWeight = FontWeight.Normal,
                    fontSize = 15.sp,
                    color = GratiaTheme.colors.textSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            Spacer(Modifier.height(48.dp))

            // --- Theme Picker ---
            AnimatedVisibility(
                visible = showThemePicker,
                enter = fadeIn(tween(400)) + slideInVertically(
                    initialOffsetY = { 30 },
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                )
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Choose your look",
                        fontFamily = SpaceGrotesk,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        color = GratiaTheme.colors.textPrimary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(16.dp))

                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            ThemeCard(
                                label = "System",
                                icon = Icons.Default.Settings,
                                isSelected = selectedTheme == ThemeOption.SYSTEM,
                                onClick = {
                                    scope.launch { settingsDataStore.setThemeOption(ThemeOption.SYSTEM) }
                                },
                                modifier = Modifier.weight(1f)
                            )
                            ThemeCard(
                                label = "Light",
                                icon = Icons.Default.LightMode,
                                isSelected = selectedTheme == ThemeOption.LIGHT,
                                onClick = {
                                    scope.launch { settingsDataStore.setThemeOption(ThemeOption.LIGHT) }
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            ThemeCard(
                                label = "Dark",
                                icon = Icons.Default.DarkMode,
                                isSelected = selectedTheme == ThemeOption.DARK,
                                onClick = {
                                    scope.launch { settingsDataStore.setThemeOption(ThemeOption.DARK) }
                                },
                                modifier = Modifier.weight(1f)
                            )
                            ThemeCard(
                                label = "AMOLED",
                                icon = Icons.Default.DarkMode, // Both use DarkMode icon for now
                                isSelected = selectedTheme == ThemeOption.AMOLED,
                                onClick = {
                                    scope.launch { settingsDataStore.setThemeOption(ThemeOption.AMOLED) }
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            // --- Get Started Button ---
            AnimatedVisibility(
                visible = showButton,
                enter = fadeIn(tween(400)) + slideInVertically(
                    initialOffsetY = { 40 },
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                )
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Button(
                        onClick = onContinue,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GratiaTheme.colors.accent,
                            contentColor = GratiaTheme.colors.background
                        )
                    ) {
                        Text(
                            text = "Get Started",
                            fontFamily = Inter,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp
                        )
                    }

                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }
}

/**
 * Selectable theme card for the welcome screen.
 */
@Composable
private fun ThemeCard(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.0f else 0.95f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "themeCardScale"
    )

    Column(
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isSelected) GratiaTheme.colors.accent.copy(alpha = 0.15f)
                else GratiaTheme.colors.surface
            )
            .border(
                width = if (isSelected) 2.dp else 0.5.dp,
                color = if (isSelected) GratiaTheme.colors.accent
                else GratiaTheme.colors.textSecondary.copy(alpha = 0.2f),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isSelected) GratiaTheme.colors.accent else GratiaTheme.colors.textSecondary,
            modifier = Modifier.size(28.dp)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = label,
            fontFamily = Inter,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            fontSize = 13.sp,
            color = if (isSelected) GratiaTheme.colors.accent else GratiaTheme.colors.textSecondary
        )
    }
}
