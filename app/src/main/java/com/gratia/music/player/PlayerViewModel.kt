package com.gratia.music.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gratia.music.GratiaApp
import com.gratia.music.data.model.SongEntity
import com.gratia.music.data.repository.SongRepository
import com.gratia.music.data.model.LyricsEntity
import com.gratia.music.data.repository.LyricsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
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
    private val playlistDao = GratiaApp.instance.database.playlistDao()
    private val lyricsManager = GratiaApp.instance.lyricsManager
    val sleepTimerManager = GratiaApp.instance.sleepTimerManager

    val currentSong = playerManager.currentSong
    val isPlaying = playerManager.isPlaying
    val currentTimeMs = playerManager.currentTimeMs
    val durationMs = playerManager.durationMs
    val queue = playerManager.queue
    val shuffleEnabled = playerManager.shuffleEnabled
    val repeatMode = playerManager.repeatMode
    val playbackError = playerManager.playbackError

    val songCount = songRepository.getSongCount()
    val playlistCount = playlistDao.getPlaylistCount()

    private val _expandedPlayerOpen = MutableStateFlow(false)
    val expandedPlayerOpen: StateFlow<Boolean> = _expandedPlayerOpen.asStateFlow()

    private val _lyricsOverlayOpen = MutableStateFlow(false)
    val lyricsOverlayOpen: StateFlow<Boolean> = _lyricsOverlayOpen.asStateFlow()

    val currentLyrics: StateFlow<LyricsEntity?> = lyricsManager.currentLyrics
    val isLyricsLoading: StateFlow<Boolean> = lyricsManager.isLoading

    val sleepTimerActive = sleepTimerManager.isActive
    val sleepTimerRemainingMs = sleepTimerManager.remainingMs
    val sleepTimerDurationMs = sleepTimerManager.durationMs
    val sleepTimerAction = sleepTimerManager.action

    init {
        // PlayerViewModel doesn't need to observe currentSong for lyrics anymore, LyricsManager handles it.
    }

    fun refreshLyrics(force: Boolean = true) {
        if (force) {
            lyricsManager.refreshLyrics()
        }
    }

    fun saveManualLyrics(text: String, isSynced: Boolean) {
        lyricsManager.saveLyrics(text, isSynced)
    }

    fun updateLyricsOffset(offsetMs: Long) {
        lyricsManager.setOffset(offsetMs)
    }

    fun deleteLyrics() {
        lyricsManager.deleteLyrics()
    }

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

    fun removeFromQueue(songId: String) = playerManager.removeFromQueue(songId)
    fun moveInQueue(from: Int, to: Int) = playerManager.moveInQueue(from, to)
    fun playFromQueue(index: Int) = playerManager.playFromQueue(index)
    fun clearQueue() = playerManager.clearQueue()
    
    fun startSleepTimer(minutes: Int, action: com.gratia.music.player.SleepAction) {
        sleepTimerManager.startTimer(minutes, action)
    }
    
    fun stopSleepTimer() {
        sleepTimerManager.stopTimer()
    }

    fun playNext(song: SongEntity) = playerManager.playNext(song)
    fun addToQueue(song: SongEntity) = playerManager.addToQueue(song)

    fun toggleFavorite(song: SongEntity) {
        viewModelScope.launch {
            songRepository.toggleFavorite(song.id, !song.isFavorite)
        }
    }

    fun updateSong(song: SongEntity) {
        viewModelScope.launch {
            songRepository.updateSong(song)
            if (currentSong.value?.id == song.id) {
                // To trigger UI updates if current song is updated
                // The easiest way is to reload or rely on Flow, but PlayerManager might need it
                playerManager.playSong(song, queue.value)
            }
        }
    }
    fun deleteSong(song: SongEntity, onUndoExpired: () -> Unit) {
        viewModelScope.launch {
            // Remove from queue if present
            playerManager.removeFromQueue(song.id)
            if (currentSong.value?.id == song.id) {
                playerManager.nextSong()
            }
            // Delete from DB (temporarily)
            songRepository.deleteSong(song)
            
            // Allow caller to show snackbar, and on expiry delete from storage
            kotlinx.coroutines.delay(4000) // Snackbar duration
            onUndoExpired()
        }
    }

    fun restoreSong(song: SongEntity) {
        viewModelScope.launch {
            songRepository.insertSong(song)
        }
    }
    override fun onCleared() {
        super.onCleared()
        // IMPORTANT: Do NOT call playerManager.release() here.
        // PlayerManager is a singleton owned by GratiaApp.
        // Releasing it here would kill playback when the Activity is recreated.
    }
}
