/*
 * Copyright (C) 2026 Kushagra Singh / Gratia contributors
 * Modified for Gratia by Hussain / Gratia contributors, 2026
 * Licensed under the GNU General Public License v3.0
 *
 * Source: Gratia data/sources/SourceResolver.kt (trimmed to the parts
 * Gratia's playback architecture can serve today).
 * https://github.com/kushagrasinghx/Gratia (GPLv3)
 *
 * Modifications for Gratia: the download path, module ranking and
 * SourceRegistry config URIs are Gratia-side and are not carried — Gratia's
 * base source is YouTube through its own StreamResolver, and this object
 * answers the two questions Gratia's player asks: "can anything outrank
 * YouTube?" and "is there a copy of this recording that beats what is
 * playing?". requestForNow reads Gratia's SettingsDataStore quality setting
 * instead of the original AppSettings.
 */
package com.gratia.music.provider.sources

import android.util.Log
import com.gratia.music.data.SettingsDataStore
import com.gratia.music.provider.RemoteTrack
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.selects.select

/**
 * Turns a queued recording into an openable stream from a source better than
 * the one already serving it — when such a source exists, and only then.
 *
 * Two things happen here that don't happen in any single [MusicSource]:
 *
 *  1. **The quality question is answered once**, from the connection in hand
 *     and the user's ceiling for it — see [requestForNow]. Sources are told
 *     what to serve; they don't each re-derive it.
 *
 *  2. **The order is applied.** All enabled sources are asked at once and the
 *     best answer wins; a source that fails gets stepped over rather than
 *     failing the track.
 *
 * Whether another source *has* the same recording is [TrackMatcher]'s question,
 * not this one's.
 */
object SourceResolver {

    private const val TAG = "GratiaSource"

    /**
     * What to ask a source for, right now.
     *
     * Gratia's quality setting is LOW / NORMAL / HIGH; LOSSLESS is offered by
     * the settings UI only when a lossless-capable source is actually enabled,
     * so no caller can ask a lossless source that does not exist.
     */
    fun requestForNow(qualitySetting: String): StreamRequest = when {
        qualitySetting.equals("LOSSLESS", ignoreCase = true) &&
            SourceRegistry.activeForPlayback().any { it.canServeLossless } -> StreamRequest.Lossless
        qualitySetting.equals("HIGH", ignoreCase = true) -> StreamRequest.Best
        qualitySetting.equals("NORMAL", ignoreCase = true) -> StreamRequest.Capped(160)
        else -> StreamRequest.Capped(96)
    }

    /**
     * A stream that genuinely beats [playing], for a track that is already
     * playing on something worse — or null if there isn't one.
     *
     * Every enabled source is asked **at once**, raced rather than queued: the
     * sources differ in speed by two orders of magnitude (JioSaavn answers in
     * ~0.4s; a slow module needed 13.5s in the original measurements), and asked
     * in order the slow one holds up the fast one. Raced, the swap happens
     * inside a second.
     *
     * [target] must carry the runtime of the track *actually playing*. Swapping
     * the audio under a listener is only defensible when the replacement is the
     * same recording, and length is the check that a title cannot fake.
     *
     * @param playing what the listener is hearing now, so a lossy candidate
     *   can be judged against it rather than against the request. Null means
     *   unknown, and an unknown floor is treated as one nothing lossy clears:
     *   a swap that might be a downgrade is worse than no swap at all.
     */
    suspend fun upgradeFor(
        target: TrackMatcher.Target,
        playing: StreamFormat? = null,
        qualitySetting: String = "HIGH",
    ): SourceStream? {
        if (target.title.isBlank() || target.durationSec == null || target.isVideo) return null
        val sources = SourceRegistry.activeForPlayback()
        if (sources.isEmpty()) return null
        val request = requestForNow(qualitySetting)
        val (source, chosen) = bestAcross(sources, target, request, waitForAll = true, strictLength = true) {
                candidate,
                stream,
            ->
            worthSwapping(stream.format, playing).also { worth ->
                // Named rather than skipped silently — a silent skip reads in
                // the log exactly like a source having nothing.
                if (!worth) {
                    Log.d(
                        TAG,
                        "${candidate.displayName}'s ${stream.format.summary} isn't worth swapping " +
                            "'${target.title}' off ${playing?.summary ?: "an unmeasured stream"}",
                    )
                }
            }
        } ?: return null
        Log.d(TAG, "upgrade found: '${target.title}' at ${chosen.format.summary} from ${source.displayName}")
        return chosen
    }

    /**
     * Whether a lossy [candidate] is worth the break in the audio that
     * swapping to it costs.
     *
     * Lossless always is. A lossy candidate has to clear [UPGRADE_MIN_GAIN_KBPS]
     * over what is already playing, which is deliberately a wide gap rather
     * than a strict improvement. Bitrate compares poorly across codecs — Opus
     * at 160kbps and AAC at 256kbps are much the same thing to listen to — so
     * a margin narrow enough to be codec-sensitive would be a margin that buys
     * a seam in the audio for nothing. 160 to 320 clears it; 128 to 192 does
     * not.
     */
    internal fun worthSwapping(candidate: StreamFormat, playing: StreamFormat?): Boolean {
        if (candidate.isLossless == true || candidate.isDolbyAtmos) return true
        val gain = (candidate.kbps ?: return false) - (playing?.kbps ?: return false)
        return gain >= UPGRADE_MIN_GAIN_KBPS
    }

    /**
     * Whether two runtimes are close enough to be the same recording, for a
     * swap into a track that is already playing.
     *
     * Either side being unknown is a no. An unverifiable length is not a
     * length that agrees, and the cost of being wrong here is a listener's
     * song replaced mid-play by a different cut of it.
     */
    fun sameRecordingAs(candidateSec: Int?, playingSec: Int?): Boolean {
        if (candidateSec == null || playingSec == null) return false
        return kotlin.math.abs(candidateSec - playingSec) <= UPGRADE_DRIFT_SEC
    }

    /**
     * Whether anything outranks YouTube right now — i.e. whether a YouTube
     * track is worth offering around before it is resolved.
     *
     * Answerable from the source list alone, without a search, which is what
     * lets the player decide cheaply whether an upgrade pass is worth starting.
     */
    fun canSubstituteForYouTube(): Boolean = SourceRegistry.activeForPlayback().isNotEmpty()

    /**
     * The best stream the configured [sources] can serve for [target] — **all
     * of them asked at once** — or null if none of them has the recording.
     *
     * ### Why they race rather than queue
     *
     * Asking them in rank order is the obvious reading of an ordered list, and
     * it is wrong, because the sources differ in speed by nearly two orders of
     * magnitude. Queued behind a slow module, a fast source's answer arrives
     * after the listener has already been left waiting; raced, it arrives in
     * time to matter.
     *
     * ### What rank still decides
     *
     * Rank breaks ties between answers that arrive together, since each sweep
     * folds in everything that has already crossed the line and picks the best
     * of them with [isBetter]. What it no longer does is let a slow favourite
     * hold up a fast alternative.
     *
     * On the latency-critical path, sources still running when an answer is
     * taken are cancelled. The background-upgrade path passes [waitForAll]:
     * it already chose to wait for every source's patient search window, so it
     * must also collect the resulting streams before choosing.
     *
     * @return the winning source alongside its stream, so callers can name it
     *   in a log line without searching the list again.
     */
    internal suspend fun bestAcross(
        sources: List<MusicSource>,
        target: TrackMatcher.Target,
        request: StreamRequest,
        waitForAll: Boolean = false,
        strictLength: Boolean = false,
        accept: (MusicSource, SourceStream) -> Boolean = { _, _ -> true },
    ): Pair<MusicSource, SourceStream>? = coroutineScope {
        val running: MutableList<Deferred<Pair<MusicSource, SourceStream?>>> = sources
            .map { source ->
                async {
                    source to matchAndStream(source, target, request, waitForAll, strictLength)
                }
            }
            .toMutableList()
        var best: Pair<MusicSource, SourceStream>? = null
        try {
            while (running.isNotEmpty()) {
                val first = select {
                    running.forEach { candidate -> candidate.onAwait { candidate } }
                }
                // Anything that crossed the line while that one was being waited
                // on is already sitting there. Folding those in costs no time at
                // all and is what lets rank break a tie between two sources that
                // both answered quickly.
                val ready = listOf(first) + running.filter { it !== first && it.isCompleted }
                running -= ready.toSet()
                for (done in ready) {
                    val (source, stream) = done.await()
                    if (stream == null) continue
                    if (!accept(source, stream)) continue
                    if (isBetter(stream.format, best?.second?.format)) best = source to stream
                }
                // Playback needs the first usable answer so sound can start.
                // An upgrade is different: it runs while audio is already
                // playing, and its caller explicitly requested every source's
                // patient result.
                if (best != null && !waitForAll) break
            }
        } finally {
            running.forEach { it.cancel() }
        }
        best
    }

    /**
     * Searches [source] for the recording in [target] and streams it if one of
     * the answers really is that recording — see [TrackMatcher].
     *
     * Each query the matcher offers is tried in turn, because the first one
     * failing is usually the catalogue disagreeing about how a track is
     * *filed*, not about whether it holds it.
     *
     * @param strictLength requires a candidate's runtime to agree with
     *   [target]'s to within [UPGRADE_DRIFT_SEC]. Only meaningful when the
     *   target *has* a runtime: [TrackMatcher.withinSeconds] answers false for
     *   every candidate against a null one.
     */
    private suspend fun matchAndStream(
        source: MusicSource,
        target: TrackMatcher.Target,
        request: StreamRequest,
        waitForAll: Boolean = false,
        strictLength: Boolean = false,
    ): SourceStream? {
        for (query in TrackMatcher.queries(target)) {
            val candidates = attempt(source) {
                source.search(query, limit = MATCH_CANDIDATES, waitForAll = waitForAll)
            } ?: return null
            var matches = TrackMatcher.ranked(candidates, target)
            // JioSaavn can return different audio under the same title and
            // artist on different releases. With no album on the requested
            // track there is no honest way to choose between those rows;
            // duration is not enough when the wrong recording is only a second
            // away. Treat it as this source missing and retain the known-good
            // fallback.
            if (source.kind == SourceKind.JIOSAAVN &&
                TrackMatcher.hasConflictingAlbums(matches, target)
            ) {
                val canonical = TrackMatcher.uniquelyMostCreditedCloseMatch(matches, target)
                if (canonical == null) {
                    Log.w(
                        TAG,
                        "${source.displayName} returned conflicting albums for '${target.title}'; refusing to guess",
                    )
                    continue
                }
                Log.d(
                    TAG,
                    "${source.displayName} resolved conflicting albums for '${target.title}' " +
                        "using the uniquely fullest credit: '${canonical.artistDisplay}'",
                )
                matches = listOf(canonical)
            }
            // The extra bar for standing in for one specific recording: the
            // replacement has to be the same *length*, to the second or so.
            if (strictLength) {
                matches = matches.filter { TrackMatcher.withinSeconds(it, target, UPGRADE_DRIFT_SEC) }
            }
            if (matches.isEmpty()) continue
            return streamBest(source, matches, target, request)
        }
        return null
    }

    /**
     * Opens the best of [matches] that can actually serve [request].
     *
     * Rows that advertise the tier asked for go first, and what comes back is
     * checked against what was asked for — a source that cannot serve lossless
     * does not always say so. The under-quality stream is kept rather than
     * dropped: if nothing better exists anywhere, playing the MP3 is still
     * better than skipping the track. It is a floor, not a first choice.
     */
    private suspend fun streamBest(
        source: MusicSource,
        matches: List<RemoteTrack>,
        target: TrackMatcher.Target,
        request: StreamRequest,
    ): SourceStream? {
        val wantsLossless = request is StreamRequest.Lossless
        var settleFor: SourceStream? = null
        for (match in matches.take(STREAM_ATTEMPTS)) {
            val trackId = SourceTrackKey.parse(match.videoId)?.second ?: match.videoId
            val opened = attempt(source) { source.stream(trackId, request) } ?: continue
            // The row this URL came from knows how long the recording is; the
            // URL itself doesn't. Carried along so a caller swapping this into
            // a track already playing can check it.
            val stream = opened.copy(
                durationSec = TrackMatcher.secondsOf(match.durationText),
                sourceConfigId = source.configId,
            )
            val served = stream.format
            if (!wantsLossless || served.isLossless == true || served.isDolbyAtmos || served.statesNothingLossy) {
                Log.d(
                    TAG,
                    "${source.displayName} matched '${match.title}' by '${match.artistDisplay}' " +
                        "id='${match.videoId}' album='${match.album?.name ?: "?"}' " +
                        "duration=${match.durationText ?: "?"} explicit=${match.isExplicit} " +
                        "→ ${served.summary}",
                )
                return stream
            }
            Log.d(
                TAG,
                "${source.displayName} offered ${served.summary} for '${match.title}'; looking further",
            )
            // The floor is the *best* of what was refused, not the first of it.
            settleFor = betterOf(settleFor, stream.copy(belowRequest = true))
        }
        return settleFor
    }

    /**
     * Whether [candidate] is a better rendition than [current], by codec first
     * and bitrate second. A null [current] is beaten by anything.
     *
     * Note that this is *not* [worthSwapping]. This asks which of two streams
     * is better; that one asks whether the difference is worth a break in the
     * audio.
     */
    internal fun isBetter(candidate: StreamFormat, current: StreamFormat?): Boolean {
        if (current == null) return true
        if (candidate.isLossless != current.isLossless) return candidate.isLossless == true
        if (candidate.isDolbyAtmos != current.isDolbyAtmos) return candidate.isDolbyAtmos
        return (candidate.kbps ?: 0) > (current.kbps ?: 0)
    }

    /** The higher-quality of two streams — see [isBetter]. */
    private fun betterOf(current: SourceStream?, candidate: SourceStream): SourceStream =
        if (current == null || isBetter(candidate.format, current.format)) candidate else current

    /**
     * Whether a format has said nothing that rules lossless out.
     *
     * Unknown is not the same as lossy. A stated bitrate is different: nothing
     * states a bitrate for a FLAC.
     */
    private val StreamFormat.statesNothingLossy: Boolean
        get() = isLossless == null && kbps == null

    /**
     * Runs [block], turning any failure into null and a log line.
     *
     * Every call into a source is a call to somebody else's server, and a
     * source that throws must cost the *source* its turn, not the track its
     * playback. Cancellation is re-thrown.
     */
    private suspend fun <T> attempt(source: MusicSource, block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "${source.displayName} failed: ${e.javaClass.simpleName}: ${e.message}")
        null
    }

    /** How many answers per query are worth weighing. */
    private const val MATCH_CANDIDATES = 15

    /** How many of the matching rows are worth actually opening. */
    private const val STREAM_ATTEMPTS = 3

    /**
     * How far a replacement's runtime may sit from the playing track's before
     * it stops being the same recording. Two seconds allows for a service
     * rounding a runtime differently and nothing else.
     */
    private const val UPGRADE_DRIFT_SEC = 2

    /**
     * How many kbps a lossy stream has to gain before it earns a seam in the
     * audio — see [worthSwapping]. Sized off the two rates this actually
     * decides between: YouTube's Opus, which lands around 160, and a lossy
     * source's 320 tier. Anything much smaller would start firing on
     * differences no one can hear.
     */
    private const val UPGRADE_MIN_GAIN_KBPS = 96
}

