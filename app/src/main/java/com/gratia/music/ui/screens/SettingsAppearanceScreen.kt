package com.gratia.music.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.gratia.music.data.SettingsDataStore
import com.gratia.music.data.ThemeOption
import com.gratia.music.ui.components.AppleLargeTitleHeader
import com.gratia.music.ui.components.GratiaText
import com.gratia.music.ui.theme.GratiaTheme
import kotlinx.coroutines.launch

@Composable
fun SettingsAppearanceScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsDataStore = remember { SettingsDataStore(context) }
    val themeOption by settingsDataStore.themeOptionFlow.collectAsState(initial = ThemeOption.SYSTEM)
    val oledThemeEnabled by settingsDataStore.oledThemeEnabledFlow.collectAsState(initial = false)
    // Determine if dark mode is actually active (for showing OLED toggle)
    val isSystemDark = isSystemInDarkTheme()
    val isDarkActive = when (themeOption) {
        ThemeOption.DARK -> true
        ThemeOption.SYSTEM -> isSystemDark
        ThemeOption.LIGHT -> false
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(GratiaTheme.colors.background)
            .statusBarsPadding(),
        contentPadding = PaddingValues(bottom = GratiaTheme.spacing.heroLarge)
    ) {
        item {
            AppleLargeTitleHeader(
                title = "Appearance",
                onBack = onNavigateBack
            )
            Spacer(Modifier.height(8.dp))
        }

        // ── THEME ──
        item {
            AppearanceSectionCard {
                ThemeRow(
                    icon = Icons.Default.BrightnessAuto,
                    iconBg = Color(0xFF007AFF),
                    title = "System",
                    isSelected = themeOption == ThemeOption.SYSTEM,
                    onClick = { scope.launch { settingsDataStore.setThemeOption(ThemeOption.SYSTEM) } }
                )
                AppearanceDivider()
                ThemeRow(
                    icon = Icons.Default.LightMode,
                    iconBg = Color(0xFFFF9500),
                    title = "Light",
                    isSelected = themeOption == ThemeOption.LIGHT,
                    onClick = { scope.launch { settingsDataStore.setThemeOption(ThemeOption.LIGHT) } }
                )
                AppearanceDivider()
                ThemeRow(
                    icon = Icons.Default.DarkMode,
                    iconBg = Color(0xFF5856D6),
                    title = "Dark",
                    isSelected = themeOption == ThemeOption.DARK,
                    onClick = { scope.launch { settingsDataStore.setThemeOption(ThemeOption.DARK) } }
                )

                // OLED toggle — visible whenever dark mode is actually active
                AnimatedVisibility(
                    visible = isDarkActive,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    Column {
                        AppearanceDivider()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    scope.launch { settingsDataStore.setOledThemeEnabled(!oledThemeEnabled) }
                                }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(Color.Black)
                                    .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(7.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Contrast,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                GratiaText(
                                    text = "Pure Black",
                                    style = GratiaTheme.typography.body,
                                    color = GratiaTheme.colors.textPrimary
                                )
                                GratiaText(
                                    text = "OLED-optimized true black background",
                                    style = GratiaTheme.typography.caption,
                                    color = GratiaTheme.colors.textSecondary
                                )
                            }

                            Switch(
                                checked = oledThemeEnabled,
                                onCheckedChange = { scope.launch { settingsDataStore.setOledThemeEnabled(it) } },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = GratiaTheme.colors.accent,
                                    uncheckedThumbColor = GratiaTheme.colors.textSecondary,
                                    uncheckedTrackColor = GratiaTheme.colors.surfaceHover,
                                    uncheckedBorderColor = Color.Transparent
                                )
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }


    }
}

// ── Reusable Appearance-page components ──

@Composable
private fun AppearanceSectionCard(
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(GratiaTheme.colors.surface),
        content = content
    )
}

@Composable
private fun ThemeRow(
    icon: ImageVector,
    iconBg: Color,
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(Modifier.width(14.dp))

        GratiaText(
            text = title,
            style = GratiaTheme.typography.body,
            color = GratiaTheme.colors.textPrimary,
            modifier = Modifier.weight(1f)
        )

        if (isSelected) {
            Icon(
                Icons.Default.Check,
                contentDescription = "Selected",
                tint = GratiaTheme.colors.accent,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun AppearanceDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 62.dp),
        thickness = 0.5.dp,
        color = GratiaTheme.colors.textSecondary.copy(alpha = 0.15f)
    )
}
