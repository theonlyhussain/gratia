package com.gratia.music.download

import com.gratia.music.data.model.SongEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Where a remote track stands with respect to being on this device.
 *
 * Distinct from [SongEntity.isDownloaded], which is the *persisted* fact about
 * a finished download. A download in flight is real to the user — they tapped
 * a button and are owed a progress bar — but has nothing to persist yet, and a
 * failed attempt must never be allowed to look like either.
 */
sealed interface DownloadState {

    /** No download exists and none is running. */
    data object NotDownloaded : DownloadState

    /**
     * The worker has the track but its stream has not been resolved yet.
     * Distinct from [Downloading] because the two fail differently and take
     * different times: resolution is a lookup that can refuse a bot-checking
     * client for seconds, fetching is a transfer with a percentage. A UI that
     * shows "Downloading…" during resolution is claiming bytes are moving
     * when none are (spec 5).
     */
    data object Resolving : DownloadState

    /**
     * Bytes are being fetched. [progress] is 0..1 when the response carries a
     * content length, and null when it does not — the UI shows a real
     * percentage in the first case and the word "Downloading…" in the second,
     * never an invented number.
     */
    data class Downloading(val progress: Float?) : DownloadState

    /** Complete, on disk, playable offline. */
    data object Downloaded : DownloadState

    /** The attempt failed; partial bytes were cleaned up. Retry is safe. */
    data class Failed(val message: String) : DownloadState
}

/**
 * The in-memory ledger of download activity, keyed by stable recording id.
 *
 * One instance for the whole app (owned by [com.gratia.music.GratiaApp]) so
 * every surface — song menus, downloads shelf, all-on-device — reads the same
 * fact about what is in flight. Persisted completion still lives in Room via
 * [SongEntity.isDownloaded]; this ledger covers the transient states Room
 * should not hold.
 *
 * Keying note: entries are keyed by the same stable remote id the download
 * itself is keyed by ([DownloadManager.downloadKey]). Downloading the same
 * recording twice cannot therefore produce two parallel entries — the second
 * request joins the first's state, and the dedupe is enforced at the manager
 * level.
 */
class DownloadStateHolder {

    private val _states = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val states: StateFlow<Map<String, DownloadState>> = _states.asStateFlow()

    /** The state of [key], defaulting to [DownloadState.NotDownloaded]. */
    fun stateFor(key: String): DownloadState =
        _states.value[key] ?: DownloadState.NotDownloaded

    fun setResolving(key: String) {
        _states.value = _states.value + (key to DownloadState.Resolving)
    }

    fun setDownloading(key: String, progress: Float?) {
        _states.value = _states.value + (key to DownloadState.Downloading(progress))
    }

    /**
     * Marks [key] finished and keeps that answer in the ledger.
     *
     * Completion is persisted in Room as well, but the surfaces that show a
     * menu hold a `SongEntity` snapshot taken when their list was read. Dropping
     * the entry here would let such a snapshot fall back to "not downloaded"
     * for as long as it lives — the row would offer Download again seconds after
     * the file landed. Keeping the terminal state means the menu says
     * "Downloaded" from the moment the bytes arrive, whatever the caller's
     * snapshot says (spec 121). [clear] removes it when the file goes.
     */
    fun setDownloaded(key: String) {
        _states.value = _states.value + (key to DownloadState.Downloaded)
    }

    fun setFailed(key: String, message: String) {
        _states.value = _states.value + (key to DownloadState.Failed(message))
    }

    fun clear(key: String) {
        _states.value = _states.value - key
    }
}

/**
 * The stable identity a download is keyed by.
 *
 * Two downloads of "the same song" must not silently produce two files, and a
 * download must never overwrite an unrelated local file that happens to share
 * title and artist — so the key is the remote recording identity, not a name.
 * For a YouTube track that is the videoId; the entity's own id is `ytm_<id>`,
 * from which the key is derived, and a scanned local file has no remote
 * identity and can never reach this code path.
 */
fun SongEntity.downloadKey(): String = providerTrackId ?: id.removePrefix("ytm_")
