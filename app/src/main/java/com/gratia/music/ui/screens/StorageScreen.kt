package com.gratia.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gratia.music.ui.theme.GratiaTheme
import com.gratia.music.ui.theme.Inter
import com.gratia.music.ui.theme.SpaceGrotesk

@Composable
fun StorageScreen(onNavigateBack: () -> Unit) {
    var showCloudDialog by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GratiaTheme.colors.background)
            .verticalScroll(rememberScrollState())
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconButton(
                onClick = onNavigateBack,
                modifier = Modifier.size(36.dp).clip(CircleShape).background(GratiaTheme.colors.surface)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", Modifier.size(16.dp), tint = GratiaTheme.colors.textSecondary)
            }
            Column {
                Text("Music Storage", fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = GratiaTheme.colors.textPrimary)
                Text("Choose where Gratia keeps your music.", fontFamily = Inter, fontSize = 12.sp, color = GratiaTheme.colors.textSecondary)
            }
        }

        Spacer(Modifier.height(16.dp))

        // Storage cards
        StorageCard(
            icon = Icons.Default.PhoneAndroid,
            title = "Local Device",
            subtitle = "Songs stored on this device",
            isActive = true,
            accentColor = GratiaTheme.colors.success,
            onClick = { }
        )

        StorageCard(
            icon = Icons.Default.Cloud,
            title = "Google Drive",
            subtitle = "Not connected",
            isActive = false,
            accentColor = Color(0xFF4285F4),
            onClick = { showCloudDialog = "Google Drive" }
        )

        StorageCard(
            icon = Icons.Default.CloudSync,
            title = "Nextcloud / WebDAV",
            subtitle = "Not connected",
            isActive = false,
            accentColor = Color(0xFF0082C9),
            onClick = { showCloudDialog = "Nextcloud" }
        )

        StorageCard(
            icon = Icons.Default.Settings,
            title = "Advanced",
            subtitle = "Not configured",
            isActive = false,
            accentColor = GratiaTheme.colors.textSecondary,
            onClick = { showCloudDialog = "Advanced storage" }
        )

        Spacer(Modifier.height(32.dp))
    }

    // Cloud not-configured dialog
    if (showCloudDialog != null) {
        AlertDialog(
            onDismissRequest = { showCloudDialog = null },
            containerColor = GratiaTheme.colors.surface,
            title = {
                Text("$showCloudDialog", fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, color = GratiaTheme.colors.textPrimary)
            },
            text = {
                Text(
                    "$showCloudDialog connection is not configured yet.\n\nUse Local Device mode for now.",
                    fontFamily = Inter,
                    fontSize = 14.sp,
                    color = GratiaTheme.colors.textSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = { showCloudDialog = null }) {
                    Text("OK", color = GratiaTheme.colors.accent, fontFamily = Inter)
                }
            }
        )
    }
}

@Composable
private fun StorageCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isActive: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = GratiaTheme.colors.surface,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = RoundedCornerShape(12.dp),
                color = accentColor.copy(alpha = 0.1f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = accentColor, modifier = Modifier.size(22.dp))
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = GratiaTheme.colors.textPrimary)
                Text(subtitle, fontFamily = Inter, fontSize = 12.sp, color = GratiaTheme.colors.textSecondary)
            }

            if (isActive) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = accentColor.copy(alpha = 0.15f),
                ) {
                    Text("Active", fontFamily = Inter, fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
                        color = accentColor, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                }
            } else {
                Icon(Icons.Default.ChevronRight, null, tint = GratiaTheme.colors.textSecondary, modifier = Modifier.size(18.dp))
            }
        }
    }
}
