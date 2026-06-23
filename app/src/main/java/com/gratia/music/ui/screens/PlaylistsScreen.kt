package com.gratia.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gratia.music.ui.theme.GratiaTheme
import com.gratia.music.ui.theme.Inter
import com.gratia.music.ui.theme.SpaceGrotesk

/**
 * Playlists screen — shows empty state with guidance.
 * Real playlist creation is planned for a future update.
 */
@Composable
fun PlaylistsScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GratiaTheme.colors.cotton)
    ) {
        Text(
            "Playlists",
            fontFamily = SpaceGrotesk,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            color = GratiaTheme.colors.textPrimary,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Decorative icon
            Surface(
                modifier = Modifier.size(88.dp),
                shape = RoundedCornerShape(24.dp),
                color = GratiaTheme.colors.surfaceCard,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.QueueMusic,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        tint = GratiaTheme.colors.textMuted
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            Text(
                "No playlists yet",
                fontFamily = Inter,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = GratiaTheme.colors.textSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(8.dp))

            Text(
                "Create your first playlist to organize\nyour favorite songs",
                fontFamily = Inter,
                fontSize = 13.sp,
                color = GratiaTheme.colors.textMuted,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )

            Spacer(Modifier.height(28.dp))

            // Coming soon badge
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = GratiaTheme.colors.cherryRed.copy(alpha = 0.08f),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = GratiaTheme.colors.cherryRed
                    )
                    Text(
                        "Coming soon",
                        fontFamily = Inter,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = GratiaTheme.colors.cherryRed
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Text(
                "Start by uploading songs to your library",
                fontFamily = Inter,
                fontSize = 11.sp,
                color = GratiaTheme.colors.textMuted,
                textAlign = TextAlign.Center
            )
        }
    }
}
