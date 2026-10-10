package com.gratia.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gratia.music.data.SettingsDataStore
import com.gratia.music.data.model.musicSource
import com.gratia.music.player.PlayerViewModel
import com.gratia.music.ui.theme.GratiaTheme
import com.gratia.music.ui.theme.Inter
import com.gratia.music.ui.theme.JetBrainsMono
import com.gratia.music.ui.theme.SpaceGrotesk

/**
 * Developer-facing playback truth.
 *
 * Everything on this page is a fact the app already holds about the current
 * track — the recording id it is keyed by, the source that is actually
 * playing, the decoder's own report of codec/sample rate/bit depth/bitrate,
 * the persisted download and lyric state, and the active lyric offset. None of
 * it is inferred and none of it is decorative: it exists so the quality badge
 * on the player can be checked against reality, and so a report of "it says
 * lossless but sounds wrong" can be answered with the number the decoder
 * reported rather than the number a resolver claimed.
 *
 * Gated behind developer mode. See [SettingsDataStore.developerModeFlow].
 */
@Composable
fun PlaybackDiagnosticsScreen(
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val settings = remember { SettingsDataStore(context) }

    val song by playerViewModel.currentSong.collectAsState()
    val audioFormat by playerViewModel.audioFormat.collectAsState()
    val lyrics by playerViewModel.currentLyrics.collectAsState()
    val isUpgraded by playerViewModel.playerManager.isUpgradedStream.collectAsState()
    val globalQuality by settings.audioQualityFlow.collectAsState(initial = null)
    val wifiQuality by settings.wifiQualityFlow.collectAsState(initial = null)
    val mobileQuality by settings.mobileQualityFlow.collectAsState(initial = null)

    val downloadStates by playerViewModel.downloadStates.collectAsState()
    val downloadState = remember(song, downloadStates) {
        song?.let { playerViewModel.downloadStateFor(it) }
    }

    val requestedQuality = remember(wifiQuality, mobileQuality) {
        // Which ceiling is actually in force depends on the connection, so both
        // are shown rather than a single invented "current" value.
        "Wi-Fi: ${wifiQuality ?: "—"} · Mobile: ${mobileQuality ?: "—"} · Global: ${globalQuality ?: "—"}"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GratiaTheme.colors.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = GratiaTheme.colors.accent,
                )
            }
            Text(
                text = "Playback diagnostics",
                fontFamily = SpaceGrotesk,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = GratiaTheme.colors.textPrimary,
            )
        }

        if (song == null) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Nothing is playing",
                    fontFamily = Inter,
                    fontSize = 15.sp,
                    color = GratiaTheme.colors.textSecondary,
                )
            }
            return@Column
        }

        val track = song!!
        val source = when (track.musicSource) {
            com.gratia.music.data.model.MusicSource.LOCAL -> "Local file"
            com.gratia.music.data.model.MusicSource.DOWNLOADED_REMOTE -> "Downloaded (remote identity retained)"
            com.gratia.music.data.model.MusicSource.YOUTUBE_MUSIC -> "YouTube Music (streamed)"
        }

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                DiagnosticSection("Recording identity") {
                    DiagnosticRow("Song row id", track.id)
                    DiagnosticRow("Provider track id", track.providerTrackId ?: "—")
                    DiagnosticRow("Artist id", track.providerArtistId ?: "—")
                    DiagnosticRow("Album id", track.providerAlbumId ?: "—")
                    DiagnosticRow("ISRC", track.isrc ?: "—")
                }
            }

            item {
                DiagnosticSection("Source") {
                    DiagnosticRow("Playing source", source)
                    DiagnosticRow("Storage provider", track.storageProvider)
                    DiagnosticRow("Requested quality", requestedQuality)
                    DiagnosticRow("Upgraded stream", if (isUpgraded) "yes — swapped after validation" else "no")
                    DiagnosticRow("Artwork URL", track.artworkUrl ?: track.coverArtPath ?: "—")
                }
            }

            item {
                DiagnosticSection("Actual stream (decoder-reported)") {
                    if (audioFormat == null) {
                        DiagnosticRow("Codec", "not reported by the decoder")
                    } else {
                        val fmt = audioFormat!!
                        DiagnosticRow("Codec", fmt.mimeType ?: "—")
                        DiagnosticRow("Bitrate", if (fmt.bitrate > 0) "${fmt.bitrate / 1000} kbps" else "—")
                        DiagnosticRow("Sample rate", if (fmt.sampleRate > 0) "${fmt.sampleRate} Hz" else "—")
                        DiagnosticRow("Channels", if (fmt.channelCount > 0) fmt.channelCount.toString() else "—")
                        DiagnosticRow("Bit depth", if (fmt.bitDepth > 0) "${fmt.bitDepth}-bit" else "—")
                    }
                }
            }

            item {
                DiagnosticSection("Download state") {
                    DiagnosticRow(
                        "State",
                        when (downloadState) {
                            is com.gratia.music.download.DownloadState.Downloading ->
                                downloadState.progress?.let { "${(it * 100).toInt()}%" } ?: "Downloading…"
                            is com.gratia.music.download.DownloadState.Downloaded -> "Downloaded"
                            is com.gratia.music.download.DownloadState.Failed -> "Failed: ${downloadState.message}"
                            else -> "Not downloaded"
                        },
                    )
                    DiagnosticRow("File format", track.format ?: "—")
                    DiagnosticRow("File bitrate", track.bitrate?.let { "$it kbps" } ?: "—")
                    DiagnosticRow("File size", track.fileSizeBytes?.let { "${it / 1024 / 1024} MB" } ?: "—")
                    DiagnosticRow("Download path", track.downloadPath ?: "—")
                }
            }

            item {
                DiagnosticSection("Lyrics") {
                    DiagnosticRow("Provider", lyrics?.provider ?: "—")
                    DiagnosticRow(
                        "Sync type",
                        when {
                            lyrics == null -> "—"
                            lyrics!!.isWordLevel -> "word-synced"
                            lyrics!!.isSynced -> "line-synced"
                            else -> "unsynced"
                        },
                    )
                    DiagnosticRow("Offset", "${lyrics?.offsetMs ?: 0L} ms")
                    DiagnosticRow("Active override", if (lyrics?.isActiveOverride == true) "yes" else "no")
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun DiagnosticSection(title: String, content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = GratiaTheme.colors.surface,
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = title,
                fontFamily = SpaceGrotesk,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = GratiaTheme.colors.textPrimary,
            )
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun DiagnosticRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            text = label,
            fontFamily = Inter,
            fontSize = 11.sp,
            color = GratiaTheme.colors.textSecondary,
        )
        Text(
            text = value,
            fontFamily = JetBrainsMono,
            fontSize = 12.sp,
            color = GratiaTheme.colors.textPrimary,
        )
        HorizontalDivider(
            color = GratiaTheme.colors.glassBorder,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}
