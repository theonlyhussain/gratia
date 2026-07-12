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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Launch
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gratia.music.R
import com.gratia.music.player.PlayerViewModel
import com.gratia.music.ui.theme.GratiaTheme
import com.gratia.music.ui.theme.Inter
import com.gratia.music.ui.theme.SpaceGrotesk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AboutScreen(
    playerViewModel: PlayerViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToLicenses: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val songCount by playerViewModel.songCount.collectAsState(initial = 0)
    val playlistCount by playerViewModel.playlistCount.collectAsState(initial = 0)
    
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
            verticalArrangement = Arrangement.spacedBy(24.dp)
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
                    text = "Version 2.0.0",
                    fontFamily = Inter,
                    fontSize = 14.sp,
                    color = GratiaTheme.colors.accent,
                    fontWeight = FontWeight.Medium
                )
            }

            // Software & Device Info Section
            SectionCard("System Information") {
                InfoRow("Build Number", "200")
                HorizontalDivider(color = GratiaTheme.colors.glassBorder)
                InfoRow("Release Channel", "Stable")
                HorizontalDivider(color = GratiaTheme.colors.glassBorder)
                InfoRow("App Version", "2.0.0")
                HorizontalDivider(color = GratiaTheme.colors.glassBorder)
                InfoRow("Android Version", Build.VERSION.RELEASE)
                HorizontalDivider(color = GratiaTheme.colors.glassBorder)
                InfoRow("Device Model", "${Build.MANUFACTURER} ${Build.MODEL}")
            }

            // Library Stats Section
            SectionCard("Library Statistics") {
                InfoRow("Songs", songCount.toString())
                HorizontalDivider(color = GratiaTheme.colors.glassBorder)
                InfoRow("Playlists", playlistCount.toString())
                HorizontalDivider(color = GratiaTheme.colors.glassBorder)
                InfoRow("Storage Used", storageUsed)
            }

            // Links Section
            SectionCard("Developer") {
                ActionRow(
                    icon = Icons.Outlined.Person,
                    title = "Developer",
                    subtitle = "Hussain Shaikh",
                    onClick = null
                )
                HorizontalDivider(color = GratiaTheme.colors.glassBorder)
                ActionRow(
                    icon = Icons.Outlined.Code,
                    title = "GitHub",
                    subtitle = "Open Source Repository",
                    onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/theonlyhussain/gratia")))
                    }
                )
            }

            // Legal & More
            SectionCard("Legal & More") {
                ActionRow(
                    icon = Icons.Outlined.Description,
                    title = "Open-Source Licenses",
                    subtitle = "Libraries used in Gratia",
                    onClick = onNavigateToLicenses
                )
                HorizontalDivider(color = GratiaTheme.colors.glassBorder)
                ActionRow(
                    icon = Icons.Outlined.PrivacyTip,
                    title = "Privacy Policy",
                    subtitle = "Local-first data policy",
                    onClick = { /* Privacy Policy implementation */ }
                )
                HorizontalDivider(color = GratiaTheme.colors.glassBorder)
                ActionRow(
                    icon = Icons.Outlined.Update,
                    title = "Changelog",
                    subtitle = "What's new in v2.0.0",
                    onClick = { /* Changelog implementation */ }
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            text = title.uppercase(),
            fontFamily = Inter,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            color = GratiaTheme.colors.textSecondary,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = GratiaTheme.colors.surface,
            border = ButtonDefaults.outlinedButtonBorder.copy(
                brush = androidx.compose.ui.graphics.SolidColor(GratiaTheme.colors.glassBorder)
            ),
            shadowElevation = 0.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                content()
            }
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
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
fun ActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = GratiaTheme.colors.accent,
            modifier = Modifier.size(24.dp)
        )
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
                    fontSize = 12.sp,
                    color = GratiaTheme.colors.textSecondary
                )
            }
        }
        if (onClick != null) {
            Icon(
                imageVector = Icons.Default.Launch,
                contentDescription = null,
                tint = GratiaTheme.colors.textSecondary.copy(alpha = 0.5f),
                modifier = Modifier.size(16.dp)
            )
        }
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
