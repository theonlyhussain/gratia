package com.gratia.music.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.gratia.music.player.PlayerViewModel

val LocalPlayerViewModel = staticCompositionLocalOf<PlayerViewModel> {
    error("No PlayerViewModel provided")
}
