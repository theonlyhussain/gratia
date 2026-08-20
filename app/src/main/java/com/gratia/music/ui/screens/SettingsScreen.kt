package com.gratia.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.gratia.music.GratiaApp
import com.gratia.music.data.SettingsDataStore
import com.gratia.music.ui.components.AppleLargeTitleHeader
import com.gratia.music.ui.components.AppleListRow
import com.gratia.music.ui.components.AppleSectionHeader
import com.gratia.music.ui.components.GratiaText
import com.gratia.music.ui.theme.GratiaTheme

@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToSmartUpdate: () -> Unit,
    onNavigateToEqualizer: () -> Unit,
    onNavigateToAppearance: () -> Unit,
    onNavigateToLibrary: () -> Unit
) {
    val context = LocalContext.current
    val settingsDataStore = remember { SettingsDataStore(context) }
    val smartUpdateEnabled by settingsDataStore.smartUpdateEnabledFlow.collectAsState(initial = false)
    val updateState by GratiaApp.instance.updateManager.state.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(GratiaTheme.colors.background)
            .statusBarsPadding(),
        contentPadding = PaddingValues(bottom = GratiaTheme.spacing.heroLarge)
    ) {
        item {
            AppleLargeTitleHeader(
                title = "Settings",
                onBack = onNavigateBack
            )
            Spacer(Modifier.height(16.dp))
        }

        // Top prominent card for Library Settings (mimicking reference)
        item {
            AppleSectionHeader(title = "General")
            AppleListRow(
                title = "Library Settings",
                subtitle = "Manage local audio files and sync.",
                onClick = onNavigateToLibrary,
                leadingContent = {
                    Icon(
                        imageVector = Icons.Default.LibraryMusic,
                        contentDescription = null,
                        tint = GratiaTheme.colors.textSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                },
                showDivider = false,
                trailingContent = {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Expand",
                        tint = GratiaTheme.colors.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            )
            Spacer(Modifier.height(24.dp))
        }

        item {
            AppleSectionHeader(title = "Look & Feel")
            AppleListRow(
                title = "Appearance",
                subtitle = "Theme, OLED mode",
                onClick = onNavigateToAppearance,
                leadingContent = {
                    Icon(
                        imageVector = Icons.Default.Palette,
                        contentDescription = null,
                        tint = GratiaTheme.colors.textSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                },
                showDivider = false,
                trailingContent = {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Expand",
                        tint = GratiaTheme.colors.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            )
            Spacer(Modifier.height(24.dp))
        }

        item {
            AppleSectionHeader(title = "Audio")
            AppleListRow(
                title = "Equalizer",
                subtitle = "Adjust frequencies and audio effects",
                onClick = onNavigateToEqualizer,
                leadingContent = {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = null,
                        tint = GratiaTheme.colors.textSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                },
                showDivider = false,
                trailingContent = {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Expand",
                        tint = GratiaTheme.colors.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            )
            Spacer(Modifier.height(24.dp))
        }

        item {
            AppleSectionHeader(title = "System")
            AppleListRow(
                title = "Smart Update",
                subtitle = if (smartUpdateEnabled) "Enabled" else "Disabled",
                onClick = onNavigateToSmartUpdate,
                leadingContent = {
                    Icon(
                        imageVector = Icons.Default.Update,
                        contentDescription = null,
                        tint = GratiaTheme.colors.textSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                },
                showDivider = false,
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!smartUpdateEnabled) {
                            GratiaText(
                                text = "Recommended",
                                style = GratiaTheme.typography.caption,
                                color = GratiaTheme.colors.accent
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        if (updateState is com.gratia.music.updater.UpdateState.UpdateAvailable || updateState is com.gratia.music.updater.UpdateState.ReadyToInstall) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(GratiaTheme.colors.error)
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Expand",
                            tint = GratiaTheme.colors.textSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}
