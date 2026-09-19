/*
 * Copyright (C) 2026 Kushagra Singh / Gratia contributors
 * Modified for Gratia by Hussain / Gratia contributors, 2026
 * Licensed under the GNU General Public License v3.0
 *
 * Source: Gratia playback/QualityUpgrade.kt.
 * https://github.com/kushagrasinghx/Gratia (GPLv3)
 *
 * Modifications for Gratia: the URI re-marking half (upgradedUri) is not
 * carried, because Gratia's player plays a resolved URL directly rather than
 * re-resolving a queue URI at open time; the caller swaps media items itself
 * (see PlayerManager.maybeUpgradeCurrentStream). The state machine — pending,
 * asked, refused, follow-ups, shelved, forced, the audition, the effective
 * duration rule and the cache tag — is carried over exactly. Every guard errs
 * towards not swapping: a missed upgrade is a quieter failure than an
 * interrupted song.
 */
package com.gratia.music.provider.sources

import android.util.Log
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Deferred

/**
 * The second look: a track that started on less than was asked for gets the
 * question asked again, properly, while it plays.
 *
 * The live path has to answer in the time a listener will wait for a track to
 * start, and it buys that by giving up on the slow catalogue — which is
 * regularly the one holding the better copy. So: play whatever can be had now,
 * then look again with no time limit, and swap only if the answer is genuinely
 * the same recording *and* genuinely better than what is playing — see
 * [SourceResolver.worthSwapping] for where that line is drawn. The swap is not
 * free — the player cannot change sources gaplessly mid-track, so there is a
 * short break in the audio — which is why every guard here errs towards not
 * doing it.
 */
object QualityUpgrade {

    private const val TAG = "GratiaSource"

    /**
     * A track playing on less than was asked for.
     *
     * [inFlight] is a live lookup still running — worth waiting on rather than
     * repeating. Null once the first look has finished and come back with
     * nothing better.
     */
    private data class Pending(
        val target: TrackMatcher.Target,
        val inFlight: Deferred<SourceStream?>? = null,
        /**
         * What the listener is actually hearing — the yardstick a lossy
         * candidate is measured against in [SourceResolver.worthSwapping].
         * Null only when neither the resolve nor the decoder could measure it,
         * and an unknown floor is one nothing lossy clears.
         */
        val playing: StreamFormat? = null,
        /** The quality setting the second look should ask under. */
        val qualitySetting: String = "HIGH",
    )

    private val pending = ConcurrentHashMap<String, Pending>()

    /**
     * A second pass to make after a worthwhile lossy upgrade. The first pass
     * intentionally takes the first source that beats Opus so playback
     * improves quickly; with lossless requested, JioSaavn can be that answer
     * while a slower lossless source is still searching. Once the better
     * lossy stream is playing, ask again with its bitrate as the floor.
     */
    private val followUps = ConcurrentHashMap<String, Pending>()

    /** Streams found, proved and parked for the player to swap in. */
    private val forced = ConcurrentHashMap<String, SourceStream>()

    /**
     * Upgrades that were found, proved and cached, and then never got to
     * happen because the queue moved on in the last moments before the swap.
     * Held against the listener coming back: the swap that follows is the
     * cheap kind — no search, no download.
     */
    private val shelved = ConcurrentHashMap<String, SourceStream>()

    /** Keeps a proved-but-unused upgrade for [mediaId] against a return visit. */
    fun shelve(mediaId: String, stream: SourceStream) {
        shelved[mediaId] = stream
        asked -= mediaId
    }

    /** The upgrade already proved for [mediaId], if one ran out of track. */
    fun shelvedFor(mediaId: String): SourceStream? = shelved[mediaId]

    /** Takes [mediaId]'s upgrade off the shelf — it has happened. */
    fun unshelve(mediaId: String) {
        shelved.remove(mediaId)
    }

    /**
     * Records that [mediaId] is playing on less than was asked for.
     *
     * Not gated on lossless: a source ranked above YouTube can be worth
     * swapping to on bitrate alone, and requiring lossless here would cancel
     * a still-running lookup that could yet have delivered.
     */
    fun settledForLess(
        mediaId: String,
        target: TrackMatcher.Target,
        inFlight: Deferred<SourceStream?>? = null,
        playing: StreamFormat? = null,
        qualitySetting: String = "HIGH",
    ): Boolean {
        if (target.title.isBlank() || target.isVideo ||
            mediaId in refused ||
            !SourceResolver.canSubstituteForYouTube()
        ) {
            inFlight?.cancel()
            return false
        }
        pending[mediaId] = Pending(target, inFlight, playing, qualitySetting)
        Log.d(
            TAG,
            if (inFlight != null) {
                "'${target.title}' started on the fallback; its lookup is still running"
            } else {
                "below request for '${target.title}'; will look again during playback"
            },
        )
        return true
    }

    /** Whether [mediaId] is worth a second look — and hasn't already had one. */
    fun isPending(mediaId: String?) = mediaId != null && pending.containsKey(mediaId)

    /**
     * Tracks whose upgraded stream broke the playback it was supposed to
     * improve. Nothing here expires on a timer: the entry is worth exactly as
     * long as the player that broke on it, and [forgetLastSession] draws the
     * process boundary.
     */
    private val refused: MutableSet<String> = Collections.newSetFromMap(ConcurrentHashMap())

    /** Stops offering [mediaId] any further upgrades this session. */
    fun refuseUpgrades(mediaId: String) {
        refused += mediaId
        Log.d(TAG, "$mediaId broke on its upgrade; no more swaps for it")
    }

    /** Tracks that have already had their second look, whether it found anything or not. */
    private val asked: MutableSet<String> = Collections.newSetFromMap(ConcurrentHashMap())

    /** Whether [mediaId] is still worth *evaluating* for a second look. */
    fun couldStillUpgrade(mediaId: String): Boolean =
        mediaId !in asked && mediaId !in refused && !pending.containsKey(mediaId) &&
            SourceResolver.canSubstituteForYouTube()

    /**
     * Marks a track that is playing without ever having been resolved — the
     * cache-served case the resolving path never sees.
     *
     * @param playingMime what the *decoder* says about the bytes it is being
     *   fed, and it must be this track's — the one thing worth not doing here
     *   is hunting a better copy of a track already playing one.
     */
    fun adoptUnresolved(
        mediaId: String,
        target: TrackMatcher.Target,
        playingMime: String?,
        playing: StreamFormat?,
        qualitySetting: String = "HIGH",
    ): Boolean {
        if (!couldStillUpgrade(mediaId)) return false
        if (playingMime?.let { isLosslessMime(it) } == true) {
            asked += mediaId
            return false
        }
        if (target.title.isBlank()) {
            asked += mediaId
            return false
        }
        pending[mediaId] = Pending(target, inFlight = null, playing = playing, qualitySetting = qualitySetting)
        Log.d(
            TAG,
            "'${target.title}' is playing ${playing?.summary ?: "an unmeasured stream"} from cache " +
                "and was never resolved; looking for a better copy",
        )
        return true
    }

    /** Whether a mime type names a lossless codec. Gratia's own half of the check. */
    fun isLosslessMime(mime: String): Boolean = LOSSLESS_MIMES.any { mime.lowercase().contains(it) }

    private val LOSSLESS_MIMES = listOf("flac", "alac", "wav", "aiff")

    /**
     * Computes the duration to match against during an upgrade.
     *
     * When the decoder's runtime differs severely from the known catalogue
     * duration (e.g. a 3:29 video edit playing for a 5:02 album track), the
     * authoritative catalogue duration is preserved so the correct recording
     * can be found.
     */
    fun effectiveTargetDuration(expectedSec: Int?, playingSec: Int?): Int? =
        if (expectedSec != null && playingSec != null &&
            TrackMatcher.isSevereMismatch(expectedSec, playingSec)
        ) {
            expectedSec
        } else {
            playingSec ?: expectedSec
        }

    /**
     * Looks for a stream that actually satisfies the request, for a track
     * already playing.
     *
     * @param playingDurationSec the runtime the *decoder* reports, which is
     *   the one thing here that is measured rather than claimed. A candidate
     *   has to match it — see [SourceResolver.sameRecordingAs].
     * @return the better stream, or null if there isn't one, in which case
     *   this track is never asked about again.
     */
    suspend fun lookAgain(mediaId: String, playingDurationSec: Int?): SourceStream? {
        val waiting = pending[mediaId] ?: return null
        var found: SourceStream? = null
        var answered = false
        val expectedSec = waiting.target.durationSec
        val effectiveDurationSec = effectiveTargetDuration(expectedSec, playingDurationSec)
        if (expectedSec != null && playingDurationSec != null &&
            TrackMatcher.isSevereMismatch(expectedSec, playingDurationSec)
        ) {
            Log.w(
                TAG,
                "playing duration (${playingDurationSec}s) drifted severely from catalogue " +
                    "duration (${expectedSec}s); preserving catalogue duration for upgrade",
            )
        }
        return try {
            SourceResolver.upgradeFor(
                waiting.target.copy(durationSec = effectiveDurationSec),
                playing = waiting.playing,
                qualitySetting = waiting.qualitySetting,
            ).also {
                found = it
                if (it != null && needsLosslessFollowUp(it.format, waiting.qualitySetting)) {
                    followUps[mediaId] = waiting.copy(
                        target = waiting.target.copy(durationSec = effectiveDurationSec),
                        inFlight = null,
                        playing = it.format,
                    )
                }
                answered = true
            }
        } finally {
            if (answered) {
                // The question has now been asked, whatever the answer. Leaving
                // it pending would re-run the whole search on every pause and
                // resume; leaving it out of [asked] would let the same track be
                // offered again at the next progress sample.
                pending.remove(mediaId)
                asked += mediaId
            }
            // Otherwise the search was cancelled — the queue moved on while it
            // was still running — and *nothing was learned*. The track is left
            // exactly as it was found: still pending, still worth asking about
            // if the listener comes back to it.
        }
    }

    /** Abandons the second look for [mediaId] — the queue has moved on. */
    fun forget(mediaId: String) {
        pending.remove(mediaId)?.inFlight?.cancel()
        followUps.remove(mediaId)?.inFlight?.cancel()
        forced.remove(mediaId)
        shelved.remove(mediaId)
        handAsked -= mediaId
    }

    /** Re-opens the upgrade question for [mediaId] because the listener asked it. */
    fun askByHand(mediaId: String) {
        handAsked += mediaId
        asked -= mediaId
        refused -= mediaId
    }

    private val handAsked: MutableSet<String> = Collections.newSetFromMap(ConcurrentHashMap())

    /**
     * Abandons the second look for every track at once, because the player all
     * of it was about is gone. Clearing the *verdicts* when a new service
     * starts makes a warm restart behave like a cold one — a session's "no"
     * must not outlive the player that earned it.
     */
    fun forgetLastSession() {
        (pending.keys + followUps.keys + forced.keys + shelved.keys).forEach(::forget)
        asked.clear()
        refused.clear()
    }

    // ── Handing the stream to the player ────────────────────────────────

    /** Parks [stream] for [mediaId], to be picked up when the player is ready to swap. */
    fun force(mediaId: String, stream: SourceStream) {
        forced[mediaId] = stream
    }

    /** The parked upgraded stream for [mediaId], if any. */
    fun forcedStream(mediaId: String): SourceStream? = forced[mediaId]

    /** The parked stream has been consumed by the player — release it. */
    fun consumeForced(mediaId: String): SourceStream? = forced.remove(mediaId)

    /** Whether [mediaId]'s bytes are being *proved* rather than played right now. */
    fun isAuditioning(mediaId: String?): Boolean = mediaId != null && mediaId in auditioning

    private val auditioning: MutableSet<String> = Collections.newSetFromMap(ConcurrentHashMap())

    fun beginAudition(mediaId: String) {
        auditioning += mediaId
    }

    fun endAudition(mediaId: String) {
        auditioning -= mediaId
    }

    /**
     * Re-arms the lossless pass only after the lossy stream has actually
     * swapped in. A failed audition must leave the original search as the last
     * word rather than triggering a second interruption attempt.
     */
    fun continueAfterLossySwap(mediaId: String): Boolean {
        val next = followUps.remove(mediaId) ?: return false
        pending[mediaId] = next
        asked -= mediaId
        return true
    }

    private fun needsLosslessFollowUp(format: StreamFormat, qualitySetting: String): Boolean =
        qualitySetting.equals("LOSSLESS", ignoreCase = true) &&
            format.isLossless != true &&
            !format.isDolbyAtmos

    /**
     * The rendition tag a cache key for an upgraded stream must carry.
     *
     * A cache key has to identify both the recording *and* the rendition —
     * otherwise one source can corrupt the cached representation of another.
     * Gratia's player prefixes its cache keys with this tag when it swaps a
     * better rendition in.
     */
    fun cacheTag(mediaId: String): String? = forced[mediaId]?.let { stream ->
        buildString {
            append(stream.sourceConfigId ?: "src")
            append('-')
            append(stream.format.codec ?: "fmt")
            stream.format.kbps?.let { append('-').append(it) }
        }
    }
}

