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
    val autoplayEnabled = playerManager.autoplayEnabled
    val history = playerManager.history
    val playbackError = playerManager.playbackError
    val audioFormat = playerManager.audioFormat

    val songCount = songRepository.getSongCount()
    val playlistCount = playlistDao.getPlaylistCount()

    private val _expandedPlayerOpen = MutableStateFlow(false)
    val expandedPlayerOpen: StateFlow<Boolean> = _expandedPlayerOpen.asStateFlow()

    private val _lyricsOverlayOpen = MutableStateFlow(false)
    val lyricsOverlayOpen: StateFlow<Boolean> = _lyricsOverlayOpen.asStateFlow()

    val currentLyrics: StateFlow<LyricsEntity?> = lyricsManager.currentLyrics
    val isLyricsLoading: StateFlow<Boolean> = lyricsManager.isLoading

    private val _artistInfos = MutableStateFlow<Map<String, com.gratia.music.data.repository.ArtistInfo?>>(emptyMap())
    val artistInfos: StateFlow<Map<String, com.gratia.music.data.repository.ArtistInfo?>> = _artistInfos.asStateFlow()

    val sleepTimerActive = sleepTimerManager.isActive
    val sleepTimerRemainingMs = sleepTimerManager.remainingMs
    val sleepTimerDurationMs = sleepTimerManager.durationMs
    val sleepTimerAction = sleepTimerManager.action

    private val _favoriteSongIds = MutableStateFlow<Set<String>>(emptySet())
    val favoriteSongIds: StateFlow<Set<String>> = _favoriteSongIds.asStateFlow()

    private val _trackCredits = MutableStateFlow<List<com.gratia.music.data.repository.ContributorInfo>>(emptyList())
    val trackCredits: StateFlow<List<com.gratia.music.data.repository.ContributorInfo>> = _trackCredits.asStateFlow()

    init {
        viewModelScope.launch {
            songRepository.getFavorites().collectLatest { favs ->
                _favoriteSongIds.value = favs.map { it.id }.toSet()
            }
        }
        
        viewModelScope.launch {
            currentSong.collectLatest { song ->
                if (song != null) {
                    _artistInfos.value = emptyMap() // Clear old ones
                    _trackCredits.value = emptyList() // Clear old credits
                    
                    val contributors = com.gratia.music.data.repository.ArtistInfoRepository.getTrackContributors(song.title, song.artist)
                    _trackCredits.value = contributors

                    val infoMap = mutableMapOf<String, com.gratia.music.data.repository.ArtistInfo?>()

                    if (contributors.isNotEmpty()) {
                        contributors.forEach { contributor ->
                            val info = com.gratia.music.data.repository.ArtistInfoRepository.getArtistInfo(contributor.name)
                            infoMap[contributor.name] = info
                            _artistInfos.value = infoMap.toMap() // Update state incrementally
                        }
                    } else {
                        val artists = com.gratia.music.utils.ArtistParser.parseArtists(song.artist)
                        artists.forEach { artistName ->
                            val info = com.gratia.music.data.repository.ArtistInfoRepository.getArtistInfo(artistName)
                            infoMap[artistName] = info
                            _artistInfos.value = infoMap.toMap() // Update state incrementally
                        }
                    }
                } else {
                    _artistInfos.value = emptyMap()
                    _trackCredits.value = emptyList()
                }
            }
        }
        
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(10000L)
                if (isPlaying.value) {
                    val id = currentSong.value?.id
                    if (id != null) {
                        songRepository.incrementListenTime(id, 10000L)
                    }
                }
            }
        }
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
    fun cycleRepeatMode() {
        playerManager.cycleRepeatMode()
    }

    fun toggleAutoplay() {
        playerManager.toggleAutoplay()
    }

    fun clearHistory() {
        playerManager.clearHistory()
    }

    fun clearError() = playerManager.clearError()

    fun setExpandedPlayerOpen(open: Boolean) {
        _expandedPlayerOpen.value = open
    }

    fun setLyricsOverlayOpen(open: Boolean) {
        _lyricsOverlayOpen.value = open
    }

    fun removeFromQueue(songId: String) = playerManager.removeFromQueue(songId)
    fun moveInQueue(from: Int, to: Int) = playerManager.moveInQueue(from, to)
    
    fun updateUpcomingQueue(newUpcomingQueue: List<SongEntity>, startIndex: Int) {
        playerManager.updateUpcomingQueue(newUpcomingQueue, startIndex)
    }
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
        val newFavorite = !song.isFavorite
        viewModelScope.launch {
            // Optimistically update the global set
            val currentFavs = _favoriteSongIds.value.toMutableSet()
            if (newFavorite) currentFavs.add(song.id) else currentFavs.remove(song.id)
            _favoriteSongIds.value = currentFavs

            // 1. Persist to database
            songRepository.toggleFavorite(song.id, newFavorite)

            // 2. Update in-memory currentSong so the UI immediately reflects
            val updatedSong = song.copy(isFavorite = newFavorite)
            if (playerManager.currentSong.value?.id == song.id) {
                playerManager.updateCurrentSongState(updatedSong)
            }

            // 3. Update the song in the queue list as well
            playerManager.updateSongInQueue(song.id, updatedSong)
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
