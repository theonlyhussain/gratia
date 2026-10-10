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
                    // Show whatever is already stored for *this* song before
                    // asking any provider for it.
                    //
                    // The previous behaviour was to clear the lyrics outright and
                    // then go to the network, so opening the lyrics pane right
                    // after a track change showed a spinner for words Gratia had
                    // already saved. This reads the same song's cached lyrics — so
                    // there is still no chance of the previous track's words
                    // showing — and the provider lookup then refines or replaces
                    // them in the background. The UI appears at once either way.
                    _currentLyrics.value = runCatching {
                        lyricsRepository.getLyricsOnce(song.id)
                    }.getOrNull()
                    fetchLyricsForSong(song, forceRefresh = false)
                    prefetchNextSongLyrics(song)
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
            // State writes are keyed to the song that is current NOW. A slow
            // fetch for the previous track can finish (or fail) after the
            // listener has already moved on, and its answer must not replace
            // the new song's lyrics (lyrics spec 27).
            if (playerManager.currentSong.value?.id == song.id) {
                _currentLyrics.value = lyrics
                if (lyrics == null) {
                    _error.value = "No lyrics found"
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            // collectLatest superseded this fetch with the next song's. Let the
            // cancellation propagate — swallowing it would let this coroutine
            // keep running and write the OLD song's lyrics (or an old-song
            // error) over the new one, because assignments after the catch do
            // not suspend and therefore never observe the cancellation.
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching lyrics", e)
            if (playerManager.currentSong.value?.id == song.id) {
                _error.value = "Failed to fetch lyrics"
                // If it failed to fetch, keep whatever is in cache or null
                if (_currentLyrics.value == null) {
                    _currentLyrics.value = runCatching {
                        lyricsRepository.getLyricsOnce(song.id)
                    }.getOrNull()
                }
            }
        } finally {
            if (playerManager.currentSong.value?.id == song.id) {
                _isLoading.value = false
            }
        }
    }

    private fun prefetchNextSongLyrics(currentSong: SongEntity) {
        scope.launch(Dispatchers.IO) {
            val queue = playerManager.queue.value
            val currentIndex = queue.indexOfFirst { it.id == currentSong.id }
            if (currentIndex != -1 && currentIndex + 1 < queue.size) {
                val nextSong = queue[currentIndex + 1]
                // Only fetch if we don't already have it in the DB
                val local = lyricsRepository.getLyricsOnce(nextSong.id)
                if (local == null) {
                    try {
                        lyricsRepository.getLyrics(nextSong, forceRefresh = false)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to prefetch lyrics for next song", e)
                    }
                }
            }
        }
    }

    fun refreshLyrics() {
        val song = playerManager.currentSong.value ?: return
        scope.launch {
            fetchLyricsForSong(song, forceRefresh = true)
        }
    }

    /**
     * Save manual lyrics with automatic format detection from content.
     * This is the preferred entry point — the UI should not specify format flags.
     */
    fun saveLyrics(text: String, isActive: Boolean = true) {
        val song = playerManager.currentSong.value ?: return
        scope.launch {
            lyricsRepository.saveManualLyrics(song.id, text, isActive)
            fetchLyricsForSong(song, forceRefresh = false)
        }
    }

    /**
     * Legacy save with explicit format flags. Kept for backwards compatibility.
     */
    fun saveLyrics(text: String, isSynced: Boolean, isWordLevel: Boolean, isActive: Boolean) {
        val song = playerManager.currentSong.value ?: return
        scope.launch {
            lyricsRepository.saveManualLyrics(song.id, text, isSynced, isWordLevel, isActive)
            fetchLyricsForSong(song, forceRefresh = false)
        }
    }

    fun deleteLyrics(provider: String? = null) {
        val song = playerManager.currentSong.value ?: return
        scope.launch {
            lyricsRepository.deleteLyrics(song.id, provider)
            fetchLyricsForSong(song, forceRefresh = false)
        }
    }

    suspend fun getAllLyricsForCurrentSong(): List<LyricsEntity> {
        val song = playerManager.currentSong.value ?: return emptyList()
        return lyricsRepository.getAllLyricsForSong(song.id)
    }

    fun setActiveLyrics(provider: String) {
        val song = playerManager.currentSong.value ?: return
        scope.launch {
            lyricsRepository.setActiveLyrics(song.id, provider)
            fetchLyricsForSong(song, forceRefresh = false)
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
