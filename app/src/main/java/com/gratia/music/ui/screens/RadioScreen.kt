package com.gratia.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gratia.music.GratiaApp
import com.gratia.music.data.repository.SongRepository
import com.gratia.music.player.PlayerViewModel
import com.gratia.music.ui.theme.GratiaTheme
import com.gratia.music.ui.theme.Inter
import com.gratia.music.ui.theme.SpaceGrotesk
import kotlinx.coroutines.launch

/**
 * Radio = shuffle songs from user's private library.
 * NOT internet radio. NOT external streams.
 */
@Composable
fun RadioScreen(playerViewModel: PlayerViewModel) {
    val songRepo = remember { SongRepository(GratiaApp.instance.database.songDao()) }
    val songs by songRepo.getAllSongs().collectAsState(initial = emptyList())
    val currentSong by playerViewModel.currentSong.collectAsState()
    val isPlaying by playerViewModel.isPlaying.collectAsState()
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GratiaTheme.colors.cotton),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header
        Text(
            "Gratia Radio",
            fontFamily = SpaceGrotesk,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            color = GratiaTheme.colors.textPrimary,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp)
                .fillMaxWidth()
        )

        Spacer(Modifier.height(60.dp))

        // Radio icon with glow
        Surface(
            modifier = Modifier.size(120.dp),
            shape = RoundedCornerShape(32.dp),
            color = GratiaTheme.colors.cherryRed.copy(alpha = 0.08f),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Surface(
                    modifier = Modifier.size(80.dp),
                    shape = RoundedCornerShape(24.dp),
                    color = GratiaTheme.colors.cherryRed.copy(alpha = 0.12f),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Radio,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = GratiaTheme.colors.cherryRed
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(32.dp))

        if (songs.isEmpty()) {
            // Empty library state
            Text(
                "Add songs to start\nGratia Radio",
                fontFamily = Inter,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = GratiaTheme.colors.textSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Your library is empty. Add some songs first.",
                fontFamily = Inter,
                fontSize = 12.sp,
                color = GratiaTheme.colors.textMuted,
                textAlign = TextAlign.Center
            )
        } else {
            Text(
                "Shuffle your library",
                fontFamily = Inter,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = GratiaTheme.colors.textSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "${songs.size} song${if (songs.size > 1) "s" else ""} in your library",
                fontFamily = Inter,
                fontSize = 12.sp,
                color = GratiaTheme.colors.textMuted
            )
            Spacer(Modifier.height(32.dp))

            // Play Radio button — Maroon
            Button(
                onClick = {
                    scope.launch {
                        val shuffled = songs.shuffled()
                        playerViewModel.playSong(shuffled.first(), shuffled)
                    }
                },
                modifier = Modifier
                    .width(200.dp)
                    .height(52.dp),
                shape = RoundedCornerShape(26.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = GratiaTheme.colors.maroon,
                    contentColor = GratiaTheme.colors.cotton
                )
            ) {
                Icon(Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Play Radio", fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }

            // Current playing info
            if (currentSong != null && isPlaying) {
                Spacer(Modifier.height(32.dp))
                Text("Now playing", fontFamily = Inter, fontSize = 11.sp, color = GratiaTheme.colors.textMuted)
                Spacer(Modifier.height(4.dp))
                Text(currentSong!!.title, fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = GratiaTheme.colors.textPrimary)
                Text(currentSong!!.artist, fontFamily = Inter, fontSize = 13.sp, color = GratiaTheme.colors.textSecondary)
            }
        }
    }
}
