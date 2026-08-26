package com.gratia.music.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gratia.music.ui.theme.GratiaTheme
import com.gratia.music.ui.theme.Inter
import com.gratia.music.ui.theme.SpaceGrotesk
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
                text = "Gratia checks for updates and allows you to download them directly from our official GitHub release. Smart Update needs permission to download these APK update files to your device.",
                style = GratiaTheme.typography.body,
                color = GratiaTheme.colors.textSecondary,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Spacer(modifier = Modifier.height(32.dp))

            OnboardingFeatureRow(
                icon = Icons.Default.Sync,
                title = "Background Checks",
                description = "Gratia checks for new versions in the background once a day. When an update is available, you will be notified."
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
                description = "The APK will only be downloaded when you choose to update. Android will always ask for your confirmation before installing."
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

private enum class UpdateSheetCategory {
    AVAILABLE,
    DOWNLOADING,
    READY,
    ERROR,
    UP_TO_DATE,
    CHECKING
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdatePromptSheet(
    state: UpdateState,
    onUpdateNow: (downloadUrl: String) -> Unit,
    onCancelDownload: () -> Unit = {},
    onLater: () -> Unit,
    onInstall: (apkFile: File) -> Unit,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val currentVersion = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
    }

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

    val category = remember(state::class) {
        when (state) {
            is UpdateState.UpdateAvailable -> UpdateSheetCategory.AVAILABLE
            is UpdateState.Downloading -> UpdateSheetCategory.DOWNLOADING
            is UpdateState.ReadyToInstall -> UpdateSheetCategory.READY
            is UpdateState.Error -> UpdateSheetCategory.ERROR
            is UpdateState.UpToDate -> UpdateSheetCategory.UP_TO_DATE
            is UpdateState.Checking -> UpdateSheetCategory.CHECKING
            is UpdateState.Idle -> UpdateSheetCategory.UP_TO_DATE
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = GratiaTheme.colors.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 8.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(GratiaTheme.colors.textSecondary.copy(alpha = 0.4f))
            )
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp)
        ) {
            androidx.compose.animation.AnimatedContent(
                targetState = category,
                modifier = Modifier.fillMaxWidth(),
                transitionSpec = {
                    androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(250)) togetherWith 
                    androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(250))
                },
                label = "UpdateCategoryAnimation"
            ) { targetCategory ->
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when (targetCategory) {
                        UpdateSheetCategory.AVAILABLE -> {
                            val available = state as? UpdateState.UpdateAvailable
                            if (available != null) {
                                UpdateAvailableView(
                                    version = available.version,
                                    changelog = available.changelog,
                                    currentVersion = currentVersion,
                                    onDownload = { onUpdateNow(available.downloadUrl) },
                                    onLater = onLater
                                )
                            }
                        }
                        UpdateSheetCategory.DOWNLOADING -> {
                            val downloading = state as? UpdateState.Downloading
                            val progress = downloading?.progress ?: 0f
                            val downloadedBytes = downloading?.downloadedBytes ?: 0L
                            val totalBytes = downloading?.totalBytes ?: 0L
                            
                            UpdateDownloadingView(
                                progress = progress,
                                downloadedBytes = downloadedBytes,
                                totalBytes = totalBytes,
                                onCancel = {
                                    onCancelDownload()
                                    onDismiss()
                                }
                            )
                        }
                        UpdateSheetCategory.READY -> {
                            val ready = state as? UpdateState.ReadyToInstall
                            UpdateReadyView(
                                version = ready?.version ?: "",
                                onInstall = {
                                    if (ready != null) {
                                        val canInstall = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                            context.packageManager.canRequestPackageInstalls()
                                        } else true
                                        
                                        if (canInstall) {
                                            onInstall(ready.apkFile)
                                        } else {
                                            val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                                                data = android.net.Uri.parse("package:${context.packageName}")
                                            }
                                            installLauncher.launch(intent)
                                        }
                                    }
                                }
                            )
                        }
                        UpdateSheetCategory.ERROR -> {
                            val error = state as? UpdateState.Error
                            UpdateErrorView(
                                message = error?.message ?: "An error occurred while downloading the update.",
                                onDismiss = onDismiss
                            )
                        }
                        UpdateSheetCategory.UP_TO_DATE -> {
                            UpdateUpToDateView(
                                currentVersion = currentVersion,
                                onDismiss = onDismiss
                            )
                        }
                        UpdateSheetCategory.CHECKING -> {
                            UpdateCheckingView()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UpdateAvailableView(
    version: String,
    changelog: String,
    currentVersion: String,
    onDownload: () -> Unit,
    onLater: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = "Software Update",
            fontFamily = SpaceGrotesk,
            fontWeight = FontWeight.Bold,
            fontSize = 28.sp,
            color = GratiaTheme.colors.textPrimary
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "A new version of Gratia is available",
            fontFamily = Inter,
            fontSize = 13.sp,
            color = GratiaTheme.colors.textSecondary
        )
        
        Spacer(Modifier.height(20.dp))
        
        // Version badge card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(GratiaTheme.colors.surfaceHover)
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Gratia $version",
                        fontFamily = SpaceGrotesk,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = GratiaTheme.colors.textPrimary
                    )
                    Text(
                        text = "Current: v$currentVersion",
                        fontFamily = Inter,
                        fontSize = 12.sp,
                        color = GratiaTheme.colors.textSecondary
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(GratiaTheme.colors.accent.copy(alpha = 0.15f))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "Update",
                        fontFamily = Inter,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = GratiaTheme.colors.accent
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // What's New Box
        Text(
            text = "What's New",
            fontFamily = SpaceGrotesk,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = GratiaTheme.colors.textPrimary
        )
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 200.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(GratiaTheme.colors.surfaceHover.copy(alpha = 0.6f))
                .padding(14.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = changelog.trim().ifEmpty { "Bug fixes and performance improvements." },
                fontFamily = Inter,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = GratiaTheme.colors.textSecondary
            )
        }

        Spacer(Modifier.height(24.dp))

        // Action Buttons
        Button(
            onClick = onDownload,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = GratiaTheme.colors.accent,
                contentColor = Color.White
            )
        ) {
            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "Download Update",
                fontFamily = Inter,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp
            )
        }

        Spacer(Modifier.height(10.dp))

        TextButton(
            onClick = onLater,
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
        ) {
            Text(
                "Not Now",
                fontFamily = Inter,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                color = GratiaTheme.colors.textSecondary
            )
        }
    }
}

@Composable
private fun UpdateDownloadingView(
    progress: Float,
    downloadedBytes: Long,
    totalBytes: Long,
    onCancel: () -> Unit
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = androidx.compose.animation.core.tween(300),
        label = "progressAnim"
    )

    val percent = (progress * 100).toInt().coerceIn(0, 100)
    
    val bytesText = if (totalBytes > 0) {
        val dlMb = downloadedBytes.toFloat() / (1024 * 1024)
        val totalMb = totalBytes.toFloat() / (1024 * 1024)
        String.format("%.1f MB / %.1f MB", dlMb, totalMb)
    } else if (downloadedBytes > 0) {
        val dlMb = downloadedBytes.toFloat() / (1024 * 1024)
        String.format("%.1f MB", dlMb)
    } else {
        "Preparing download..."
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Downloading Update",
            fontFamily = SpaceGrotesk,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
            color = GratiaTheme.colors.textPrimary
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Please wait while Gratia is downloading",
            fontFamily = Inter,
            fontSize = 13.sp,
            color = GratiaTheme.colors.textSecondary
        )

        Spacer(Modifier.height(32.dp))

        // Material-style Determinate Circular Progress Indicator
        Box(
            modifier = Modifier.size(150.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                progress = { 1f },
                modifier = Modifier.fillMaxSize(),
                color = GratiaTheme.colors.surfaceHover,
                strokeWidth = 8.dp,
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
            )
            CircularProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier.fillMaxSize(),
                color = GratiaTheme.colors.accent,
                strokeWidth = 8.dp,
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "$percent%",
                    fontFamily = SpaceGrotesk,
                    fontWeight = FontWeight.Bold,
                    fontSize = 30.sp,
                    color = GratiaTheme.colors.textPrimary
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        Text(
            text = bytesText,
            fontFamily = Inter,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            color = GratiaTheme.colors.textSecondary
        )

        Spacer(Modifier.height(32.dp))

        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, GratiaTheme.colors.surfaceHover)
        ) {
            Text(
                "Cancel",
                fontFamily = Inter,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = GratiaTheme.colors.textPrimary
            )
        }
    }
}

@Composable
private fun UpdateReadyView(
    version: String,
    onInstall: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(GratiaTheme.colors.accent.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = GratiaTheme.colors.accent,
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(Modifier.height(20.dp))

        Text(
            text = "Update Downloaded",
            fontFamily = SpaceGrotesk,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
            color = GratiaTheme.colors.textPrimary
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (version.isNotBlank()) "Gratia $version is ready to install." else "The update is ready to install.",
            fontFamily = Inter,
            fontSize = 14.sp,
            color = GratiaTheme.colors.textSecondary,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = onInstall,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = GratiaTheme.colors.accent,
                contentColor = Color.White
            )
        ) {
            Text(
                "Install Update",
                fontFamily = Inter,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp
            )
        }
    }
}

@Composable
private fun UpdateErrorView(
    message: String,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(GratiaTheme.colors.error.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = GratiaTheme.colors.error,
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(Modifier.height(20.dp))

        Text(
            text = "Update couldn't be downloaded",
            fontFamily = SpaceGrotesk,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            color = GratiaTheme.colors.textPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = message,
            fontFamily = Inter,
            fontSize = 13.sp,
            color = GratiaTheme.colors.textSecondary,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = onDismiss,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = GratiaTheme.colors.surfaceHover,
                contentColor = GratiaTheme.colors.textPrimary
            )
        ) {
            Text(
                "Close",
                fontFamily = Inter,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun UpdateUpToDateView(
    currentVersion: String,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(GratiaTheme.colors.accent.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = GratiaTheme.colors.accent,
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(Modifier.height(20.dp))

        Text(
            text = "You're up to date",
            fontFamily = SpaceGrotesk,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
            color = GratiaTheme.colors.textPrimary
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Gratia v$currentVersion is currently the latest version.",
            fontFamily = Inter,
            fontSize = 14.sp,
            color = GratiaTheme.colors.textSecondary,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = onDismiss,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = GratiaTheme.colors.surfaceHover,
                contentColor = GratiaTheme.colors.textPrimary
            )
        ) {
            Text(
                "Done",
                fontFamily = Inter,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun UpdateCheckingView() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(44.dp),
            strokeWidth = 4.dp,
            color = GratiaTheme.colors.accent
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Checking for updates...",
            fontFamily = Inter,
            fontSize = 14.sp,
            color = GratiaTheme.colors.textSecondary
        )
    }
}

