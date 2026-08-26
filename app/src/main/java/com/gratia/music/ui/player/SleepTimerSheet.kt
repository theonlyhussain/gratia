package com.gratia.music.ui.player

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gratia.music.player.PlayerViewModel
import com.gratia.music.player.SleepAction
import com.gratia.music.ui.components.GratiaButton
import com.gratia.music.ui.components.clickableWithScale
import com.gratia.music.ui.theme.GratiaTheme
import com.gratia.music.ui.theme.Inter
import com.gratia.music.ui.theme.JetBrainsMono
import com.gratia.music.ui.theme.SpaceGrotesk

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepTimerSheet(
    playerViewModel: PlayerViewModel,
    onDismiss: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    
    val isActive by playerViewModel.sleepTimerActive.collectAsState()
    val remainingMs by playerViewModel.sleepTimerRemainingMs.collectAsState()
    val currentAction by playerViewModel.sleepTimerAction.collectAsState()
    
    var selectedAction by remember { mutableStateOf(currentAction) }
    var showCustomDialog by remember { mutableStateOf(false) }

    val quickPresets = listOf(
        Triple(5, "5 min", Icons.Outlined.Coffee),
        Triple(15, "15 min", Icons.Outlined.Coffee),
        Triple(30, "30 min", Icons.Outlined.WbSunny),
        Triple(45, "45 min", Icons.Outlined.Bedtime),
        Triple(60, "60 min", Icons.Outlined.Nightlight),
        Triple(90, "90 min", Icons.Outlined.HourglassBottom),
        Triple(120, "120 min", Icons.Outlined.Hotel)
    )

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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .padding(bottom = 36.dp)
        ) {
            // Header
            Text(
                text = "Sleep Timer",
                fontFamily = SpaceGrotesk,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                color = GratiaTheme.colors.textPrimary
            )
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(GratiaTheme.colors.surfaceHover)
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "Set automatic playback control",
                    fontFamily = Inter,
                    fontSize = 12.sp,
                    color = GratiaTheme.colors.textSecondary
                )
            }

            Spacer(Modifier.height(20.dp))

            // Active Countdown or Presets
            if (isActive) {
                // Active View
                ActiveTimerCard(
                    remainingMs = remainingMs,
                    action = currentAction,
                    onAddMinutes = { mins ->
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        playerViewModel.sleepTimerManager.addMinutes(mins)
                    },
                    onStop = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        playerViewModel.stopSleepTimer()
                    }
                )
                Spacer(Modifier.height(20.dp))
            }

            // Quick Timer Section
            SectionCard(
                icon = Icons.Outlined.Timer,
                title = "Quick Timer",
                subtitle = null
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    quickPresets.forEach { (mins, label, icon) ->
                        PresetTile(
                            minutes = mins,
                            label = label,
                            icon = icon,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                playerViewModel.startSleepTimer(mins, selectedAction)
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Custom Timer Section
            SectionCard(
                icon = Icons.Outlined.AccessTime,
                title = "Custom Timer",
                subtitle = "Set a specific time duration"
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(GratiaTheme.colors.surfaceHover)
                        .clickableWithScale {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            showCustomDialog = true
                        }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = null,
                            tint = GratiaTheme.colors.textPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Set Custom Time",
                            fontFamily = Inter,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = GratiaTheme.colors.textPrimary
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Timer Action Section
            SectionCard(
                icon = Icons.Outlined.PlayArrow,
                title = "Timer Action",
                subtitle = "What happens when the timer ends"
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionOptionRow(
                        title = "Fade Out",
                        icon = Icons.Outlined.VolumeDown,
                        isSelected = selectedAction == SleepAction.FADE_OUT,
                        onClick = {
                            selectedAction = SleepAction.FADE_OUT
                            playerViewModel.sleepTimerManager.setAction(SleepAction.FADE_OUT)
                        }
                    )
                    ActionOptionRow(
                        title = "Pause",
                        icon = Icons.Outlined.Pause,
                        isSelected = selectedAction == SleepAction.PAUSE,
                        onClick = {
                            selectedAction = SleepAction.PAUSE
                            playerViewModel.sleepTimerManager.setAction(SleepAction.PAUSE)
                        }
                    )
                    ActionOptionRow(
                        title = "Stop",
                        icon = Icons.Outlined.Stop,
                        isSelected = selectedAction == SleepAction.STOP,
                        onClick = {
                            selectedAction = SleepAction.STOP
                            playerViewModel.sleepTimerManager.setAction(SleepAction.STOP)
                        }
                    )
                }
            }

            if (isActive) {
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        playerViewModel.stopSleepTimer()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GratiaTheme.colors.error.copy(alpha = 0.15f),
                        contentColor = GratiaTheme.colors.error
                    )
                ) {
                    Text(
                        "Turn Off Timer",
                        fontFamily = Inter,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }

    // Custom Time Dialog
    if (showCustomDialog) {
        var minutesText by remember { mutableStateOf("") }
        var isError by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showCustomDialog = false },
            title = {
                Text(
                    "Custom Duration",
                    fontFamily = SpaceGrotesk,
                    fontWeight = FontWeight.Bold,
                    color = GratiaTheme.colors.textPrimary
                )
            },
            text = {
                Column {
                    Text(
                        "Enter duration in minutes (1 to 720):",
                        fontFamily = Inter,
                        fontSize = 13.sp,
                        color = GratiaTheme.colors.textSecondary
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = minutesText,
                        onValueChange = { input ->
                            if (input.all { it.isDigit() }) {
                                minutesText = input
                                isError = false
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        placeholder = { Text("e.g. 45") },
                        isError = isError,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (isError) {
                        Text(
                            "Please enter a valid duration (1-720 min)",
                            color = GratiaTheme.colors.error,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val mins = minutesText.toIntOrNull()
                        if (mins != null && mins in 1..720) {
                            playerViewModel.startSleepTimer(mins, selectedAction)
                            showCustomDialog = false
                        } else {
                            isError = true
                        }
                    }
                ) {
                    Text("Set Timer", color = GratiaTheme.colors.accent, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomDialog = false }) {
                    Text("Cancel", color = GratiaTheme.colors.textSecondary)
                }
            },
            containerColor = GratiaTheme.colors.surface
        )
    }
}

@Composable
private fun SectionCard(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(GratiaTheme.colors.surfaceHover.copy(alpha = 0.5f))
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = GratiaTheme.colors.textPrimary,
                modifier = Modifier.size(18.dp)
            )
            Column {
                Text(
                    text = title,
                    fontFamily = SpaceGrotesk,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = GratiaTheme.colors.textPrimary
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        fontFamily = Inter,
                        fontSize = 12.sp,
                        color = GratiaTheme.colors.textSecondary
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        content()
    }
}

@Composable
private fun PresetTile(
    minutes: Int,
    label: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(width = 80.dp, height = 80.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(GratiaTheme.colors.surfaceHover)
            .clickableWithScale(onClick = onClick)
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = GratiaTheme.colors.textPrimary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = label,
                fontFamily = Inter,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                color = GratiaTheme.colors.textPrimary
            )
        }
    }
}

@Composable
private fun ActionOptionRow(
    title: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bgColor = if (isSelected) GratiaTheme.colors.surface else GratiaTheme.colors.surfaceHover.copy(alpha = 0.6f)
    val textColor = if (isSelected) GratiaTheme.colors.textPrimary else GratiaTheme.colors.textSecondary
    val iconTint = if (isSelected) GratiaTheme.colors.accent else GratiaTheme.colors.textSecondary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .clickableWithScale(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = title,
                fontFamily = Inter,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                fontSize = 14.sp,
                color = textColor
            )
        }

        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Selected",
                tint = GratiaTheme.colors.accent,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun ActiveTimerCard(
    remainingMs: Long,
    action: SleepAction,
    onAddMinutes: (Int) -> Unit,
    onStop: () -> Unit
) {
    val totalSeconds = remainingMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    val timeString = if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(GratiaTheme.colors.surfaceHover)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Timer Active",
            fontFamily = SpaceGrotesk,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = GratiaTheme.colors.accent
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = timeString,
            fontFamily = JetBrainsMono,
            fontWeight = FontWeight.Bold,
            fontSize = 36.sp,
            color = GratiaTheme.colors.textPrimary
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Action: ${action.name.replace("_", " ")}",
            fontFamily = Inter,
            fontSize = 12.sp,
            color = GratiaTheme.colors.textSecondary
        )
        Spacer(Modifier.height(16.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AdjustPill("-5m") { onAddMinutes(-5) }
            AdjustPill("+5m") { onAddMinutes(5) }
            AdjustPill("+15m") { onAddMinutes(15) }
        }
    }
}

@Composable
private fun AdjustPill(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(GratiaTheme.colors.surface)
            .clickableWithScale(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontFamily = Inter,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            color = GratiaTheme.colors.textPrimary
        )
    }
}
