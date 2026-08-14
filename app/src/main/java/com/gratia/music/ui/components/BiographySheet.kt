package com.gratia.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gratia.music.data.repository.ArtistInfo
import com.gratia.music.ui.theme.GratiaTheme
import com.gratia.music.ui.theme.Inter
import com.gratia.music.ui.theme.SpaceGrotesk

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BiographySheet(
    artistName: String,
    artistInfo: ArtistInfo?,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = GratiaTheme.colors.background,
        dragHandle = { BottomSheetDefaults.DragHandle(color = GratiaTheme.colors.surfaceHover) },
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 48.dp)
        ) {
            Text(
                text = "About $artistName",
                fontFamily = SpaceGrotesk,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                color = GratiaTheme.colors.textPrimary,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            LazyColumn {
                item {
                    val bio = artistInfo?.biography
                    if (!bio.isNullOrBlank()) {
                        Text(
                            text = bio,
                            fontFamily = Inter,
                            fontWeight = FontWeight.Normal,
                            fontSize = 16.sp,
                            color = GratiaTheme.colors.textPrimary.copy(alpha = 0.9f),
                            lineHeight = 24.sp
                        )
                    } else {
                        Text(
                            text = "No biography available.",
                            fontFamily = Inter,
                            fontWeight = FontWeight.Normal,
                            fontSize = 16.sp,
                            color = GratiaTheme.colors.textSecondary
                        )
                    }
                }
            }
        }
    }
}
