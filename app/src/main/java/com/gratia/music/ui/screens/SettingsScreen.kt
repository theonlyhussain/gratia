package com.gratia.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.gratia.music.GratiaApp
import com.gratia.music.data.SettingsDataStore
import com.gratia.music.ui.components.AppleLargeTitleHeader
import com.gratia.music.ui.components.GratiaText
import com.gratia.music.ui.components.clickableWithScale
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
            Spacer(Modifier.height(8.dp))
        }

        // ── GENERAL ──
        item {
            SettingsSectionCard {
                SettingsRow(
                    icon = Icons.Default.LibraryMusic,
                    iconBg = Color(0xFF5856D6),
                    title = "Library",
                    subtitle = "Local files & sync",
                    onClick = onNavigateToLibrary
                )
            }
            Spacer(Modifier.height(20.dp))
        }

        // ── LOOK & FEEL ──
        item {
            SettingsSectionCard {
                SettingsRow(
                    icon = Icons.Default.Palette,
                    iconBg = Color(0xFFFF9500),
                    title = "Appearance",
                    subtitle = "Theme, accent color, OLED",
                    onClick = onNavigateToAppearance
                )
                SettingsDivider()
                SettingsRow(
                    icon = Icons.Default.GraphicEq,
                    iconBg = Color(0xFFFF2D55),
                    title = "Equalizer",
                    subtitle = "Audio effects & frequencies",
                    onClick = onNavigateToEqualizer
                )
            }
            Spacer(Modifier.height(20.dp))
        }

        // ── SYSTEM ──
        item {
            SettingsSectionCard {
                SettingsRow(
                    icon = Icons.Default.Update,
                    iconBg = Color(0xFF34C759),
                    title = "Smart Update",
                    subtitle = if (smartUpdateEnabled) "Enabled" else "Disabled",
                    onClick = onNavigateToSmartUpdate,
                    badge = if (!smartUpdateEnabled) "Recommended" else null,
                    showDot = updateState is com.gratia.music.updater.UpdateState.UpdateAvailable ||
                              updateState is com.gratia.music.updater.UpdateState.ReadyToInstall
                )
            }
            Spacer(Modifier.height(20.dp))
        }

        // ── ABOUT ──
        item {
            SettingsSectionCard {
                SettingsRow(
                    icon = Icons.Default.Info,
                    iconBg = Color(0xFF007AFF),
                    title = "About Gratia",
                    subtitle = "Version ${getAppVersion(context)}",
                    onClick = { },
                    showChevron = false
                )
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

// ── Reusable Apple-style settings components ──

@Composable
private fun SettingsSectionCard(
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(GratiaTheme.colors.surface),
        content = content
    )
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    iconBg: Color,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
    badge: String? = null,
    showDot: Boolean = false,
    showChevron: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickableWithScale(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Colored icon container
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

        Column(modifier = Modifier.weight(1f)) {
            GratiaText(
                text = title,
                style = GratiaTheme.typography.body,
                color = GratiaTheme.colors.textPrimary
            )
            if (subtitle != null) {
                GratiaText(
                    text = subtitle,
                    style = GratiaTheme.typography.caption,
                    color = GratiaTheme.colors.textSecondary
                )
            }
        }

        if (badge != null) {
            GratiaText(
                text = badge,
                style = GratiaTheme.typography.caption,
                color = GratiaTheme.colors.accent
            )
            Spacer(Modifier.width(8.dp))
        }

        if (showDot) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(GratiaTheme.colors.error)
            )
            Spacer(Modifier.width(8.dp))
        }

        if (showChevron) {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = GratiaTheme.colors.textSecondary.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 62.dp),
        thickness = 0.5.dp,
        color = GratiaTheme.colors.textSecondary.copy(alpha = 0.15f)
    )
}

private fun getAppVersion(context: android.content.Context): String {
    return try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"
    } catch (_: Exception) {
        "1.0"
    }
}
