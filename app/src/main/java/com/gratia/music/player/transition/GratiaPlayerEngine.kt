package com.gratia.music.player.transition

import android.content.Context
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.DefaultAudioOffloadSupportProvider
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.common.TrackSelectionParameters.AudioOffloadPreferences
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import android.os.Handler
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.DataSpec
import android.os.Build
import androidx.media3.common.TrackSelectionParameters
// Gratia imports
import com.gratia.music.audio.EqualizerManager
import com.gratia.music.player.PlaybackDataSources
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Manages two ExoPlayer instances (A and B) to enable seamless crossfade transitions.
 *
 * Player A is the designated "master" player, which is exposed to the MediaSession.
 * Player B is the auxiliary player used to pre-buffer and fade in the next track.
 * After a transition, the players swap roles — Player A adopts the state of Player B,
 * ensuring continuity for the MediaSession.
 */
@OptIn(UnstableApi::class)
class GratiaPlayerEngine(
    private val context: Context
) {
    companion object {
        private const val TAG = "GratiaPlayerEngine"

        /**
         * Process-wide handle on the live engine, for components outside the
         * service that must reach it synchronously — the only one is
         * [com.gratia.music.player.PlayerManager] cancelling a scheduled
         * crossfade the moment the listener manually picks a different track
         * (old audio stops now; the pre-buffered cargo belongs to the replaced
         * track). Set in [initialize]; absent before the service starts, and
         * every caller tolerates that.
         */
        @Volatile
        var hook: GratiaPlayerEngine? = null
            private set
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var transitionJob: Job? = null
    @Volatile
    private var transitionRunning = false

    private lateinit var playerA: ExoPlayer
    private lateinit var playerB: ExoPlayer

    private val onPlayerSwappedListeners = mutableListOf<(Player) -> Unit>()
    private val onTrackTransitionedListeners = mutableListOf<(String) -> Unit>()

    /**
     * Supplies the media item that should crossfade in after the current one.
     *
     * The engine cannot work this out for itself. Gratia's playback keeps one
     * MediaItem in the player at a time — [com.gratia.music.player.PlayerManager]
     * resolves a stream and sets that track as it starts — so the player's own
     * timeline has nothing after the current item, and every timeline-derived
     * "next" is empty. Asking Gratia's queue is the only answer that is ever
     * right.
     *
     * Implementation lives in the playback service so the engine stays free of
     * app-layer dependencies. Returning null means "there is genuinely nothing
     * next", which cancels the transition rather than guessing.
     */
    fun interface NextItemProvider {
        suspend fun nextMediaItemAfter(currentMediaId: String): MediaItem?
    }

    private var nextItemProvider: NextItemProvider? = null

    fun setNextItemProvider(provider: NextItemProvider?) {
        nextItemProvider = provider
    }

    /** The next track, resolved and ready to hand to [prepareNext]. */
    suspend fun resolveNextMediaItem(currentMediaId: String): MediaItem? =
        nextItemProvider?.nextMediaItemAfter(currentMediaId)

    /**
     * Notified with the media id the engine has advanced onto. Gratia's queue
     * state follows this, because a crossfade changes the track without the app
     * ever issuing a play command.
     */
    fun addTrackTransitionedListener(listener: (String) -> Unit) {
        onTrackTransitionedListeners.add(listener)
    }

    fun removeTrackTransitionedListener(listener: (String) -> Unit) {
        onTrackTransitionedListeners.remove(listener)
    }

    // Active Audio Session ID Flow — used for equalizer re-attachment
    private val _activeAudioSessionId = MutableStateFlow(0)
    val activeAudioSessionId: StateFlow<Int> = _activeAudioSessionId.asStateFlow()

    // Audio Focus Management — managed manually so both players share a single focus request
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var isFocusLossPause = false

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                Log.d(TAG, "AudioFocus LOSS. Pausing both players.")
                isFocusLossPause = false
                playerA.playWhenReady = false
                playerB.playWhenReady = false
                abandonAudioFocus()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                Log.d(TAG, "AudioFocus LOSS_TRANSIENT. Pausing.")
                isFocusLossPause = true
                playerA.playWhenReady = false
                playerB.playWhenReady = false
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                Log.d(TAG, "AudioFocus GAIN. Resuming if paused by loss.")
                if (isFocusLossPause) {
                    isFocusLossPause = false
                    playerA.playWhenReady = true
                    if (transitionRunning) playerB.playWhenReady = true
                }
            }
        }
    }

    // Listener attached to the active master player (playerA) for audio focus management
    private val masterPlayerListener = object : Player.Listener {
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (playWhenReady) {
                requestAudioFocus()
            } else {
                if (!isFocusLossPause) {
                    abandonAudioFocus()
                }
            }
        }
        
        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            _activeAudioSessionId.value = audioSessionId
            Log.d(TAG, "Audio session ID changed: $audioSessionId")
        }
    }

    fun addPlayerSwapListener(listener: (Player) -> Unit) {
        onPlayerSwappedListeners.add(listener)
    }

    fun removePlayerSwapListener(listener: (Player) -> Unit) {
        onPlayerSwappedListeners.remove(listener)
    }

    /** The master player instance that should be connected to the MediaSession. */
    val masterPlayer: Player
        get() = playerA

    fun isTransitionRunning(): Boolean = transitionRunning || transitionJob?.isActive == true

    fun getAudioSessionId(): Int = if (::playerA.isInitialized) playerA.audioSessionId else 0

    private var isReleased = false

    fun initialize() {
        if (!isReleased && ::playerA.isInitialized && playerA.applicationLooper.thread.isAlive) return

        if (::playerA.isInitialized) {
            try { playerA.release() } catch (_: Exception) {}
        }
        if (::playerB.isInitialized) {
            try { playerB.release() } catch (_: Exception) {}
        }

        playerA = buildPlayer(handleAudioFocus = false)
        playerB = buildPlayer(handleAudioFocus = false)
        hook = this

        playerA.addListener(masterPlayerListener)

        _activeAudioSessionId.value = playerA.audioSessionId

        isReleased = false
        Log.d(TAG, "GratiaPlayerEngine initialized. SessionA=${playerA.audioSessionId}")
    }

    private fun requestAudioFocus() {
        if (audioFocusRequest != null) return

        val attributes = android.media.AudioAttributes.Builder()
            .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener(focusChangeListener)
            .build()

        val result = audioManager.requestAudioFocus(request)
        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            audioFocusRequest = request
        } else {
            Log.w(TAG, "AudioFocus Request Failed: $result")
            playerA.playWhenReady = false
        }
    }

    private fun abandonAudioFocus() {
        audioFocusRequest?.let {
            audioManager.abandonAudioFocusRequest(it)
            audioFocusRequest = null
        }
    }

    private fun buildPlayer(
        handleAudioFocus: Boolean,
    ): ExoPlayer {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(15_000, 30_000, 1_500, 2_500)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        // Create high-fidelity audio sink
        val audioSink = DefaultAudioSink.Builder(context)
            .setEnableFloatOutput(true) // Crucial to prevent down-conversion of 24/32 bit to 16 bit PCM
            .setEnableAudioTrackPlaybackParams(false) // Prevent sonic artifacts from speed algorithms
            .setAudioOffloadSupportProvider(DefaultAudioOffloadSupportProvider(context)) // Enable offloading queries
            .build()

        val renderersFactory = object : DefaultRenderersFactory(context) {
            override fun buildAudioRenderers(
                context: Context,
                extensionRendererMode: Int,
                mediaCodecSelector: MediaCodecSelector,
                enableDecoderFallback: Boolean,
                sink: AudioSink,
                eventHandler: Handler,
                eventListener: AudioRendererEventListener,
                out: ArrayList<Renderer>
            ) {
                // Pass our custom audioSink instead of the default one
                super.buildAudioRenderers(
                    context,
                    extensionRendererMode,
                    mediaCodecSelector,
                    enableDecoderFallback,
                    audioSink,
                    eventHandler,
                    eventListener,
                    out
                )
            }
        }.setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        // Every read the player makes goes through the shared chain — ranged
        // fetches, and the per-request headers of whichever client minted the
        // URL. Without it a stream resolves fine and then trickles in at
        // playback speed, which is heard as silence. See [PlaybackDataSources].
        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(PlaybackDataSources.create(context))

        val trackSelector = DefaultTrackSelector(context)
        val audioOffloadPreferences = AudioOffloadPreferences.Builder()
            .setAudioOffloadMode(AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED)
            .setIsGaplessSupportRequired(true)
            .build()
        trackSelector.parameters = trackSelector.buildUponParameters()
            .setAudioOffloadPreferences(audioOffloadPreferences)
            .build()

        return ExoPlayer.Builder(context, renderersFactory)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(mediaSourceFactory)
            .setTrackSelector(trackSelector)
            .build().apply {
                setAudioAttributes(audioAttributes, handleAudioFocus)
                setHandleAudioBecomingNoisy(true)
                setWakeMode(C.WAKE_MODE_LOCAL)
                playWhenReady = false
            }
    }

    fun setPauseAtEndOfMediaItems(shouldPause: Boolean) {
        playerA.pauseAtEndOfMediaItems = shouldPause
    }

    /**
     * Enables or disables gapless playback.
     * When enabled, uses ExoPlayer's native gapless mechanism.
     */
    fun setGaplessPlayback(enabled: Boolean) {
        if (::playerA.isInitialized) {
            playerA.pauseAtEndOfMediaItems = !enabled
        }
        if (::playerB.isInitialized) {
            playerB.pauseAtEndOfMediaItems = !enabled
        }
        Log.d(TAG, "Gapless playback ${if (enabled) "enabled" else "disabled"}")
    }

    fun setSkipSilenceEnabled(enabled: Boolean) {
        if (::playerA.isInitialized) {
            playerA.skipSilenceEnabled = enabled
        }
        if (::playerB.isInitialized) {
            playerB.skipSilenceEnabled = enabled
        }
        Log.d(TAG, "Skip silence ${if (enabled) "enabled" else "disabled"}")
    }

    /**
     * Pre-buffers the next track on Player B.
     * Sets volume to 0 and pauses, ready for the crossfade transition.
     */
    fun prepareNext(mediaItem: MediaItem, startPositionMs: Long = 0L) {
        try {
            Log.d(
                TAG,
                "CROSSFADE_PREPARE next=${mediaItem.mediaId} " +
                    "outgoing=${playerA.currentMediaItem?.mediaId ?: "none"} " +
                    "playerAState=${playerA.playbackState} position=${playerA.currentPosition}"
            )
            playerB.stop()
            playerB.clearMediaItems()
            playerB.playWhenReady = false
            playerB.setMediaItem(mediaItem)
            playerB.prepare()
            playerB.volume = 0f
            if (startPositionMs > 0) {
                playerB.seekTo(startPositionMs)
            } else {
                playerB.seekTo(0)
            }
            playerB.pause()
            Log.d(TAG, "CROSSFADE_READY next=${mediaItem.mediaId} state=${playerB.playbackState} volume=0")
        } catch (e: Exception) {
            Log.e(TAG, "CROSSFADE_ERROR stage=prepare ${e.message}")
        }
    }

    /**
     * Cancels any pending transition and resets Player B.
     */
    fun cancelNext() {
        val wasRunning = isTransitionRunning()
        transitionJob?.cancel()
        transitionRunning = false
        if (wasRunning) Log.d(TAG, "CROSSFADE_CANCELLED")
        if (::playerB.isInitialized && playerB.mediaItemCount > 0) {
            Log.d(TAG, "CROSSFADE_CANCEL next=${playerB.currentMediaItem?.mediaId}")
            playerB.stop()
            playerB.clearMediaItems()
        }
        if (::playerA.isInitialized) {
            playerA.volume = 1f
            setPauseAtEndOfMediaItems(false)
        }
    }

    /**
     * Performs the crossfade transition using the given settings.
     */
    @Synchronized
    fun performTransition(durationMs: Int) {
        if (isTransitionRunning()) {
            Log.w(TAG, "Ignoring duplicate transition request; a transition is already active.")
            return
        }

        Log.d(
            TAG,
            "CROSSFADE_TRIGGER durationMs=$durationMs " +
                "outgoing=${playerA.currentMediaItem?.mediaId ?: "none"} " +
                "incoming=${playerB.currentMediaItem?.mediaId ?: "none"} " +
                "position=${playerA.currentPosition} trackDuration=${playerA.duration}",
        )

        transitionRunning = true
        transitionJob = scope.launch {
            try {
                performOverlapTransition(durationMs)
            } catch (_: CancellationException) {
                Log.d(TAG, "CROSSFADE_CANCELLED stage=fade")
            } catch (e: Exception) {
                Log.e(TAG, "CROSSFADE_ERROR stage=fade ${e.message}")
                playerA.volume = 1f
                setPauseAtEndOfMediaItems(false)
                playerB.stop()
            } finally {
                transitionRunning = false
            }
        }
    }

    /**
     * Core crossfade logic:
     * 1. Waits for Player B to be ready
     * 2. Starts Player B at volume 0
     * 3. Swaps players EARLY so UI immediately shows the new song
     * 4. Transfers queue history/future and playback settings
     * 5. Runs a fade loop with shaped curves
     * 6. Releases old player and recreates it fresh
     */
    private suspend fun performOverlapTransition(durationMs: Int) {
        if (playerB.mediaItemCount == 0) {
            Log.w(TAG, "CROSSFADE_CANCEL reason=incoming_not_prepared")
            playerA.volume = 1f
            setPauseAtEndOfMediaItems(false)
            return
        }

        val outgoingMediaId = playerA.currentMediaItem?.mediaId
        val incomingMediaIdForLog = playerB.currentMediaItem?.mediaId

        val outgoingPlayer = playerA
        val incomingPlayer = playerB

        val isSelfTransition = outgoingPlayer.currentMediaItem?.mediaId == incomingPlayer.currentMediaItem?.mediaId
        val outgoingMediaItemCount = outgoingPlayer.mediaItemCount
        val currentOutgoingIndex = outgoingPlayer.currentMediaItemIndex
            .takeIf { it in 0 until outgoingMediaItemCount }
            ?: 0

        val outgoingTimeline = outgoingPlayer.currentTimeline
        val timelineTargetIndex = if (!outgoingTimeline.isEmpty && currentOutgoingIndex != C.INDEX_UNSET) {
            outgoingTimeline.getNextWindowIndex(
                currentOutgoingIndex,
                outgoingPlayer.repeatMode,
                outgoingPlayer.shuffleModeEnabled
            )
        } else {
            C.INDEX_UNSET
        }
        val incomingMediaId = incomingPlayer.currentMediaItem?.mediaId
        val incomingQueueIndex = when {
            isSelfTransition -> currentOutgoingIndex
            timelineTargetIndex in 0 until outgoingMediaItemCount && 
                outgoingPlayer.getMediaItemAt(timelineTargetIndex).mediaId == incomingMediaId -> timelineTargetIndex
            incomingMediaId != null -> (0 until outgoingMediaItemCount)
                .firstOrNull { index -> outgoingPlayer.getMediaItemAt(index).mediaId == incomingMediaId }
                ?: currentOutgoingIndex
            else -> currentOutgoingIndex
        }

        val historyToTransfer = mutableListOf<MediaItem>()
        for (i in 0 until incomingQueueIndex) {
            historyToTransfer.add(outgoingPlayer.getMediaItemAt(i))
        }

        val futureToTransfer = mutableListOf<MediaItem>()
        for (i in (incomingQueueIndex + 1) until outgoingMediaItemCount) {
            futureToTransfer.add(outgoingPlayer.getMediaItemAt(i))
        }

        incomingPlayer.repeatMode = outgoingPlayer.repeatMode
        incomingPlayer.shuffleModeEnabled = outgoingPlayer.shuffleModeEnabled
        incomingPlayer.playbackParameters = outgoingPlayer.playbackParameters

        if (historyToTransfer.isNotEmpty()) {
            incomingPlayer.addMediaItems(0, historyToTransfer)
        }

        if (futureToTransfer.isNotEmpty()) {
            incomingPlayer.addMediaItems(futureToTransfer)
        }

        incomingPlayer.seekTo(incomingQueueIndex, 0)

        outgoingPlayer.removeListener(masterPlayerListener)

        playerA = incomingPlayer
        playerB = outgoingPlayer
        Log.d(TAG, "CROSSFADE_SWAP outgoing=$outgoingMediaId incoming=$incomingMediaIdForLog")

        playerB.pauseAtEndOfMediaItems = true
        playerA.pauseAtEndOfMediaItems = false

        playerA.addListener(masterPlayerListener)
        if (playerA.playWhenReady) {
            requestAudioFocus()
        }

        onPlayerSwappedListeners.forEach { it(playerA) }

        // The app's queue has no idea it changed tracks: nothing asked it to.
        // Reporting the incoming media id is how the mini player, the expanded
        // player and AutoPlay follow a crossfade instead of describing the
        // previous song until the next command.
        incomingMediaIdForLog?.let { id ->
            onTrackTransitionedListeners.forEach { listener ->
                runCatching { listener(id) }
            }
        }

        _activeAudioSessionId.value = playerA.audioSessionId

        if (playerA.playbackState == Player.STATE_IDLE) {
            playerA.prepare()
        }

        var readinessChecks = 0
        val maxReadinessChecks = 120
        while (playerA.playbackState == Player.STATE_BUFFERING && readinessChecks < maxReadinessChecks) {
            delay(25)
            readinessChecks++
        }

        val incomingReady = playerA.playbackState == Player.STATE_READY
        var isFading = false

        if (incomingReady) {
            playerA.volume = 0f
            playerB.volume = 1f
            if (!playerB.isPlaying && playerB.playbackState == Player.STATE_READY) {
                playerB.play()
            }

            playerA.playWhenReady = true
            playerA.play()

            var playChecks = 0
            val maxPlayChecks = 80
            while (!playerA.isPlaying && playChecks < maxPlayChecks) {
                delay(25)
                playChecks++
            }

            if (playerA.isPlaying) {
                isFading = true
                delay(75)
            }
        }

        if (isFading) {
            val duration = durationMs.toLong().coerceAtLeast(500L)
            val stepMs = 16L
            val startTime = android.os.SystemClock.elapsedRealtime()
            var elapsed = 0L
            var lastLoggedBucket = -1

            Log.d(
                TAG,
                "CROSSFADE_START outgoing=$outgoingMediaId incoming=$incomingMediaIdForLog " +
                    "duration=${duration}ms outgoingPlaying=${playerB.isPlaying} incomingPlaying=${playerA.isPlaying}",
            )

            while (elapsed <= duration) {
                val progress = (elapsed.toFloat() / duration).coerceIn(0f, 1f)

                // Equal-Power Crossfade Curve
                // Using sine/cosine maintains constant acoustic energy: sin^2(x) + cos^2(x) = 1
                val theta = progress * (Math.PI / 2.0)
                val volIn = kotlin.math.sin(theta).toFloat()
                val volOut = kotlin.math.cos(theta).toFloat()

                playerA.volume = volIn
                playerB.volume = volOut.coerceIn(0f, 1f)

                // A few progress points, not one per frame: 60 log lines a
                // second is its own performance problem.
                val bucket = (progress * 4).toInt()
                if (bucket != lastLoggedBucket) {
                    lastLoggedBucket = bucket
                    Log.d(
                        TAG,
                        "CROSSFADE_PROGRESS ${(progress * 100).toInt()}% " +
                            "in=$volIn out=$volOut outgoingPlaying=${playerB.isPlaying} incomingPlaying=${playerA.isPlaying}",
                    )
                    Log.d(TAG, "CROSSFADE_IN volume=$volIn incoming#$incomingMediaIdForLog")
                    Log.d(TAG, "CROSSFADE_OUT volume=$volOut outgoing#$outgoingMediaId")
                }

                if (playerA.playbackState == Player.STATE_ENDED || playerB.playbackState == Player.STATE_ENDED) {
                    Log.w(TAG, "CROSSFADE_SHORT track ended mid-fade; completing immediately")
                    break
                }

                // The listener paused. Finish now and cut the outgoing audio,
                // rather than leaving the previous song playing on underneath a
                // paused screen — the old track fading out is only acceptable
                // while something is meant to be heard.
                if (!playerA.playWhenReady) {
                    Log.d(TAG, "CROSSFADE_CANCELLED reason=paused_during_fade")
                    break
                }

                delay(stepMs)
                elapsed = android.os.SystemClock.elapsedRealtime() - startTime
            }
        } else {
            Log.w(TAG, "CROSSFADE_DEGRADED reason=incoming_never_started — cutting over instead")
            playerA.volume = 1f
            if (playerA.playbackState == Player.STATE_READY) {
                playerA.play()
            } else {
                playerA.playWhenReady = true
            }
        }

        playerB.volume = 0f
        playerA.volume = 1f

        playerB.pause()
        playerB.stop()
        playerB.clearMediaItems()
        Log.d(TAG, "CROSSFADE_COMPLETE now=${playerA.currentMediaItem?.mediaId} masterState=${playerA.playbackState}")

        try {
            playerB.seekTo(0)
            playerB.setPlaybackSpeed(1.0f)
            playerB.setPlaybackParameters(playerB.playbackParameters)
        } catch (e: Exception) {
            playerB.release()
            playerB = buildPlayer(
                handleAudioFocus = false,
            )
        }
    }

    fun release() {
        setPauseAtEndOfMediaItems(false)
        transitionJob?.cancel()
        abandonAudioFocus()
        if (::playerA.isInitialized) {
            playerA.removeListener(masterPlayerListener)
            playerA.release()
        }
        if (::playerB.isInitialized) playerB.release()
        if (hook === this) hook = null
        isReleased = true
        Log.d(TAG, "GratiaPlayerEngine released.")
    }
}
