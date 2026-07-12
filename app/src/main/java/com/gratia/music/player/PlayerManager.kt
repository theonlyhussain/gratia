package com.gratia.music.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.gratia.music.GratiaApp
import com.gratia.music.data.model.SongEntity
import com.gratia.music.data.repository.ListeningEventRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import android.graphics.BitmapFactory
import android.graphics.Bitmap

/**
 * Manages real audio playback using Media3 MediaController connected to PlaybackService.
 * 
 * IMPORTANT: This is a singleton owned by GratiaApp. It must NEVER be released by a ViewModel.
 * The MediaController connection is self-healing — if the service dies, it reconnects automatically.
 */
class PlayerManager(private val context: Context) {

    companion object {
        private const val TAG = "GratiaPlayer"
    }

    private var mediaController: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var isConnecting = false

    private val listeningRepo = ListeningEventRepository(GratiaApp.instance.database.listeningEventDao())

    private var currentSessionStartTimeMs: Long = 0L

    private val _currentSong = MutableStateFlow<SongEntity?>(null)
    val currentSong: StateFlow<SongEntity?> = _currentSong.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentTimeMs = MutableStateFlow(0L)
    val currentTimeMs: StateFlow<Long> = _currentTimeMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _queue = MutableStateFlow<List<SongEntity>>(emptyList())
    val queue: StateFlow<List<SongEntity>> = _queue.asStateFlow()

    private val _shuffleEnabled = MutableStateFlow(false)
    val shuffleEnabled: StateFlow<Boolean> = _shuffleEnabled.asStateFlow()

    private val _repeatMode = MutableStateFlow(RepeatMode.OFF)
    val repeatMode: StateFlow<RepeatMode> = _repeatMode.asStateFlow()

    private val _playbackError = MutableStateFlow<String?>(null)
    val playbackError: StateFlow<String?> = _playbackError.asStateFlow()

    private var progressJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    // Pending playback request — used when we need to reconnect before playing
    private var pendingPlay: Pair<SongEntity, List<SongEntity>>? = null

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            Log.d(TAG, "onPlaybackStateChanged: state=$playbackState (IDLE=1, BUFFERING=2, READY=3, ENDED=4)")
            val controller = mediaController ?: return
            when (playbackState) {
                Player.STATE_READY -> {
                    val dur = controller.duration.coerceAtLeast(0)
                    Log.d(TAG, "STATE_READY: duration=${dur}ms")
                    _durationMs.value = dur
                    _playbackError.value = null
                }
                Player.STATE_ENDED -> {
                    Log.d(TAG, "STATE_ENDED: handling song end")
                    logListeningEvent("complete", completed = true)
                    currentSessionStartTimeMs = 0L
                    handleSongEnded()
                }
                Player.STATE_IDLE -> {
                    Log.d(TAG, "STATE_IDLE")
                }
                Player.STATE_BUFFERING -> {
                    Log.d(TAG, "STATE_BUFFERING")
                }
            }
        }

        override fun onIsPlayingChanged(playing: Boolean) {
            Log.d(TAG, "onIsPlayingChanged: playing=$playing")
            _isPlaying.value = playing
            if (playing) {
                currentSessionStartTimeMs = System.currentTimeMillis()
                startProgressUpdates()
            } else {
                stopProgressUpdates()
                logListeningEvent("pause")
                currentSessionStartTimeMs = 0L
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e(TAG, "onPlayerError: ${error.errorCodeName} — ${error.message}")
            _playbackError.value = "Couldn't play this song. Try another file or check permission."
            // Don't manually set _isPlaying here — ExoPlayer will fire onIsPlayingChanged(false) 
        }
    }

    init {
        Log.d(TAG, "PlayerManager init — connecting to PlaybackService")
        connect()
    }

    /**
     * Connect (or reconnect) to the PlaybackService via MediaController.
     * Safe to call multiple times — no-ops if already connected.
     */
    private fun connect() {
        // Already connected and alive
        if (mediaController?.isConnected == true) {
            Log.d(TAG, "connect(): already connected")
            return
        }

        // Already in the process of connecting
        if (isConnecting) {
            Log.d(TAG, "connect(): connection already in progress")
            return
        }

        isConnecting = true
        Log.d(TAG, "connect(): building new MediaController connection")

        // Clean up old future if any
        controllerFuture?.let {
            try {
                MediaController.releaseFuture(it)
            } catch (e: Exception) {
                Log.w(TAG, "connect(): error releasing old future: ${e.message}")
            }
        }
        mediaController = null

        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture?.addListener(
            {
                try {
                    val controller = controllerFuture?.get()
                    if (controller != null) {
                        mediaController = controller
                        controller.addListener(playerListener)
                        Log.d(TAG, "connect(): SUCCESS — MediaController connected")
                        
                        // Sync state from the player (in case service was already playing)
                        syncStateFromController(controller)
                        
                        // If there's a pending play request, execute it now
                        val pending = pendingPlay
                        if (pending != null) {
                            pendingPlay = null
                            Log.d(TAG, "connect(): executing pending play for '${pending.first.title}'")
                            playSong(pending.first, pending.second)
                        }
                    } else {
                        Log.e(TAG, "connect(): controller is null after Future.get()")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "connect(): FAILED — ${e.message}")
                } finally {
                    isConnecting = false
                }
            },
            ContextCompat.getMainExecutor(context)
        )
    }

    /**
     * Ensure we have a live connection. If not, trigger reconnection.
     * Returns true if connected right now, false if reconnecting (caller should retry or queue).
     */
    private fun ensureConnected(): Boolean {
        val controller = mediaController
        if (controller != null && controller.isConnected) {
            return true
        }
        Log.w(TAG, "ensureConnected(): controller is dead/disconnected, reconnecting...")
        connect()
        return false
    }

    /**
     * Sync our StateFlows from the controller's current state.
     * Used after reconnection to pick up where the service left off.
     */
    private fun syncStateFromController(controller: MediaController) {
        val isActuallyPlaying = controller.isPlaying
        _isPlaying.value = isActuallyPlaying
        _durationMs.value = controller.duration.coerceAtLeast(0)
        _currentTimeMs.value = controller.currentPosition.coerceAtLeast(0)
        Log.d(TAG, "syncState: isPlaying=$isActuallyPlaying, duration=${_durationMs.value}ms, position=${_currentTimeMs.value}ms")
        
        if (isActuallyPlaying) {
            startProgressUpdates()
        } else {
            stopProgressUpdates()
        }
    }

    fun playSong(song: SongEntity, songQueue: List<SongEntity>) {
        Log.d(TAG, "playSong: '${song.title}' by ${song.artist}")
        
        // Log listening event for previously playing song
        val previousSong = _currentSong.value
        if (previousSong != null && previousSong.id != song.id) {
            logListeningEvent("skip", skipped = true)
            currentSessionStartTimeMs = 0L
        }

        _currentSong.value = song
        _queue.value = songQueue
        _currentTimeMs.value = 0L
        _playbackError.value = null

        val uri = song.localUri
        if (uri == null) {
            Log.e(TAG, "playSong: localUri is null for '${song.title}'")
            return
        }

        if (!ensureConnected()) {
            Log.w(TAG, "playSong: not connected, queuing for after reconnect")
            pendingPlay = Pair(song, songQueue)
            return
        }
        
        val controller = mediaController!!

        try {
            val metadataBuilder = MediaMetadata.Builder()
                .setTitle(song.title)
                .setArtist(song.artist)
                .setAlbumTitle(song.album ?: "Gratia")
            
            if (!song.coverArtPath.isNullOrBlank()) {
                try {
                    val bitmap = BitmapFactory.decodeFile(song.coverArtPath)
                    if (bitmap != null) {
                        val stream = ByteArrayOutputStream()
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                        metadataBuilder.setArtworkData(stream.toByteArray(), MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "playSong: failed to load cover art: ${e.message}")
                }
            }

            val mediaItem = MediaItem.Builder()
                .setUri(Uri.parse(uri))
                .setMediaId(song.id)
                .setMediaMetadata(metadataBuilder.build())
                .build()

            controller.setMediaItem(mediaItem)
            controller.prepare()
            controller.play()
            Log.d(TAG, "playSong: commands sent to controller")
            
        } catch (e: Exception) {
            Log.e(TAG, "playSong: exception — ${e.message}")
            _playbackError.value = "Couldn't play this song. Try another file or check permission."
        }
    }

    fun togglePlay() {
        if (!ensureConnected()) return
        val controller = mediaController ?: return
        if (controller.isPlaying) {
            Log.d(TAG, "togglePlay: pausing")
            controller.pause()
        } else {
            Log.d(TAG, "togglePlay: playing")
            controller.play()
        }
    }

    fun pause() {
        if (!ensureConnected()) return
        Log.d(TAG, "pause()")
        mediaController?.pause()
    }

    fun resume() {
        if (!ensureConnected()) return
        Log.d(TAG, "resume()")
        mediaController?.play()
    }

    fun seekTo(positionMs: Long) {
        if (!ensureConnected()) return
        val controller = mediaController ?: return
        val clampedPosition = positionMs.coerceIn(0, _durationMs.value.coerceAtLeast(0))
        Log.d(TAG, "seekTo: ${clampedPosition}ms")
        controller.seekTo(clampedPosition)
        _currentTimeMs.value = clampedPosition
    }

    fun clearQueue() {
        mediaController?.clearMediaItems()
        _currentSong.value = null
        _queue.value = emptyList()
    }

    fun nextSong() {
        val current = _currentSong.value ?: return
        val q = _queue.value
        if (q.isEmpty()) return

        val currentIndex = q.indexOfFirst { it.id == current.id }
        val nextIndex = if (currentIndex == -1) {
            0
        } else if (_shuffleEnabled.value) {
            (q.indices).random()
        } else {
            (currentIndex + 1) % q.size
        }
        
        Log.d(TAG, "nextSong: index $currentIndex -> $nextIndex")
        playSong(q[nextIndex], q)
    }

    fun prevSong() {
        val current = _currentSong.value ?: return
        val q = _queue.value
        if (q.isEmpty()) return

        if (!ensureConnected()) return
        val controller = mediaController ?: return

        if (controller.currentPosition > 3000) {
            Log.d(TAG, "prevSong: restarting current (position > 3s)")
            controller.seekTo(0)
            _currentTimeMs.value = 0
            return
        }

        val currentIndex = q.indexOfFirst { it.id == current.id }
        val prevIndex = if (currentIndex == -1) {
            0
        } else {
            (currentIndex - 1 + q.size) % q.size
        }
        
        Log.d(TAG, "prevSong: index $currentIndex -> $prevIndex")
        playSong(q[prevIndex], q)
    }

    fun toggleShuffle() {
        _shuffleEnabled.value = !_shuffleEnabled.value
        Log.d(TAG, "toggleShuffle: ${_shuffleEnabled.value}")
    }

    fun cycleRepeatMode() {
        _repeatMode.value = when (_repeatMode.value) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        Log.d(TAG, "cycleRepeatMode: ${_repeatMode.value}")
    }

    fun clearError() {
        _playbackError.value = null
    }

    /** Remove a song from the queue by its ID. */
    fun removeFromQueue(songId: String) {
        val current = _currentSong.value
        if (current?.id == songId) return // Can't remove currently playing song
        val newQueue = _queue.value.filterNot { it.id == songId }
        _queue.value = newQueue
        Log.d(TAG, "removeFromQueue: removed $songId, queue size=${newQueue.size}")
    }

    /** Move a song within the queue from one position to another. */
    fun moveInQueue(from: Int, to: Int) {
        val q = _queue.value.toMutableList()
        if (from < 0 || from >= q.size || to < 0 || to >= q.size) return
        val item = q.removeAt(from)
        q.add(to, item)
        _queue.value = q
        Log.d(TAG, "moveInQueue: $from -> $to")
    }

    /** Play a specific song from the queue by index. */
    fun playFromQueue(index: Int) {
        val q = _queue.value
        if (index < 0 || index >= q.size) return
        Log.d(TAG, "playFromQueue: index=$index, song='${q[index].title}'")
        playSong(q[index], q)
    }

    private fun handleSongEnded() {
        when (_repeatMode.value) {
            RepeatMode.ONE -> {
                Log.d(TAG, "handleSongEnded: REPEAT_ONE — restarting")
                if (!ensureConnected()) return
                val controller = mediaController ?: return
                controller.seekTo(0)
                controller.play()
            }
            RepeatMode.ALL -> {
                Log.d(TAG, "handleSongEnded: REPEAT_ALL — next song")
                nextSong()
            }
            RepeatMode.OFF -> {
                val current = _currentSong.value ?: return
                val q = _queue.value
                val currentIndex = q.indexOfFirst { it.id == current.id }
                if (currentIndex < q.size - 1) {
                    Log.d(TAG, "handleSongEnded: REPEAT_OFF — next in queue")
                    nextSong()
                } else {
                    Log.d(TAG, "handleSongEnded: REPEAT_OFF — end of queue, stopping")
                    // Don't manually set _isPlaying — ExoPlayer will emit onIsPlayingChanged(false)
                }
            }
        }
    }

    private fun startProgressUpdates() {
        stopProgressUpdates()
        progressJob = scope.launch {
            while (isActive) {
                val controller = mediaController
                if (controller != null && controller.isConnected) {
                    if (controller.isPlaying) {
                        val pos = controller.currentPosition.coerceAtLeast(0)
                        val dur = _durationMs.value
                        _currentTimeMs.value = if (dur > 0) pos.coerceAtMost(dur) else pos
                    }
                } else {
                    // Controller died — stop polling and reflect reality
                    Log.w(TAG, "progressUpdate: controller disconnected, stopping updates")
                    _isPlaying.value = false
                    stopProgressUpdates()
                    // Attempt reconnection
                    connect()
                    return@launch
                }
                delay(100) // 100ms for smooth UI updates
            }
        }
    }

    private fun stopProgressUpdates() {
        progressJob?.cancel()
        progressJob = null
    }

    /**
     * Helper to log listening events with safety checks.
     */
    private fun logListeningEvent(reason: String, completed: Boolean = false, skipped: Boolean = false) {
        val current = _currentSong.value ?: return
        if (currentSessionStartTimeMs <= 0) return
        val listenedSec = (System.currentTimeMillis() - currentSessionStartTimeMs) / 1000
        if (listenedSec <= (if (reason == "pause") 1 else 0)) return
        scope.launch(Dispatchers.IO) {
            listeningRepo.logEvent(current.id, reason, listenedSec, completed = completed, skipped = skipped)
        }
    }

    /**
     * Release is only for app-level cleanup (e.g., Application.onTerminate).
     * ViewModels must NEVER call this.
     */
    fun release() {
        Log.d(TAG, "release(): cleaning up PlayerManager")
        stopProgressUpdates()
        mediaController?.removeListener(playerListener)
        controllerFuture?.let {
            try {
                MediaController.releaseFuture(it)
            } catch (e: Exception) {
                Log.w(TAG, "release(): error releasing future: ${e.message}")
            }
        }
        mediaController = null
        controllerFuture = null
        isConnecting = false
    }
}

enum class RepeatMode {
    OFF, ALL, ONE
}
