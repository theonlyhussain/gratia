package com.gratia.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gratia.music.ui.theme.GratiaTheme
import com.gratia.music.updater.UpdateState
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartUpdateOnboardingSheet(
    onEnable: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = GratiaTheme.colors.surface,
        scrimColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.5f),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .windowInsetsPadding(WindowInsets.navigationBars),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.SystemUpdate,
                contentDescription = null,
                tint = GratiaTheme.colors.accent,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            GratiaText(
                text = "Smart Update",
                style = GratiaTheme.typography.title,
                color = GratiaTheme.colors.textPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            GratiaText(
                text = "Keep Gratia up to date with the latest features and bug fixes.",
                style = GratiaTheme.typography.body,
                color = GratiaTheme.colors.textSecondary,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Spacer(modifier = Modifier.height(32.dp))

            OnboardingFeatureRow(
                icon = Icons.Default.Sync,
                title = "Background Checks",
                description = "Gratia checks for new versions in the background once a day."
            )
            Spacer(modifier = Modifier.height(24.dp))
            OnboardingFeatureRow(
                icon = Icons.Default.Security,
                title = "Safe & Official",
                description = "Updates are downloaded directly from the official GitHub Releases."
            )
            Spacer(modifier = Modifier.height(24.dp))
            OnboardingFeatureRow(
                icon = Icons.Default.PanTool,
                title = "You Are In Control",
                description = "Android will always ask for your confirmation before installing any update. You can turn this off anytime."
            )

            Spacer(modifier = Modifier.height(48.dp))

            GratiaButton(
                text = "Enable Smart Update",
                onClick = onEnable,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            TextButton(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth()
            ) {
                GratiaText(
                    text = "Not Now",
                    style = GratiaTheme.typography.body.copy(fontWeight = FontWeight.Medium),
                    color = GratiaTheme.colors.textSecondary
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun OnboardingFeatureRow(icon: ImageVector, title: String, description: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(GratiaTheme.colors.glassBorder),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = GratiaTheme.colors.accent,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            GratiaText(
                text = title,
                style = GratiaTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
                color = GratiaTheme.colors.textPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            GratiaText(
                text = description,
                style = GratiaTheme.typography.caption,
                color = GratiaTheme.colors.textSecondary
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdatePromptSheet(
    state: UpdateState,
    onUpdateNow: (downloadUrl: String) -> Unit,
    onLater: () -> Unit,
    onInstall: (apkFile: File) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = androidx.compose.ui.platform.LocalContext.current
    val installLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) {
        val canInstall = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else true
        
        if (canInstall && state is UpdateState.ReadyToInstall) {
            onInstall(state.apkFile)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = GratiaTheme.colors.surface,
        scrimColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.5f),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .windowInsetsPadding(WindowInsets.navigationBars),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (state) {
                is UpdateState.UpdateAvailable -> {
                    Icon(
                        imageVector = Icons.Default.NewReleases,
                        contentDescription = null,
                        tint = GratiaTheme.colors.accent,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    GratiaText(
                        text = "Update Available",
                        style = GratiaTheme.typography.title,
                        color = GratiaTheme.colors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    GratiaText(
                        text = "Version ${state.version} is ready to download.",
                        style = GratiaTheme.typography.body,
                        color = GratiaTheme.colors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(GratiaTheme.colors.glassBorder)
                            .padding(16.dp)
                    ) {
                        Column {
                            GratiaText(
                                text = "Release Notes",
                                style = GratiaTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
                                color = GratiaTheme.colors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            GratiaText(
                                text = state.changelog,
                                style = GratiaTheme.typography.caption,
                                color = GratiaTheme.colors.textSecondary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                    GratiaButton(
                        text = "Download Now",
                        onClick = { onUpdateNow(state.downloadUrl) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    TextButton(
                        onClick = onLater,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        GratiaText(
                            text = "Later",
                            style = GratiaTheme.typography.body.copy(fontWeight = FontWeight.Medium),
                            color = GratiaTheme.colors.textSecondary
                        )
                    }
                }
                is UpdateState.Downloading -> {
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = null,
                        tint = GratiaTheme.colors.accent,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    GratiaText(
                        text = "Downloading Update...",
                        style = GratiaTheme.typography.title,
                        color = GratiaTheme.colors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    LinearProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = GratiaTheme.colors.accent,
                        trackColor = GratiaTheme.colors.glassBorder
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    GratiaText(
                        text = "${(state.progress * 100).toInt()}%",
                        style = GratiaTheme.typography.body.copy(fontWeight = FontWeight.Bold),
                        color = GratiaTheme.colors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                }
                is UpdateState.ReadyToInstall -> {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = GratiaTheme.colors.success,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    GratiaText(
                        text = "Download Complete",
                        style = GratiaTheme.typography.title,
                        color = GratiaTheme.colors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    GratiaText(
                        text = "The update is ready to be installed. Your data will be preserved.",
                        style = GratiaTheme.typography.body,
                        color = GratiaTheme.colors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    GratiaButton(
                        text = "Install Update",
                        onClick = { 
                            val canInstall = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                context.packageManager.canRequestPackageInstalls()
                            } else true
                            
                            if (canInstall) {
                                onInstall(state.apkFile) 
                            } else {
                                val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                                    data = android.net.Uri.parse("package:${context.packageName}")
                                }
                                installLauncher.launch(intent)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                is UpdateState.Error -> {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = GratiaTheme.colors.error,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    GratiaText(
                        text = "Update Failed",
                        style = GratiaTheme.typography.title,
                        color = GratiaTheme.colors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    GratiaText(
                        text = state.message,
                        style = GratiaTheme.typography.body,
                        color = GratiaTheme.colors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    GratiaButton(
                        text = "Okay",
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                else -> {
                    CircularProgressIndicator(color = GratiaTheme.colors.accent)
                    Spacer(modifier = Modifier.height(16.dp))
                    GratiaText(
                        text = "Checking for updates...",
                        style = GratiaTheme.typography.body,
                        color = GratiaTheme.colors.textSecondary
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
