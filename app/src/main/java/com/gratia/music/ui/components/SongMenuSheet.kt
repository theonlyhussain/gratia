package com.gratia.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
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
    onDismiss: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onToggleLike: () -> Unit,
    onGoToAlbum: () -> Unit,
    onGoToArtist: () -> Unit,
    onEditLyrics: () -> Unit,
    onShare: () -> Unit,
    onSongInfo: () -> Unit,
    onDelete: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = GratiaTheme.colors.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // We could use CollageArtwork or a simple placeholder here, but plain text works well
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        fontFamily = SpaceGrotesk,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = GratiaTheme.colors.textPrimary,
                        maxLines = 1
                    )
                    Text(
                        text = "${song.artist} • ${song.album ?: "Unknown"}",
                        fontFamily = Inter,
                        fontSize = 13.sp,
                        color = GratiaTheme.colors.textSecondary,
                        maxLines = 1
                    )
                }
            }
            
            HorizontalDivider(color = GratiaTheme.colors.glassBorder)
            
            // Actions
            MenuActionRow(icon = Icons.Outlined.SkipNext, text = "Play Next", onClick = { onPlayNext(); onDismiss() })
            MenuActionRow(icon = Icons.Outlined.QueueMusic, text = "Add to Queue", onClick = { onAddToQueue(); onDismiss() })
            MenuActionRow(icon = Icons.Outlined.PlaylistAdd, text = "Add to Playlist", onClick = { onAddToPlaylist(); onDismiss() })
            MenuActionRow(
                icon = if (song.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, 
                text = if (song.isFavorite) "Unlike" else "Like", 
                onClick = { onToggleLike(); onDismiss() },
                tint = if (song.isFavorite) GratiaTheme.colors.accent else GratiaTheme.colors.textPrimary
            )
            MenuActionRow(icon = Icons.Outlined.Album, text = "Go to Album", onClick = { onGoToAlbum(); onDismiss() })
            MenuActionRow(icon = Icons.Outlined.Person, text = "Go to Artist", onClick = { onGoToArtist(); onDismiss() })
            MenuActionRow(icon = Icons.Outlined.Edit, text = "Edit Lyrics", onClick = { onEditLyrics(); onDismiss() })
            MenuActionRow(icon = Icons.Outlined.Share, text = "Share", onClick = { onShare(); onDismiss() })
            MenuActionRow(icon = Icons.Outlined.Info, text = "Song Info", onClick = { onSongInfo(); onDismiss() })
            
            HorizontalDivider(color = GratiaTheme.colors.glassBorder)
            
            MenuActionRow(
                icon = Icons.Outlined.Delete, 
                text = "Delete from Library", 
                onClick = { onDelete(); onDismiss() },
                tint = GratiaTheme.colors.error
            )
        }
    }
}

@Composable
private fun MenuActionRow(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
    tint: androidx.compose.ui.graphics.Color = GratiaTheme.colors.textPrimary
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = text,
            fontFamily = Inter,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = tint
        )
    }
}
