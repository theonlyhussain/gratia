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

/**
 * Lyrics preferences, on their own screen rather than buried at the bottom of
 * Playback.
 *
 * Lyrics have enough knobs now — word animation, motion, focus blur, the scroll
 * debug overlay — that a shared page made them read as an afterthought to
 * crossfade and quality. They get their own section, in the order a listener
 * meets them: how lyrics animate, how they settle, how far-out ones look.
 *
 * Every switch here controls *presentation only*. None of them turns off
 * synchronisation, word timing, auto-scroll or the active-line highlight.
 */
@Composable
fun SettingsLyricsScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsDataStore = remember { SettingsDataStore(context) }

    val animatedWordLyrics by settingsDataStore.animatedWordLyricsFlow.collectAsState(initial = true)
    val reduceAnimation by settingsDataStore.reduceAnimationFlow.collectAsState(initial = false)
    val blurUnfocusedLyrics by settingsDataStore.blurUnfocusedLyricsFlow.collectAsState(initial = true)
    val lyricsScrollDebug by settingsDataStore.lyricsScrollDebugFlow.collectAsState(initial = false)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(GratiaTheme.colors.background)
            .statusBarsPadding(),
        contentPadding = PaddingValues(bottom = GratiaTheme.spacing.heroLarge)
    ) {
        item {
            AppleLargeTitleHeader(
                title = "Lyrics",
                onBack = onNavigateBack
            )
            Spacer(Modifier.height(8.dp))
        }

        item {
            GratiaText(
                text = "SYNCED LYRICS",
                style = GratiaTheme.typography.caption,
                color = GratiaTheme.colors.textSecondary,
                modifier = Modifier.padding(start = 32.dp, bottom = 8.dp)
            )

            LyricsSectionCard {
                LyricsSwitchRow(
                    title = "Animated word lyrics",
                    subtitle = "Animate word-synced lyrics as they are sung.",
                    checked = animatedWordLyrics,
                    onCheckedChange = { enabled ->
                        scope.launch { settingsDataStore.setAnimatedWordLyrics(enabled) }
                    }
                )
                LyricsRowDivider()
                LyricsSwitchRow(
                    title = "Reduce animation",
                    subtitle = "Hold lyrics still — no sweep, no bloom, and the page jumps to the line being sung.",
                    checked = reduceAnimation,
                    onCheckedChange = { enabled ->
                        scope.launch { settingsDataStore.setReduceAnimation(enabled) }
                    }
                )
                LyricsRowDivider()
                LyricsSwitchRow(
                    title = "Blur unfocused lyrics",
                    subtitle = "Soften lyrics that are not currently in focus.",
                    checked = blurUnfocusedLyrics,
                    onCheckedChange = { enabled ->
                        scope.launch { settingsDataStore.setBlurUnfocusedLyrics(enabled) }
                    }
                )
            }

            Spacer(Modifier.height(24.dp))

            GratiaText(
                text = "DEVELOPER",
                style = GratiaTheme.typography.caption,
                color = GratiaTheme.colors.textSecondary,
                modifier = Modifier.padding(start = 32.dp, bottom = 8.dp)
            )

            LyricsSectionCard {
                LyricsSwitchRow(
                    title = "Lyrics scroll debug",
                    subtitle = "Overlay the safe band, the lines being sung and the scroll the follow would make.",
                    checked = lyricsScrollDebug,
                    onCheckedChange = { enabled ->
                        scope.launch { settingsDataStore.setLyricsScrollDebug(enabled) }
                    }
                )
            }
        }
    }
}

@Composable
private fun LyricsSectionCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(GratiaTheme.colors.surface)
            .padding(vertical = 4.dp),
        content = content
    )
}

@Composable
private fun LyricsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            GratiaText(
                text = title,
                style = GratiaTheme.typography.body,
                color = GratiaTheme.colors.textPrimary
            )
            Spacer(Modifier.height(2.dp))
            GratiaText(
                text = subtitle,
                style = GratiaTheme.typography.caption,
                color = GratiaTheme.colors.textSecondary
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = GratiaTheme.colors.background,
                checkedTrackColor = GratiaTheme.colors.accent,
                uncheckedThumbColor = GratiaTheme.colors.textSecondary,
                uncheckedTrackColor = GratiaTheme.colors.surfaceHover
            )
        )
    }
}

@Composable
private fun LyricsRowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        thickness = 0.5.dp,
        color = GratiaTheme.colors.textSecondary.copy(alpha = 0.15f)
    )
}
