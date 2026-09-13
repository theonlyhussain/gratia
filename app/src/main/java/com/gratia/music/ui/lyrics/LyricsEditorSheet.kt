package com.gratia.music.ui.lyrics

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.CheckCircle
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
import com.gratia.music.lyrics.LyricsFormatDetector
import com.gratia.music.lyrics.LyricsFormat
import com.gratia.music.lyrics.LyricsParser
import com.gratia.music.lyrics.LyricsDocument
import com.gratia.music.lyrics.LyricsQuality
import com.gratia.music.ui.components.CoverArtImage
import com.gratia.music.ui.theme.GratiaTheme
import com.gratia.music.ui.theme.Inter
import com.gratia.music.ui.theme.JetBrainsMono
import com.gratia.music.ui.theme.SpaceGrotesk
import kotlinx.coroutines.delay

/**
 * Unified lyrics editor with automatic format detection.
 *
 * No format chips — the user pastes or types lyrics in any format
 * (LRC, ELRC, TTML, JSON, plain) and Gratia automatically detects
 * the format and sync quality. A status bar shows what was detected.
 *
 * @param onSave Called with the raw lyrics text. Format detection and
 *               database flag setting happen in the repository layer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsEditorSheet(
    song: SongEntity,
    allLyrics: List<com.gratia.music.data.model.LyricsEntity>,
    currentTimeMs: Long,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onDelete: (String) -> Unit,
    onSetActive: (String) -> Unit
) {
    val automaticLyrics = remember(allLyrics) { allLyrics.find { it.provider == "automatic" } }
    val manualLyrics = remember(allLyrics) { allLyrics.find { it.provider == "manual" } }

    val activeProvider = remember(allLyrics) {
        if (manualLyrics?.isActiveOverride == true) "manual" else "automatic"
    }

    // Single unified text state — the user's content, regardless of format
    val initialText = remember(allLyrics) {
        when {
            manualLyrics != null -> manualLyrics.text
            automaticLyrics != null -> automaticLyrics.text
            else -> ""
        }
    }

    var editorText by remember(allLyrics) {
        mutableStateOf(TextFieldValue(text = initialText))
    }

    // Live format detection — debounced to avoid lag while typing
    var detectedFormat by remember { mutableStateOf(LyricsFormatDetector.detect(initialText)) }
    var parsedDocument by remember { mutableStateOf(LyricsParser.parse(initialText)) }

    // Stats derived from parsed document
    val lineCount = remember(parsedDocument) {
        when (parsedDocument) {
            is LyricsDocument.WordSynced -> (parsedDocument as LyricsDocument.WordSynced).lines.count { it.text.isNotBlank() }
            is LyricsDocument.LineSynced -> (parsedDocument as LyricsDocument.LineSynced).lines.count { it.text.isNotBlank() }
            is LyricsDocument.Plain -> (parsedDocument as LyricsDocument.Plain).text.lines().count { it.isNotBlank() }
        }
    }
    val wordCount = remember(parsedDocument) {
        when (parsedDocument) {
            is LyricsDocument.WordSynced -> (parsedDocument as LyricsDocument.WordSynced).lines.sumOf { it.words.size }
            is LyricsDocument.LineSynced -> (parsedDocument as LyricsDocument.LineSynced).lines.sumOf {
                it.text.split(Regex("\\s+")).count { w -> w.isNotBlank() }
            }
            is LyricsDocument.Plain -> (parsedDocument as LyricsDocument.Plain).text.split(Regex("\\s+")).count { it.isNotBlank() }
        }
    }
    val timedWordCount = remember(parsedDocument) {
        when (parsedDocument) {
            is LyricsDocument.WordSynced -> (parsedDocument as LyricsDocument.WordSynced).lines.sumOf { it.words.size }
            else -> 0
        }
    }

    // Debounced re-detection when text changes
    LaunchedEffect(editorText.text) {
        delay(300) // debounce 300ms
        detectedFormat = LyricsFormatDetector.detect(editorText.text)
        parsedDocument = LyricsParser.parse(editorText.text)
    }

    // Determine if content uses timestamps (for showing line numbers + monospace)
    val hasSyncedContent = detectedFormat != LyricsFormat.PLAIN && detectedFormat != LyricsFormat.UNKNOWN

    val focusRequester = remember { FocusRequester() }
    val verticalScrollState = rememberScrollState()

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
                    onClick = { onSave(editorText.text) }
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

            // ── Format Detection Status ──────────────────────────
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = GratiaTheme.spacing.mediumLarge),
                shape = RoundedCornerShape(12.dp),
                color = GratiaTheme.colors.surfaceHover
            ) {
                Column(
                    modifier = Modifier.padding(
                        horizontal = GratiaTheme.spacing.base,
                        vertical = GratiaTheme.spacing.small
                    )
                ) {
                    // Format and quality
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val isValid = editorText.text.isNotBlank() && (
                            parsedDocument is LyricsDocument.WordSynced ||
                            parsedDocument is LyricsDocument.LineSynced ||
                            (parsedDocument is LyricsDocument.Plain && detectedFormat == LyricsFormat.PLAIN)
                        )
                        val statusColor = if (isValid) Color(0xFF4CAF50) else GratiaTheme.colors.textSecondary
                        val statusIcon = if (isValid) Icons.Default.CheckCircle else Icons.Default.Info

                        Icon(
                            imageVector = statusIcon,
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (editorText.text.isBlank()) "No content"
                                   else detectedFormat.label,
                            fontFamily = SpaceGrotesk,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = GratiaTheme.colors.textPrimary
                        )

                        if (editorText.text.isNotBlank() && parsedDocument.quality != LyricsQuality.PLAIN) {
                            Text(
                                text = " · ",
                                fontSize = 13.sp,
                                color = GratiaTheme.colors.textSecondary
                            )
                            Text(
                                text = detectedFormat.syncDescription(parsedDocument.quality),
                                fontFamily = Inter,
                                fontSize = 13.sp,
                                color = GratiaTheme.colors.textSecondary
                            )
                        }
                    }

                    // Stats line
                    if (editorText.text.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = buildString {
                                    append("$lineCount lines · $wordCount words")
                                    if (timedWordCount > 0) append(" · $timedWordCount timed")
                                },
                                fontFamily = Inter,
                                fontSize = 11.sp,
                                color = GratiaTheme.colors.textSecondary.copy(alpha = 0.7f)
                            )

                            // Manual lyrics management
                            if (manualLyrics != null) {
                                Spacer(Modifier.weight(1f))
                                TextButton(
                                    onClick = { onDelete("manual") },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                    modifier = Modifier.height(22.dp)
                                ) {
                                    Text("Delete Manual", fontSize = 10.sp, color = GratiaTheme.colors.error)
                                }
                            }
                        }
                    }

                    // Provenance info
                    if (manualLyrics?.isActiveOverride == true) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "Manual lyrics active",
                            fontFamily = Inter,
                            fontSize = 11.sp,
                            color = Color(0xFF4CAF50).copy(alpha = 0.8f)
                        )
                    }
                }
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
                    // Line number gutter (shown for synced content)
                    if (hasSyncedContent) {
                        val rawLineCount = editorText.text.lines().size.coerceAtLeast(1)
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
                            for (i in 1..rawLineCount) {
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

                    // Main text field
                    BasicTextField(
                        value = editorText,
                        onValueChange = { editorText = it },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(verticalScrollState)
                            .padding(GratiaTheme.spacing.base)
                            .focusRequester(focusRequester),
                        textStyle = if (hasSyncedContent) {
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
                                if (editorText.text.isEmpty()) {
                                    Text(
                                        text = "Paste or type lyrics here…",
                                        fontFamily = if (hasSyncedContent) JetBrainsMono else Inter,
                                        fontSize = if (hasSyncedContent) 13.sp else 15.sp,
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
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Clear button
                FilledIconButton(
                    onClick = {
                        editorText = TextFieldValue("")
                    },
                    modifier = Modifier.size(40.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = GratiaTheme.colors.error.copy(alpha = 0.15f),
                        contentColor = GratiaTheme.colors.error
                    )
                ) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = "Clear",
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Timestamp insertion buttons — always available for manual LRC authoring
                val timeTag = remember(currentTimeMs) {
                    formatLrcTimestamp(currentTimeMs)
                }
                val enhancedTimeTag = remember(currentTimeMs) {
                    formatEnhancedTimestamp(currentTimeMs)
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(GratiaTheme.spacing.small),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Enhanced timestamp <mm:ss.xx>
                    FilledTonalButton(
                        onClick = {
                            val cursor = editorText.selection.start
                            val currentText = editorText.text
                            val tag = "$enhancedTimeTag "
                            val newText = currentText.substring(0, cursor) +
                                    tag +
                                    currentText.substring(cursor)
                            editorText = TextFieldValue(
                                text = newText,
                                selection = TextRange(cursor + tag.length)
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = GratiaTheme.colors.accent.copy(alpha = 0.15f),
                            contentColor = GratiaTheme.colors.accent
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Icon(
                            Icons.Default.Timer,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(GratiaTheme.spacing.extraSmall))
                        Text(
                            text = enhancedTimeTag,
                            fontFamily = JetBrainsMono,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Line timestamp [mm:ss.xx]
                    FilledTonalButton(
                        onClick = {
                            val cursor = editorText.selection.start
                            val currentText = editorText.text
                            val tag = "$timeTag "
                            val newText = currentText.substring(0, cursor) +
                                    tag +
                                    currentText.substring(cursor)
                            editorText = TextFieldValue(
                                text = newText,
                                selection = TextRange(cursor + tag.length)
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = GratiaTheme.colors.surfaceHover,
                            contentColor = GratiaTheme.colors.textPrimary
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp)
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

                    // Insert Gap
                    FilledTonalButton(
                        onClick = {
                            val cursor = editorText.selection.start
                            val currentText = editorText.text
                            val tag = "\n$timeTag \n"
                            val newText = currentText.substring(0, cursor) +
                                    tag +
                                    currentText.substring(cursor)
                            editorText = TextFieldValue(
                                text = newText,
                                selection = TextRange(cursor + tag.length)
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = GratiaTheme.colors.surfaceHover,
                            contentColor = GratiaTheme.colors.textPrimary
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Icon(
                            Icons.Default.MoreHoriz,
                            contentDescription = "Insert Gap",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
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

/**
 * Formats milliseconds into an Enhanced LRC timestamp tag: `<mm:ss.xx>`
 */
private fun formatEnhancedTimestamp(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val hundredths = (ms % 1000) / 10
    return "<%02d:%02d.%02d>".format(minutes, seconds, hundredths)
}
