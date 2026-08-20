package com.gratia.music.ui.screens

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.gratia.music.GratiaApp
import com.gratia.music.data.repository.SongRepository
import com.gratia.music.data.scan.MediaStoreScanner
import com.gratia.music.ui.components.AppleLargeTitleHeader
import com.gratia.music.ui.components.AppleSectionHeader
import com.gratia.music.ui.components.GratiaText
import com.gratia.music.ui.components.GratiaButton
import com.gratia.music.ui.theme.GratiaTheme
import kotlinx.coroutines.launch

@Composable
fun SettingsLibraryScreen(
    onNavigateBack: () -> Unit
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

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(GratiaTheme.colors.background)
            .statusBarsPadding(),
        contentPadding = PaddingValues(bottom = GratiaTheme.spacing.heroLarge)
    ) {
        item {
            AppleLargeTitleHeader(
                title = "Library Settings",
                onBack = onNavigateBack
            )
            Spacer(Modifier.height(16.dp))
        }

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
