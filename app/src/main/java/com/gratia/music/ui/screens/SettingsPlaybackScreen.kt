package com.gratia.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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

        // --- STREAMING QUALITY (the original per-network model) ---
        item {
            Spacer(Modifier.height(24.dp))

            GratiaText(
                text = "STREAMING QUALITY",
                style = GratiaTheme.typography.caption,
                color = GratiaTheme.colors.textSecondary,
                modifier = Modifier.padding(start = 32.dp, bottom = 8.dp)
            )

            val wifiQuality by settingsDataStore.wifiQualityFlow.collectAsState(initial = "BEST")
            val mobileQuality by settingsDataStore.mobileQualityFlow.collectAsState(initial = "NORMAL")
            // No lossless source is configured in this build, so Lossless is
            // never offered here — the setting must not advertise what no
            // source can deliver.
            val losslessAvailable = false

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(GratiaTheme.colors.surface)
                    .padding(16.dp)
            ) {
                QualitySettingRow(
                    label = "On Wi-Fi",
                    current = wifiQuality,
                    losslessAvailable = losslessAvailable,
                    onSelect = { scope.launch { settingsDataStore.setWifiQuality(it) } }
                )
                Spacer(Modifier.height(16.dp))
                QualitySettingRow(
                    label = "On Mobile Data",
                    current = mobileQuality,
                    losslessAvailable = losslessAvailable,
                    onSelect = { scope.launch { settingsDataStore.setMobileQuality(it) } }
                )
                Spacer(Modifier.height(8.dp))
                GratiaText(
                    text = "Gratia always starts with YouTube's stream. When a better rendition of the same recording is genuinely available, it may swap over — matched by title, artist and duration, never by title alone.",
                    style = GratiaTheme.typography.caption,
                    color = GratiaTheme.colors.textSecondary
                )
            }
        }

        // --- BETTER-RENDITION SOURCES ---
        item {
            Spacer(Modifier.height(24.dp))

            GratiaText(
                text = "SOURCES",
                style = GratiaTheme.typography.caption,
                color = GratiaTheme.colors.textSecondary,
                modifier = Modifier.padding(start = 32.dp, bottom = 8.dp)
            )

            val jioSaavnEnabled by settingsDataStore.jioSaavnEnabledFlow.collectAsState(initial = false)

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
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        GratiaText(
                            text = "JioSaavn better renditions",
                            style = GratiaTheme.typography.body,
                            color = GratiaTheme.colors.textPrimary
                        )
                        Spacer(Modifier.height(2.dp))
                        GratiaText(
                            text = "Look for a higher-bitrate AAC copy of the same recording while it plays. Never downgrades, never a sound \"enhancer\".",
                            style = GratiaTheme.typography.caption,
                            color = GratiaTheme.colors.textSecondary
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = jioSaavnEnabled,
                        onCheckedChange = { enabled ->
                            scope.launch {
                                settingsDataStore.setJioSaavnEnabled(enabled)
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = GratiaTheme.colors.background,
                            checkedTrackColor = GratiaTheme.colors.accent,
                            uncheckedThumbColor = GratiaTheme.colors.textSecondary,
                            uncheckedTrackColor = GratiaTheme.colors.surfaceHover
                        )
                    )
                }
            }
        }

        // --- LYRICS ---
        item {
            Spacer(Modifier.height(24.dp))

            GratiaText(
                text = "LYRICS",
                style = GratiaTheme.typography.caption,
                color = GratiaTheme.colors.textSecondary,
                modifier = Modifier.padding(start = 32.dp, bottom = 8.dp)
            )

            val animatedWordLyrics by settingsDataStore.animatedWordLyricsFlow.collectAsState(initial = true)
            val reduceAnimation by settingsDataStore.reduceAnimationFlow.collectAsState(initial = false)
            val lyricsScrollDebug by settingsDataStore.lyricsScrollDebugFlow.collectAsState(initial = false)

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
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        GratiaText(
                            text = "Animated word lyrics",
                            style = GratiaTheme.typography.body,
                            color = GratiaTheme.colors.textPrimary
                        )
                        Spacer(Modifier.height(2.dp))
                        GratiaText(
                            text = "Animate word-synced lyrics as they are sung.",
                            style = GratiaTheme.typography.caption,
                            color = GratiaTheme.colors.textSecondary
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = animatedWordLyrics,
                        onCheckedChange = { enabled ->
                            scope.launch {
                                settingsDataStore.setAnimatedWordLyrics(enabled)
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = GratiaTheme.colors.background,
                            checkedTrackColor = GratiaTheme.colors.accent,
                            uncheckedThumbColor = GratiaTheme.colors.textSecondary,
                            uncheckedTrackColor = GratiaTheme.colors.surfaceHover
                        )
                    )
                }

                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        GratiaText(
                            text = "Reduce animation",
                            style = GratiaTheme.typography.body,
                            color = GratiaTheme.colors.textPrimary
                        )
                        Spacer(Modifier.height(2.dp))
                        GratiaText(
                            text = "Hold lyrics still — no sweep, no bloom, and the page jumps to the line being sung.",
                            style = GratiaTheme.typography.caption,
                            color = GratiaTheme.colors.textSecondary
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = reduceAnimation,
                        onCheckedChange = { enabled ->
                            scope.launch {
                                settingsDataStore.setReduceAnimation(enabled)
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = GratiaTheme.colors.background,
                            checkedTrackColor = GratiaTheme.colors.accent,
                            uncheckedThumbColor = GratiaTheme.colors.textSecondary,
                            uncheckedTrackColor = GratiaTheme.colors.surfaceHover
                        )
                    )
                }

                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        GratiaText(
                            text = "Lyrics scroll debug",
                            style = GratiaTheme.typography.body,
                            color = GratiaTheme.colors.textPrimary
                        )
                        Spacer(Modifier.height(2.dp))
                        GratiaText(
                            text = "Overlay the safe band, the lines being sung and the scroll the follow would make.",
                            style = GratiaTheme.typography.caption,
                            color = GratiaTheme.colors.textSecondary
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = lyricsScrollDebug,
                        onCheckedChange = { enabled ->
                            scope.launch {
                                settingsDataStore.setLyricsScrollDebug(enabled)
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = GratiaTheme.colors.background,
                            checkedTrackColor = GratiaTheme.colors.accent,
                            uncheckedThumbColor = GratiaTheme.colors.textSecondary,
                            uncheckedTrackColor = GratiaTheme.colors.surfaceHover
                        )
                    )
                }
            }
        }
    }
}

/**
 * Playback diagnostics for the currently playing remote track — the "stats for
 * nerds" view that makes the source-selection system verifiable: which source
 * is serving the track, what the decoder reports, and what the resolver
 * promised. Developer aid only.
 */
@Composable
fun PlaybackDiagnosticsCard(
    source: com.gratia.music.provider.PlaybackSource?,
    audioFormat: com.gratia.music.player.AudioFormatInfo?,
    modifier: Modifier = Modifier,
) {
    if (source == null && audioFormat == null) return
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(GratiaTheme.colors.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        GratiaText(
            text = "PLAYBACK DIAGNOSTICS",
            style = GratiaTheme.typography.caption,
            color = GratiaTheme.colors.textSecondary
        )
        source?.let {
            DiagnosticsRow("Source", "YouTube Music")
            DiagnosticsRow("Promised format", it.format ?: "unknown")
            DiagnosticsRow("Promised bitrate", it.bitrate?.let { b -> "$b kbps" } ?: "unreported")
        }
        audioFormat?.let {
            DiagnosticsRow("Decoder mime", it.mimeType ?: "unknown")
            DiagnosticsRow("Sample rate", "${it.sampleRate} Hz")
            DiagnosticsRow("Bit depth", if (it.bitDepth > 0) "${it.bitDepth}-bit" else "unreported")
            DiagnosticsRow("Decoder bitrate", if (it.bitrate > 0) "${it.bitrate / 1000} kbps" else "unreported")
        }
    }
}

@Composable
private fun DiagnosticsRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        GratiaText(
            text = label,
            style = GratiaTheme.typography.caption,
            color = GratiaTheme.colors.textSecondary
        )
        GratiaText(
            text = value,
            style = GratiaTheme.typography.caption,
            color = GratiaTheme.colors.textPrimary
        )
    }
}

/** One per-network quality selector, offering only what actually exists. */
@Composable
private fun QualitySettingRow(
    label: String,
    current: String,
    losslessAvailable: Boolean,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val options = listOfNotNull(
        "LOW" to "Low · data saver",
        "NORMAL" to "Normal",
        "HIGH" to "High",
        "BEST" to "Best available",
        if (losslessAvailable) "LOSSLESS" to "Lossless" else null,
    )
    val currentLabel = options.firstOrNull { it.first == current }?.second ?: "Best available"

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            GratiaText(
                text = label,
                style = GratiaTheme.typography.body,
                color = GratiaTheme.colors.textPrimary
            )
            GratiaText(
                text = currentLabel,
                style = GratiaTheme.typography.caption,
                color = GratiaTheme.colors.textSecondary
            )
        }
        Box {
            TextButton(onClick = { expanded = true }) {
                Text("Change")
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEach { (value, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            expanded = false
                            onSelect(value)
                        }
                    )
                }
            }
        }
    }
}

