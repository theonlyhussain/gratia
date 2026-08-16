package com.gratia.music.ui.screens

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gratia.music.GratiaApp
import com.gratia.music.data.repository.SongRepository
import com.gratia.music.data.scan.MediaStoreScanner
import com.gratia.music.ui.components.AppleLargeTitleHeader
import com.gratia.music.ui.components.AppleListRow
import com.gratia.music.ui.components.AppleSectionHeader
import com.gratia.music.ui.components.GratiaText
import com.gratia.music.ui.components.GratiaButton
import com.gratia.music.ui.theme.GratiaTheme
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToSmartUpdate: () -> Unit,
    onNavigateToEqualizer: () -> Unit
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

    val settingsDataStore = remember { com.gratia.music.data.SettingsDataStore(context) }
    val themeOption by settingsDataStore.themeOptionFlow.collectAsState(initial = com.gratia.music.data.ThemeOption.SYSTEM)
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
                title = "Settings",
                onBack = onNavigateBack
            )
        }

        // Smart Update Section
        item {
            AppleSectionHeader(title = "Updates")
            
            val smartUpdateEnabled by settingsDataStore.smartUpdateEnabledFlow.collectAsState(initial = false)
            val updateState by GratiaApp.instance.updateManager.state.collectAsState()
            
            AppleListRow(
                title = "Smart Update",
                subtitle = if (smartUpdateEnabled) "Enabled" else "Disabled",
                onClick = onNavigateToSmartUpdate,
                showDivider = false,
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
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

        // Appearance
        item {
            AppleSectionHeader(title = "Appearance")
            AppleListRow(
                title = "System",
                onClick = { 
                    scope.launch { 
                        settingsDataStore.setThemeOption(com.gratia.music.data.ThemeOption.SYSTEM) 
                        android.widget.Toast.makeText(context, "System theme selected", android.widget.Toast.LENGTH_SHORT).show()
                    } 
                },
                trailingContent = {
                    if (themeOption == com.gratia.music.data.ThemeOption.SYSTEM) {
                        GratiaText(text = "Selected", style = GratiaTheme.typography.caption, color = GratiaTheme.colors.accent)
                    }
                }
            )
            AppleListRow(
                title = "Light",
                onClick = { 
                    scope.launch { 
                        settingsDataStore.setThemeOption(com.gratia.music.data.ThemeOption.LIGHT) 
                        android.widget.Toast.makeText(context, "Light theme selected", android.widget.Toast.LENGTH_SHORT).show()
                    } 
                },
                trailingContent = {
                    if (themeOption == com.gratia.music.data.ThemeOption.LIGHT) {
                        GratiaText(text = "Selected", style = GratiaTheme.typography.caption, color = GratiaTheme.colors.accent)
                    }
                }
            )
            AppleListRow(
                title = "Dark",
                onClick = { 
                    scope.launch { 
                        settingsDataStore.setThemeOption(com.gratia.music.data.ThemeOption.DARK) 
                        android.widget.Toast.makeText(context, "Dark theme selected", android.widget.Toast.LENGTH_SHORT).show()
                    } 
                },
                showDivider = themeOption == com.gratia.music.data.ThemeOption.DARK, // Show divider if OLED option will be below it
                trailingContent = {
                    if (themeOption == com.gratia.music.data.ThemeOption.DARK) {
                        GratiaText(text = "Selected", style = GratiaTheme.typography.caption, color = GratiaTheme.colors.accent)
                    }
                }
            )
            
            androidx.compose.animation.AnimatedVisibility(
                visible = themeOption == com.gratia.music.data.ThemeOption.DARK
            ) {
                AppleListRow(
                    title = "OLED Theme",
                    subtitle = "Pure black theme for OLED displays",
                    onClick = { 
                        scope.launch { 
                            val newState = !oledThemeEnabled
                            settingsDataStore.setOledThemeEnabled(newState) 
                            android.widget.Toast.makeText(context, if (newState) "OLED Theme enabled" else "OLED Theme disabled", android.widget.Toast.LENGTH_SHORT).show()
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

        // Audio
        item {
            AppleSectionHeader(title = "Audio")
            AppleListRow(
                title = "Equalizer",
                onClick = onNavigateToEqualizer,
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

        // Library Sync
        item {
            AppleSectionHeader(title = "Library Sync")
            Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                GratiaText(
                    text = "Scan your device storage to automatically import offline audio files.",
                    style = GratiaTheme.typography.body,
                    color = GratiaTheme.colors.textSecondary
                )
                Spacer(Modifier.height(16.dp))

                if (scanning) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = GratiaTheme.colors.accent
                        )
                        Spacer(Modifier.width(16.dp))
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
                    Spacer(Modifier.height(8.dp))
                    GratiaText(
                        text = scanResult,
                        style = GratiaTheme.typography.caption,
                        color = if (scanResult.contains("complete")) GratiaTheme.colors.textSecondary else GratiaTheme.colors.accent
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
