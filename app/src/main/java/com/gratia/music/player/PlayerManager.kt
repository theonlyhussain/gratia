package com.gratia.music.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
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
 */
class PlayerManager(context: Context) {

    private var mediaController: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null

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

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            val controller = mediaController ?: return
            when (playbackState) {
                Player.STATE_READY -> {
                    _durationMs.value = controller.duration.coerceAtLeast(0)
                    _playbackError.value = null
                }
                Player.STATE_ENDED -> {
                    val current = _currentSong.value
                    if (current != null && currentSessionStartTimeMs > 0) {
                        val listenedSec = (System.currentTimeMillis() - currentSessionStartTimeMs) / 1000
                        if (listenedSec > 0) {
                            scope.launch(Dispatchers.IO) {
                                listeningRepo.logEvent(current.id, "complete", listenedSec, completed = true)
                            }
                        }
                    }
                    currentSessionStartTimeMs = 0L
                    handleSongEnded()
                }
                Player.STATE_IDLE -> {}
                Player.STATE_BUFFERING -> {}
            }
        }

        override fun onIsPlayingChanged(playing: Boolean) {
            _isPlaying.value = playing
            if (playing) {
                currentSessionStartTimeMs = System.currentTimeMillis()
                startProgressUpdates()
            } else {
                stopProgressUpdates()
                val current = _currentSong.value
                if (current != null && currentSessionStartTimeMs > 0) {
                    val listenedSec = (System.currentTimeMillis() - currentSessionStartTimeMs) / 1000
                    if (listenedSec > 1) {
                        scope.launch(Dispatchers.IO) {
                            listeningRepo.logEvent(current.id, "pause", listenedSec)
                        }
                    }
                }
                currentSessionStartTimeMs = 0L
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            _playbackError.value = "Couldn't play this song. Try another file or check permission."
            _isPlaying.value = false
        }
    }

    init {
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture?.addListener(
            {
                mediaController = controllerFuture?.get()
                mediaController?.addListener(playerListener)
            },
            ContextCompat.getMainExecutor(context)
        )
    }

    fun playSong(song: SongEntity, songQueue: List<SongEntity>) {
        val previousSong = _currentSong.value
        if (previousSong != null && previousSong.id != song.id && currentSessionStartTimeMs > 0) {
            val listenedSec = (System.currentTimeMillis() - currentSessionStartTimeMs) / 1000
            if (listenedSec > 1) {
                scope.launch(Dispatchers.IO) {
                    listeningRepo.logEvent(previousSong.id, "skip", listenedSec, skipped = true)
                }
            }
            currentSessionStartTimeMs = 0L
        }

        _currentSong.value = song
        _queue.value = songQueue
        _currentTimeMs.value = 0L
        _playbackError.value = null

        val uri = song.localUri ?: return
        val controller = mediaController ?: return

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
                } catch (e: Exception) { }
            }

            val mediaItem = MediaItem.Builder()
                .setUri(Uri.parse(uri))
                .setMediaId(song.id)
                .setMediaMetadata(metadataBuilder.build())
                .build()
                
            controller.setMediaItem(mediaItem)
            controller.prepare()
            controller.play()
            
        } catch (e: Exception) {
            _playbackError.value = "Couldn't play this song. Try another file or check permission."
        }
    }

    fun togglePlay() {
        val controller = mediaController ?: return
        if (controller.isPlaying) {
            controller.pause()
        } else {
            controller.play()
        }
    }

    fun pause() {
        mediaController?.pause()
    }

    fun resume() {
        mediaController?.play()
    }

    fun seekTo(positionMs: Long) {
        val controller = mediaController ?: return
        controller.seekTo(positionMs)
        _currentTimeMs.value = positionMs
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
        
        playSong(q[nextIndex], q)
    }

    fun prevSong() {
        val current = _currentSong.value ?: return
        val q = _queue.value
        val controller = mediaController ?: return
        if (q.isEmpty()) return

        if (controller.currentPosition > 3000) {
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
        
        playSong(q[prevIndex], q)
    }

    fun toggleShuffle() {
        _shuffleEnabled.value = !_shuffleEnabled.value
    }

    fun cycleRepeatMode() {
        _repeatMode.value = when (_repeatMode.value) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
    }

    fun clearError() {
        _playbackError.value = null
    }

    private fun handleSongEnded() {
        val controller = mediaController ?: return
        when (_repeatMode.value) {
            RepeatMode.ONE -> {
                controller.seekTo(0)
                controller.play()
            }
            RepeatMode.ALL -> nextSong()
            RepeatMode.OFF -> {
                val current = _currentSong.value ?: return
                val q = _queue.value
                val currentIndex = q.indexOfFirst { it.id == current.id }
                if (currentIndex < q.size - 1) {
                    nextSong()
                } else {
                    _isPlaying.value = false
                }
            }
        }
    }

    private fun startProgressUpdates() {
        stopProgressUpdates()
        progressJob = scope.launch {
            while (isActive) {
                val controller = mediaController
                if (controller != null) {
                    _currentTimeMs.value = controller.currentPosition.coerceAtLeast(0)
                }
                delay(200)
            }
        }
    }

    private fun stopProgressUpdates() {
        progressJob?.cancel()
        progressJob = null
    }

    fun release() {
        stopProgressUpdates()
        mediaController?.removeListener(playerListener)
        controllerFuture?.let {
            MediaController.releaseFuture(it)
        }
        mediaController = null
    }
}

enum class RepeatMode {
    OFF, ALL, ONE
}
