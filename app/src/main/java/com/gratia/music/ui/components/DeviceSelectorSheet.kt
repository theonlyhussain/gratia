package com.gratia.music.ui.components

import android.content.Intent
import android.media.AudioDeviceInfo
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothAudio
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gratia.music.player.ConnectedDevice
import com.gratia.music.player.DeviceManager
import com.gratia.music.ui.theme.GratiaTheme
import com.gratia.music.ui.theme.Inter
import com.gratia.music.ui.theme.SpaceGrotesk

@Composable
fun rememberDeviceManager(): DeviceManager {
    val context = LocalContext.current
    val deviceManager = remember(context) { DeviceManager(context) }
    
    DisposableEffect(deviceManager) {
        deviceManager.startListening()
        onDispose {
            deviceManager.stopListening()
        }
    }
    
    return deviceManager
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceSelectorSheet(
    songTitle: String,
    artistName: String,
    onDismissRequest: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val deviceManager = rememberDeviceManager()
    val devices by deviceManager.connectedDevices.collectAsState()

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = GratiaTheme.colors.surface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 16.dp)
                    .width(32.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.2f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp) // extra padding for bottom navigation
        ) {
            Text(
                text = "Connect",
                fontFamily = SpaceGrotesk,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                color = GratiaTheme.colors.textPrimary,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            LazyColumn(
                modifier = Modifier.weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(devices, key = { it.id }) { device ->
                    DeviceItem(
                        device = device,
                        songTitle = songTitle,
                        artistName = artistName,
                        onClick = {
                            if (!device.isCurrent) {
                                // Fallback to system media output switcher intent if supported, 
                                // otherwise open Bluetooth settings
                                try {
                                    context.startActivity(Intent("com.android.settings.panel.action.MEDIA_OUTPUT"))
                                } catch (e: Exception) {
                                    context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                                }
                                onDismissRequest()
                            }
                        }
                    )
                }

                if (devices.size <= 1) {
                    item {
                        Text(
                            text = "No other devices found",
                            fontFamily = Inter,
                            fontSize = 14.sp,
                            color = GratiaTheme.colors.textSecondary,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Find Bluetooth devices button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(GratiaTheme.colors.surfaceHover)
                    .clickable {
                        context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                        onDismissRequest()
                    }
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Bluetooth,
                    contentDescription = "Bluetooth",
                    tint = GratiaTheme.colors.textPrimary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Find Bluetooth devices",
                    fontFamily = Inter,
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp,
                    color = GratiaTheme.colors.textPrimary
                )
            }
        }
    }
}

@Composable
private fun DeviceItem(
    device: ConnectedDevice,
    songTitle: String,
    artistName: String,
    onClick: () -> Unit
) {
    val isPhone = device.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER

    val backgroundColor = if (device.isCurrent) GratiaTheme.colors.surfaceHover else Color.Transparent
    val borderColor = if (device.isCurrent) GratiaTheme.colors.accent.copy(alpha = 0.5f) else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(backgroundColor)
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = device.name,
                    fontFamily = Inter,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = if (device.isCurrent) GratiaTheme.colors.accent else GratiaTheme.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                if (isPhone) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.White.copy(alpha = 0.1f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "Normal",
                            fontFamily = Inter,
                            fontSize = 10.sp,
                            color = GratiaTheme.colors.textSecondary
                        )
                    }
                }
            }

            if (device.isCurrent) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "$songTitle — $artistName",
                    fontFamily = Inter,
                    fontSize = 14.sp,
                    color = GratiaTheme.colors.accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        val icon = when {
            isPhone -> Icons.Default.Smartphone
            device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || device.type == AudioDeviceInfo.TYPE_BLE_HEADSET -> Icons.Default.BluetoothAudio
            device.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || device.type == AudioDeviceInfo.TYPE_WIRED_HEADSET -> Icons.Default.Headset
            else -> Icons.Default.Speaker
        }

        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (device.isCurrent) GratiaTheme.colors.accent else GratiaTheme.colors.textSecondary,
            modifier = Modifier.size(24.dp)
        )
    }
}
