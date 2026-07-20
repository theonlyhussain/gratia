package com.gratia.music.lyrics

import android.util.Log
import com.gratia.music.data.model.LyricsEntity
import com.gratia.music.data.model.SongEntity
import com.gratia.music.data.repository.LyricsRepository
import com.gratia.music.player.PlayerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Singleton manager for Lyrics state.
 * Observes PlayerManager and orchestrates lyrics fetching, caching, and state updates.
 */
class LyricsManager(
    private val playerManager: PlayerManager,
    private val lyricsRepository: LyricsRepository
) {
    private val TAG = "LyricsManager"
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _currentLyrics = MutableStateFlow<LyricsEntity?>(null)
    val currentLyrics: StateFlow<LyricsEntity?> = _currentLyrics.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        scope.launch {
            playerManager.currentSong.collectLatest { song ->
                if (song != null) {
                    fetchLyricsForSong(song, forceRefresh = false)
                } else {
                    _currentLyrics.value = null
                    _error.value = null
                    _isLoading.value = false
                }
            }
        }
    }

    private suspend fun fetchLyricsForSong(song: SongEntity, forceRefresh: Boolean) {
        _isLoading.value = true
        _error.value = null
        
        try {
            val lyrics = lyricsRepository.getLyrics(song, forceRefresh)
            _currentLyrics.value = lyrics
            if (lyrics == null) {
                _error.value = "No lyrics found"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching lyrics", e)
            _error.value = "Failed to fetch lyrics"
            // If it failed to fetch, keep whatever is in cache or null
            if (_currentLyrics.value == null) {
                _currentLyrics.value = lyricsRepository.getLyricsOnce(song.id)
            }
        } finally {
            _isLoading.value = false
        }
    }

    fun refreshLyrics() {
        val song = playerManager.currentSong.value ?: return
        scope.launch {
            fetchLyricsForSong(song, forceRefresh = true)
        }
    }

    fun saveLyrics(text: String, isSynced: Boolean) {
        val song = playerManager.currentSong.value ?: return
        scope.launch {
            lyricsRepository.saveManualLyrics(song.id, text, isSynced)
            fetchLyricsForSong(song, forceRefresh = false)
        }
    }

    fun deleteLyrics() {
        val song = playerManager.currentSong.value ?: return
        scope.launch {
            lyricsRepository.deleteLyrics(song.id)
            _currentLyrics.value = null
            _error.value = "No lyrics found"
        }
    }

    fun setOffset(offsetMs: Long) {
        val song = playerManager.currentSong.value ?: return
        scope.launch {
            lyricsRepository.updateOffset(song.id, offsetMs)
            fetchLyricsForSong(song, forceRefresh = false)
        }
    }
}
