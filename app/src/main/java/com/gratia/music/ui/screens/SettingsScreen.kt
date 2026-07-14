package com.gratia.music.ui.screens

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gratia.music.GratiaApp
import com.gratia.music.data.repository.SongRepository
import com.gratia.music.data.scan.MediaStoreScanner
import com.gratia.music.ui.components.GratiaText
import com.gratia.music.ui.components.GratiaIcon
import com.gratia.music.ui.components.GratiaIconButton
import com.gratia.music.ui.components.GratiaCard
import com.gratia.music.ui.components.GratiaCardStatic
import com.gratia.music.ui.components.GratiaButton
import com.gratia.music.ui.components.clickableWithScale
import com.gratia.music.ui.theme.GratiaTheme
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToLicenses: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val songRepo = remember { SongRepository(GratiaApp.instance.database.songDao()) }

    var scanning by remember { mutableStateOf(false) }
    var scanResult by remember { mutableStateOf("") }

    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        android.Manifest.permission.READ_MEDIA_AUDIO
    } else {
        android.Manifest.permission.READ_EXTERNAL_STORAGE
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            scope.launch {
                scanning = true
                scanResult = ""
                try {
                    val count = MediaStoreScanner.scanLocalMusic(context, songRepo)
                    scanResult = "Sync complete! Found $count new songs."
                } catch (e: Exception) {
                    scanResult = "Sync failed. Try again."
                } finally {
                    scanning = false
                }
            }
        } else {
            scanResult = "Permission denied. Cannot scan media files."
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GratiaTheme.colors.background)
            .statusBarsPadding()
    ) {
        // Header
        Row(
            modifier = Modifier.padding(horizontal = GratiaTheme.spacing.large, vertical = GratiaTheme.spacing.large),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GratiaTheme.spacing.mediumSmall)
        ) {
            GratiaIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                onClick = onNavigateBack,
                modifier = Modifier
                    .size(36.dp)
                    .border(1.dp, GratiaTheme.colors.glassBorder, CircleShape),
                tint = GratiaTheme.colors.textSecondary
            )
            GratiaText(
                text = "Settings",
                style = GratiaTheme.typography.title,
                color = GratiaTheme.colors.textPrimary
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = GratiaTheme.spacing.large)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(GratiaTheme.spacing.medium)
        ) {
            // Appearance Card
            val settingsDataStore = remember { com.gratia.music.data.SettingsDataStore(context) }
            val themeOption by settingsDataStore.themeOptionFlow.collectAsState(initial = com.gratia.music.data.ThemeOption.SYSTEM)

            GratiaCardStatic(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(GratiaTheme.spacing.large)) {
                    GratiaText(
                        text = "Appearance",
                        style = GratiaTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
                        color = GratiaTheme.colors.textPrimary
                    )
                    Spacer(Modifier.height(GratiaTheme.spacing.medium))
                    GratiaText(
                        text = "Theme",
                        style = GratiaTheme.typography.caption,
                        color = GratiaTheme.colors.textSecondary
                    )
                    Spacer(Modifier.height(GratiaTheme.spacing.small))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(GratiaTheme.spacing.small)
                    ) {
                        ThemeOptionButton(
                            text = "System",
                            selected = themeOption == com.gratia.music.data.ThemeOption.SYSTEM,
                            onClick = { scope.launch { settingsDataStore.setThemeOption(com.gratia.music.data.ThemeOption.SYSTEM) } },
                            modifier = Modifier.weight(1f)
                        )
                        ThemeOptionButton(
                            text = "Light",
                            selected = themeOption == com.gratia.music.data.ThemeOption.LIGHT,
                            onClick = { scope.launch { settingsDataStore.setThemeOption(com.gratia.music.data.ThemeOption.LIGHT) } },
                            modifier = Modifier.weight(1f)
                        )
                        ThemeOptionButton(
                            text = "Dark",
                            selected = themeOption == com.gratia.music.data.ThemeOption.DARK,
                            onClick = { scope.launch { settingsDataStore.setThemeOption(com.gratia.music.data.ThemeOption.DARK) } },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Library Sync Card
            GratiaCardStatic(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(GratiaTheme.spacing.large)) {
                    GratiaText(
                        text = "Library Sync",
                        style = GratiaTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
                        color = GratiaTheme.colors.textPrimary
                    )
                    Spacer(Modifier.height(GratiaTheme.spacing.small))
                    GratiaText(
                        text = "Scan your device storage to automatically import offline audio files.",
                        style = GratiaTheme.typography.caption,
                        color = GratiaTheme.colors.textSecondary
                    )
                    Spacer(Modifier.height(GratiaTheme.spacing.medium))

                    if (scanning) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = GratiaTheme.colors.accent
                            )
                            Spacer(Modifier.width(GratiaTheme.spacing.small))
                            GratiaText(
                                text = "Scanning local storage...",
                                style = GratiaTheme.typography.caption,
                                color = GratiaTheme.colors.accent
                            )
                        }
                    } else {
                        GratiaButton(
                            text = "Sync Now",
                            onClick = {
                                val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    androidx.core.content.ContextCompat.checkSelfPermission(
                                        context,
                                        android.Manifest.permission.READ_MEDIA_AUDIO
                                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                } else {
                                    androidx.core.content.ContextCompat.checkSelfPermission(
                                        context,
                                        android.Manifest.permission.READ_EXTERNAL_STORAGE
                                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                }

                                if (hasPermission) {
                                    scope.launch {
                                        scanning = true
                                        scanResult = ""
                                        try {
                                            val count = MediaStoreScanner.scanLocalMusic(context, songRepo)
                                            scanResult = "Sync complete! Found $count new songs."
                                        } catch (e: Exception) {
                                            scanResult = "Sync failed. Try again."
                                        } finally {
                                            scanning = false
                                        }
                                    }
                                } else {
                                    permissionLauncher.launch(permission)
                                }
                            }
                        )
                    }

                    if (scanResult.isNotEmpty()) {
                        Spacer(Modifier.height(GratiaTheme.spacing.small))
                        GratiaText(
                            text = scanResult,
                            style = GratiaTheme.typography.caption,
                            color = if (scanResult.contains("complete")) GratiaTheme.colors.textSecondary else GratiaTheme.colors.accent
                        )
                    }
                }
            }

            // Legal & More Card
            GratiaCardStatic(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    SettingsRow(
                        title = "GitHub",
                        subtitle = "Open source repository",
                        onClick = {
                            context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/theonlyhussain/gratia")))
                        }
                    )
                    HorizontalDivider(color = GratiaTheme.colors.glassBorder)
                    SettingsRow(
                        title = "Privacy Policy",
                        subtitle = "Local-first data policy",
                        onClick = { 
                            context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/theonlyhussain/gratia/blob/main/PRIVACY.md")))
                        }
                    )
                    HorizontalDivider(color = GratiaTheme.colors.glassBorder)
                    SettingsRow(
                        title = "Changelog",
                        subtitle = "What's new in v2.1.1",
                        onClick = { 
                            context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/theonlyhussain/gratia/releases")))
                        }
                    )
                    HorizontalDivider(color = GratiaTheme.colors.glassBorder)
                    SettingsRow(
                        title = "Licenses",
                        subtitle = "Open-source libraries",
                        onClick = onNavigateToLicenses
                    )
                    HorizontalDivider(color = GratiaTheme.colors.glassBorder)
                    SettingsRow(
                        title = "About",
                        subtitle = "App info, developer, and device stats",
                        onClick = onNavigateToAbout
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickableWithScale(onClick = onClick)
            .padding(GratiaTheme.spacing.large),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            GratiaText(
                text = title,
                style = GratiaTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
                color = GratiaTheme.colors.textPrimary
            )
            if (subtitle.isNotEmpty()) {
                GratiaText(
                    text = subtitle,
                    style = GratiaTheme.typography.caption.copy(fontSize = 11.sp),
                    color = GratiaTheme.colors.textSecondary
                )
            }
        }
        GratiaIcon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = "Expand item",
            tint = GratiaTheme.colors.textSecondary,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun ThemeOptionButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(40.dp)
            .clip(GratiaTheme.shapes.medium)
            .background(if (selected) GratiaTheme.colors.accent.copy(alpha = 0.15f) else GratiaTheme.colors.background)
            .border(
                1.dp,
                if (selected) GratiaTheme.colors.accent else GratiaTheme.colors.surfaceHover,
                GratiaTheme.shapes.medium
            )
            .clickableWithScale(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        GratiaText(
            text = text,
            style = GratiaTheme.typography.body.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium),
            color = if (selected) GratiaTheme.colors.accent else GratiaTheme.colors.textSecondary
        )
    }
}
