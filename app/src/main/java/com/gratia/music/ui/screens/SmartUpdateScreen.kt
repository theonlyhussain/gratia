package com.gratia.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.gratia.music.GratiaApp
import com.gratia.music.ui.components.AppleLargeTitleHeader
import com.gratia.music.ui.components.AppleListRow
import com.gratia.music.ui.components.AppleSectionHeader
import com.gratia.music.ui.theme.GratiaTheme
import kotlinx.coroutines.launch

@Composable
fun SmartUpdateScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsDataStore = remember { com.gratia.music.data.SettingsDataStore(context) }
    
    val smartUpdateEnabled by settingsDataStore.smartUpdateEnabledFlow.collectAsState(initial = false)
    val smartUpdateOnboardingShown by settingsDataStore.smartUpdateOnboardingShownFlow.collectAsState(initial = false)
    var showOnboarding by remember { mutableStateOf(false) }
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
                title = "Smart Update",
                onBack = onNavigateBack
            )
        }

        item {
            AppleSectionHeader(title = "Updates")
            
            AppleListRow(
                title = "Smart Update",
                subtitle = "Automatically check for updates in the background",
                onClick = { 
                    if (!smartUpdateEnabled && !smartUpdateOnboardingShown) {
                        showOnboarding = true
                    } else {
                        val newValue = !smartUpdateEnabled
                        scope.launch { 
                            settingsDataStore.setSmartUpdateEnabled(newValue) 
                            if (newValue) {
                                com.gratia.music.updater.UpdateCheckWorker.schedule(context)
                            } else {
                                com.gratia.music.updater.UpdateCheckWorker.cancel(context)
                            }
                        }
                    }
                },
                trailingContent = {
                    Switch(
                        checked = smartUpdateEnabled,
                        onCheckedChange = { checked ->
                            if (checked && !smartUpdateOnboardingShown) {
                                showOnboarding = true
                            } else {
                                scope.launch { 
                                    settingsDataStore.setSmartUpdateEnabled(checked) 
                                    if (checked) {
                                        com.gratia.music.updater.UpdateCheckWorker.schedule(context)
                                    } else {
                                        com.gratia.music.updater.UpdateCheckWorker.cancel(context)
                                    }
                                }
                            }
                        },
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
            AppleListRow(
                title = "Check for Updates",
                onClick = { 
                    scope.launch { GratiaApp.instance.updateManager.checkForUpdate(manualCheck = true) }
                },
                showDivider = false,
                trailingContent = {
                    if (updateState is com.gratia.music.updater.UpdateState.Checking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = GratiaTheme.colors.accent
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Check",
                            tint = GratiaTheme.colors.textSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            )
            
            if (showOnboarding) {
                com.gratia.music.ui.components.SmartUpdateOnboardingSheet(
                    onEnable = {
                        showOnboarding = false
                        scope.launch {
                            settingsDataStore.setSmartUpdateOnboardingShown(true)
                            settingsDataStore.setSmartUpdateEnabled(true)
                            com.gratia.music.updater.UpdateCheckWorker.schedule(context)
                            android.widget.Toast.makeText(context, "Smart Update enabled", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    },
                    onCancel = {
                        showOnboarding = false
                    },
                    onDismiss = {
                        showOnboarding = false
                    }
                )
            }
            
            if (updateState !is com.gratia.music.updater.UpdateState.Idle && updateState !is com.gratia.music.updater.UpdateState.UpToDate) {
                com.gratia.music.ui.components.UpdatePromptSheet(
                    state = updateState,
                    onUpdateNow = { url ->
                        scope.launch { GratiaApp.instance.updateManager.downloadUpdate(url) }
                    },
                    onLater = {
                        GratiaApp.instance.updateManager.resetState()
                    },
                    onInstall = { file ->
                        GratiaApp.instance.updateManager.installUpdate(file)
                    },
                    onDismiss = {
                        if (updateState !is com.gratia.music.updater.UpdateState.Downloading) {
                            GratiaApp.instance.updateManager.resetState()
                        }
                    }
                )
            } else if (updateState is com.gratia.music.updater.UpdateState.UpToDate) {
                // If it is up to date, show a sheet or we can modify UpdatePromptSheet to handle it
                com.gratia.music.ui.components.UpdatePromptSheet(
                    state = updateState,
                    onUpdateNow = {},
                    onLater = {},
                    onInstall = {},
                    onDismiss = {
                        GratiaApp.instance.updateManager.resetState()
                    }
                )
            }
        }
    }
}
