package com.gratia.music.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.gratia.music.data.SettingsDataStore
import com.gratia.music.data.ThemeOption
import com.gratia.music.ui.components.AppleLargeTitleHeader
import com.gratia.music.ui.components.AppleListRow
import com.gratia.music.ui.components.AppleSectionHeader
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
            Spacer(Modifier.height(16.dp))
        }

        item {
            AppleSectionHeader(title = "Theme")
            AppleListRow(
                title = "System",
                onClick = {
                    scope.launch {
                        settingsDataStore.setThemeOption(ThemeOption.SYSTEM)
                    }
                },
                trailingContent = {
                    if (themeOption == ThemeOption.SYSTEM) {
                        GratiaText(text = "Selected", style = GratiaTheme.typography.caption, color = GratiaTheme.colors.accent)
                    }
                }
            )
            AppleListRow(
                title = "Light",
                onClick = {
                    scope.launch {
                        settingsDataStore.setThemeOption(ThemeOption.LIGHT)
                    }
                },
                trailingContent = {
                    if (themeOption == ThemeOption.LIGHT) {
                        GratiaText(text = "Selected", style = GratiaTheme.typography.caption, color = GratiaTheme.colors.accent)
                    }
                }
            )
            AppleListRow(
                title = "Dark",
                onClick = {
                    scope.launch {
                        settingsDataStore.setThemeOption(ThemeOption.DARK)
                    }
                },
                showDivider = themeOption == ThemeOption.DARK, // Show divider if OLED option will be below it
                trailingContent = {
                    if (themeOption == ThemeOption.DARK) {
                        GratiaText(text = "Selected", style = GratiaTheme.typography.caption, color = GratiaTheme.colors.accent)
                    }
                }
            )

            AnimatedVisibility(
                visible = themeOption == ThemeOption.DARK
            ) {
                AppleListRow(
                    title = "OLED Theme",
                    subtitle = "Pure black theme for OLED displays",
                    onClick = {
                        scope.launch {
                            val newState = !oledThemeEnabled
                            settingsDataStore.setOledThemeEnabled(newState)
                        }
                    },
                    showDivider = false,
                    trailingContent = {
                        Switch(
                            checked = oledThemeEnabled,
                            onCheckedChange = { scope.launch { settingsDataStore.setOledThemeEnabled(it) } },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = GratiaTheme.colors.surface,
                                checkedTrackColor = GratiaTheme.colors.accent,
                                uncheckedThumbColor = GratiaTheme.colors.textSecondary,
                                uncheckedTrackColor = GratiaTheme.colors.surfaceHover,
                                uncheckedBorderColor = androidx.compose.ui.graphics.Color.Transparent
                            )
                        )
                    }
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
