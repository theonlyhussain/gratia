package com.gratia.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gratia.music.data.model.SongEntity
import com.gratia.music.ui.theme.GratiaTheme
import com.gratia.music.ui.theme.Inter
import com.gratia.music.ui.theme.SpaceGrotesk
import com.gratia.music.ui.player.formatTime
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongInfoDialog(
    song: SongEntity,
    onDismiss: () -> Unit
) {
    val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    val dateAdded = dateFormat.format(Date(song.createdAt))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Song Info",
                fontFamily = SpaceGrotesk,
                fontWeight = FontWeight.Bold,
                color = GratiaTheme.colors.textPrimary
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                InfoItem("Title", song.title)
                InfoItem("Artist", song.artist)
                InfoItem("Album", song.album ?: "Unknown")
                HorizontalDivider(color = GratiaTheme.colors.glassBorder)
                InfoItem("Duration", formatTime(song.durationMs))
                InfoItem("File Path", song.storagePath ?: "Unknown")
                InfoItem("Date Added", dateAdded)
                InfoItem("Play Count", "${song.playCount} plays")
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = GratiaTheme.colors.accent)
            ) {
                Text("Close", fontFamily = Inter, fontWeight = FontWeight.SemiBold)
            }
        },
        containerColor = GratiaTheme.colors.surface,
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
private fun InfoItem(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            fontFamily = Inter,
            fontSize = 11.sp,
            color = GratiaTheme.colors.textSecondary,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value,
            fontFamily = Inter,
            fontSize = 14.sp,
            color = GratiaTheme.colors.textPrimary,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}
