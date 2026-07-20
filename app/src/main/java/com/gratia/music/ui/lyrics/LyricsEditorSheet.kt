package com.gratia.music.ui.lyrics

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gratia.music.data.model.SongEntity
import com.gratia.music.ui.components.CoverArtImage
import com.gratia.music.ui.theme.GratiaTheme
import com.gratia.music.ui.theme.Inter
import com.gratia.music.ui.theme.JetBrainsMono
import com.gratia.music.ui.theme.SpaceGrotesk

/**
 * Premium full-screen lyrics editor.
 *
 * Replaces the old AlertDialog-based editor with a ModalBottomSheet
 * featuring format toggle, monospaced LRC editing, line numbers,
 * and a real-time timestamp insertion button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsEditorSheet(
    song: SongEntity,
    initialLyrics: String,
    currentTimeMs: Long,
    onDismiss: () -> Unit,
    onSave: (String, Boolean) -> Unit
) {
    // Detect initial mode from content
    val initialSynced = remember(initialLyrics) {
        initialLyrics.contains(Regex("\\[\\d{2}:\\d{2}"))
    }
    var isSyncedMode by remember { mutableStateOf(initialSynced) }
    var textFieldValue by remember {
        mutableStateOf(TextFieldValue(text = initialLyrics))
    }
    val focusRequester = remember { FocusRequester() }
    val verticalScrollState = rememberScrollState()

    // Request focus after composition
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = GratiaTheme.colors.background,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = null,
        modifier = Modifier.fillMaxHeight(0.95f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
        ) {
            // ── Top Bar ──────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = GratiaTheme.spacing.base,
                        vertical = GratiaTheme.spacing.mediumSmall
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cancel",
                        tint = GratiaTheme.colors.textSecondary
                    )
                }

                Spacer(Modifier.weight(1f))

                Text(
                    text = "Edit Lyrics",
                    fontFamily = SpaceGrotesk,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = GratiaTheme.colors.textPrimary
                )

                Spacer(Modifier.weight(1f))

                IconButton(
                    onClick = { onSave(textFieldValue.text, isSyncedMode) }
                ) {
                    Icon(
                        imageVector = Icons.Default.Save,
                        contentDescription = "Save",
                        tint = GratiaTheme.colors.accent
                    )
                }
            }

            HorizontalDivider(color = GratiaTheme.colors.glassBorder)

            // ── Song Context Header ──────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = GratiaTheme.spacing.mediumLarge,
                        vertical = GratiaTheme.spacing.base
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CoverArtImage(
                    coverArtPath = song.coverArtPath,
                    title = song.title,
                    artist = song.artist,
                    size = 48.dp,
                    cornerRadius = 10.dp,
                    fontSize = 14.sp
                )
                Spacer(Modifier.width(GratiaTheme.spacing.mediumSmall))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        fontFamily = SpaceGrotesk,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        color = GratiaTheme.colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = song.artist,
                        fontFamily = Inter,
                        fontSize = 13.sp,
                        color = GratiaTheme.colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // ── Format Toggle Chips ──────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = GratiaTheme.spacing.mediumLarge),
                horizontalArrangement = Arrangement.spacedBy(GratiaTheme.spacing.small)
            ) {
                FormatChip(
                    label = "Plain",
                    selected = !isSyncedMode,
                    onClick = { isSyncedMode = false }
                )
                FormatChip(
                    label = "Synced (LRC)",
                    selected = isSyncedMode,
                    onClick = { isSyncedMode = true }
                )
            }

            Spacer(Modifier.height(GratiaTheme.spacing.mediumSmall))

            // ── Editor Area ──────────────────────────────────────
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = GratiaTheme.spacing.mediumSmall)
                    .clip(RoundedCornerShape(16.dp))
                    .background(GratiaTheme.colors.surface)
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    // Line number gutter (only in synced mode)
                    AnimatedContent(
                        targetState = isSyncedMode,
                        transitionSpec = {
                            fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) togetherWith
                                fadeOut(spring(stiffness = Spring.StiffnessMediumLow))
                        },
                        label = "gutterAnim"
                    ) { showGutter ->
                        if (showGutter) {
                            val lineCount = textFieldValue.text.lines().size.coerceAtLeast(1)
                            Column(
                                modifier = Modifier
                                    .width(40.dp)
                                    .fillMaxHeight()
                                    .verticalScroll(verticalScrollState)
                                    .padding(
                                        top = GratiaTheme.spacing.base,
                                        bottom = GratiaTheme.spacing.base
                                    ),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                for (i in 1..lineCount) {
                                    Text(
                                        text = "$i",
                                        fontFamily = JetBrainsMono,
                                        fontSize = 11.sp,
                                        color = GratiaTheme.colors.textSecondary.copy(alpha = 0.4f),
                                        lineHeight = 20.sp,
                                        modifier = Modifier.padding(vertical = 1.dp)
                                    )
                                }
                            }
                        } else {
                            Spacer(Modifier.width(GratiaTheme.spacing.mediumSmall))
                        }
                    }

                    // Main text field
                    BasicTextField(
                        value = textFieldValue,
                        onValueChange = { textFieldValue = it },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(verticalScrollState)
                            .padding(GratiaTheme.spacing.base)
                            .focusRequester(focusRequester),
                        textStyle = if (isSyncedMode) {
                            GratiaTheme.typography.monoMetadata.copy(
                                fontSize = 13.sp,
                                lineHeight = 20.sp,
                                color = GratiaTheme.colors.textPrimary
                            )
                        } else {
                            GratiaTheme.typography.body.copy(
                                fontSize = 15.sp,
                                lineHeight = 24.sp,
                                color = GratiaTheme.colors.textPrimary
                            )
                        },
                        cursorBrush = SolidColor(GratiaTheme.colors.accent),
                        decorationBox = { innerTextField ->
                            Box {
                                if (textFieldValue.text.isEmpty()) {
                                    Text(
                                        text = if (isSyncedMode)
                                            "[00:00.00] Paste or type synced lyrics…"
                                        else
                                            "Paste or type lyrics here…",
                                        fontFamily = if (isSyncedMode) JetBrainsMono else Inter,
                                        fontSize = if (isSyncedMode) 13.sp else 15.sp,
                                        color = GratiaTheme.colors.textSecondary.copy(alpha = 0.4f)
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                }
            }

            // ── Bottom Action Bar ────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = GratiaTheme.spacing.mediumLarge,
                        vertical = GratiaTheme.spacing.mediumSmall
                    ),
                horizontalArrangement = Arrangement.spacedBy(GratiaTheme.spacing.small),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Clear button
                OutlinedButton(
                    onClick = {
                        textFieldValue = TextFieldValue("")
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = GratiaTheme.colors.error
                    ),
                    border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                        brush = SolidColor(GratiaTheme.colors.error.copy(alpha = 0.3f))
                    )
                ) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(GratiaTheme.spacing.extraSmall))
                    Text("Clear", fontFamily = Inter, fontSize = 13.sp)
                }

                Spacer(Modifier.weight(1f))

                // Timestamp insert (only in synced mode)
                if (isSyncedMode) {
                    val timeTag = remember(currentTimeMs) {
                        formatLrcTimestamp(currentTimeMs)
                    }

                    FilledTonalButton(
                        onClick = {
                            val cursor = textFieldValue.selection.start
                            val currentText = textFieldValue.text
                            val tag = "$timeTag "
                            val newText = currentText.substring(0, cursor) +
                                    tag +
                                    currentText.substring(cursor)
                            textFieldValue = TextFieldValue(
                                text = newText,
                                selection = TextRange(cursor + tag.length)
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = GratiaTheme.colors.accent.copy(alpha = 0.15f),
                            contentColor = GratiaTheme.colors.accent
                        )
                    ) {
                        Icon(
                            Icons.Default.Timer,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(GratiaTheme.spacing.extraSmall))
                        Text(
                            text = timeTag,
                            fontFamily = JetBrainsMono,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Save button
                Button(
                    onClick = { onSave(textFieldValue.text, isSyncedMode) },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GratiaTheme.colors.accent,
                        contentColor = GratiaTheme.colors.background
                    )
                ) {
                    Text(
                        "Save",
                        fontFamily = Inter,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

/**
 * Format chip for toggling between Plain and Synced modes.
 */
@Composable
private fun FormatChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (selected) GratiaTheme.colors.accent else GratiaTheme.colors.surface,
        animationSpec = tween(GratiaTheme.motion.normal),
        label = "chipBg"
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) GratiaTheme.colors.background else GratiaTheme.colors.textSecondary,
        animationSpec = tween(GratiaTheme.motion.normal),
        label = "chipContent"
    )

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = backgroundColor,
        modifier = Modifier.height(36.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = GratiaTheme.spacing.base)
        ) {
            Text(
                text = label,
                fontFamily = Inter,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                fontSize = 13.sp,
                color = contentColor
            )
        }
    }
}

/**
 * Formats milliseconds into an LRC timestamp tag: `[mm:ss.xx]`
 */
private fun formatLrcTimestamp(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val hundredths = (ms % 1000) / 10
    return "[%02d:%02d.%02d]".format(minutes, seconds, hundredths)
}
