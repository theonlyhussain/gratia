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
import androidx.media3.common.Tracks
import androidx.media3.common.Format
import androidx.media3.common.C
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.gratia.music.GratiaApp
import com.gratia.music.data.model.SongEntity
import com.gratia.music.data.model.playsFromDevice
import com.gratia.music.data.repository.ListeningEventRepository
import com.gratia.music.data.worker.MetadataSyncWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import android.graphics.BitmapFactory
import android.graphics.Bitmap

data class AudioFormatInfo(
    val mimeType: String?,
    val sampleRate: Int,
    val bitDepth: Int,
    val channelCount: Int,
    val bitrate: Int
)

/**
 * Manages real audio playback using Media3 MediaController connected to PlaybackService.
 * 
 * IMPORTANT: This is a singleton owned by GratiaApp. It must NEVER be released by a ViewModel.
 * The MediaController connection is self-healing — if the service dies, it reconnects automatically.
 */
class PlayerManager(private val context: Context) {

    companion object {
        private const val TAG = "GratiaPlayer"

        /**
         * Stream refresh attempts allowed per track before its failure is
         * declared final and surfaced. Two: one covers the URL-was-retired
         * case (403 refresh genuinely fixes it), the second covers a
         * resolution that came back stale. Beyond that the failure is not a
         * stream problem but a network or playability one, and no amount of
         * re-resolution changes that.
         */
        private const val MAX_STREAM_REFRESHES = 2

        /**
         * Ceiling on the queue persisted with the playback snapshot. A queue
         * can legitimately grow large (radio top-ups, big playlists); rows
         * beyond this are simply not part of the restored session.
         */
        private const val MAX_PERSISTED_QUEUE = 200

        /** A bounded wait: an upgrade search must never outlive its usefulness. */
        const val UPGRADE_SEARCH_TIMEOUT_MS = 20_000L
    }

    private var mediaController: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var isConnecting = false

    private val listeningRepo = ListeningEventRepository(GratiaApp.instance.database.listeningEventDao())
    private val songRepo = com.gratia.music.data.repository.SongRepository(GratiaApp.instance.database.songDao())
    private val historyRepo = GratiaApp.instance.playbackHistoryRepository
    private val settingsDataStore = com.gratia.music.data.SettingsDataStore(context)

    /**
     * The playback-history row for the track currently playing. Set when the
     * player accepts a source and playback actually starts — never when a
     * screen is merely opened — and cleared with the track. Home's Recently
     * Played and the Listening History screen read the table this feeds.
     */
    @Volatile
    private var currentHistoryRowId: String? = null

    private val retentionManager = RetentionManager()
    private val autoPlayEngine = AutoPlayEngine(songRepo)

    // ---- AutoPlay (Infinity Play) ------------------------------------------
    //
    // Two sets, because they answer two different questions.
    //
    // [autoplayQueuedIds] are the suggestions *currently in the queue*. The
    // trailing run of them is where the listener's own queue ends, which is how
    // "has the queue they built run out?" is answered without storing an index
    // that the first drag would invalidate (see [RadioQueue.boundary]).
    //
    // [autoplayPlayedIds] are everything AutoPlay has ever contributed this
    // session, whether or not it is still queued. That is the deduplication
    // memory: a suggestion that has been offered once is not offered again, even
    // after it has been played and scrolled off.
    private val autoplayQueuedIds: MutableSet<String> =
        java.util.Collections.synchronizedSet(mutableSetOf())
    private val autoplayPlayedIds: MutableSet<String> =
        java.util.Collections.synchronizedSet(mutableSetOf())

    /**
     * Tracks the listener put in the queue by hand — "Play next" and "Add to
     * queue". AutoPlay's suggestions must never land in front of one of these
     * (spec rule 1), so the insertion point is derived from them.
     */
    private val explicitQueueIds: MutableSet<String> =
        java.util.Collections.synchronizedSet(mutableSetOf())

    /** The video id the current chain of suggestions was generated from. */
    @Volatile
    private var autoplaySeedVideoId: String? = null

    /**
     * Tracks the listener has explicitly removed from the queue this session,
     * by stable id. The suggestion engine reads this before appending: a
     * removed song that is still in the radio's answer is not a suggestion
     * any more, it is the song the user threw away (spec 7/21).
     */
    private val removedThisSession: MutableSet<String> =
        java.util.Collections.synchronizedSet(mutableSetOf())

    /** The retention signal, applied only when the seed itself is a local file. */
    @Volatile
    private var autoplayLocalAction: QueueAction = QueueAction.MAINTAIN_VIBE

    /** One radio request at a time: a refill must never stack up behind itself. */
    private val autoplayLock = kotlinx.coroutines.sync.Mutex()

    /**
     * Whether the persisted AutoPlay default has been applied to the current
     * session. Toggling the Infinity button mid-session is the listener's
     * override; it is never second-guessed by the default again until a new
     * session begins.
     */
    @Volatile
    private var playbackSessionAutoplayApplied = false

    // ---- Listening session accounting (stats spec 5–7) -----------------------
    //
    // One session per track. Time is measured off the player's own position on
    // the 100 ms poll — buffering credits nothing, a seek credits nothing — and
    // every write is the INCREMENT since the previous flush, so pausing,
    // seeking and crossfading can never inflate or duplicate the aggregates.
    // All reads/writes of this state happen on the Main dispatcher (player
    // callbacks and the poll both run there); only the Room write leaves it.

    /** The track the session belongs to — captured at track start, never re-read. */
    private var sessionSong: SongEntity? = null

    /** Listening already written into event rows for this session. */
    private var sessionLoggedMs: Long = 0L

    /** Whether the session already had its terminal (complete/skip) flush. */
    private var sessionClosed: Boolean = false

    /** Last player position credited — the baseline the next tick diffs against. */
    private var sessionLastPositionMs: Long = -1L

    /** Listening credited but not yet written to Room. */
    private var currentSongAccumulatedTimeMs: Long = 0L

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

    private val _currentQueueIndex = MutableStateFlow(-1)
    val currentQueueIndexFlow: StateFlow<Int> = _currentQueueIndex.asStateFlow()

    var currentQueueIndex: Int
        get() = _currentQueueIndex.value
        set(value) {
            _currentQueueIndex.value = value
        }

    private var baseQueue: List<SongEntity> = emptyList()

    private val _shuffleEnabled = MutableStateFlow(false)
    val shuffleEnabled: StateFlow<Boolean> = _shuffleEnabled.asStateFlow()

    private val _repeatMode = MutableStateFlow(RepeatMode.OFF)
    val repeatMode: StateFlow<RepeatMode> = _repeatMode.asStateFlow()

    private val _autoplayEnabled = MutableStateFlow(false)
    val autoplayEnabled: StateFlow<Boolean> = _autoplayEnabled.asStateFlow()

    /**
     * Where the AutoPlay machinery stands for the current session — one state
     * the queue screen reads to render its AutoPlay section. Idle and empty is
     * how "off" renders as the absence of a section; Loading is the "Finding
     * similar music…" state; Failed is the retry state; Ready carries what is
     * actually queued (spec 68–71).
     */
    sealed interface AutoPlayStatus {
        data object Idle : AutoPlayStatus
        data object Loading : AutoPlayStatus
        data class Failed(val message: String?) : AutoPlayStatus
        data class Ready(val videoIds: List<String>) : AutoPlayStatus
    }

    private val _autoplayStatus = MutableStateFlow<AutoPlayStatus>(AutoPlayStatus.Idle)
    val autoplayStatus: StateFlow<AutoPlayStatus> = _autoplayStatus.asStateFlow()

    /**
     * Where the current session started. Set when a queue begins and only
     * changed to [PlaybackOrigin.AUTOPLAY] when the player moves onto a track
     * the radio generated — never re-derived from the visible screen. The one
     * source the player UI reads for its context line.
     */
    private val _playbackOrigin = MutableStateFlow(PlaybackOrigin.UNKNOWN)
    val playbackOrigin: StateFlow<PlaybackOrigin> = _playbackOrigin.asStateFlow()

    private val _history = MutableStateFlow<List<SongEntity>>(emptyList())
    val history: StateFlow<List<SongEntity>> = _history.asStateFlow()

    private val _playbackError = MutableStateFlow<String?>(null)
    val playbackError: StateFlow<String?> = _playbackError.asStateFlow()

    /**
     * The one load-state machine the player UI reads (spec 38/50/80).
     *
     * RESOLVING is Gratia's own walk for a playable URL — no source exists yet.
     * BUFFERING is the player holding a source that has not produced audio yet.
     * Everything else follows the player itself, so the UI can never disagree
     * with what the service is doing. The separate booleans below are kept as
     * flows derived from this state for existing readers.
     */
    enum class PlaybackLoadState { IDLE, RESOLVING, BUFFERING, PLAYING, PAUSED, ERROR }

    private val _loadState = MutableStateFlow(PlaybackLoadState.IDLE)
    val loadState: StateFlow<PlaybackLoadState> = _loadState.asStateFlow()

    /**
     * Identity of the manual play request currently being honoured.
     * Incremented on every [playSong]; an asynchronous resolution that comes
     * back under a different id belongs to a track the listener has already
     * replaced, and its callback must not start audio or restore UI (spec 47/48).
     */
    private val _playRequestId = MutableStateFlow(0L)
    @Volatile
    private var playRequestId: Long = 0L

    /**
     * True while a remote stream is being resolved for the current request.
     * Surfaced so the UI can show "Resolving…" instead of looking dead while
     * the network walk runs — and so it doesn't claim the new track is playing
     * before playback has actually been accepted.
     */
    private val _isResolvingRemote = MutableStateFlow(false)
    val isResolvingRemote: StateFlow<Boolean> = _isResolvingRemote.asStateFlow()

    /** The load state derived line for buffering — source prepared but silent. */
    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    private fun setLoadState(state: PlaybackLoadState) {
        if (_loadState.value == state) return
        _loadState.value = state
        _isResolvingRemote.value = state == PlaybackLoadState.RESOLVING
        _isBuffering.value = state == PlaybackLoadState.BUFFERING
        Log.d(TAG, "PLAYBACK_STATE state=$state track='${_currentSong.value?.title ?: "none"}'")
    }

    private val _audioFormat = MutableStateFlow<AudioFormatInfo?>(null)
    val audioFormat: StateFlow<AudioFormatInfo?> = _audioFormat.asStateFlow()

    /**
     * Whether the stream actually playing was swapped in by a quality upgrade.
     *
     * Set in exactly one place — [swapToUpgradedStream], after the better
     * rendition has been validated and the player has actually been pointed at
     * it — and cleared whenever a different track starts. It is never set
     * because a search began, because a candidate was found, or because a
     * source merely supports a better format: a "Quality upgraded" badge that
     * fired before the audio changed would be claiming an event that never
     * happened, and the badge below it would describe a rendition the listener
     * is not hearing. See [maybeMarkPendingUpgrade] for the search that may —
     * or may not — eventually earn this flag.
     */
    private val _isUpgradedStream = MutableStateFlow(false)
    val isUpgradedStream: StateFlow<Boolean> = _isUpgradedStream.asStateFlow()

    /**
     * True while a better-rendition search is running for the current track —
     * the window between "this stream is below what was asked for" and either
     * an actual swap or a concluded "nothing better exists". The metadata line
     * says "Quality optimizing…" for exactly this window and never claims a
     * success that has not happened (spec 50/51).
     */
    private val _isUpgradeOptimizing = MutableStateFlow(false)
    val isUpgradeOptimizing: StateFlow<Boolean> = _isUpgradeOptimizing.asStateFlow()

    /** How many AutoPlay suggestions are actually queued and still ahead. */
    private val _autoplayQueuedCount = MutableStateFlow(0)
    val autoplayQueuedCount: StateFlow<Int> = _autoplayQueuedCount.asStateFlow()

    private var progressJob: Job? = null
    private var backgroundSyncJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    private data class PendingPlayRequest(val song: SongEntity, val queue: List<SongEntity>, val playImmediately: Boolean, val seekPosition: Long)
    // Pending playback request — used when we need to reconnect before playing
    private var pendingPlay: PendingPlayRequest? = null
    private var isRetryingPlayback = false

    /**
     * Refresh attempts spent per videoId since it last played. A stream that
     * fails twice in a row after a fresh resolution is not going to improve
     * by asking a third time — the third cycle just burns data while the
     * listener stares at a stalled bar — so the budget is spent, the real
     * error is surfaced and the UI rolls back to what is actually playable.
     * Cleared on a new play request for the track and on STATE_READY.
     */
    private val streamRefreshBudget = mutableMapOf<String, Int>()

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            Log.d(TAG, "onPlaybackStateChanged: state=$playbackState (IDLE=1, BUFFERING=2, READY=3, ENDED=4)")
            val controller = mediaController ?: return
            // The load state is the player's own words (spec 7): every
            // transition below is derived by the shared rule set rather than
            // hand-written per case, so the UI can never disagree with what
            // the service is doing — and a prepared-but-paused track can
            // never be presented as still loading. The rules live in
            // [PlaybackLoadRules] precisely so this machine is testable.
            when (playbackState) {
                Player.STATE_READY -> {
                    val dur = controller.duration.coerceAtLeast(0)
                    Log.d(TAG, "STATE_READY: duration=${dur}ms")
                    _durationMs.value = dur
                    _playbackError.value = null
                    _currentSong.value?.let { streamRefreshBudget.remove(it.id) }
                    // A source the player has accepted ends every wait — the
                    // resolving walk AND prepare-time buffering. The old
                    // rule (derive from READY only when RESOLVING) is what
                    // left the cold-start restore stuck on BUFFERING with a
                    // spinner that only a Play tap could clear.
                    PlaybackLoadRules.onPlaybackStateChanged(_loadState.value, playbackState, controller.playWhenReady)
                        ?.let { setLoadState(it) }
                }
                Player.STATE_ENDED -> {
                    Log.d(TAG, "STATE_ENDED: handling song end")
                    scope.launch {
                        // Terminal flush of the track that just finished. Runs
                        // before the queue advance so the skip flush the advance
                        // triggers finds the session already closed.
                        flushListeningSession("complete", completed = true)
                        handleSongEndedSuspend()
                    }
                }
                Player.STATE_IDLE -> {
                    Log.d(TAG, "STATE_IDLE")
                    // Idle without an error is the player at rest between
                    // commands; with one, the error path owns the state.
                    if (_playbackError.value == null) {
                        PlaybackLoadRules.onPlaybackStateChanged(_loadState.value, playbackState, controller.playWhenReady)
                            ?.let { setLoadState(it) }
                    }
                }
                Player.STATE_BUFFERING -> {
                    Log.d(TAG, "STATE_BUFFERING")
                    // A source is prepared and the player is waiting for data
                    // (spec 51) — distinct from RESOLVING, where no source exists.
                    PlaybackLoadRules.onPlaybackStateChanged(_loadState.value, playbackState, controller.playWhenReady)
                        ?.let { setLoadState(it) }
                }
            }
        }

        override fun onIsPlayingChanged(playing: Boolean) {
            Log.d(TAG, "onIsPlayingChanged: playing=$playing")
            _isPlaying.value = playing
            // The load state follows the real player: PLAYING/PAUSED are the
            // player's own words, so the UI can never drift from the service
            // (spec 50). RESOLVING is not overwritten here — the resolver owns
            // it until the player accepts or rejects the source.
            val playWhenReady = mediaController?.playWhenReady ?: false
            PlaybackLoadRules.onIsPlayingChanged(_loadState.value, playing, playWhenReady)
                ?.let { setLoadState(it) }
            if (playing) {
                // Baseline the position credit at the moment audio resumed: the
                // next tick credits only what advances from here, so pause gaps
                // and buffering are excluded by construction (stats spec 6).
                sessionLastPositionMs = mediaController?.currentPosition?.coerceAtLeast(0) ?: -1L
                startProgressUpdates()
                scheduleBackgroundSync()
            } else {
                stopProgressUpdates()
                cancelBackgroundSync()
                flushListeningSession("pause")
            }
        }

        /**
         * Re-derives what is playing from the player itself.
         *
         * Gratia's queue is app-owned, so nothing else tells this class that a
         * track changed except the code that changed it. A crossfade is the
         * exception: the engine advances onto the next track by swapping its own
         * players, and this is how the UI follows. Reading the media id rather
         * than trusting the last command also means a player that advanced on its
         * own can never leave the mini player describing the previous song.
         */
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val mediaId = mediaItem?.mediaId ?: return
            syncNowPlayingFrom(mediaId, transitionReason = reason)
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e(TAG, "onPlayerError: ${error.errorCodeName} — ${error.message}")
            val current = _currentSong.value
            if (current != null && current.storageProvider != "local" && !isRetryingPlayback) {
                val spent = streamRefreshBudget.getOrDefault(current.id, 0)
                if (spent >= MAX_STREAM_REFRESHES) {
                    Log.e(TAG, "REMOTE_PLAY_FAILED reason=refresh_budget_spent videoId=${current.providerTrackId} attempts=$spent")
                    _playbackError.value = "Couldn't play this song. Check your connection and try again."
                    restoreUiAfterFailedRemoteRequest(current.id)
                    return
                }
                setLoadState(PlaybackLoadState.RESOLVING)
                Log.w(TAG, "Remote stream failed. Invalidate cache and attempt fresh resolution (${spent + 1}/$MAX_STREAM_REFRESHES).")
                isRetryingPlayback = true
                // Name the reason so the refresh of a 403 (googlevideo retiring
                // the URL) reads differently from any other stream failure.
                val refreshReason = if (error.errorCodeName.contains("HTTP", ignoreCase = true)) "403" else "stream_error"
                Log.w(TAG, "STREAM_REFRESH reason=$refreshReason videoId=${current.providerTrackId}")
                scope.launch {
                    val freshSource = withContext(Dispatchers.IO) {
                        GratiaApp.instance.providerManager.resolvePlayback(current, forceFresh = true, reason = refreshReason)
                    }
                    // Bounded by isRetryingPlayback and the budget above: at most
                    // MAX_STREAM_REFRESHES re-resolutions per track, each resumed
                    // at the position the listener was at. No loop.
                    if (freshSource != null && freshSource.streamUrl.isNotBlank()) {
                        Log.d(TAG, "STREAM_RESOLVE_SUCCESS (refresh) videoId=${current.providerTrackId} — resuming at ${_currentTimeMs.value}ms")
                        playResolvedMediaItem(current, freshSource.streamUrl, playImmediately = true, seekPosition = _currentTimeMs.value)
                    } else {
                        Log.e(TAG, "REMOTE_PLAY_FAILED reason=refresh_failed videoId=${current.providerTrackId}")
                        _playbackError.value = "Couldn't play this song. Try again."
                        setLoadState(PlaybackLoadState.ERROR)
                    }
                    isRetryingPlayback = false
                }
                return
            }
            _playbackError.value = "Couldn't play this song. Try another file or check permission."
            // Don't manually set _isPlaying here — ExoPlayer will fire onIsPlayingChanged(false) 
        }

        override fun onTracksChanged(tracks: Tracks) {
            super.onTracksChanged(tracks)
            var foundAudioFormat = false
            for (group in tracks.groups) {
                if (group.type == C.TRACK_TYPE_AUDIO) {
                    for (i in 0 until group.length) {
                        val format = group.getTrackFormat(i)
                        
                        // Extract bit depth from PCM encoding if available
                        val bitDepth = when (format.pcmEncoding) {
                            C.ENCODING_PCM_16BIT -> 16
                            C.ENCODING_PCM_24BIT -> 24
                            C.ENCODING_PCM_32BIT -> 32
                            C.ENCODING_PCM_FLOAT -> 32
                            C.ENCODING_PCM_8BIT -> 8
                            else -> -1
                        }

                        val formatInfo = AudioFormatInfo(
                            mimeType = format.sampleMimeType,
                            sampleRate = format.sampleRate,
                            bitDepth = bitDepth,
                            channelCount = format.channelCount,
                            bitrate = format.bitrate
                        )
                        Log.d(TAG, "AudioFormatExtracted: $formatInfo")
                        _audioFormat.value = formatInfo
                        foundAudioFormat = true
                        break
                    }
                }
                if (foundAudioFormat) break
            }
            if (!foundAudioFormat) {
                _audioFormat.value = null
            }
        }
    }

    init {
        Log.d(TAG, "PlayerManager init — connecting to PlaybackService")
        connect()

        // Keep the AutoPlay status's queued count in step with the queue it
        // describes: rows the listener removed (or drags that moved things)
        // must be reflected in what the AutoPlay section claims to hold.
        scope.launch {
            kotlinx.coroutines.flow.combine(_queue, _currentQueueIndex) { q, idx ->
                RadioQueue.remainingSuggestions(q, idx, autoplayQueuedIds)
            }.collect { remaining ->
                _autoplayQueuedCount.value = remaining
                val status = _autoplayStatus.value
                if (status is AutoPlayStatus.Ready && remaining == 0) {
                    // The suggestion run the status described is gone. Idle is
                    // honest: with the feature still enabled the next refill
                    // attempt will move the status forward again.
                    _autoplayStatus.value = AutoPlayStatus.Idle
                }
            }
        }
        
        // Cold-start restoration — one deterministic flow.
        //
        // The persisted snapshot carries the whole session, so restoring
        // rediscovers nothing and does not depend on Room holding every row.
        // The source decides the path, exactly once: a local file and a
        // downloaded file are prepared from their own bytes — never through
        // the remote resolver — a genuinely remote track stays a paused
        // snapshot whose resolution is deferred to the first play request,
        // and a source that is genuinely gone clears the snapshot instead of
        // being silently re-resolved into some other recording.
        //
        // pendingPlay is consumed by whoever finishes last — the controller
        // connect listener or this coroutine's flush — so the order in which
        // disk reading and service binding complete cannot lose the restore.
        // (Previously only the connect listener consumed it: a restore that
        // finished after the connection left the track displayed but never
        // prepared, the exact startup race this replaces.)
        scope.launch(Dispatchers.IO) {
            try {
                val snapshot = PlaybackSnapshot.decode(settingsDataStore.savedPlaybackSnapshotJsonFlow.first())
                    ?: legacySnapshotFromRoom()
                val song = snapshot?.currentSong
                if (snapshot == null || song == null) return@launch

                val position = snapshot.positionMs.coerceAtLeast(0L)
                val source = restorableSourceOf(
                    song = song,
                    localUriUsable = isLocalUriReadable(song),
                    downloadFileExists = !song.downloadPath.isNullOrBlank() &&
                        java.io.File(song.downloadPath).exists(),
                )
                if (source is RestorableSource.Unavailable) {
                    // Truthful state, not a fallback: the file the snapshot
                    // names is gone, so the session is not restorable.
                    Log.w(TAG, "RESTORE_UNAVAILABLE track='${song.title}' localUri=${song.localUri} — clearing snapshot")
                    settingsDataStore.clearPlaybackSnapshot()
                    return@launch
                }

                // The queue must at least contain the track the player was on;
                // the snapshot normally carries all of it.
                val queue = snapshot.queue
                    .ifEmpty { listOf(song) }
                    .let { q -> if (q.none { it.id == song.id }) q + song else q }

                withContext(Dispatchers.Main) {
                    Log.d(
                        TAG,
                        "RESTORE_SNAPSHOT song='${song.title}' source=${source::class.simpleName} " +
                            "position=${position}ms queueSize=${queue.size} origin=${snapshot.origin.name}",
                    )
                    _queue.value = queue
                    _currentSong.value = song
                    currentQueueIndex = queue.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
                    _currentTimeMs.value = position
                    // Duration is already known from the row itself (spec 1:
                    // "duration where available"); the player's own READY
                    // duration replaces it the moment it arrives.
                    _durationMs.value = song.durationMs.coerceAtLeast(0L)
                    _playbackOrigin.value = snapshot.origin
                    // Queue tier context: rows the AutoPlay radio generated
                    // are restored as generated, so the queue screen keeps
                    // its two sections instead of presenting radio rows as
                    // the listener's own.
                    autoplayQueuedIds.addAll(snapshot.autoplayIds)
                    // Restore paused — that is the product's resume behavior:
                    // nothing plays until the listener asks, and nothing is
                    // started twice to get there.
                    pendingPlay = PendingPlayRequest(song, queue, false, position)

                    when (source) {
                        is RestorableSource.OnDevice ->
                            // Bytes are here: prepare from the local URI right
                            // now. The player's own BUFFERING/READY state then
                            // settles the display — no remote spinner, no
                            // fake loading timer (spec 6/7).
                            flushPendingPlay()
                        is RestorableSource.NeedsNetwork -> {
                            // Resolution is this track's correct path, but it
                            // is paid when the listener actually presses play
                            // — never as a startup network walk.
                            setLoadState(PlaybackLoadState.PAUSED)
                        }
                        is RestorableSource.Unavailable -> Unit // handled above
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to restore playback state", e)
            }
        }
        
        scope.launch {
            kotlinx.coroutines.flow.combine(currentSong, isPlaying) { song, playing ->
                Pair(song, playing)
            }.collect { (song, playing) ->
                try {
                    androidx.glance.appwidget.GlanceAppWidgetManager(context)
                        .getGlanceIds(com.gratia.music.ui.widget.GratiaMusicWidget::class.java)
                        .forEach { glanceId ->
                            androidx.glance.appwidget.state.updateAppWidgetState(context, glanceId) { prefs ->
                                prefs[com.gratia.music.ui.widget.GratiaMusicWidget.KEY_SONG_TITLE] = song?.title ?: "Not Playing"
                                prefs[com.gratia.music.ui.widget.GratiaMusicWidget.KEY_ARTIST_NAME] = song?.artist ?: "Gratia"
                                if (song?.coverArtPath != null) {
                                    prefs[com.gratia.music.ui.widget.GratiaMusicWidget.KEY_COVER_PATH] = song.coverArtPath
                                } else {
                                    prefs.remove(com.gratia.music.ui.widget.GratiaMusicWidget.KEY_COVER_PATH)
                                }
                                prefs[com.gratia.music.ui.widget.GratiaMusicWidget.KEY_IS_PLAYING] = playing
                            }
                            com.gratia.music.ui.widget.GratiaMusicWidget().update(context, glanceId)
                        }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to update widget", e)
                }
            }
        }
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
                        flushPendingPlay()
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
        // An empty service has no position and no duration of its own —
        // zeroing the app state here would clobber a restore that completed
        // while the connection was still being built (the restored position
        // would briefly read 0 and the seek bar would lose its span until
        // READY). A service that actually holds media is the truth and wins.
        if (controller.mediaItemCount > 0) {
            _durationMs.value = controller.duration.coerceAtLeast(0)
            _currentTimeMs.value = controller.currentPosition.coerceAtLeast(0)
        }
        Log.d(TAG, "syncState: isPlaying=$isActuallyPlaying, duration=${_durationMs.value}ms, position=${_currentTimeMs.value}ms")
        
        if (isActuallyPlaying) {
            startProgressUpdates()
        } else {
            stopProgressUpdates()
        }
    }

    /**
     * Executes the pending restored-play request if the controller is live.
     *
     * Both possible "finishers" — the controller connect listener and the
     * startup restore coroutine — call this, which is what removes the old
     * startup race (spec 14): the connect listener used to be the only
     * consumer, so a restore that completed after the connection set
     * pendingPlay with nobody left to run it — the restored track stayed
     * displayed but was never prepared, and Play seemed dead. Both callers
     * run on the main thread, so exactly one of them can consume it.
     */
    private fun flushPendingPlay() {
        val controller = mediaController ?: return
        if (!controller.isConnected) return
        val pending = pendingPlay ?: return
        pendingPlay = null
        Log.d(
            TAG,
            "PENDING_PLAY_EXECUTED track='${pending.song.title}' " +
                "immediately=${pending.playImmediately} seek=${pending.seekPosition}ms",
        )
        playSongInternal(pending.song, pending.queue, playImmediately = pending.playImmediately, seekPosition = pending.seekPosition)
    }

    /**
     * Rebuilds a snapshot from the pre-snapshot persistence (queue ids +
     * song id + position, resolved through Room). One-time migration for
     * installs that last saved under the old scheme; rows Room does not
     * hold are gone for good — which is exactly the limitation the snapshot
     * format exists to fix.
     */
    private suspend fun legacySnapshotFromRoom(): PlaybackSnapshot? {
        val queueIds = settingsDataStore.savedQueueIdsFlow.first()
        val currentId = settingsDataStore.savedCurrentSongIdFlow.first()
        val position = settingsDataStore.savedCurrentTimeMsFlow.first()
        if (currentId == null) return null
        val current = songRepo.getSongById(currentId) ?: return null
        val queue = queueIds.mapNotNull { songRepo.getSongById(it) }
        return PlaybackSnapshot(currentSong = current, queue = queue, positionMs = position)
    }

    /**
     * Whether a local track's persisted URI can still be opened (spec 13:
     * validate the saved source at startup). A MediaStore or SAF content
     * URI is probed by actually opening it — a metadata-only query can pass
     * on rows the player would still fail to read — and a file path is
     * checked on disk.
     *
     * Returns false when there is no localUri at all; [restorableSourceOf]
     * then considers the recorded file path, which is the stable identity
     * for a URI that went stale (a re-scan can change the media id while
     * the file itself is still there).
     */
    private fun isLocalUriReadable(song: SongEntity): Boolean {
        val uri = song.localUri?.takeIf { it.isNotBlank() } ?: return false
        return try {
            when {
                uri.startsWith("content://") -> {
                    context.contentResolver.openAssetFileDescriptor(Uri.parse(uri), "r")?.use { true } ?: false
                }
                uri.startsWith("file://") -> java.io.File(Uri.parse(uri).path ?: "").exists()
                else -> java.io.File(uri).exists()
            }
        } catch (e: Exception) {
            Log.w(TAG, "LOCAL_URI_UNREADABLE uri=${uri.take(64)}: ${e.message}")
            false
        }
    }

    private fun updatePreloadManager() {
        try {
            GratiaApp.instance.preloadManager.updateQueue(_queue.value, _currentSong.value?.id)
        } catch (e: Exception) {
            Log.e(TAG, "updatePreloadManager failed: ${e.message}")
        }
    }

    /**
     * Persists the complete playback snapshot (spec 1): the full current
     * track — identity, source type, local URI, remote id, artwork — the
     * queue, the position, the session origin and the queue's AutoPlay tier.
     * A cold start restores from this without rediscovering a single thing,
     * and without depending on Room holding every row.
     *
     * Written on every track change, queue change and seek, and on the
     * periodic position tick while playing.
     */
    private fun saveStateToDataStore() {
        scope.launch(Dispatchers.IO) {
            val snapshot = PlaybackSnapshot(
                currentSong = _currentSong.value,
                queue = _queue.value.take(MAX_PERSISTED_QUEUE),
                positionMs = _currentTimeMs.value,
                origin = _playbackOrigin.value,
                autoplayIds = autoplayQueuedIds.toSet(),
                savedAtMs = System.currentTimeMillis(),
            )
            settingsDataStore.savePlaybackSnapshot(snapshot.encode())
        }
    }

    fun playSong(
        song: SongEntity,
        list: List<SongEntity>,
        playImmediately: Boolean = true,
        origin: PlaybackOrigin = PlaybackOrigin.UNKNOWN,
    ) {
        val oldCurrent = _currentSong.value
        _playbackOrigin.value = origin
        // A new manual request invalidates every callback from the previous
        // one: a resolution still in flight for the old track must neither
        // start audio nor restore the old UI (spec 47/48/49).
        val requestId = ++playRequestId
        _playRequestId.value = requestId
        Log.d(TAG, "PLAY_REQUEST id=$requestId origin=${origin.name} track='${song.title}'")
        Log.d(TAG, "playSong: '${song.title}' from list of size ${list.size}")
        streamRefreshBudget.remove(song.id)
        upgradeJob?.cancel()
        // A fresh session choice retires the removal memory: the listener is
        // building a new queue, and yesterday's discarded rows may legitimately
        // be suggested again.
        removedThisSession.clear()
        
        baseQueue = list
        if (_shuffleEnabled.value) {
            val idx = list.indexOfFirst { it.id == song.id }
            val remainder = if (idx >= 0 && idx < list.size - 1) list.subList(idx + 1, list.size) else emptyList()
            val before = if (idx > 0) list.subList(0, idx) else emptyList()
            _queue.value = before + listOf(song) + remainder.shuffled()
            currentQueueIndex = before.size
        } else {
            _queue.value = list
            currentQueueIndex = list.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
        }
        
        _currentSong.value = song
        if (oldCurrent?.id != song.id) {
            _history.value = (listOf(song) + _history.value).distinctBy { it.id }.take(20)
            
            // Log listening event for previously playing song
            if (oldCurrent != null) {
                // The flush names the track that WAS being listened to — the
                // capture happens before the current-song swap below could
                // misattribute the old track's time to the new one (stats spec
                // 11: the reported "song in stats that was never played").
                flushListeningSession("skip", song = oldCurrent, skipped = true)
                resetListeningSession(song)
            }
        }

        // The listener chose this track. Whatever the player is doing —
        // playing, buffering, mid-crossfade — it stops now (spec 37/57).
        // Crossfade remains untouched: it advances on its own schedule at the
        // end of a track, never on a manual selection.
        if (oldCurrent?.id != song.id) {
            stopPlaybackImmediately()
        }
        // Only a track whose source still has to be fetched shows the remote
        // resolving state (spec 0/1/2). A local MediaStore file and a remote
        // track Gratia has already downloaded both have their bytes on the
        // device, so their source exists before the player is touched — asking
        // the resolver to announce itself here would put a network loader on a
        // tap that never needed the network.
        setLoadState(if (playableWithoutNetwork(song)) PlaybackLoadState.IDLE else PlaybackLoadState.RESOLVING)

        // A new list replaces the queue outright, so any generated tail belongs
        // to a queue that no longer exists. The dedup memory is reset with it:
        // Infinity Play restarts around whatever the listener just chose, and
        // suggestions already heard under a different queue must not make the
        // new chain come up empty.
        autoplayQueuedIds.clear()
        autoplayPlayedIds.clear()
        explicitQueueIds.clear()

        // New session, new default: the persisted preference applies to remote
        // tracks; local playback keeps its previous session state, because a
        // local collection must not be silently handed to a remote radio (spec
        // 16/17/93). toggleAutoplay remains the per-session override either way.
        if (song.storageProvider != "local") {
            scope.launch {
                val pref = settingsDataStore.autoplayDefaultFlow.first()
                if (!playbackSessionAutoplayApplied && pref != _autoplayEnabled.value) {
                    if (pref) enableAutoplay() else disableAutoplay()
                }
                playbackSessionAutoplayApplied = true
            }
        }

        playSongInternal(song, _queue.value, playImmediately = playImmediately, seekPosition = 0L)
    }

    /**
     * Whether this track's audio bytes are already on the device.
     *
     * The classification itself is [com.gratia.music.data.model.playsFromDevice]
     * — local original or downloaded remote — so the player and the shelves
     * cannot disagree about what "on the device" means. What this adds is the
     * one extra fact only the player cares about: a download whose file has gone
     * missing is *not* playable from the device, however the row is classified,
     * so it must fall back to the resolve path rather than being announced as
     * instant.
     */
    private fun playableWithoutNetwork(song: SongEntity): Boolean {
        if (!song.playsFromDevice()) return false
        // A downloaded file is the case that can go stale underneath us: the
        // entry says downloaded, the bytes may no longer be there.
        if (song.isDownloaded && !song.downloadPath.isNullOrBlank()) {
            return java.io.File(song.downloadPath).exists()
        }
        return true
    }

    /**
     * Halts the audible player at once.
     *
     * `pause()` alone leaves the decoder holding the old source, which the
     * speaker keeps auditioning until the new one arrives. `stop()` is the
     * command the requirement actually names: silence now, and the new track
     * takes over whenever its own source is ready. Player B (crossfade's
     * pre-buffer) is cleared with it — its cargo belongs to the track that was
     * just replaced.
     */
    private fun stopPlaybackImmediately() {
        try {
            mediaController?.let { controller ->
                if (controller.isPlaying || controller.playbackState != Player.STATE_IDLE) {
                    controller.stop()
                    Log.d(TAG, "OLD_TRACK_STOPPED wasPlaying=${controller.isPlaying}")
                }
                controller.clearMediaItems()
            }
        } catch (e: Exception) {
            Log.w(TAG, "stopPlaybackImmediately failed: ${e.message}")
        }
        try {
            com.gratia.music.player.transition.GratiaPlayerEngine.hook?.cancelNext()
        } catch (e: Exception) {
            Log.w(TAG, "crossfade cancelNext failed: ${e.message}")
        }
    }

    fun playSongAtQueueIndex(index: Int, playImmediately: Boolean = true) {
        val q = _queue.value
        if (index < 0 || index >= q.size) return
        val song = q[index]
        val oldCurrent = _currentSong.value
        currentQueueIndex = index
        _currentSong.value = song
        // A track the radio generated is identified as AutoPlay for as long as
        // it is current; anything else keeps the origin the session started
        // with. This is what stops a suggestion from reading as "Your Library".
        if (song.id in autoplayQueuedIds) {
            _playbackOrigin.value = PlaybackOrigin.AUTOPLAY
        }
        
        if (oldCurrent?.id != song.id) {
            _history.value = (listOf(song) + _history.value).distinctBy { it.id }.take(20)
            if (oldCurrent != null) {
                // Same attribution rule as playSong: the old track's listening
                // is flushed under the old track's identity before the session
                // is rebound to the new one (stats spec 11).
                flushListeningSession("skip", song = oldCurrent, skipped = true)
                resetListeningSession(song)
            }
        }
        
        playSongInternal(song, q, playImmediately = playImmediately, seekPosition = 0L)
    }

    private fun playSongInternal(song: SongEntity, songQueue: List<SongEntity>, playImmediately: Boolean = true, seekPosition: Long = 0L) {
        Log.d(
            TAG,
            "playSongInternal: '${song.title}' by ${song.artist} (provider=${song.storageProvider}, " +
                "downloaded=${song.isDownloaded}, local=${playableWithoutNetwork(song)})",
        )

        // Only the newest request may speak (spec 47/48). Everything this
        // method does to the player is gated on the id captured when the
        // listener tapped the track.
        val requestId = playRequestId

        _currentSong.value = song
        _queue.value = songQueue.toList() // Copy to prevent ConcurrentModificationException
        _currentTimeMs.value = seekPosition
        _playbackError.value = null
        // A new track starts on whatever it starts on; the previous track's
        // upgrade must not bleed into its badge.
        _isUpgradedStream.value = false
        _isUpgradeOptimizing.value = false
        updatePreloadManager()

        // While resolving, the seek bar must not pretend the song is playing:
        // position stays put and duration is unknown until the source is in
        // the player (spec 52).
        if (_loadState.value == PlaybackLoadState.RESOLVING) {
            _durationMs.value = 0L
        }

        // Whatever route reached here (a queue tap, a deep link, autoplay), a
        // track whose bytes are already on the device must never be announced
        // as "resolving from the network": the previous request may have left
        // the loader on, and it belongs to that track, not this one.
        if (playableWithoutNetwork(song) && _loadState.value == PlaybackLoadState.RESOLVING) {
            Log.d(TAG, "LOAD_STATE_LOCAL_OVERRIDE track='${song.title}' downloaded=${song.isDownloaded}")
            setLoadState(PlaybackLoadState.IDLE)
        }

        // Infinity Play follows whatever has just started. See
        // [maybeRefillAutoplay] for how the seed and the existing chain are
        // reconciled.
        maybeRefillAutoplay()

        if (!ensureConnected()) {
            Log.w(TAG, "playSong: not connected, queuing for after reconnect")
            pendingPlay = PendingPlayRequest(song, songQueue, playImmediately, seekPosition)
            return
        }

        val isRemote = song.storageProvider != "local"

        if (song.isDownloaded && !song.downloadPath.isNullOrBlank()) {
            val file = java.io.File(song.downloadPath)
            if (file.exists()) {
                Log.d(TAG, "Playing downloaded offline file: ${file.absolutePath}")
                playResolvedMediaItem(song, file.absolutePath, playImmediately, seekPosition)
                return
            } else {
                Log.w(TAG, "Downloaded file missing at ${file.absolutePath}, falling back to stream")
            }
        }

        if (isRemote) {
            // A restored snapshot the listener has not asked to play yet must
            // not open a network walk on its own: no remote spinner, no
            // offline error, before the first play (spec 16/21). The session
            // stays paused at its restored position; the first play request
            // comes back through this same path with playImmediately = true
            // and resolves then. playImmediately = false exists only on the
            // restore path.
            if (!playImmediately) {
                Log.d(TAG, "REMOTE_RESTORE_DEFERRED videoId=${song.providerTrackId} — resolution waits for play")
                setLoadState(PlaybackLoadState.PAUSED)
                return
            }
            // Asynchronously resolve stream from remote provider. The player
            // was already stopped by playSong — this walk only decides what
            // plays *next*, never whether something keeps playing.
            Log.d(TAG, "SOURCE_RESOLUTION_START videoId=${song.providerTrackId} title='${song.title}' request=$requestId")
            setLoadState(PlaybackLoadState.RESOLVING)
            scope.launch {
                try {
                    val source = withContext(Dispatchers.IO) {
                        Log.d(TAG, "STREAM_RESOLVE_START videoId=${song.providerTrackId}")
                        GratiaApp.instance.providerManager.resolvePlayback(song, forceFresh = false)
                    }

                    // Stale-resolution gate, checked before anything is applied.
                    if (requestId != playRequestId || _currentSong.value?.id != song.id) {
                        Log.d(TAG, "SOURCE_RESOLUTION_STALE videoId=${song.providerTrackId} request=$requestId current=$playRequestId")
                        return@launch
                    }

                    if (source == null || source.streamUrl.isBlank()) {
                        Log.e(TAG, "SOURCE_RESOLUTION_FAIL reason=resolve_empty videoId=${song.providerTrackId}")
                        _playbackError.value = "Couldn't resolve stream for this song. Try again."
                        setLoadState(PlaybackLoadState.ERROR)
                        return@launch
                    }

                    // Phase 27 — never play a stream minted for a different
                    // video than the one asked for. A resolver that answered
                    // with a fallback id is a resolver that has already lost
                    // the track it was given.
                    if (source.videoId != song.providerTrackId) {
                        Log.e(
                            TAG,
                            "SOURCE_RESOLUTION_FAIL reason=video_id_mismatch " +
                                "videoId=${song.providerTrackId} resolved=${source.videoId}",
                        )
                        _playbackError.value = "Couldn't play this song. Try again."
                        setLoadState(PlaybackLoadState.ERROR)
                        return@launch
                    }

                    Log.d(TAG, "SOURCE_RESOLUTION_SUCCESS videoId=${song.providerTrackId} request=$requestId")
                    // The stream URL is in hand; from here the player's own
                    // BUFFERING state takes over the loading display.
                    setLoadState(PlaybackLoadState.BUFFERING)

                    Log.d(
                        TAG,
                        "STREAM_RESOLVE_SUCCESS videoId=${song.providerTrackId} " +
                            "bitrate=${source.bitrate ?: "?"}kbps expiry=${source.expiresAtMs ?: "unknown"}",
                    )
                    // The second look: with a better-rendition source enabled,
                    // mark this track for a bounded background search once its
                    // stream is up. Playback continues either way — see
                    // [startUpgradeLook].
                    maybeMarkPendingUpgrade(song, source)
                    playResolvedMediaItem(song, source.streamUrl, playImmediately, seekPosition)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "SOURCE_RESOLUTION_FAIL reason=exception videoId=${song.providerTrackId}: ${e.message}")
                    _playbackError.value = "Couldn't play this song. Check network connection."
                    setLoadState(PlaybackLoadState.ERROR)
                }
            }
        } else {
            // The MediaStore URI is the primary local identity; the recorded
            // file path is the stable fallback for a URI that went stale
            // while the file itself is still there.
            val uri = song.localUri ?: song.storagePath
            if (uri == null) {
                Log.e(TAG, "playSong: no local source for '${song.title}'")
                _playbackError.value = "File not found."
                return
            }
            playResolvedMediaItem(song, uri, playImmediately, seekPosition)
        }
    }

    private fun playResolvedMediaItem(song: SongEntity, mediaUri: String, playImmediately: Boolean, seekPosition: Long) {
        val controller = mediaController ?: return

        try {
            if (song.storageProvider != "local") {
                Log.d(TAG, "MEDIA_ITEM_CREATED uri=${mediaUri.take(48)}… videoId=${song.providerTrackId}")
            }
            val mediaItem = buildMediaItem(song, mediaUri)

            controller.setMediaItem(mediaItem)
            controller.prepare()
            if (seekPosition > 0) {
                controller.seekTo(seekPosition)
            }
            if (playImmediately) {
                controller.play()
                if (song.storageProvider != "local") {
                    Log.d(TAG, "REMOTE_PLAY_STARTED videoId=${song.providerTrackId}")
                }
                // The unified playback timeline: recorded the moment the player
                // actually starts the track, with the song's own snapshot. The
                // old row-level lastPlayedAt write was an UPDATE on a row that
                // usually does not exist for a streamed track — remote plays
                // were invisible to Recently Played. (spec 17–19)
                scope.launch(Dispatchers.IO) {
                    GratiaApp.instance.database.songDao().updateLastPlayedAt(song.id, System.currentTimeMillis())
                    currentHistoryRowId = historyRepo.record(song)
                }
            }
            Log.d(TAG, "playResolvedMediaItem: commands sent to controller for '${song.title}'")
            _isResolvingRemote.value = false
            saveStateToDataStore()
            
        } catch (e: Exception) {
            Log.e(TAG, "playResolvedMediaItem: exception — ${e.message}")
            _playbackError.value = "Couldn't play this song. Try another file or check permission."
        }
    }

    /**
     * Rolls the visible state back to what the player is actually playing after
     * a remote request failed before reaching the player. The old song kept
     * playing through the failed resolution — destroying it was never the deal
     * — so the UI is put back in step with it and the error is raised on top.
     * Without this, the mini player claims a song that will never play.
     */
    /**
     * The failed remote request keeps the listener's chosen track on screen
     * (spec 55): the old track is not silently resurrected — the user selected
     * a new song, so the new song stays current in its error state, with
     * Retry available.
     */
    private fun restoreUiAfterFailedRemoteRequest(failedSongId: String) {
        _isResolvingRemote.value = false
        val failed = _queue.value.firstOrNull { it.id == failedSongId }
        if (failed != null) {
            _currentSong.value = failed
            currentQueueIndex = _queue.value.indexOfFirst { it.id == failedSongId }.coerceAtLeast(0)
            Log.d(TAG, "PLAYBACK_FAILED kept selection '${failed.title}' current (spec 55)")
        }
        setLoadState(PlaybackLoadState.ERROR)
    }

    /**
     * Retry a failed remote play with a fresh request id (spec 78): the old
     * failed request is replaced, never doubled up.
     */
    fun retryCurrentSong() {
        val song = _currentSong.value ?: return
        val q = _queue.value
        Log.d(TAG, "PLAY_RETRY id=${playRequestId + 1} track='${song.title}'")
        playSong(song, q, origin = _playbackOrigin.value)
    }

    /**
     * One MediaItem, built the same way whether it is being played now or parked
     * on the crossfade engine's second player. Two builders would eventually
     * disagree about artwork or metadata, and the song that crossfaded in would
     * be the one that looked different.
     */
    private fun buildMediaItem(song: SongEntity, mediaUri: String): MediaItem {
        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(song.title)
            .setArtist(song.artist)
            .setAlbumTitle(song.album ?: "Gratia")

        val artwork = song.artworkUrl ?: song.coverArtPath
        if (!artwork.isNullOrBlank()) {
            try {
                val uriString = if (artwork.startsWith("content://") || artwork.startsWith("http")) {
                    artwork
                } else {
                    "file://$artwork"
                }
                metadataBuilder.setArtworkUri(Uri.parse(uriString))
            } catch (e: Exception) {
                Log.e(TAG, "buildMediaItem: invalid artwork uri", e)
            }
        }

        return MediaItem.Builder()
            .setUri(Uri.parse(mediaUri))
            .setMediaId(song.id)
            .setMediaMetadata(metadataBuilder.build())
            .build()
    }

    /**
     * The next track in the queue, resolved and ready to play — for the crossfade
     * engine.
     *
     * Touches no playback state: the engine parks the result on its idle player,
     * and the transition itself is what starts it. Returns null when there is
     * genuinely nothing next, which is what tells the engine to leave the
     * ordinary end-of-track path alone.
     *
     * A downloaded next track is handed over as a file, an on-device one as its
     * content URI, and a remote one as a freshly resolved stream — so a remote →
     * downloaded transition and a downloaded → remote one both work, and neither
     * is forced down the other's path.
     */
    suspend fun resolveNextMediaItemForCrossfade(currentMediaId: String): MediaItem? {
        val q = _queue.value
        if (q.isEmpty()) return null

        var index = q.indexOfFirst { it.id == currentMediaId }
        if (index < 0) index = currentQueueIndex
        if (index < 0 || index >= q.size) return null

        val nextIndex = index + 1
        if (nextIndex >= q.size) {
            if (_repeatMode.value != RepeatMode.ALL) return null
            return mediaItemForCrossfade(q.first())
        }
        return mediaItemForCrossfade(q[nextIndex])
    }

    private suspend fun mediaItemForCrossfade(song: SongEntity): MediaItem? {
        val localFile = song.downloadPath
            ?.takeIf { song.isDownloaded && !it.isNullOrBlank() && java.io.File(it).exists() }
        if (localFile != null) return buildMediaItem(song, localFile)

        if (song.storageProvider == "local") {
            val uri = song.localUri ?: return null
            return buildMediaItem(song, uri)
        }

        val expectedVideoId = song.providerTrackId ?: song.id.removePrefix("ytm_")
        val source = try {
            withContext(Dispatchers.IO) {
                GratiaApp.instance.providerManager.resolvePlayback(song, forceFresh = false)
            }
        } catch (e: Exception) {
            Log.w(TAG, "CROSSFADE_ERROR stage=resolve_next_track ${song.id} ${e.message}")
            null
        }

        if (source == null || source.streamUrl.isBlank()) {
            Log.w(TAG, "CROSSFADE_CANCEL reason=next_stream_unresolved videoId=$expectedVideoId")
            return null
        }
        if (source.videoId != expectedVideoId) {
            Log.w(TAG, "CROSSFADE_CANCEL reason=next_stream_id_mismatch videoId=$expectedVideoId")
            return null
        }
        return buildMediaItem(song, source.streamUrl)
    }

    /**
     * Called by the playback engine once it has advanced onto another track.
     *
     * A crossfade changes the song without the app ever issuing a play command,
     * so the queue index, the now-playing state and the AutoPlay seed are all
     * re-derived here rather than assumed.
     */
    fun adoptEngineTrack(mediaId: String) {
        // The crossfade engine's own advance: the outgoing track reached its
        // scheduled end, so its session closes as heard (complete), the same
        // ruling an AUTO media-item transition gets.
        scope.launch { syncNowPlayingFrom(mediaId, transitionReason = Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) }
    }

    /**
     * Brings queue and UI state into step with what the player is actually on.
     * Used by both the media-item listener and the crossfade hand-off, because
     * in both cases the truth comes from the player, not from a command.
     */
    private fun syncNowPlayingFrom(mediaId: String, transitionReason: Int) {
        val reason = "media_item_transition($transitionReason)"
        val index = _queue.value.indexOfFirst { it.id == mediaId }
        if (index < 0) {
            Log.w(TAG, "NOW_PLAYING_UNKNOWN mediaId=$mediaId reason=$reason")
            return
        }
        if (currentQueueIndex != index) currentQueueIndex = index
        val song = _queue.value[index]
        if (_currentSong.value?.id == song.id) return

        Log.d(TAG, "NOW_PLAYING_CHANGED reason=$reason song='${song.title}' index=$index")
        // A crossfade advance is the one track change that never passes through
        // playSong*/STATE_ENDED, so the outgoing session is flushed here or its
        // listening time is simply lost. An AUTO transition means the track ran
        // to its own scheduled end — it was heard, not skipped; anything else
        // was a listener decision and the playSong*/queue path has already
        // flushed and closed the session before the player could report it.
        val autoAdvance = transitionReason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO
        flushListeningSession(
            if (autoAdvance) "complete" else "skip",
            completed = autoAdvance,
            skipped = !autoAdvance,
        )
        _currentSong.value = song
        _history.value = (listOf(song) + _history.value).distinctBy { it.id }.take(20)
        _currentTimeMs.value = 0L
        resetListeningSession(song)
        saveStateToDataStore()
        // A crossfade advance is a track change like any other: if the engine
        // walked onto a radio-generated track, the context line must say
        // AutoPlay — not the origin the session started from.
        if (song.id in autoplayQueuedIds) {
            _playbackOrigin.value = PlaybackOrigin.AUTOPLAY
            Log.d(TAG, "PLAYER_CONTEXT origin=AUTOPLAY track='${song.title}'")
        }
        maybeRefillAutoplay()
    }

    /**
     * Whether the service's player is actually holding the current track's
     * source. A restored remote snapshot whose resolution was deferred is
     * not held by the player, and pressing play on it must run the
     * source-aware play path — a bare `play()` on an empty player is a
     * silent no-op, which is exactly the "tap does nothing" dead end this
     * replaces.
     */
    private fun playerHoldsCurrentSource(): Boolean {
        val controller = mediaController ?: return false
        if (!controller.isConnected) return false
        val song = _currentSong.value ?: return false
        return controller.mediaItemCount > 0 && controller.currentMediaItem?.mediaId == song.id
    }

    /**
     * Plays the current snapshot's track through the source-aware path.
     * Used when a play request arrives for a track the player holds no
     * source for — the deferred remote restore being the only case — so
     * the resolution (and only that track's resolution) happens now.
     */
    private fun startCurrentSongPlayback() {
        val song = _currentSong.value ?: return
        val requestId = ++playRequestId
        _playRequestId.value = requestId
        Log.d(TAG, "PLAY_REQUEST id=$requestId origin=${_playbackOrigin.value.name} reason=restored_source_not_prepared track='${song.title}'")
        playSongInternal(song, _queue.value, playImmediately = true, seekPosition = _currentTimeMs.value)
    }

    fun togglePlay() {
        if (!ensureConnected()) return
        val controller = mediaController ?: return
        if (controller.isPlaying) {
            Log.d(TAG, "togglePlay: pausing")
            controller.pause()
        } else {
            if (!playerHoldsCurrentSource()) {
                Log.d(TAG, "togglePlay: player holds no source for the current track — running the play path")
                startCurrentSongPlayback()
                return
            }
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
        val controller = mediaController ?: return
        if (!playerHoldsCurrentSource()) {
            startCurrentSongPlayback()
            return
        }
        controller.play()
    }

    /** Set playback volume (0.0 to 1.0) for fade effects. */
    fun setVolume(volume: Float) {
        if (!ensureConnected()) return
        mediaController?.volume = volume.coerceIn(0f, 1f)
    }

    fun seekTo(positionMs: Long) {
        if (!ensureConnected()) return
        val controller = mediaController ?: return
        val clampedPosition = positionMs.coerceIn(0, _durationMs.value.coerceAtLeast(0))
        Log.d(TAG, "seekTo: ${clampedPosition}ms")
        controller.seekTo(clampedPosition)
        _currentTimeMs.value = clampedPosition
        saveStateToDataStore()
    }

    fun clearQueue() {
        mediaController?.clearMediaItems()
        _currentSong.value = null
        _queue.value = emptyList()
        autoplayQueuedIds.clear()
        autoplayPlayedIds.clear()
        explicitQueueIds.clear()
        autoplaySeedVideoId = null
        _autoplayStatus.value = AutoPlayStatus.Idle
        removedThisSession.clear()
        updatePreloadManager()
        saveStateToDataStore()
    }

    /** Snapshot for callers that must exclude removed rows by identity. */
    fun removedIdsSnapshot(): Set<String> = synchronized(removedThisSession) { removedThisSession.toSet() }

    fun nextSong() {
        val q = _queue.value
        if (q.isEmpty()) return
        val current = _currentSong.value ?: q.firstOrNull() ?: return

        var currentIndex = currentQueueIndex
        if (currentIndex < 0 || currentIndex >= q.size || q[currentIndex].id != current.id) {
            currentIndex = q.indexOfFirst { it.id == current.id }
        }
        
        // Autoplay logic if at the end of queue
        // Still inside the queue the listener built (or the rest of it).
        if (currentIndex in 0 until q.size - 1) {
            val nextIndex = currentIndex + 1
            Log.d(TAG, "nextSong: index $currentIndex -> $nextIndex (total: ${q.size})")
            currentQueueIndex = nextIndex
            playSongAtQueueIndex(nextIndex)
            return
        }

        // At the end of everything queued. With Infinity Play on that is not an
        // ending — top the radio up and move into what it returns. This waits
        // for the radio rather than skipping to another track, because skipping
        // to an unrelated queued row is precisely the bug this replaces.
        if (_autoplayEnabled.value) {
            scope.launch {
                topUpAutoplay(force = true)
                withContext(Dispatchers.Main) {
                    val refreshed = _queue.value
                    if (currentIndex in 0 until refreshed.size - 1) {
                        currentQueueIndex = currentIndex + 1
                        playSongAtQueueIndex(currentIndex + 1)
                    } else if (refreshed.isNotEmpty()) {
                        // The radio had nothing new. Next must still do
                        // something rather than become a dead button, so the
                        // pre-existing wrap stands.
                        Log.d(TAG, "AUTOPLAY_EXHAUSTED reason=next_at_end — wrapping")
                        currentQueueIndex = 0
                        playSongAtQueueIndex(0)
                    }
                }
            }
            return
        }

        val nextIndex = if (currentIndex == -1) {
            0
        } else {
            (currentIndex + 1) % q.size
        }

        Log.d(TAG, "nextSong: index $currentIndex -> $nextIndex (total: ${q.size})")
        currentQueueIndex = nextIndex
        playSongAtQueueIndex(nextIndex)
    }

    fun prevSong() {
        val q = _queue.value
        if (q.isEmpty()) return
        val current = _currentSong.value ?: return

        if (!ensureConnected()) return
        val controller = mediaController ?: return

        if (controller.currentPosition > 3000) {
            Log.d(TAG, "prevSong: restarting current (position > 3s)")
            controller.seekTo(0)
            _currentTimeMs.value = 0
            return
        }

        var currentIndex = currentQueueIndex
        if (currentIndex < 0 || currentIndex >= q.size || q[currentIndex].id != current.id) {
            currentIndex = q.indexOfFirst { it.id == current.id }
        }

        val prevIndex = if (currentIndex <= 0) {
            (currentIndex - 1 + q.size) % q.size
        } else {
            currentIndex - 1
        }
        
        Log.d(TAG, "prevSong: index $currentIndex -> $prevIndex")
        currentQueueIndex = prevIndex
        playSongAtQueueIndex(prevIndex)
    }

    fun toggleShuffle() {
        val currentShuffle = _shuffleEnabled.value
        _shuffleEnabled.value = !currentShuffle
        Log.d(TAG, "toggleShuffle: ${_shuffleEnabled.value}")
        
        val current = _currentSong.value ?: return
        val currentId = current.id
        
        if (!currentShuffle) {
            // Turning ON shuffle: shuffle the remaining songs in the queue
            var currentIndexInQueue = currentQueueIndex
            if (currentIndexInQueue < 0 || currentIndexInQueue >= _queue.value.size || _queue.value[currentIndexInQueue].id != currentId) {
                currentIndexInQueue = _queue.value.indexOfFirst { it.id == currentId }
            }
            if (currentIndexInQueue >= 0 && currentIndexInQueue < _queue.value.size - 1) {
                val before = _queue.value.subList(0, currentIndexInQueue + 1)
                val after = _queue.value.subList(currentIndexInQueue + 1, _queue.value.size).shuffled()
                _queue.value = before + after
                currentQueueIndex = currentIndexInQueue
                updatePreloadManager()
            }
        } else {
            // Turning OFF shuffle: restore order from baseQueue 
            if (baseQueue.isNotEmpty()) {
                val currentIndexInBase = baseQueue.indexOfFirst { it.id == currentId }
                if (currentIndexInBase >= 0) {
                    _queue.value = baseQueue
                    currentQueueIndex = currentIndexInBase
                    updatePreloadManager()
                }
            }
        }
    }

    fun cycleRepeatMode() {
        val next = when (_repeatMode.value) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        _repeatMode.value = next
        Log.d(TAG, "Repeat mode set to $next")
    }

    fun toggleAutoplay() {
        if (_autoplayEnabled.value) disableAutoplay() else enableAutoplay()
    }

    /**
     * Retry a failed radio fetch, from the queue's AutoPlay error state. The
     * request is the same bounded, deduplicated one — a retry just removes the
     * backoff that a failure installed.
     */
    fun retryAutoplay() {
        if (!_autoplayEnabled.value) return
        Log.d(TAG, "AUTOPLAY_STATE retry requested")
        maybeRefillAutoplay(force = true)
    }

    /**
     * Infinity Play, switched on.
     *
     * The first thing this does is ask the radio about the track playing right
     * now — not about the next item in the queue, and not once the queue runs
     * out. That is the whole difference between a radio and a "next" button,
     * and it is what the listener should see within a second of pressing it.
     */
    fun enableAutoplay() {
        if (_autoplayEnabled.value) return
        _autoplayEnabled.value = true
        Log.d(TAG, "AUTOPLAY_STATE enabled seed=${RadioQueue.seedVideoIdOf(_currentSong.value) ?: "none"}")
        maybeRefillAutoplay(force = true)
    }

    /**
     * Infinity Play, switched off. Suggestions AutoPlay added are removed; the
     * tracks the listener queued themselves are untouched.
     */
    fun disableAutoplay() {
        if (!_autoplayEnabled.value) return
        _autoplayEnabled.value = false
        clearAutoplayRegion()
        Log.d(TAG, "AUTOPLAY_STATE disabled")
    }

    /**
     * Drops the generated tail of the queue.
     *
     * If the listener was hearing a suggestion when this ran, that song stays as
     * the head of the queue — switching a feature off must not change what is
     * playing, and it must not leave the queue without the track the player is
     * on.
     */
    private fun clearAutoplayRegion() {
        if (autoplayQueuedIds.isEmpty()) return
        val kept = _queue.value.filterNot { it.id in autoplayQueuedIds }
        autoplayQueuedIds.clear()
        _autoplayStatus.value = AutoPlayStatus.Idle

        val current = _currentSong.value
        // Switching the feature off must not change what is playing, and must
        // not leave the queue without the track the player is on.
        _queue.value = if (current != null && kept.none { it.id == current.id }) {
            listOf(current) + kept
        } else {
            kept
        }
        if (current != null) {
            currentQueueIndex = _queue.value.indexOfFirst { it.id == current.id }.coerceAtLeast(0)
        }
        Log.d(TAG, "AUTOPLAY_REGION_CLEARED queueSize=${_queue.value.size}")
        updatePreloadManager()
    }

    /**
     * Fire-and-forget top-up, used on every track start so the next batch is
     * already in the queue before the current suggestion ends.
     */
    private fun maybeRefillAutoplay(force: Boolean = false) {
        if (!_autoplayEnabled.value) return
        val remaining = RadioQueue.remainingSuggestions(
            _queue.value, currentQueueIndex, autoplayQueuedIds
        )
        if (!force && !RadioQueue.needsRefill(remaining)) {
            Log.d(TAG, "AUTOPLAY_REFILL_SKIPPED remaining=$remaining")
            return
        }
        // The queue section shows the request in flight rather than a blank
        // block that could be read as "off" (spec 70).
        if (_autoplayStatus.value is AutoPlayStatus.Idle || _autoplayStatus.value is AutoPlayStatus.Failed) {
            _autoplayStatus.value = AutoPlayStatus.Loading
        }
        scope.launch { topUpAutoplay(force) }
    }

    /**
     * Ensures the AutoPlay continuation has suggestions for the current seed.
     *
     * The seed follows the track being listened to, which is what makes the
     * chain behave like a radio: letting a suggestion play walks the mix forward,
     * while manually choosing a new remote song re-bases the whole chain on that
     * song instead of continuing the previous track's forever.
     *
     * @return how many suggestions were added.
     */
    private suspend fun topUpAutoplay(force: Boolean): Int = autoplayLock.withLock {
        if (!_autoplayEnabled.value) return@withLock 0

        val song = _currentSong.value
        val seed = RadioQueue.seedVideoIdOf(song)
        val playingGenerated = song != null && song.id in autoplayQueuedIds

        if (seed != autoplaySeedVideoId) {
            Log.d(TAG, "AUTOPLAY_SEED_CHANGED from=${autoplaySeedVideoId ?: "none"} to=${seed ?: "none"}")
            // A manual pick invalidates the old chain: suggestions generated for
            // a different track are not suggestions for this one. A suggestion
            // simply finishing does not — that chain is still walking.
            if (!playingGenerated && autoplaySeedVideoId != null) clearAutoplayRegion()
            autoplaySeedVideoId = seed
        }

        val remaining = RadioQueue.remainingSuggestions(
            _queue.value, currentQueueIndex, autoplayQueuedIds
        )
        if (!force && !RadioQueue.needsRefill(remaining)) {
            Log.d(TAG, "AUTOPLAY_REFILL_SKIPPED remaining=$remaining")
            return@withLock 0
        }
        if (remaining >= RadioQueue.MAX_PENDING) return@withLock 0

        Log.d(TAG, "AUTOPLAY_REQUEST seed=${seed ?: "local"} remaining=$remaining force=$force")
        val added = fetchAutoplayBatch(seed, song)
        if (added == 0) Log.d(TAG, "AUTOPLAY_EXHAUSTED seed=${seed ?: "local"}")
        added
    }

    /**
     * One radio round trip, appended to the queue.
     *
     * Everything that must not be suggested again is gathered into one set
     * before the request rather than checked piecemeal afterwards: the user's
     * queue, the generated region, recent history, the current track, and every
     * id AutoPlay has offered this session.
     */
    private suspend fun fetchAutoplayBatch(
        seedVideoId: String?,
        seedSong: SongEntity?,
    ): Int = withContext(Dispatchers.IO) {
        if (seedSong == null) return@withContext 0

        val known = buildSet {
            _queue.value.forEach { add(it.id) }
            addAll(autoplayQueuedIds)
            addAll(autoplayPlayedIds)
            _history.value.forEach { add(it.id) }
            seedSong.providerTrackId?.let { add(it) }
            // Rows the listener removed are not suggestions, however warmly
            // the radio still recommends them (spec 7/8).
            addAll(removedThisSession)
        }

        val outcome = if (seedVideoId != null) {
            autoPlayEngine.radioSuggestions(seedVideoId, known)
        } else {
            // Local playback: there is no radio to ask, so the local generator
            // stands in. Its output never mixes into a remote chain — see
            // [RadioQueue.filterSuggestions].
            listOfNotNull(autoPlayEngine.localSuggestion(autoplayLocalAction, seedSong, known))
                .let { RadioOutcome.Tracks(it) }
        }

        val suggestions = when (outcome) {
            is RadioOutcome.Tracks -> outcome.suggestions
            RadioOutcome.Failed -> {
                // A real failure is said out loud in the queue's AutoPlay
                // section — silence here would be indistinguishable from an
                // exhausted radio, and "Retry" keeps manual playback whole
                // either way (spec 71).
                withContext(Dispatchers.Main) {
                    _autoplayStatus.value = AutoPlayStatus.Failed("Couldn't load AutoPlay")
                }
                return@withContext 0
            }
        }

        if (suggestions.isEmpty()) {
            withContext(Dispatchers.Main) {
                _autoplayStatus.value = AutoPlayStatus.Ready(emptyList())
            }
            return@withContext 0
        }

        val q = _queue.value
        val room = (RadioQueue.MAX_PENDING -
            RadioQueue.remainingSuggestions(q, currentQueueIndex, autoplayQueuedIds))
            .coerceAtLeast(0)
        if (room == 0) return@withContext 0

        val additions = suggestions
            .filterNot { it.id in autoplayQueuedIds || it.id in autoplayPlayedIds }
            .filterNot { it.id in removedThisSession || it.providerTrackId in removedThisSession }
            .take(room)
        if (additions.isEmpty()) return@withContext 0

        val at = RadioQueue.insertionIndex(
            queue = q,
            currentIndex = currentQueueIndex,
            generatedIds = autoplayQueuedIds,
            explicitIds = explicitQueueIds,
        ).coerceIn(0, q.size)

        autoplayQueuedIds.addAll(additions.map { it.id })
        autoplayPlayedIds.addAll(additions.map { it.id })
        _queue.value = q.subList(0, at).toList() + additions + q.subList(at, q.size)
        withContext(Dispatchers.Main) {
            updatePreloadManager()
            _autoplayStatus.value = AutoPlayStatus.Ready(additions.map { it.id })
        }
        Log.d(
            TAG,
            "AUTOPLAY_APPENDED count=${additions.size} at=$at seed=${seedVideoId ?: "local"}",
        )
        additions.size
    }

    fun clearHistory() {
        _history.value = emptyList()
        scope.launch(Dispatchers.IO) {
            listeningRepo.clearHistory()
        }
    }

    fun clearError() {
        _playbackError.value = null
    }

    /** Remove a song from the queue by its ID.
     *
     * The removal reaches every layer the queue lives in, in one call: the
     * in-memory queue, the player's Media3 timeline, the persisted queue and
     * the AutoPlay exclusion set. Before this, only the Compose list was
     * updated — the player still held the row and played it later (spec 3–6).
     */
    fun removeFromQueue(songId: String) {
        val current = _currentSong.value
        if (current?.id == songId) return
        val currentQueue = _queue.value
        val newQueue = currentQueue.filterNot { it.id == songId }
        _queue.value = newQueue
        if (current != null) {
            currentQueueIndex = newQueue.indexOfFirst { it.id == current.id }
        }
        rememberRemoved(currentQueue.firstOrNull { it.id == songId })
        // The actual playback timeline must agree — it is what will play.
        try {
            mediaController?.let { controller ->
                val mediaIndex = (0 until controller.mediaItemCount)
                    .firstOrNull { controller.getMediaItemAt(it).mediaId == songId }
                if (mediaIndex != null) controller.removeMediaItem(mediaIndex)
            }
        } catch (e: Exception) {
            Log.w(TAG, "QUEUE_TIMELINE removeMediaItem failed for $songId: ${e.message}")
        }
        saveStateToDataStore()
        updatePreloadManager()
        Log.d(TAG, "QUEUE_REMOVE id=$songId queueSize=${newQueue.size}")
    }

    /** Remove a specific item from the queue by its index. */
    fun removeQueueItemAt(index: Int) {
        val q = _queue.value.toMutableList()
        if (index in q.indices) {
            if (index == _currentQueueIndex.value) return
            
            val removed = q.removeAt(index)
            _queue.value = q
            
            if (index < _currentQueueIndex.value) {
                _currentQueueIndex.value -= 1
            }
            rememberRemoved(removed)
            try {
                mediaController?.let { controller ->
                    val mediaIndex = (0 until controller.mediaItemCount)
                        .firstOrNull { controller.getMediaItemAt(it).mediaId == removed.id }
                    if (mediaIndex != null) controller.removeMediaItem(mediaIndex)
                }
            } catch (e: Exception) {
                Log.w(TAG, "QUEUE_TIMELINE removeMediaItem failed for ${removed.id}: ${e.message}")
            }
            saveStateToDataStore()
            updatePreloadManager()
            Log.d(TAG, "QUEUE_REMOVE id=${removed.id} at=$index queueSize=${q.size}")
        }
    }

    /**
     * Records a removal in the exclusion set, under both identities the
     * dedup engine compares (spec 7): the queue row id and the stable remote
     * video id when there is one.
     */
    private fun rememberRemoved(song: SongEntity?) {
        song ?: return
        removedThisSession.add(song.id)
        song.providerTrackId?.takeIf { it.isNotBlank() }?.let { removedThisSession.add(it) }
    }

    fun playNext(song: SongEntity) {
        val current = _currentSong.value
        val currentQueue = _queue.value.toMutableList()
        var currentSongIndex = currentQueueIndex
        if (currentSongIndex < 0 || currentSongIndex >= currentQueue.size || (current != null && currentQueue[currentSongIndex].id != current.id)) {
            currentSongIndex = if (current != null) currentQueue.indexOfFirst { it.id == current.id } else 0
        }
        
        if (currentSongIndex != -1) {
            // Remove existing upcoming occurrence of this song (after currentSongIndex) so it cleanly moves to next position
            val upcomingStart = currentSongIndex + 1
            if (upcomingStart < currentQueue.size) {
                val existingUpcomingIndex = currentQueue.subList(upcomingStart, currentQueue.size).indexOfFirst { it.id == song.id }
                if (existingUpcomingIndex != -1) {
                    currentQueue.removeAt(upcomingStart + existingUpcomingIndex)
                }
            }
            currentQueue.add(currentSongIndex + 1, song)
        } else {
            currentQueue.add(0, song)
            currentQueueIndex = 0
        }
        // An explicit instruction: AutoPlay must never insert in front of it.
        explicitQueueIds.add(song.id)
        _queue.value = currentQueue
        updatePreloadManager()
        Log.d(TAG, "playNext: '${song.title}' added after index $currentSongIndex, queue size=${currentQueue.size}")
    }

    fun addToQueue(song: SongEntity) {
        val currentQueue = _queue.value.toMutableList()
        // "Add to queue" means the end of the queue the listener built — which
        // is never after the suggestions AutoPlay appended. Adding past them
        // would put a deliberately chosen track behind automatically chosen
        // ones, and the listener would never hear it in the order they asked.
        val boundary = RadioQueue.boundary(currentQueue, autoplayQueuedIds)
            .coerceIn(0, currentQueue.size)
        currentQueue.add(boundary, song)
        explicitQueueIds.add(song.id)
        _queue.value = currentQueue
        updatePreloadManager()
        Log.d(TAG, "addToQueue: '${song.title}' at $boundary (autoplayRegionStartsAt=$boundary)")
    }

    /** Move a song within the queue from one position to another. */
    fun moveInQueue(from: Int, to: Int) {
        val q = _queue.value.toMutableList()
        if (from < 0 || from >= q.size || to < 0 || to >= q.size) return
        val item = q.removeAt(from)
        q.add(to, item)
        _queue.value = q
        val current = _currentSong.value
        if (current != null) {
            currentQueueIndex = q.indexOfFirst { it.id == current.id }
        }
        updatePreloadManager()
        Log.d(TAG, "moveInQueue: $from -> $to")
    }
    
    /** Update the upcoming portion of the queue completely (used after drag completes) */
    fun updateUpcomingQueue(newUpcomingQueue: List<SongEntity>, startIndex: Int) {
        val currentQueue = _queue.value
        if (startIndex < 0 || startIndex > currentQueue.size) return
        
        val newQueue = currentQueue.subList(0, startIndex) + newUpcomingQueue
        _queue.value = newQueue
        val current = _currentSong.value
        if (current != null) {
            currentQueueIndex = newQueue.indexOfFirst { it.id == current.id }
        }
        updatePreloadManager()
        Log.d(TAG, "updateUpcomingQueue: updated from index $startIndex")
    }

    /**
     * Snapshot of the ids AutoPlay generated and that are still marked as
     * suggestions, for the queue screen's tier split. The set only ever
     * changes together with the queue itself, so callers keying recomposition
     * on the queue see a consistent pair.
     */
    fun autoplayIdsSnapshot(): Set<String> = synchronized(autoplayQueuedIds) { autoplayQueuedIds.toSet() }

    /**
     * Append generated tracks as suggestions, without touching the context.
     *
     * This is the tier-aware way for any context (Daily Mix, an album, a
     * playlist, a search session) to hand its tail to AutoPlay. It differs from
     * the old wholesale [updateUpcomingQueue] in three ways that matter:
     *
     *  1. The existing queue — the context's own rows — is never rewritten.
     *     Only additions are made, after everything already queued.
     *  2. Every addition is registered in [autoplayQueuedIds] *before* it
     *     enters the queue, so the queue screen splits CONTEXT from AUTOPLAY on
     *     the same tick the row appears, and a user removal of one of them
     *     clears it from the right set.
     *  3. Removal memory, played memory and what is already queued all
     *     deduplicate, so a song the listener removed or already heard cannot
     *     re-enter through a later batch.
     *
     * Returns the tracks actually appended; a caller that needs to know
     * whether anything changed can use the list length instead of diffing
     * queue snapshots.
     */
    fun appendAutoplaySuggestions(candidates: List<SongEntity>): List<SongEntity> {
        if (candidates.isEmpty()) return emptyList()
        val queued = _queue.value
        val queuedIds = queued.mapTo(HashSet()) { it.id }
        val appended = mutableListOf<SongEntity>()
        for (song in candidates) {
            val key = song.id
            if (key.isBlank()) continue
            // One identity, three memories: already queued, already offered
            // this session, or explicitly removed — any of the three means the
            // candidate is not a new suggestion.
            if (key in queuedIds || key in autoplayPlayedIds || key in removedThisSession) continue
            synchronized(autoplayQueuedIds) {
                if (!autoplayQueuedIds.add(key)) return@synchronized
                autoplayPlayedIds.add(key)
                appended.add(song)
            }
        }
        if (appended.isEmpty()) return emptyList()

        _queue.value = queued + appended
        updatePreloadManager()
        saveStateToDataStore()
        Log.d(TAG, "AUTOPLAY_APPEND tier=CONTEXT+AUTOPLAY added=${appended.size} contextEndsAt=${queued.size}")
        return appended
    }

    /**
     * Replace the user-queue region the queue screen edits — drag-to-reorder or
     * row removal — while leaving the AutoPlay region attached behind it.
     *
     * The old wholesale [updateUpcomingQueue] would cut the generated tail off
     * any time the listener dragged a row or removed one, because it could not
     * tell the two tiers apart. This one rebuilds only the rows it was given:
     * the suggestions stay appended after the user's run, in the order they
     * were generated.
     */
    fun updateUserQueueUpcoming(newUserQueue: List<SongEntity>, startIndex: Int) {
        val current = _currentSong.value
        val q = _queue.value
        if (startIndex < 0 || startIndex > q.size) return

        val generatedTail = q.drop(startIndex).filter { it.id in autoplayQueuedIds }
        val newQueue = q.subList(0, startIndex) + newUserQueue + generatedTail

        _queue.value = newQueue
        if (current != null) {
            currentQueueIndex = newQueue.indexOfFirst { it.id == current.id }
        }
        updatePreloadManager()
        Log.d(TAG, "updateUserQueueUpcoming: from=$startIndex userSize=${newUserQueue.size} keptAutoplay=${generatedTail.size}")
    }

    /** Play a specific song from the queue by index. */
    fun playFromQueue(index: Int) {
        val q = _queue.value
        if (index < 0 || index >= q.size) return
        Log.d(TAG, "playFromQueue: index=$index, song='${q[index].title}'")
        currentQueueIndex = index
        playSongAtQueueIndex(index)
    }

    private suspend fun handleSongEndedSuspend() {
        if (_repeatMode.value == RepeatMode.ONE) {
            Log.d(TAG, "handleSongEnded: REPEAT_ONE — restarting current song")
            withContext(Dispatchers.Main) {
                if (!ensureConnected()) return@withContext
                val controller = mediaController ?: return@withContext
                controller.seekTo(0)
                controller.play()
            }
            return
        }

        val current = _currentSong.value ?: return
        var q = _queue.value
        var currentIndex = currentQueueIndex
        if (currentIndex < 0 || currentIndex >= q.size || q[currentIndex].id != current.id) {
            currentIndex = q.indexOfFirst { it.id == current.id }
        }

        // End of everything queued, with Infinity Play on: make sure the radio
        // has something ahead and move into it. The fetching normally already
        // happened while the previous tracks were playing, so this is usually
        // just an advance — the wait only exists for the first track after the
        // feature was switched on.
        if (currentIndex >= q.size - 1 && _autoplayEnabled.value) {
            Log.d(TAG, "handleSongEnded: end of queue, Infinity Play on — topping up")
            topUpAutoplay(force = false)
            q = _queue.value
            if (currentIndex < q.size - 1) {
                withContext(Dispatchers.Main) {
                    currentQueueIndex = currentIndex + 1
                    playSongAtQueueIndex(currentIndex + 1)
                }
                return
            }
            Log.d(TAG, "AUTOPLAY_EXHAUSTED reason=track_ended")
        }

        if (_repeatMode.value == RepeatMode.ALL) {
            if (currentIndex < q.size - 1) {
                Log.d(TAG, "handleSongEnded: REPEAT_ALL — next in queue")
                withContext(Dispatchers.Main) { nextSong() }
            } else {
                Log.d(TAG, "handleSongEnded: REPEAT_ALL — looping back to start")
                withContext(Dispatchers.Main) {
                    if (q.isNotEmpty()) {
                        currentQueueIndex = 0
                        playSongAtQueueIndex(0)
                    }
                }
            }
        } else if (_repeatMode.value == RepeatMode.OFF) {
            if (currentIndex < q.size - 1) {
                Log.d(TAG, "handleSongEnded: REPEAT_OFF — next in queue")
                withContext(Dispatchers.Main) { nextSong() }
            } else {
                Log.d(TAG, "handleSongEnded: REPEAT_OFF — end of queue, stopping")
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
                        // Listening time is the distance the audio itself
                        // advanced — credited here, off the player's own
                        // position, so buffering (position frozen) and seeks
                        // (position jumps) credit nothing (stats spec 6/7).
                        creditPlaybackPosition(pos)
                        // Persist periodically so a force-close mid-track still
                        // leaves almost everything behind (stats spec 10).
                        if (currentSongAccumulatedTimeMs - sessionLoggedMs >= ListeningSession.PERIODIC_FLUSH_MS) {
                            flushListeningSession("periodic")
                        }
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
                
                // Save state periodically if playing
                if (controller?.isPlaying == true && (System.currentTimeMillis() % 5000 < 100)) {
                    saveStateToDataStore()
                }
            }
        }
    }

    private fun stopProgressUpdates() {
        progressJob?.cancel()
        progressJob = null
    }

    /**
     * Binds the session to a new track with a clean slate. Called only after
     * the outgoing session has been flushed — everything the flush needed was
     * captured before this runs.
     */
    private fun resetListeningSession(song: SongEntity?) {
        sessionSong = song
        sessionLoggedMs = 0L
        sessionClosed = false
        sessionLastPositionMs = -1L
        currentSongAccumulatedTimeMs = 0L
    }

    /**
     * Credits the distance the audio advanced since the previous observation.
     * Runs on the poll tick while the player reports it is playing.
     */
    private fun creditPlaybackPosition(positionMs: Long) {
        if (sessionLastPositionMs >= 0) {
            currentSongAccumulatedTimeMs += ListeningSession.creditTick(sessionLastPositionMs, positionMs)
        }
        sessionLastPositionMs = positionMs
    }

    /**
     * Writes the listening this session has earned but Room does not have yet.
     *
     * Every write is the INCREMENT since the previous flush, so a pause, a
     * buffering gap, or a pause-before-complete sequence each contribute their
     * own slice and the aggregates sum to the truth — the replaced routine
     * re-logged the running total on every pause, so a track that buffered
     * twice counted its whole length three times (stats spec 5).
     *
     * The song and history-row identity are captured synchronously here, on
     * the Main dispatcher, before any caller can swap the current track — the
     * old code read `_currentSong` from a launched coroutine, by which time a
     * fast tap had already re-pointed it, which is how a song the listener
     * never played appeared in stats wearing another song's listening time
     * (stats spec 11). Only the Room write leaves the Main dispatcher.
     *
     * A terminal flush (complete/skip) closes the session: later flushes for
     * the same track — the pause that follows STATE_ENDED, the queue advance
     * after a crossfade — find it closed and write nothing.
     */
    private fun flushListeningSession(
        reason: String,
        song: SongEntity? = sessionSong,
        completed: Boolean = false,
        skipped: Boolean = false,
    ) {
        // The session track is bound on every track change; the fallback to
        // the current song covers only the bootstrap cases (the very first
        // play, a restored snapshot) where no change has happened yet.
        val track = song ?: _currentSong.value ?: return
        if (sessionClosed) return

        val totalMs = currentSongAccumulatedTimeMs
        val deltaMs = (totalMs - sessionLoggedMs).coerceAtLeast(0L)
        if (!ListeningSession.shouldWriteEvent(deltaMs, totalMs, completed, skipped)) return

        val deltaSec = deltaMs / 1000
        val totalSec = totalMs / 1000
        sessionLoggedMs = totalMs
        if (completed || skipped) sessionClosed = true

        // The timeline row this session belongs to, captured now.
        val historyRowId = currentHistoryRowId

        // The autoplay retention signal reads the whole session, as before.
        val action = retentionManager.processTrackEnd(totalMs, _durationMs.value)

        scope.launch(Dispatchers.IO) {
            // The whole identity snapshot goes with the event, so a streamed
            // track that was never persisted to `songs` still counts in the
            // aggregates and still shows its artwork (spec 12/13).
            listeningRepo.logEvent(track, reason, deltaSec, completed = completed, skipped = skipped)
            GratiaApp.instance.database.songDao().incrementListenTime(track.id, deltaMs)
            // A play is counted only when the track was genuinely heard: it
            // either ran to the end, or was skipped after a meaningful listen
            // ([ListeningSession.countsAsPlay]). The session-close guard above
            // means the terminal flush happens exactly once per track, so the
            // count can never be incremented twice for one hearing.
            if (ListeningSession.countsAsPlay(completed, skipped, totalSec)) {
                GratiaApp.instance.database.songDao().incrementPlayCount(track.id)
            }
            // The timeline row for this play gets the same truth: what was
            // actually listened to, and whether the track ran through.
            historyRowId?.let { rowId ->
                runCatching { historyRepo.updateListening(rowId, totalSec, completed) }
            }
        }

        // Auto-Play keeps the continuation stocked after the flush — off the
        // flush's own synchronous path, so a caller mid-track-change never waits
        // on a network refill.
        scope.launch { handleAutoPlaySuspend(action, track) }
    }

    /**
     * The end-of-track retention signal.
     *
     * It no longer generates a track by itself. AutoPlay's job here is to keep
     * the continuation stocked, which is the same work every other track change
     * asks for — a single appended track is exactly the "one more song" shape
     * this pass replaced. The skip/wildcard signal is still kept, and is applied
     * when the seed is a local file, where no radio can be asked.
     */
    private suspend fun handleAutoPlaySuspend(action: QueueAction, evaluatedSong: SongEntity) = withContext(Dispatchers.IO) {
        if (!_autoplayEnabled.value) {
            Log.d(TAG, "AUTOPLAY_REFILL_SKIPPED reason=disabled")
            return@withContext
        }
        autoplayLocalAction = action

        val remaining = RadioQueue.remainingSuggestions(
            _queue.value, currentQueueIndex, autoplayQueuedIds
        )
        if (RadioQueue.needsRefill(remaining)) {
            topUpAutoplay(force = false)
        } else {
            Log.d(TAG, "AUTOPLAY_REFILL_SKIPPED remaining=$remaining")
        }
    }

    /**
     * Update the in-memory currentSong state without changing playback.
     * Used when metadata (like isFavorite) changes while the song is playing.
     */
    fun updateCurrentSongState(song: SongEntity) {
        _currentSong.value = song
    }

    // ── Quality upgrade: the second look ─────────────────────────────────
    //
    // Ported from the original playback/QualityUpgrade.kt + SourceResolver.
    // The rules that matter, in the order they matter:
    //
    //  1. Never stop current playback to search for a better source.
    //  2. Never swap without a bitrate gain of at least [UPGRADE_MIN_GAIN] —
    //     a swap that might be a downgrade is worse than no swap at all.
    //  3. Never replace a recording matched on title alone: the candidate must
    //     agree on artist, and the decoder's own runtime must agree with the
    //     candidate's within two seconds.
    //  4. If anything goes wrong, continue current playback. The upgrade is
    //     optional; the music is not.

    private var upgradeJob: Job? = null

    /** Marks the currently playing remote track as "below request" once its stream is up. */
    private fun maybeMarkPendingUpgrade(song: SongEntity, source: com.gratia.music.provider.PlaybackSource?) {
        if (source == null) return
        val settings = com.gratia.music.data.SettingsDataStore(context)
        scope.launch {
            val saavnEnabled = settings.jioSaavnEnabledFlow.first()
            com.gratia.music.provider.sources.SourceRegistry.enableJioSaavn(saavnEnabled)
            if (!com.gratia.music.provider.sources.SourceResolver.canSubstituteForYouTube()) return@launch
            val target = com.gratia.music.provider.sources.TrackMatcher.Target(
                title = song.title,
                artist = song.artist,
                durationSec = song.durationMs?.let { (it / 1000).toInt() },
                album = song.album,
            )
            val playingFormat = com.gratia.music.provider.sources.StreamFormat(
                codec = source.format,
                kbps = source.bitrate,
            )
            val quality = effectiveQualitySetting()
            com.gratia.music.provider.sources.QualityUpgrade.settledForLess(
                mediaId = song.id,
                target = target,
                playing = playingFormat,
                qualitySetting = quality,
            )
            if (com.gratia.music.provider.sources.QualityUpgrade.isPending(song.id)) {
                _isUpgradeOptimizing.value = true
            }
            startUpgradeLook(song)
        }
    }

    /** The per-network quality setting for the connection in hand. */
    private suspend fun effectiveQualitySetting(): String {
        val settings = com.gratia.music.data.SettingsDataStore(context)
        return if (com.gratia.music.data.network.NetworkMonitor.isMetered()) {
            settings.mobileQualityFlow.first()
        } else {
            settings.wifiQualityFlow.first()
        }
    }

    /**
     * Kicks off the bounded background second look for the current track.
     *
     * Runs on IO, races every enabled source, and only ever ends in one of
     * three ways: a validated better stream is parked for the swap, the answer
     * is "no" and the track is marked asked, or the search is cancelled
     * because the queue moved on — in which case nothing was learned and the
     * track stays pending.
     */
    private fun startUpgradeLook(song: SongEntity) {
        upgradeJob?.cancel()
        upgradeJob = scope.launch(kotlinx.coroutines.CoroutineName("quality-upgrade")) {
            try {
                // A bounded wait: the upgrade must never outlive its usefulness.
                // A longer search would still be running when the track ended.
                val found = kotlinx.coroutines.withTimeoutOrNull(UPGRADE_SEARCH_TIMEOUT_MS) {
                    withContext(Dispatchers.IO) {
                        com.gratia.music.provider.sources.QualityUpgrade.lookAgain(
                            mediaId = song.id,
                            playingDurationSec = null,
                        )
                    }
                }
                if (found != null && _currentSong.value?.id == song.id) {
                    // Validated: the resolver already enforced worthSwapping and
                    // the strict-length match. Park it; the swap happens on the
                    // next progress sample while playback continues.
                    com.gratia.music.provider.sources.QualityUpgrade.force(song.id, found)
                    Log.d(
                        TAG,
                        "QUALITY_UPGRADE_FOUND videoId=${song.providerTrackId} " +
                            "format=${found.format.summary} from=${found.sourceConfigId}",
                    )
                    swapToUpgradedStream(song, found)
                } else if (found == null) {
                    // Nothing better exists: the search concluded without a
                    // swap, so the in-progress line has nothing more to say.
                    _isUpgradeOptimizing.value = false
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // The queue moved on — nothing was learned, and that is fine.
            } catch (e: Exception) {
                Log.w(TAG, "QUALITY_UPGRADE_FAILED videoId=${song.providerTrackId}: ${e.message}")
            }
        }
    }

    /**
     * Swaps the playing media item for the parked better stream.
     *
     * The swap costs one short seam in the audio — the player cannot change
     * sources gaplessly mid-track — which is why everything upstream has
     * already proved it worth hearing: same recording, strictly better
     * rendition, source validated.
     */
    private fun swapToUpgradedStream(
        song: SongEntity,
        stream: com.gratia.music.provider.sources.SourceStream,
    ) {
        val controller = mediaController ?: return
        if (_currentSong.value?.id != song.id) return
        val positionMs = controller.currentPosition
        val wasPlaying = controller.isPlaying
        Log.d(
            TAG,
            "QUALITY_UPGRADE_SWAP videoId=${song.providerTrackId} at=${positionMs}ms " +
                "format=${stream.format.summary}",
        )
        playResolvedMediaItem(song, stream.url, playImmediately = wasPlaying, seekPosition = positionMs)
        // The player is now actually playing the better rendition — the only
        // moment at which an upgrade may be claimed. Note playResolvedMediaItem
        // does not clear this: only playSongInternal (a new track) does.
        _isUpgradedStream.value = true
        _isUpgradeOptimizing.value = false
        com.gratia.music.provider.sources.QualityUpgrade.consumeForced(song.id)
        com.gratia.music.provider.sources.QualityUpgrade.continueAfterLossySwap(song.id)
        // The lossless follow-up re-opens the question for this same track.
        if (com.gratia.music.provider.sources.QualityUpgrade.isPending(song.id)) {
            _isUpgradeOptimizing.value = true
        }
    }

    /**
     * Update a song's metadata in the queue list without changing playback order.
     */
    fun updateSongInQueue(songId: String, updatedSong: SongEntity) {
        _queue.value = _queue.value.map { if (it.id == songId) updatedSong else it }
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

    private fun scheduleBackgroundSync() {
        cancelBackgroundSync()
        backgroundSyncJob = scope.launch {
            Log.d(TAG, "Waiting 10s before triggering background metadata sync...")
            delay(10000L) // Wait 10 seconds of playback
            if (isActive && _isPlaying.value) {
                Log.d(TAG, "Triggering WorkManager for MetadataSyncWorker")
                val request = OneTimeWorkRequestBuilder<MetadataSyncWorker>().build()
                WorkManager.getInstance(context).enqueue(request)
            }
        }
    }

    private fun cancelBackgroundSync() {
        backgroundSyncJob?.cancel()
        backgroundSyncJob = null
    }
}

/**
 * Extension to convert a SongEntity to a MediaItem that matches perfectly 
 * between the PlayerManager and PreloadManager for caching.
 */
fun SongEntity.toMediaItem(): MediaItem {
    val uri = localUri ?: ""
    val metadataBuilder = androidx.media3.common.MediaMetadata.Builder()
        .setTitle(title)
        .setArtist(artist)
        .setAlbumTitle(album ?: "Gratia")

    val artwork = artworkUrl ?: coverArtPath
    if (!artwork.isNullOrBlank()) {
        try {
            val uriString = if (artwork.startsWith("content://") || artwork.startsWith("http")) {
                artwork
            } else {
                "file://${artwork}"
            }
            metadataBuilder.setArtworkUri(android.net.Uri.parse(uriString))
        } catch (e: Exception) {
            // Ignore invalid URIs
        }
    }

    return MediaItem.Builder()
        .setUri(android.net.Uri.parse(uri))
        .setMediaId(id)
        .setMediaMetadata(metadataBuilder.build())
        .build()
}

enum class RepeatMode {
    OFF, ALL, ONE
}

