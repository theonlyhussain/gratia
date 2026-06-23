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
import com.gratia.music.ui.theme.GratiaTheme
import com.gratia.music.ui.theme.Inter
import com.gratia.music.ui.theme.SpaceGrotesk
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToAbout: () -> Unit
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
            .background(GratiaTheme.colors.cotton)
            .statusBarsPadding()
    ) {
        // Header
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconButton(
                onClick = onNavigateBack,
                modifier = Modifier
                    .size(36.dp)
                    .border(1.dp, GratiaTheme.colors.glassBorder, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    modifier = Modifier.size(16.dp),
                    tint = GratiaTheme.colors.textMuted
                )
            }
            Text(
                text = "Settings",
                fontFamily = SpaceGrotesk,
                fontWeight = FontWeight.SemiBold,
                fontSize = 20.sp,
                color = GratiaTheme.colors.textPrimary
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Library Sync Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = androidx.compose.ui.graphics.Color.White,
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = androidx.compose.ui.graphics.SolidColor(GratiaTheme.colors.glassBorder)
                ),
                shadowElevation = 4.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Library Sync",
                        fontFamily = Inter,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = GratiaTheme.colors.textPrimary
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Scan your device storage to automatically import offline audio files.",
                        fontFamily = Inter,
                        fontSize = 12.sp,
                        color = GratiaTheme.colors.textSecondary
                    )
                    Spacer(Modifier.height(16.dp))

                    if (scanning) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = GratiaTheme.colors.cherryRed
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Scanning local storage...",
                                fontFamily = Inter,
                                fontSize = 12.sp,
                                color = GratiaTheme.colors.cherryRed
                            )
                        }
                    } else {
                        Button(
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
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = GratiaTheme.colors.cherryRed,
                                contentColor = GratiaTheme.colors.cotton
                            )
                        ) {
                            Text(
                                text = "Sync Now",
                                fontFamily = Inter,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                        }
                    }

                    if (scanResult.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = scanResult,
                            fontFamily = Inter,
                            fontSize = 12.sp,
                            color = if (scanResult.contains("complete")) GratiaTheme.colors.textSecondary else GratiaTheme.colors.cherryRed
                        )
                    }
                }
            }

            // About Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = androidx.compose.ui.graphics.Color.White,
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = androidx.compose.ui.graphics.SolidColor(GratiaTheme.colors.glassBorder)
                ),
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onNavigateToAbout() }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "About",
                            fontFamily = Inter,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = GratiaTheme.colors.textPrimary
                        )
                        Text(
                            text = "App info, developer, and legal licenses",
                            fontFamily = Inter,
                            fontSize = 11.sp,
                            color = GratiaTheme.colors.textMuted
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = GratiaTheme.colors.textMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
