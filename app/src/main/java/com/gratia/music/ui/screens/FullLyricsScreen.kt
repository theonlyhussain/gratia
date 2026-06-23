package com.gratia.music.ui.screens

import androidx.compose.runtime.Composable
import com.gratia.music.player.PlayerViewModel
import com.gratia.music.ui.lyrics.LyricsScreen

/**
 * Full lyrics screen container, delegating to the rebuilt premium kinetic lyrics engine.
 */
@Composable
fun FullLyricsScreen(
    playerViewModel: PlayerViewModel,
    songId: String?,
    onBack: () -> Unit
) {
    LyricsScreen(
        playerViewModel = playerViewModel,
        songId = songId,
        onBack = onBack
    )
}
