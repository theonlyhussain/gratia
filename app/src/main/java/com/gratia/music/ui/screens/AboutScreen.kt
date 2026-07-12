package com.gratia.music.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.text.format.Formatter
import androidx.compose.foundation.Image
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gratia.music.GratiaApp
import com.gratia.music.R
import com.gratia.music.data.repository.SongRepository
import com.gratia.music.player.PlayerViewModel
import com.gratia.music.ui.theme.GratiaTheme
import com.gratia.music.ui.theme.Inter
import com.gratia.music.ui.theme.SpaceGrotesk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Composable
fun AboutScreen(
    playerViewModel: PlayerViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToLicenses: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val songRepo = remember { SongRepository(GratiaApp.instance.database.songDao()) }

    val songCount by playerViewModel.songCount.collectAsState(initial = 0)
    val playlistCount by playerViewModel.playlistCount.collectAsState(initial = 0)
    val albumCount by songRepo.getAllSongs().map { songs -> songs.mapNotNull { it.album }.distinct().size }.collectAsState(initial = 0)
    val artistCount by songRepo.getAllSongs().map { songs -> songs.map { it.artist }.distinct().size }.collectAsState(initial = 0)
    
    var storageUsed by remember { mutableStateOf("Calculating...") }
    
    LaunchedEffect(Unit) {
        storageUsed = calculateAppStorage(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GratiaTheme.colors.background)
            .statusBarsPadding()
    ) {
        // Header (Matches Settings exactly)
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
                    tint = GratiaTheme.colors.textSecondary
                )
            }
            Text(
                text = "About",
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
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Hero App Info
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    painter = painterResource(id = R.drawable.gratia_logo),
                    contentDescription = "Gratia Logo",
                    modifier = Modifier
                        .size(100.dp)
                        .clip(RoundedCornerShape(24.dp))
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Gratia",
                    fontFamily = SpaceGrotesk,
                    fontWeight = FontWeight.Bold,
                    fontSize = 32.sp,
                    color = GratiaTheme.colors.textPrimary
                )
                Text(
                    text = "Version 2.0.1",
                    fontFamily = Inter,
                    fontSize = 14.sp,
                    color = GratiaTheme.colors.accent,
                    fontWeight = FontWeight.Medium
                )
            }

            AboutCard(title = "System Information") {
                val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                val appVersion = packageInfo.versionName ?: "Unknown"
                val buildNumber = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    packageInfo.longVersionCode.toString()
                } else {
                    @Suppress("DEPRECATION")
                    packageInfo.versionCode.toString()
                }
                
                AboutInfoRow("Build Number", buildNumber)
                HorizontalDivider(color = GratiaTheme.colors.glassBorder)
                AboutInfoRow("Release Channel", "Beta")
                HorizontalDivider(color = GratiaTheme.colors.glassBorder)
                AboutInfoRow("Android Version", Build.VERSION.RELEASE)
                HorizontalDivider(color = GratiaTheme.colors.glassBorder)
                AboutInfoRow("Device Model", "${Build.MANUFACTURER} ${Build.MODEL}")
            }

            // Library Statistics Card
            AboutCard(title = "Library Statistics") {
                AboutInfoRow("Storage Used", storageUsed)
                HorizontalDivider(color = GratiaTheme.colors.glassBorder)
                AboutInfoRow("Songs", songCount.toString())
                HorizontalDivider(color = GratiaTheme.colors.glassBorder)
                AboutInfoRow("Albums", albumCount.toString())
                HorizontalDivider(color = GratiaTheme.colors.glassBorder)
                AboutInfoRow("Artists", artistCount.toString())
                HorizontalDivider(color = GratiaTheme.colors.glassBorder)
                AboutInfoRow("Playlists", playlistCount.toString())
            }

            // Developer & Links Card
            AboutCard(title = "Developer & Links") {
                AboutInfoRow("Developer", "Hussain Shaikh")
                HorizontalDivider(color = GratiaTheme.colors.glassBorder)
                AboutActionRow(
                    title = "GitHub",
                    subtitle = "Open source repository",
                    onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/theonlyhussain/gratia")))
                    }
                )
                HorizontalDivider(color = GratiaTheme.colors.glassBorder)
                AboutActionRow(
                    title = "Privacy Policy",
                    subtitle = "Local-first data policy",
                    onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/theonlyhussain/gratia/blob/main/PRIVACY.md")))
                    }
                )
                HorizontalDivider(color = GratiaTheme.colors.glassBorder)
                AboutActionRow(
                    title = "Changelog",
                    subtitle = "What's new in v2.0.1",
                    onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/theonlyhussain/gratia/releases")))
                    }
                )
                HorizontalDivider(color = GratiaTheme.colors.glassBorder)
                AboutActionRow(
                    title = "Licenses",
                    subtitle = "Open-source libraries",
                    onClick = onNavigateToLicenses
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun AboutCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = GratiaTheme.colors.surface,
        shadowElevation = 4.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                fontFamily = Inter,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = GratiaTheme.colors.textPrimary
            )
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun AboutInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontFamily = Inter,
            fontSize = 14.sp,
            color = GratiaTheme.colors.textPrimary
        )
        Text(
            text = value,
            fontFamily = Inter,
            fontSize = 14.sp,
            color = GratiaTheme.colors.textSecondary,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun AboutActionRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontFamily = Inter,
                fontSize = 14.sp,
                color = GratiaTheme.colors.textPrimary
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    fontFamily = Inter,
                    fontSize = 11.sp,
                    color = GratiaTheme.colors.textSecondary
                )
            }
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = GratiaTheme.colors.textSecondary,
            modifier = Modifier.size(20.dp)
        )
    }
}

private suspend fun calculateAppStorage(context: Context): String = withContext(Dispatchers.IO) {
    var size = 0L
    try {
        size += getDirSize(context.filesDir)
        size += getDirSize(context.cacheDir)
        size += getDirSize(context.getExternalFilesDir(null))
    } catch (e: Exception) {
        // Ignore
    }
    Formatter.formatFileSize(context, size)
}

private fun getDirSize(dir: java.io.File?): Long {
    if (dir == null || !dir.exists()) return 0
    var size = 0L
    dir.listFiles()?.forEach { file ->
        if (file.isDirectory) {
            size += getDirSize(file)
        } else {
            size += file.length()
        }
    }
    return size
}
