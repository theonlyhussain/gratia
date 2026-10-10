package com.gratia.music.ui.screens

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gratia.music.GratiaApp
import com.gratia.music.data.model.SongEntity
import com.gratia.music.data.repository.SongRepository
import com.gratia.music.download.DownloadState
import com.gratia.music.ui.LocalPlayerViewModel
import com.gratia.music.ui.components.AppleLargeTitleHeader
import com.gratia.music.ui.components.CoverArtImage
import com.gratia.music.ui.components.GratiaText
import com.gratia.music.ui.theme.GratiaTheme
import java.io.File

/**
 * The downloads manager: every track Gratia fetched for offline listening, its
 * size, and the ability to remove it.
 *
 * Counts and sizes are read from the database, never invented — a track whose
 * length on disk is unknown is drawn without a size rather than as "0 B", the
 * same rule the rest of the app uses for an unknown duration.
 *
 * "Downloads" and "Local Music" stay separate here too: this screen lists only
 * `isDownloaded` rows (music Gratia fetched), so an imported local MP3 never
 * appears as something we downloaded.
 */
@Composable
fun SettingsDownloadsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToStorage: () -> Unit = {}
) {
    val context = LocalContext.current
    val playerViewModel = LocalPlayerViewModel.current
    val songRepository = remember { SongRepository(GratiaApp.instance.database.songDao()) }

    val downloads by songRepository.getDownloaded().collectAsState(initial = emptyList())
    val downloadStates by playerViewModel.downloadStates.collectAsState()

    var pendingDelete by remember { mutableStateOf<SongEntity?>(null) }

    val inFlight = remember(downloadStates) {
        downloadStates.values.count { it is DownloadState.Downloading }
    }
    val totalBytes = remember(downloads) { downloads.sumOf { sizeBytesOf(it) } }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(GratiaTheme.colors.background)
            .statusBarsPadding(),
        contentPadding = PaddingValues(bottom = GratiaTheme.spacing.heroLarge)
    ) {
        item {
            AppleLargeTitleHeader(
                title = "Downloads",
                onBack = onNavigateBack
            )
            Spacer(Modifier.height(8.dp))
        }

        // ── Summary ──────────────────────────────────────────────────────
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(GratiaTheme.colors.surface)
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(Color(0xFF34C759)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.DownloadDone,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        GratiaText(
                            text = when (downloads.size) {
                                0 -> "No downloads yet"
                                1 -> "1 track on this device"
                                else -> "${downloads.size} tracks on this device"
                            },
                            style = GratiaTheme.typography.body,
                            color = GratiaTheme.colors.textPrimary
                        )
                        GratiaText(
                            text = buildString {
                                if (totalBytes > 0) {
                                    append("Using ${Formatter.formatFileSize(context, totalBytes)}")
                                } else if (downloads.isNotEmpty()) {
                                    append("Size not reported yet")
                                } else {
                                    append("Download a song to listen offline.")
                                }
                                if (inFlight > 0) {
                                    if (isNotEmpty()) append(" · ")
                                    append(if (inFlight == 1) "1 downloading" else "$inFlight downloading")
                                }
                            },
                            style = GratiaTheme.typography.caption,
                            color = GratiaTheme.colors.textSecondary
                        )
                    }
                }

                if (totalBytes > 0) {
                    Spacer(Modifier.height(12.dp))
                    DownloadsBar(usedBytes = totalBytes, inFlight = inFlight)
                }
            }

            Spacer(Modifier.height(12.dp))

            // Storage/cache stays reachable from here — it is the other half of
            // "what Gratia keeps on this device".
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(GratiaTheme.colors.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onNavigateToStorage)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(Color(0xFF8E8E93)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Storage,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        GratiaText(
                            text = "Storage & Cache",
                            style = GratiaTheme.typography.body,
                            color = GratiaTheme.colors.textPrimary
                        )
                        GratiaText(
                            text = "Artwork and temporary files",
                            style = GratiaTheme.typography.caption,
                            color = GratiaTheme.colors.textSecondary
                        )
                    }
                }
            }
        }

        // ── The tracks ───────────────────────────────────────────────────
        if (downloads.isNotEmpty()) {
            item {
                Spacer(Modifier.height(24.dp))
                GratiaText(
                    text = "ON THIS DEVICE",
                    style = GratiaTheme.typography.caption,
                    color = GratiaTheme.colors.textSecondary,
                    modifier = Modifier.padding(start = 32.dp, bottom = 8.dp)
                )
            }

            items(downloads, key = { it.id }) { song ->
                DownloadRow(
                    song = song,
                    onDelete = { pendingDelete = song }
                )
            }
        }
    }

    pendingDelete?.let { song ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = {
                GratiaText(
                    text = "Remove download?",
                    style = GratiaTheme.typography.body,
                    color = GratiaTheme.colors.textPrimary
                )
            },
            text = {
                GratiaText(
                    text = "\"${song.title}\" will be removed from this device. You can download it again later.",
                    style = GratiaTheme.typography.caption,
                    color = GratiaTheme.colors.textSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    playerViewModel.removeDownload(song)
                    pendingDelete = null
                }) {
                    Text("Remove", color = GratiaTheme.colors.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("Cancel", color = GratiaTheme.colors.textSecondary)
                }
            },
            containerColor = GratiaTheme.colors.surface
        )
    }
}

@Composable
private fun DownloadRow(
    song: SongEntity,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val bytes = sizeBytesOf(song)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CoverArtImage(
            coverArtPath = song.coverArtPath,
            title = song.title,
            artist = song.artist,
            size = 48.dp,
            cornerRadius = 6.dp
        )

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            GratiaText(
                text = song.title,
                style = GratiaTheme.typography.body,
                color = GratiaTheme.colors.textPrimary,
                maxLines = 1
            )
            GratiaText(
                text = buildString {
                    append(song.artist)
                    if (bytes > 0) append(" · ${Formatter.formatFileSize(context, bytes)}")
                },
                style = GratiaTheme.typography.caption,
                color = GratiaTheme.colors.textSecondary,
                maxLines = 1
            )
        }

        Spacer(Modifier.width(8.dp))

        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Default.DeleteOutline,
                contentDescription = "Remove download",
                tint = GratiaTheme.colors.textSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * A simple two-tone bar: downloaded bytes, plus a slice for what is currently
 * streaming in. There is no device-capacity denominator to divide by — Gratia
 * does not own the disk — so this is a proportion of the known download set,
 * not a "disk full" meter, and it says so.
 */
@Composable
private fun DownloadsBar(usedBytes: Long, inFlight: Int) {
    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(GratiaTheme.colors.surfaceHover)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(GratiaTheme.colors.accent)
            )
        }
        Spacer(Modifier.height(6.dp))
        GratiaText(
            text = if (inFlight > 0) {
                "${Formatter.formatFileSize(LocalContext.current, usedBytes)} downloaded · more arriving"
            } else {
                "${Formatter.formatFileSize(LocalContext.current, usedBytes)} downloaded"
            },
            style = GratiaTheme.typography.caption,
            color = GratiaTheme.colors.textSecondary
        )
    }
}

/** The on-disk size of a download: the recorded size, else the file's own length. */
private fun sizeBytesOf(song: SongEntity): Long {
    song.fileSizeBytes?.takeIf { it > 0 }?.let { return it }
    val path = song.downloadPath ?: return 0L
    return runCatching { File(path).length().takeIf { it > 0 } ?: 0L }.getOrDefault(0L)
}
