package com.gratia.music.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gratia.music.GratiaApp
import com.gratia.music.data.model.SongEntity
import com.gratia.music.data.model.toSongEntity
import com.gratia.music.download.downloadKey
import com.gratia.music.data.model.hasRemoteIdentity
import com.gratia.music.data.repository.SongRepository
import com.gratia.music.data.model.LyricsEntity
import com.gratia.music.data.repository.LyricsRepository
import kotlinx.coroutines.Dispatchers
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
    /** State of the AutoPlay machinery for the queue screen's section. */
    val autoplayStatus = playerManager.autoplayStatus
    /** How many AutoPlay suggestions are queued and still ahead. */
    val autoplayQueuedCount = playerManager.autoplayQueuedCount
    /** Where the current session started — the one source for the context line. */
    val playbackOrigin = playerManager.playbackOrigin
    val history = playerManager.history
    val playbackError = playerManager.playbackError
    val audioFormat = playerManager.audioFormat
    /** Whether the playing stream was actually swapped in by a quality upgrade. */
    val isUpgradedStream = playerManager.isUpgradedStream
    /** True while a better-rendition search is running for the current track. */
    val isUpgradeOptimizing = playerManager.isUpgradeOptimizing
    val currentQueueIndex = playerManager.currentQueueIndexFlow

    /** True while a remote stream is being resolved for the current request. */
    val isResolvingRemote = playerManager.isResolvingRemote

    val songCount = songRepository.getSongCount()
    val playlistCount = playlistDao.getPlaylistCount()

    /**
     * How the player is presented right now — the one source of truth every
     * surface reads. Deliberately independent of playback: collapsing is a
     * presentation change, and the audio service continues regardless.
     * See [PlayerPresentationState].
     */
    private val _presentation = MutableStateFlow<PlayerPresentationState>(PlayerPresentationState.Collapsed)
    val presentation: StateFlow<PlayerPresentationState> = _presentation.asStateFlow()

    /** Convenience read for non-compose call sites. */
    val isPlayerExpanded: Boolean get() = _presentation.value is PlayerPresentationState.Expanded

    private val _dailyMixSongs = MutableStateFlow<List<SongEntity>>(emptyList())
    val dailyMixSongs: StateFlow<List<SongEntity>> = _dailyMixSongs.asStateFlow()
    private var isDailyMixActive = false

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

    private var artistObserverJob: kotlinx.coroutines.Job? = null

    init {
        viewModelScope.launch {
            songRepository.getFavorites().collectLatest { favs ->
                _favoriteSongIds.value = favs.map { it.id }.toSet()
            }
        }
        
        viewModelScope.launch {
            currentSong.collectLatest { song ->
                artistObserverJob?.cancel()
                if (song != null) {
                    _artistInfos.value = emptyMap() // Clear old ones
                    _trackCredits.value = emptyList() // Clear old credits

                    val artists = com.gratia.music.utils.ArtistParser.parseArtists(song.artist).ifEmpty { listOf(song.artist) }
                    val infoMap = LinkedHashMap<String, com.gratia.music.data.repository.ArtistInfo?>()
                    artists.forEach { artistName ->
                        infoMap[artistName] = null
                    }
                    _artistInfos.value = LinkedHashMap(infoMap)

                    // Fetch base API info
                    val contributors = com.gratia.music.data.repository.ArtistInfoRepository.getTrackContributors(song.title, song.artist)
                    _trackCredits.value = contributors

                    artists.forEach { artistName ->
                        val info = com.gratia.music.data.repository.ArtistInfoRepository.getArtistInfo(artistName)
                        infoMap[artistName] = info
                        _artistInfos.value = LinkedHashMap(infoMap) 
                    }

                    // Reactively observe local ArtistEntity for custom image overrides
                    artistObserverJob = viewModelScope.launch {
                        val artistDao = GratiaApp.instance.database.artistDao()
                        artists.forEach { artistName ->
                            launch {
                                artistDao.getArtistByNameFlow(artistName).collectLatest { entity ->
                                    if (entity != null) {
                                        // Update ArtistInfos
                                        val currentInfo = infoMap[artistName]
                                        val overriddenImageUrl = entity.localPicturePath ?: entity.pictureUrl ?: currentInfo?.pictureUrl
                                        
                                        if (currentInfo != null) {
                                            infoMap[artistName] = currentInfo.copy(pictureUrl = overriddenImageUrl)
                                            _artistInfos.value = LinkedHashMap(infoMap)
                                        } else {
                                            // Fallback if API failed but we have local data
                                            infoMap[artistName] = com.gratia.music.data.repository.ArtistInfo(
                                                name = artistName,
                                                pictureUrl = overriddenImageUrl,
                                                // No metric here — the lookup failed and
                                                // nothing is invented (artist spec 3/37).
                                                fanCount = null,
                                                isVerified = false,
                                                biography = null
                                            )
                                            _artistInfos.value = LinkedHashMap(infoMap)
                                        }

                                        // Update Credits
                                        val updatedCredits = _trackCredits.value.map { credit ->
                                            if (credit.name.equals(artistName, ignoreCase = true)) {
                                                credit.copy(pictureUrl = overriddenImageUrl)
                                            } else {
                                                credit
                                            }
                                        }
                                        _trackCredits.value = updatedCredits
                                    }
                                }
                            }
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
        
        // Daily Mix tail: as the context runs low, generate the next batch and
        // hand it to the player as marked suggestions.
        //
        // The old implementation called updateUpcomingQueue(q + more, currentIndex)
        // — passing the FULL queue as the "upcoming" list. That API rebuilds the
        // queue as queue[0..currentIndex) + argument, so every batch re-appended
        // the context rows from the current position onward AND the previous
        // batches, duplicating rows wholesale; and the additions entered with no
        // tier marking, so a generated batch was indistinguishable from context
        // (and shared the shelf's fate: the next removal/reorder rebuilt the
        // whole thing). appendAutoplaySuggestions does none of that: it adds only
        // new rows, marks them AUTOPLAY, and dedups against played/removed/queued.
        viewModelScope.launch {
            kotlinx.coroutines.flow.combine(currentSong, queue) { song, q -> Pair(song, q) }
                .collectLatest { (song, q) ->
                    if (song != null && isDailyMixActive && q.isNotEmpty()) {
                        val currentIndex = q.indexOfFirst { it.id == song.id }
                        if (currentIndex >= q.size - 3) {
                            val allSongs = songRepository.getAllSongsOnce()
                            // Candidates exclude the whole current queue — not
                            // from the current index, so nothing already heard
                            // this session comes back as a "new" suggestion.
                            val excludeIds = q.map { it.id }.toSet()
                            val moreSongs = GratiaApp.instance.recommendationManager.getDailyMix(
                                allSongs, limit = 10, excludeIds = excludeIds
                            )
                            if (moreSongs.isNotEmpty()) {
                                // Tier: AUTOPLAY. The context rows stay exactly
                                // as they are; the queue screen keeps splitting
                                // "Daily Mix" from "suggestions" correctly.
                                playerManager.appendAutoplaySuggestions(moreSongs)
                            }
                        }
                    }
                }
        }
        
        // Generate initial daily mix
        viewModelScope.launch(Dispatchers.IO) {
            val allSongs = songRepository.getAllSongsOnce()
            _dailyMixSongs.value = GratiaApp.instance.recommendationManager.getDailyMix(allSongs, limit = 20)
        }
    }

    fun refreshLyrics(force: Boolean = true) {
        if (force) {
            lyricsManager.refreshLyrics()
        }
    }

    /**
     * Save manual lyrics with auto-detected format from content.
     */
    fun saveManualLyrics(text: String, isActive: Boolean = true) {
        lyricsManager.saveLyrics(text, isActive)
    }

    /**
     * Legacy save with explicit format flags.
     */
    fun saveManualLyrics(text: String, isSynced: Boolean, isWordLevel: Boolean, isActive: Boolean) {
        lyricsManager.saveLyrics(text, isSynced, isWordLevel, isActive)
    }

    fun updateLyricsOffset(offsetMs: Long) {
        lyricsManager.setOffset(offsetMs)
    }

    fun deleteLyrics(provider: String? = null) {
        lyricsManager.deleteLyrics(provider)
    }

    suspend fun getAllLyricsForCurrentSong(): List<com.gratia.music.data.model.LyricsEntity> {
        return lyricsManager.getAllLyricsForCurrentSong()
    }

    fun setActiveLyrics(provider: String) {
        lyricsManager.setActiveLyrics(provider)
    }

    /**
     * Starts a track.
     *
     * Deliberately does *not* touch the track's play count: selecting a song is
     * not listening to it. A play is recorded later, by the player, once enough
     * of the track has actually been heard (spec 14) — the old tap-time
     * increment made "Most Played" a list of everything that was ever opened.
     */
    fun playSong(
        song: SongEntity,
        songQueue: List<SongEntity>,
        origin: PlaybackOrigin = PlaybackOrigin.UNKNOWN,
    ) {
        isDailyMixActive = false // Reset daily mix state for normal playback
        playerManager.playSong(song, songQueue, origin = origin)
    }

    fun playDailyMix(song: SongEntity, mixQueue: List<SongEntity>, origin: PlaybackOrigin = PlaybackOrigin.HOME) {
        isDailyMixActive = true
        playerManager.playSong(song, mixQueue, origin = origin)
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

    /**
     * Collapse → Expanded. Idempotent: a tap on an already-expanded player
     * must not restart its entry animation, so re-expanding is a no-op.
     */
    fun expandPlayer() {
        _presentation.value = PlayerPresentationState.Expanded
    }

    /**
     * Expanded → Collapsed. The single convergence point for every path out
     * of the expanded player — the drag handle, a fast flick, system back —
     * so none of them can leave the two surfaces disagreeing. Idempotent.
     */
    fun collapsePlayer() {
        _presentation.value = PlayerPresentationState.Collapsed
    }

    fun setLyricsOverlayOpen(open: Boolean) {
        _lyricsOverlayOpen.value = open
    }

    fun removeFromQueue(songId: String) = playerManager.removeFromQueue(songId)
    fun removeQueueItemAt(index: Int) = playerManager.removeQueueItemAt(index)
    fun moveInQueue(from: Int, to: Int) = playerManager.moveInQueue(from, to)
    
    fun updateUpcomingQueue(newUpcomingQueue: List<SongEntity>, startIndex: Int) {
        playerManager.updateUpcomingQueue(newUpcomingQueue, startIndex)
    }

    /** Tier-aware variant: rebuilds only the user's rows, keeps the radio tail. */
    fun updateUserQueueUpcoming(newUserQueue: List<SongEntity>, startIndex: Int) {
        playerManager.updateUserQueueUpcoming(newUserQueue, startIndex)
    }

    /** Retry a failed AutoPlay radio fetch (queue section error state). */
    fun retryAutoplay() = playerManager.retryAutoplay()
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

    /**
     * Plays a recorded history entry (spec 23).
     *
     * The timeline row is the record of what played; the track it names is
     * reconstructed from the songs table when it has a row there (a download,
     * a favorite, a local file) and from the remote identity otherwise —
     * exactly what [SongEntity] already carries. A remote entry resolves its
     * stream through the same play path as any catalogue track; a local entry
     * plays its file.
     */
    fun playFromHistory(entry: com.gratia.music.data.model.PlaybackHistoryEntity) {
        viewModelScope.launch {
            val song = songRepository.getSongById(entry.songId)
                ?: entry.toSongEntity()
            playerManager.playSong(
                song,
                listOf(song),
                origin = when (entry.source) {
                    com.gratia.music.data.model.MusicSource.LOCAL.name -> PlaybackOrigin.LOCAL_MUSIC
                    com.gratia.music.data.model.MusicSource.DOWNLOADED_REMOTE.name -> PlaybackOrigin.DOWNLOADS
                    else -> PlaybackOrigin.HOME
                },
            )
        }
    }

    // ── Downloads ────────────────────────────────────────────────────────

    val downloadStates = GratiaApp.instance.downloadStateHolder.states

    /** The download state of [song], from the stable recording key. */
    fun downloadStateFor(song: SongEntity): com.gratia.music.download.DownloadState {
        if (!song.hasRemoteIdentity()) return com.gratia.music.download.DownloadState.NotDownloaded
        if (song.isDownloaded) return com.gratia.music.download.DownloadState.Downloaded
        return GratiaApp.instance.downloadStateHolder.stateFor(song.downloadKey())
    }

    /** Only remote tracks can be downloaded; local files already are. */
    fun canDownload(song: SongEntity): Boolean =
        song.hasRemoteIdentity() && !song.isDownloaded

    fun downloadSong(song: SongEntity) {
        if (!canDownload(song)) return
        // Persist the row BEFORE the download starts. The worker looks the
        // track up in Room by id, and a song straight from Search or a remote
        // shelf exists only in memory — that lookup failed, the worker bailed
        // with "Track not found", and the tap did nothing. Liking was the only
        // path that ever inserted a remote row, which is why downloads
        // appeared to work for liked music and for nothing else.
        if (song.storageProvider != "local") {
            viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                songRepository.insertSong(song)
            }
        }
        GratiaApp.instance.downloadManager.enqueueDownload(song.downloadKey())
    }

    fun removeDownload(song: SongEntity) {
        if (!song.hasRemoteIdentity()) return
        GratiaApp.instance.downloadManager.removeDownload(song.downloadKey(), song.id)
    }

    fun toggleFavorite(song: SongEntity) {
        val newFavorite = !song.isFavorite
        viewModelScope.launch {
            // Optimistically update the global set
            val currentFavs = _favoriteSongIds.value.toMutableSet()
            if (newFavorite) currentFavs.add(song.id) else currentFavs.remove(song.id)
            _favoriteSongIds.value = currentFavs

            // A remote song has no row in the songs table — it lives in memory
            // only until something makes it worth keeping. Liking it is that
            // something: insert it (stable ytm_ id, no localUri) so the
            // favorite UPDATE lands and the song survives the restart.
            if (song.storageProvider != "local" && newFavorite) {
                songRepository.insertSong(song)
            }

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
            if (currentSong.value?.id == song.id) {
                playerManager.nextSong()
            }
            // Now remove from queue since it's no longer the current song
            playerManager.removeFromQueue(song.id)
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
