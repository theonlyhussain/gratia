package com.gratia.music.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gratia.music.GratiaApp
import com.gratia.music.data.model.SongEntity
import com.gratia.music.data.repository.SongRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel connecting PlayerManager to Compose UI.
 * Singleton-like: one instance shared across all screens.
 */
class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    val playerManager = GratiaApp.instance.playerManager

    private val songRepository = SongRepository(
        GratiaApp.instance.database.songDao()
    )

    val currentSong = playerManager.currentSong
    val isPlaying = playerManager.isPlaying
    val currentTimeMs = playerManager.currentTimeMs
    val durationMs = playerManager.durationMs
    val queue = playerManager.queue
    val shuffleEnabled = playerManager.shuffleEnabled
    val repeatMode = playerManager.repeatMode
    val playbackError = playerManager.playbackError

    private val _expandedPlayerOpen = MutableStateFlow(false)
    val expandedPlayerOpen: StateFlow<Boolean> = _expandedPlayerOpen.asStateFlow()

    private val _lyricsOverlayOpen = MutableStateFlow(false)
    val lyricsOverlayOpen: StateFlow<Boolean> = _lyricsOverlayOpen.asStateFlow()

    fun playSong(song: SongEntity, songQueue: List<SongEntity>) {
        playerManager.playSong(song, songQueue)
        viewModelScope.launch {
            songRepository.incrementPlayCount(song.id)
        }
    }

    fun togglePlay() = playerManager.togglePlay()
    fun pause() = playerManager.pause()
    fun resume() = playerManager.resume()
    fun seekTo(positionMs: Long) = playerManager.seekTo(positionMs)
    fun nextSong() = playerManager.nextSong()
    fun prevSong() = playerManager.prevSong()
    fun toggleShuffle() = playerManager.toggleShuffle()
    fun cycleRepeatMode() = playerManager.cycleRepeatMode()
    fun clearError() = playerManager.clearError()

    fun setExpandedPlayerOpen(open: Boolean) {
        _expandedPlayerOpen.value = open
    }

    fun setLyricsOverlayOpen(open: Boolean) {
        _lyricsOverlayOpen.value = open
    }

    fun toggleFavorite(song: SongEntity) {
        viewModelScope.launch {
            songRepository.toggleFavorite(song.id, !song.isFavorite)
        }
    }

    override fun onCleared() {
        super.onCleared()
        playerManager.release()
    }
}
