package com.gratia.music.ui.components

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.gratia.music.ui.theme.GratiaTheme
import com.gratia.music.updater.UpdateState
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateBanner(
    state: UpdateState,
    onDownload: (String) -> Unit,
    onInstall: (File) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var showPermissionExplanation by remember { mutableStateOf(false) }
    var pendingApkFile by remember { mutableStateOf<File?>(null) }
    
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted && pendingApkFile != null) {
            onInstall(pendingApkFile!!)
            pendingApkFile = null
        }
    }

    if (state is UpdateState.Idle) return

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = GratiaTheme.spacing.base, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = GratiaTheme.colors.surface
        ),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(GratiaTheme.colors.accent.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SystemUpdate,
                        contentDescription = null,
                        tint = GratiaTheme.colors.accent,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    GratiaText(
                        text = "Update Available",
                        style = GratiaTheme.typography.title,
                        color = GratiaTheme.colors.textPrimary
                    )
                    if (state is UpdateState.UpdateAvailable) {
                        GratiaText(
                            text = "Version ${state.version}",
                            style = GratiaTheme.typography.caption,
                            color = GratiaTheme.colors.textSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            AnimatedContent(
                targetState = state,
                transitionSpec = {
                    fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
                },
                label = "UpdateStateTransition"
            ) { targetState ->
                when (targetState) {
                    is UpdateState.Checking -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = GratiaTheme.colors.accent,
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(12.dp))
                            GratiaText(
                                text = "Checking for updates...",
                                style = GratiaTheme.typography.body,
                                color = GratiaTheme.colors.textSecondary
                            )
                        }
                    }
                    is UpdateState.UpdateAvailable -> {
                        Column {
                            GratiaText(
                                text = targetState.changelog,
                                style = GratiaTheme.typography.body,
                                color = GratiaTheme.colors.textSecondary,
                                maxLines = 3
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            GratiaButton(
                                text = "Download Update",
                                onClick = { onDownload(targetState.downloadUrl) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    is UpdateState.Downloading -> {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                GratiaText(
                                    text = "Downloading...",
                                    style = GratiaTheme.typography.body,
                                    color = GratiaTheme.colors.textPrimary
                                )
                                GratiaText(
                                    text = "${(targetState.progress * 100).toInt()}%",
                                    style = GratiaTheme.typography.body,
                                    color = GratiaTheme.colors.textSecondary
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            LinearProgressIndicator(
                                progress = { targetState.progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = GratiaTheme.colors.accent,
                                trackColor = GratiaTheme.colors.progressTrack
                            )
                        }
                    }
                    is UpdateState.ReadyToInstall -> {
                        GratiaButton(
                            text = "Install Now",
                            onClick = {
                                val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    context.packageManager.canRequestPackageInstalls()
                                } else {
                                    true
                                }
                                
                                if (hasPermission) {
                                    onInstall(targetState.apkFile)
                                } else {
                                    pendingApkFile = targetState.apkFile
                                    showPermissionExplanation = true
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    is UpdateState.Error -> {
                        GratiaText(
                            text = targetState.message,
                            style = GratiaTheme.typography.body,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    is UpdateState.Idle, is UpdateState.UpToDate -> { /* Hidden */ }
                }
            }
        }
    }
    
    if (showPermissionExplanation) {
        AlertDialog(
            onDismissRequest = { showPermissionExplanation = false },
            title = {
                GratiaText(
                    text = "Permission Required",
                    style = GratiaTheme.typography.title,
                    color = GratiaTheme.colors.textPrimary
                )
            },
            text = {
                GratiaText(
                    text = "To install this update smoothly, Gratia needs permission to install apps. Please grant 'Install Unknown Apps' on the next screen.",
                    style = GratiaTheme.typography.body,
                    color = GratiaTheme.colors.textSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showPermissionExplanation = false
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        // For Android 8.0+, we launch the system settings page directly
                        val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                            data = android.net.Uri.parse("package:${context.packageName}")
                        }
                        context.startActivity(intent)
                        // Note: after returning from settings, the user might need to press Install again
                        // if we can't observe the setting change instantly.
                    } else {
                        permissionLauncher.launch(android.Manifest.permission.REQUEST_INSTALL_PACKAGES)
                    }
                }) {
                    GratiaText("Continue", color = GratiaTheme.colors.accent, style = GratiaTheme.typography.body)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionExplanation = false }) {
                    GratiaText("Cancel", color = GratiaTheme.colors.textSecondary, style = GratiaTheme.typography.body)
                }
            },
            containerColor = GratiaTheme.colors.background
        )
    }
}
