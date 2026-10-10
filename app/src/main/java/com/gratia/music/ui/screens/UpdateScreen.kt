package com.gratia.music.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.gratia.music.GratiaApp
import com.gratia.music.ui.components.AppleLargeTitleHeader
import com.gratia.music.ui.components.AppleSectionHeader
import com.gratia.music.ui.components.GratiaButton
import com.gratia.music.ui.components.GratiaText
import com.gratia.music.ui.theme.GratiaTheme
import com.gratia.music.updater.UpdateState
import kotlinx.coroutines.launch

/**
 * Gratia's updater.
 *
 * Manual by design: this screen is the only place an update can start, and it
 * only starts because someone pressed Check for updates. The layout is a plain
 * system-updater shape — what you have, what is available, and one button that
 * does the next sensible thing — because an updater is the last place a user
 * should have to wonder whether the app is telling them the truth.
 *
 * Animation is deliberately thin: the status block cross-fades between states,
 * the progress line animates its own value, and nothing else moves. There is no
 * scanning bar, no glow, and no per-element choreography.
 */
@Composable
fun UpdateScreen(
    onNavigateBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val updateManager = GratiaApp.instance.updateManager
    val state by updateManager.state.collectAsState()

    val currentVersion = remember { updateManager.currentVersionName() }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(GratiaTheme.colors.background)
            .statusBarsPadding(),
        contentPadding = PaddingValues(bottom = GratiaTheme.spacing.heroLarge)
    ) {
        item {
            AppleLargeTitleHeader(
                title = "Updates",
                onBack = onNavigateBack
            )
            Spacer(Modifier.height(4.dp))
        }

        item {
            AppleSectionHeader(title = "Gratia")
            VersionCard(
                currentVersion = currentVersion,
                state = state
            )
            Spacer(Modifier.height(16.dp))
        }

        item {
            UpdateActionBlock(
                state = state,
                onCheck = { scope.launch { updateManager.checkForUpdate(manualCheck = true, force = true) } },
                onDownload = { url -> scope.launch { updateManager.downloadUpdate(url) } },
                onCancelDownload = { updateManager.cancelDownload() },
                onInstall = { file -> updateManager.installUpdate(file) }
            )
            Spacer(Modifier.height(24.dp))
        }

        item {
            GratiaText(
                text = "Gratia checks for updates only when you ask it to. Nothing is downloaded in the background.",
                style = GratiaTheme.typography.caption,
                color = GratiaTheme.colors.textSecondary,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
        }
    }
}

/** The two version numbers the screen exists to state, plus the live status. */
@Composable
private fun VersionCard(
    currentVersion: String,
    state: UpdateState
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(GratiaTheme.colors.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        VersionRow("Current version", currentVersion, emphasised = false)
        VersionRow(
            label = "Latest version",
            value = latestVersionOf(state) ?: "—",
            emphasised = state is UpdateState.UpdateAvailable
        )

        Spacer(Modifier.height(2.dp))

        // One status line, cross-faded on change. The cached content around it
        // never blanks out — this is feedback, not a loading screen.
        AnimatedContent(
            targetState = statusOf(state),
            transitionSpec = {
                fadeIn(tween(180)) togetherWith fadeOut(tween(180))
            },
            label = "updateStatus"
        ) { status ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (state is UpdateState.Checking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = GratiaTheme.colors.accent
                    )
                    Spacer(Modifier.width(10.dp))
                }
                GratiaText(
                    text = status,
                    style = GratiaTheme.typography.body,
                    color = when (state) {
                        is UpdateState.Error -> GratiaTheme.colors.error
                        is UpdateState.UpdateAvailable -> GratiaTheme.colors.textPrimary
                        else -> GratiaTheme.colors.textSecondary
                    }
                )
            }
        }
    }
}

@Composable
private fun VersionRow(label: String, value: String, emphasised: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        GratiaText(
            text = label,
            style = GratiaTheme.typography.body,
            color = GratiaTheme.colors.textSecondary
        )
        GratiaText(
            text = value,
            style = GratiaTheme.typography.body,
            color = if (emphasised) GratiaTheme.colors.accent else GratiaTheme.colors.textPrimary
        )
    }
}

/**
 * The single action the current state calls for, plus release notes and download
 * progress when they exist. Slots are shown and hidden rather than re-laid out,
 * so pressing a button never makes the screen jump.
 */
@Composable
private fun UpdateActionBlock(
    state: UpdateState,
    onCheck: () -> Unit,
    onDownload: (String) -> Unit,
    onCancelDownload: () -> Unit,
    onInstall: (java.io.File) -> Unit
) {
    var notesExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .animateContentSize(animationSpec = tween(220))
    ) {
        when (state) {
            is UpdateState.UpdateAvailable -> {
                ReleaseNotes(
                    notes = state.changelog,
                    expanded = notesExpanded,
                    onToggle = { notesExpanded = !notesExpanded }
                )
                Spacer(Modifier.height(12.dp))
                GratiaButton(
                    text = buildString {
                        append("Download update")
                        state.sizeBytes?.let { append(" · ${formatBytes(it)}") }
                    },
                    onClick = { onDownload(state.downloadUrl) },
                    backgroundColor = GratiaTheme.colors.accent,
                    contentColor = GratiaTheme.colors.background,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onCheck) {
                    GratiaText(
                        text = "Check again",
                        style = GratiaTheme.typography.body,
                        color = GratiaTheme.colors.textSecondary
                    )
                }
            }

            is UpdateState.Downloading -> {
                DownloadProgress(state)
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = onCancelDownload) {
                    GratiaText(
                        text = "Cancel download",
                        style = GratiaTheme.typography.body,
                        color = GratiaTheme.colors.textSecondary
                    )
                }
            }

            is UpdateState.ReadyToInstall -> {
                GratiaButton(
                    text = "Install update",
                    onClick = { onInstall(state.apkFile) },
                    backgroundColor = GratiaTheme.colors.accent,
                    contentColor = GratiaTheme.colors.background,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onCheck) {
                    GratiaText(
                        text = "Check again",
                        style = GratiaTheme.typography.body,
                        color = GratiaTheme.colors.textSecondary
                    )
                }
            }

            is UpdateState.Error -> {
                GratiaButton(
                    text = "Try again",
                    onClick = onCheck,
                    backgroundColor = GratiaTheme.colors.surfaceHover,
                    contentColor = GratiaTheme.colors.textPrimary,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            is UpdateState.Checking -> {
                // The status line above is the feedback; a second spinner here
                // would be the screen talking over itself.
                GratiaButton(
                    text = "Checking…",
                    onClick = {},
                    backgroundColor = GratiaTheme.colors.surfaceHover,
                    contentColor = GratiaTheme.colors.textSecondary,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            is UpdateState.Idle, is UpdateState.UpToDate -> {
                GratiaButton(
                    text = "Check for updates",
                    onClick = onCheck,
                    backgroundColor = GratiaTheme.colors.surfaceHover,
                    contentColor = GratiaTheme.colors.textPrimary,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/** Real bytes, real percentage — or an honest indeterminate line when unknown. */
@Composable
private fun DownloadProgress(state: UpdateState.Downloading) {
    val known = state.totalBytes > 0L
    val animatedProgress by animateFloatAsState(
        targetValue = state.progress,
        animationSpec = tween(160),
        label = "downloadProgress"
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        if (known) {
            GratiaText(
                text = "${formatBytes(state.downloadedBytes)} / ${formatBytes(state.totalBytes)} · " +
                    "${(state.progress * 100).toInt()}%",
                style = GratiaTheme.typography.caption,
                color = GratiaTheme.colors.textSecondary
            )
        } else {
            GratiaText(
                text = "Downloading… ${formatBytes(state.downloadedBytes)}",
                style = GratiaTheme.typography.caption,
                color = GratiaTheme.colors.textSecondary
            )
        }
        Spacer(Modifier.height(8.dp))
        if (known) {
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = GratiaTheme.colors.accent,
                trackColor = GratiaTheme.colors.surfaceHover
            )
        } else {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = GratiaTheme.colors.accent,
                trackColor = GratiaTheme.colors.surfaceHover
            )
        }
    }
}

/** Release notes, clamped until asked for, so a long changelog stays readable. */
@Composable
private fun ReleaseNotes(
    notes: String,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    val long = notes.length > 400

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(GratiaTheme.colors.surface)
            .padding(16.dp)
    ) {
        GratiaText(
            text = "WHAT'S NEW",
            style = GratiaTheme.typography.caption,
            color = GratiaTheme.colors.textSecondary
        )
        Spacer(Modifier.height(8.dp))
        GratiaText(
            text = notes,
            style = GratiaTheme.typography.body,
            color = GratiaTheme.colors.textPrimary,
            maxLines = if (expanded || !long) Int.MAX_VALUE else 8
        )
        if (long) {
            Spacer(Modifier.height(4.dp))
            GratiaText(
                text = if (expanded) "Show less" else "Show more",
                style = GratiaTheme.typography.caption,
                color = GratiaTheme.colors.accent,
                modifier = Modifier.clickable { onToggle() }
            )
        }
    }
}

private fun latestVersionOf(state: UpdateState): String? = when (state) {
    is UpdateState.UpdateAvailable -> "v${state.version}"
    is UpdateState.UpToDate -> "v${state.latestVersion}"
    is UpdateState.Downloading -> state.version.takeIf { it.isNotBlank() }?.let { "v$it" }
    is UpdateState.ReadyToInstall -> state.version.takeIf { it.isNotBlank() }?.let { "v$it" }
    else -> null
}

private fun statusOf(state: UpdateState): String = when (state) {
    is UpdateState.Idle -> "No check has been made yet."
    is UpdateState.Checking -> "Checking for updates…"
    is UpdateState.UpToDate -> "You're up to date."
    is UpdateState.UpdateAvailable -> "Update available: v${state.version}"
    is UpdateState.Downloading -> if (state.totalBytes > 0L) {
        "Downloading update… ${(state.progress * 100).toInt()}%"
    } else {
        "Downloading update…"
    }
    is UpdateState.ReadyToInstall -> "Ready to install."
    is UpdateState.Error -> state.message
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 MB"
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb < 1.0) {
        "${(bytes / 1024.0).toInt()} KB"
    } else {
        String.format(java.util.Locale.US, "%.1f MB", mb)
    }
}
