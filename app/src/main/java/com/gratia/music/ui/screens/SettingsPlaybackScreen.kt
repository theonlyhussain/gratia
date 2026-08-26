package com.gratia.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.gratia.music.data.SettingsDataStore
import com.gratia.music.ui.components.AppleLargeTitleHeader
import com.gratia.music.ui.components.GratiaText
import com.gratia.music.ui.theme.GratiaTheme
import kotlinx.coroutines.launch

@Composable
fun SettingsPlaybackScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsDataStore = remember { SettingsDataStore(context) }
    
    val crossfadeDurationMs by settingsDataStore.crossfadeDurationFlow.collectAsState(initial = 4000)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(GratiaTheme.colors.background)
            .statusBarsPadding(),
        contentPadding = PaddingValues(bottom = GratiaTheme.spacing.heroLarge)
    ) {
        item {
            AppleLargeTitleHeader(
                title = "Playback",
                onBack = onNavigateBack
            )
            Spacer(Modifier.height(8.dp))
        }

        item {
            GratiaText(
                text = "CROSSFADE",
                style = GratiaTheme.typography.caption,
                color = GratiaTheme.colors.textSecondary,
                modifier = Modifier.padding(start = 32.dp, bottom = 8.dp)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(GratiaTheme.colors.surface)
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    GratiaText(
                        text = "Crossfade Duration",
                        style = GratiaTheme.typography.body,
                        color = GratiaTheme.colors.textPrimary
                    )
                    GratiaText(
                        text = if (crossfadeDurationMs == 0) "Off" else "${crossfadeDurationMs / 1000}s",
                        style = GratiaTheme.typography.body,
                        color = GratiaTheme.colors.textSecondary
                    )
                }
                
                Spacer(Modifier.height(8.dp))
                
                Slider(
                    value = crossfadeDurationMs.toFloat(),
                    onValueChange = { newValue ->
                        scope.launch {
                            settingsDataStore.setCrossfadeDuration(newValue.toInt())
                        }
                    },
                    valueRange = 0f..15000f,
                    steps = 14, // 15 seconds total, 1 second intervals (0, 1000, 2000, ..., 15000)
                    colors = SliderDefaults.colors(
                        thumbColor = GratiaTheme.colors.textPrimary,
                        activeTrackColor = GratiaTheme.colors.accent,
                        inactiveTrackColor = GratiaTheme.colors.surfaceHover
                    )
                )
                
                GratiaText(
                    text = "Smoothly transition between songs by fading out the current song and fading in the next one.",
                    style = GratiaTheme.typography.caption,
                    color = GratiaTheme.colors.textSecondary
                )
            }
        }
    }
}
