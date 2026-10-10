package com.gratia.music.player

import com.gratia.music.data.model.SongEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The complete playback snapshot, persisted so a cold start can restore the
 * session without rediscovering anything.
 *
 * The old persistence stored only queue ids, the current song's id and a
 * position, and rebuilt the session from the Room `songs` table. That made
 * restoration depend on every row having been inserted — which is true for
 * scanned local files and false for a YouTube track played straight from
 * Search (nothing inserts it unless it is liked or downloaded), and it meant
 * the source type, the origin and the artwork all had to survive a lookup
 * that could silently fail. This snapshot carries the whole truth instead:
 * the full [SongEntity] rows, the restored position, where the session
 * started, and which queue rows the AutoPlay radio generated.
 *
 * The position is part of the snapshot and rewritten with it; there is one
 * persisted playback state, not two keys that can drift apart.
 */
@Serializable
data class PlaybackSnapshot(
    /** The track the player was on. `null` is a cleared/empty player. */
    val currentSong: SongEntity? = null,
    /** The queue as it stood, current track included. */
    val queue: List<SongEntity> = emptyList(),
    /** The position the listener was at, in milliseconds. */
    val positionMs: Long = 0L,
    /** Where the session began, so the context line survives the restart. */
    val origin: PlaybackOrigin = PlaybackOrigin.UNKNOWN,
    /** Ids of queue rows the AutoPlay radio generated (queue tier context). */
    val autoplayIds: Set<String> = emptySet(),
    /** When this snapshot was written. Diagnostic only. */
    val savedAtMs: Long = 0L,
) {
    fun encode(): String = json.encodeToString(serializer(), this)

    companion object {
        /**
         * `ignoreUnknownKeys` keeps a snapshot written by a newer build
         * decodable by an older one — the persisted state must never become a
         * crash on upgrade.
         */
        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = false
        }

        /** Null for absent or malformed state — the caller treats both as nothing to restore. */
        fun decode(raw: String?): PlaybackSnapshot? = raw?.takeIf { it.isNotBlank() }?.let {
            runCatching { json.decodeFromString(serializer(), it) }.getOrNull()
        }
    }
}

/**
 * What a restored track's playback source resolves to.
 *
 * The whole point of this type is that the decision is made once, as a pure
 * function of the persisted row plus two on-disk facts, and every caller —
 * startup restore, prepare-on-play — branches the same way. A local file must
 * never reach the remote resolver, a downloaded file must be preferred over
 * its remote identity, and a source that is genuinely gone must be reported
 * as gone rather than silently "fixed" by re-resolving it from the network.
 */
sealed interface RestorableSource {

    /** The bytes are on the device at [mediaUri]: prepare this, no network. */
    data class OnDevice(val mediaUri: String) : RestorableSource

    /**
     * Remote identity only. Resolution is the correct path for this source —
     * but it is paid when the listener actually asks to play, not at cold
     * start: a restored session must open calm and offline-safe.
     */
    data object NeedsNetwork : RestorableSource

    /**
     * The persisted source no longer exists (the file was deleted or moved).
     * The snapshot must be cleared and the truth shown, never papered over
     * with a resolver walk that would play some other recording.
     */
    data object Unavailable : RestorableSource
}

/**
 * Decides how a restored track should be brought back to life.
 *
 * [localUriUsable] and [downloadFileExists] are the two facts only the
 * platform can answer (does this content URI still open; does the downloaded
 * file still exist); everything else is a pure rule on the row itself:
 *
 * ```text
 * LOCAL      → local URI (falling back to the recorded file path)
 * DOWNLOADED → downloaded file (falling back to remote resolution only if
 *              the file genuinely no longer exists)
 * REMOTE     → remote resolution
 * ```
 */
fun restorableSourceOf(
    song: SongEntity,
    localUriUsable: Boolean,
    downloadFileExists: Boolean,
): RestorableSource = when {
    // A local original: its MediaStore URI is the source. The recorded file
    // path is the stable fallback for a URI that went stale (a re-scan can
    // change the media id while the file itself is still there).
    song.storageProvider == "local" -> when {
        localUriUsable -> RestorableSource.OnDevice(song.localUri!!)
        !song.storagePath.isNullOrBlank() -> RestorableSource.OnDevice(song.storagePath!!)
        else -> RestorableSource.Unavailable
    }
    // A downloaded remote track keeps its remote identity but its bytes are
    // here — the file wins over any resolver walk. If the file is genuinely
    // gone, the track falls back to its remote self rather than vanishing.
    song.isDownloaded && !song.downloadPath.isNullOrBlank() ->
        if (downloadFileExists) RestorableSource.OnDevice(song.downloadPath!!)
        else RestorableSource.NeedsNetwork
    // Streaming catalogue track: resolution is its path, deferred to play.
    else -> RestorableSource.NeedsNetwork
}
