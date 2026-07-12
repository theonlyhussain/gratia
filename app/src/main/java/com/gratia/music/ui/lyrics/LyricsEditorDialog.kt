package com.gratia.music.ui.lyrics

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gratia.music.data.model.SongEntity
import com.gratia.music.ui.theme.GratiaTheme
import com.gratia.music.ui.theme.Inter
import com.gratia.music.ui.theme.SpaceGrotesk

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsEditorDialog(
    song: SongEntity,
    initialLyrics: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var text by remember { mutableStateOf(initialLyrics) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Edit Lyrics",
                fontFamily = SpaceGrotesk,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                color = GratiaTheme.colors.textPrimary
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Paste plain or LRC-synced lyrics below.",
                    fontFamily = Inter,
                    color = GratiaTheme.colors.textSecondary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GratiaTheme.colors.accent,
                        unfocusedBorderColor = GratiaTheme.colors.glassBorder,
                        cursorColor = GratiaTheme.colors.accent,
                        focusedTextColor = GratiaTheme.colors.textPrimary,
                        unfocusedTextColor = GratiaTheme.colors.textPrimary
                    )
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(text) },
                colors = ButtonDefaults.textButtonColors(contentColor = GratiaTheme.colors.accent)
            ) {
                Text("Save", fontFamily = Inter, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = GratiaTheme.colors.textSecondary)
            ) {
                Text("Cancel", fontFamily = Inter)
            }
        },
        containerColor = GratiaTheme.colors.surface,
        shape = RoundedCornerShape(16.dp)
    )
}
