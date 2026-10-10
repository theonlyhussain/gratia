package com.gratia.music.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gratia.music.data.model.SongEntity
import com.gratia.music.ui.theme.GratiaTheme
import com.gratia.music.ui.theme.Inter
import com.gratia.music.ui.theme.SpaceGrotesk

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongMenuSheet(
    song: SongEntity,
    isFavorite: Boolean = song.isFavorite,
    sleepTimerActive: Boolean = false,
    sleepTimerRemainingMs: Long = 0L,
    showPlayNextAndFavorite: Boolean = true,
    downloadState: com.gratia.music.download.DownloadState = com.gratia.music.download.DownloadState.NotDownloaded,
    onDownload: () -> Unit = {},
    onRemoveDownload: () -> Unit = {},
    onDismiss: () -> Unit,
    onPlayNext: () -> Unit = {},
    onAddToQueue: () -> Unit = {},
    onAddToPlaylist: () -> Unit = {},
    onToggleLike: () -> Unit = {},
    // Optional actions. Null means "this surface cannot do that", and the tile
    // is not drawn — a menu that offers Download, Go to artist or Delete and
    // then does nothing when tapped is worse than one that offers less. Each
    // surface enables exactly the actions it can honour.
    onGoToAlbum: (() -> Unit)? = null,
    onGoToArtist: (() -> Unit)? = null,
    hasLyrics: Boolean = true,
    onEditLyrics: (() -> Unit)? = null,
    onSongInfo: (() -> Unit)? = null,
    onOpenSleepTimer: () -> Unit = {},
    onOpenEqualizer: () -> Unit = {},
    onOpenLyricsTiming: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null
) {
    val context = LocalContext.current

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
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp)
        ) {
            // Title & Tag
            Text(
                text = "Player Controls",
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
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "More actions",
                    fontFamily = Inter,
                    fontSize = 12.sp,
                    color = GratiaTheme.colors.textSecondary
                )
            }

            Spacer(Modifier.height(16.dp))

            // Grid of Actions
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Add to playlist
                item {
                    ControlTile(
                        icon = Icons.Outlined.PlaylistAdd,
                        title = "Add to playlist",
                        subtitle = null,
                        onClick = {
                            onAddToPlaylist()
                            onDismiss()
                        }
                    )
                }

                // Download / Remove download — only offered for remote tracks;
                // a local file is already on the device and a download button
                // on it would be an invitation to do nothing.
                if (downloadState is com.gratia.music.download.DownloadState.Downloaded) {
                    item {
                        ControlTile(
                            icon = Icons.Outlined.CheckCircle,
                            title = "Downloaded",
                            subtitle = "Remove download",
                            iconTint = GratiaTheme.colors.accent,
                            onClick = {
                                onRemoveDownload()
                                onDismiss()
                            }
                        )
                    }
                } else if (downloadState is com.gratia.music.download.DownloadState.Resolving) {
                    // The stream is being prepared — no bytes are moving yet,
                    // so the row says "Preparing" rather than pretending a
                    // percentage it cannot know (spec 4/5).
                    item {
                        ControlTile(
                            icon = Icons.Outlined.Download,
                            title = "Preparing download…",
                            subtitle = "Finding the best audio quality",
                            iconTint = GratiaTheme.colors.accent,
                            onClick = { /* in flight; nothing to do */ }
                        )
                    }
                } else if (downloadState is com.gratia.music.download.DownloadState.Downloading) {
                    item {
                        val progress = downloadState.progress
                        ControlTile(
                            icon = Icons.Outlined.Download,
                            title = if (progress != null) "Downloading ${"%d%%".format((progress * 100).toInt())}" else "Downloading…",
                            subtitle = null,
                            iconTint = GratiaTheme.colors.accent,
                            onClick = { /* in flight; nothing to do */ }
                        )
                    }
                } else if (downloadState is com.gratia.music.download.DownloadState.Failed) {
                    item {
                        ControlTile(
                            icon = Icons.Outlined.Download,
                            title = "Retry download",
                            subtitle = downloadState.message,
                            onClick = {
                                onDownload()
                                onDismiss()
                            }
                        )
                    }
                } else {
                    item {
                        ControlTile(
                            icon = Icons.Outlined.Download,
                            title = "Download",
                            subtitle = "Listen offline",
                            onClick = {
                                onDownload()
                                onDismiss()
                            }
                        )
                    }
                }

                // Lyrics timing — the real synchronization control. Null when
                // the caller has nowhere to open it (e.g. selection contexts).
                if (onOpenLyricsTiming != null) {
                    item {
                        ControlTile(
                            icon = Icons.Outlined.Timer,
                            title = "Lyrics timing",
                            subtitle = "Adjust sync offset",
                            onClick = {
                                onOpenLyricsTiming()
                                onDismiss()
                            }
                        )
                    }
                }

                if (showPlayNextAndFavorite) {
                    // Favorite
                    item {
                        ControlTile(
                            icon = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            title = if (isFavorite) "Liked" else "Favorite",
                            subtitle = null,
                            iconTint = if (isFavorite) GratiaTheme.colors.accent else GratiaTheme.colors.textPrimary,
                            onClick = {
                                onToggleLike()
                                onDismiss()
                            }
                        )
                    }

                    // Play Next
                    item {
                        ControlTile(
                            icon = Icons.Outlined.SkipNext,
                            title = "Play Next",
                            subtitle = "Play next track",
                            onClick = {
                                onPlayNext()
                                onDismiss()
                            }
                        )
                    }
                }

                // Equalizer
                item {
                    ControlTile(
                        icon = Icons.Outlined.GraphicEq,
                        title = "Equalizer",
                        subtitle = "Audio effects",
                        onClick = {
                            onOpenEqualizer()
                            onDismiss()
                        }
                    )
                }

                // Sleep Timer
                item {
                    val timerSubtitle = if (sleepTimerActive) {
                        val totalMins = (sleepTimerRemainingMs / 60000).toInt()
                        if (totalMins > 0) "$totalMins min remaining" else "< 1 min remaining"
                    } else {
                        "Disabled"
                    }
                    ControlTile(
                        icon = Icons.Outlined.Bedtime,
                        title = "Sleep Timer",
                        subtitle = timerSubtitle,
                        iconTint = if (sleepTimerActive) GratiaTheme.colors.accent else GratiaTheme.colors.textPrimary,
                        onClick = {
                            onOpenSleepTimer()
                            onDismiss()
                        }
                    )
                }

                // Edit Lyrics — only where a lyrics editor is actually reachable.
                if (onEditLyrics != null) {
                    item {
                        ControlTile(
                            icon = Icons.Outlined.Edit,
                            title = "Edit Lyrics",
                            subtitle = if (hasLyrics) "Has Lyrics" else "No Lyrics",
                            onClick = {
                                onEditLyrics()
                                onDismiss()
                            }
                        )
                    }
                }

                // Go to Album — only where the surface can open an album.
                if (onGoToAlbum != null) {
                    item {
                        ControlTile(
                            icon = Icons.Outlined.Album,
                            title = "Go to album",
                            subtitle = song.album?.takeIf { it.isNotBlank() },
                            onClick = {
                                onGoToAlbum()
                                onDismiss()
                            }
                        )
                    }
                }

                // Go to Artist — only where the surface can open an artist.
                if (onGoToArtist != null) {
                    item {
                        ControlTile(
                            icon = Icons.Outlined.Person,
                            title = "Go to artist",
                            subtitle = song.artist.takeIf { it.isNotBlank() },
                            onClick = {
                                onGoToArtist()
                                onDismiss()
                            }
                        )
                    }
                }

                // Song Info
                if (onSongInfo != null) {
                    item {
                        ControlTile(
                            icon = Icons.Outlined.Info,
                            title = "Song info",
                            subtitle = null,
                            onClick = {
                                onSongInfo()
                                onDismiss()
                            }
                        )
                    }
                }

                // Share File — a remote stream has no file to share, so this is
                // offered only for something that is actually on the device.
                val shareableUri = song.downloadPath?.let { java.io.File(it).toURI().toString() }
                    ?: song.localUri
                if (!shareableUri.isNullOrBlank()) {
                    item {
                        ControlTile(
                            icon = Icons.Outlined.Share,
                            title = "Share File",
                            subtitle = null,
                            onClick = {
                                try {
                                    val sendIntent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(Intent.EXTRA_STREAM, Uri.parse(shareableUri))
                                        type = "audio/*"
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, "Share ${song.title}"))
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                                onDismiss()
                            }
                        )
                    }
                }

                // Delete from Library (Full width) — local rows only; a
                // catalogue track is not something the app can delete.
                if (onDelete != null) {
                item(span = { GridItemSpan(2) }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(GratiaTheme.colors.error.copy(alpha = 0.12f))
                            .clickableWithScale {
                                onDelete()
                                onDismiss()
                            }
                            .padding(vertical = 14.dp, horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = null,
                                tint = GratiaTheme.colors.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Delete from Library",
                                fontFamily = Inter,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = GratiaTheme.colors.error
                            )
                        }
                    }
                }
                } // end onDelete != null
            }
        }
    }
}

@Composable
private fun ControlTile(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    iconTint: Color = GratiaTheme.colors.textPrimary,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(86.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(GratiaTheme.colors.surfaceHover.copy(alpha = 0.7f))
            .clickableWithScale(onClick = onClick)
            .padding(14.dp),
        contentAlignment = Alignment.TopStart
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Icon in circle badge
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(GratiaTheme.colors.surface)
                    .padding(5.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(16.dp)
                )
            }

            // Title and subtitle
            Column {
                Text(
                    text = title,
                    fontFamily = SpaceGrotesk,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = GratiaTheme.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        fontFamily = Inter,
                        fontSize = 11.sp,
                        color = GratiaTheme.colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
