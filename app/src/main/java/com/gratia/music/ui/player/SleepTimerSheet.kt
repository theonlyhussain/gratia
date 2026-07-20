package com.gratia.music.ui.player

import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gratia.music.player.PlayerViewModel
import com.gratia.music.player.SleepAction
import com.gratia.music.ui.components.GratiaButton
import com.gratia.music.ui.components.GratiaText
import com.gratia.music.ui.components.clickableWithScale
import com.gratia.music.ui.theme.GratiaTheme
import com.gratia.music.ui.theme.JetBrainsMono
import kotlinx.coroutines.delay

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

    val options = listOf(
        Pair(5, "5 min"),
        Pair(15, "15 min"),
        Pair(30, "30 min"),
        Pair(45, "45 min"),
        Pair(60, "1 hour"),
        Pair(90, "1.5 hr")
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(GratiaTheme.spacing.large),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Drag handle
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(4.dp)
                .clip(CircleShape)
                .background(GratiaTheme.colors.textSecondary.copy(alpha = 0.3f))
        )
        
        Spacer(Modifier.height(GratiaTheme.spacing.large))
        
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            GratiaText(
                text = "Sleep Timer",
                style = GratiaTheme.typography.title,
                color = GratiaTheme.colors.textPrimary
            )
            
            if (isActive) {
                // Cancel button
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(GratiaTheme.colors.surfaceHover)
                        .clickableWithScale {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            playerViewModel.stopSleepTimer()
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    GratiaText(
                        text = "Stop",
                        style = GratiaTheme.typography.caption.copy(fontWeight = FontWeight.Bold),
                        color = GratiaTheme.colors.accent
                    )
                }
            }
        }

        Spacer(Modifier.height(GratiaTheme.spacing.large))

        AnimatedContent(
            targetState = isActive,
            label = "sleep_timer_content",
            transitionSpec = {
                (slideInVertically { height -> height } + fadeIn() togetherWith
                 slideOutVertically { height -> -height } + fadeOut()).using(
                    SizeTransform(clip = false)
                )
            }
        ) { active ->
            if (active) {
                // Active State View
                ActiveTimerView(
                    remainingMs = remainingMs,
                    onAddMinutes = { mins ->
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        val newTotalMinutes = (remainingMs / 60000).toInt() + mins
                        playerViewModel.startSleepTimer(newTotalMinutes, currentAction)
                    }
                )
            } else {
                // Presets View
                Column {
                    // Action Selector
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .background(GratiaTheme.colors.surfaceHover, RoundedCornerShape(22.dp))
                            .padding(4.dp)
                    ) {
                        SleepAction.values().forEach { action ->
                            val isSelected = selectedAction == action
                            val bgColor by animateColorAsState(
                                targetValue = if (isSelected) GratiaTheme.colors.surface else Color.Transparent,
                                label = "action_bg"
                            )
                            val textColor by animateColorAsState(
                                targetValue = if (isSelected) GratiaTheme.colors.textPrimary else GratiaTheme.colors.textSecondary,
                                label = "action_text"
                            )
                            
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(bgColor)
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        selectedAction = action
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                val label = when(action) {
                                    SleepAction.FADE_OUT -> "Fade"
                                    SleepAction.PAUSE -> "Pause"
                                    SleepAction.STOP -> "Stop"
                                }
                                GratiaText(
                                    text = label,
                                    style = GratiaTheme.typography.caption.copy(fontWeight = FontWeight.Medium),
                                    color = textColor
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(GratiaTheme.spacing.large))

                    // Time Grid
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        horizontalArrangement = Arrangement.spacedBy(GratiaTheme.spacing.medium),
                        verticalArrangement = Arrangement.spacedBy(GratiaTheme.spacing.medium),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(options) { (minutes, label) ->
                            TimerPresetCard(
                                label = label,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    playerViewModel.startSleepTimer(minutes, selectedAction)
                                }
                            )
                        }
                    }
                }
            }
        }
        
        Spacer(Modifier.height(GratiaTheme.spacing.large))
    }
}

@Composable
private fun ActiveTimerView(
    remainingMs: Long,
    onAddMinutes: (Int) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
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
        
        Box(
            modifier = Modifier
                .size(160.dp)
                .clip(CircleShape)
                .border(2.dp, GratiaTheme.colors.accent, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            GratiaText(
                text = timeString,
                style = androidx.compose.ui.text.TextStyle(
                    fontFamily = JetBrainsMono,
                    fontWeight = FontWeight.Medium,
                    fontSize = 32.sp
                ),
                color = GratiaTheme.colors.accent
            )
        }
        
        Spacer(Modifier.height(GratiaTheme.spacing.heroSmall))
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(GratiaTheme.spacing.medium)
        ) {
            TimerAdjustButton("-5m") { onAddMinutes(-5) }
            TimerAdjustButton("+5m") { onAddMinutes(5) }
            TimerAdjustButton("+15m") { onAddMinutes(15) }
        }
    }
}

@Composable
private fun TimerAdjustButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(GratiaTheme.colors.surfaceHover)
            .clickableWithScale(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        GratiaText(
            text = label,
            style = GratiaTheme.typography.body.copy(fontWeight = FontWeight.Medium),
            color = GratiaTheme.colors.textPrimary
        )
    }
}

@Composable
private fun TimerPresetCard(
    label: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .aspectRatio(1.2f)
            .clip(GratiaTheme.shapes.medium)
            .background(GratiaTheme.colors.surfaceHover)
            .clickableWithScale(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Bedtime,
                contentDescription = null,
                tint = GratiaTheme.colors.textSecondary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.height(8.dp))
            GratiaText(
                text = label,
                style = GratiaTheme.typography.caption.copy(fontWeight = FontWeight.Medium),
                color = GratiaTheme.colors.textPrimary
            )
        }
    }
}
