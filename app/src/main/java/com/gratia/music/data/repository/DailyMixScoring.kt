package com.gratia.music.data.repository

import kotlin.math.pow

/**
 * One engagement with a recording, taken from the playback timeline.
 *
 * This is the Daily Mix's only input, and it is deliberately not a
 * `songs`-table aggregate: a row here means the track actually played, at a
 * known moment, for a known length of time. `songs.playCount` /
 * `totalListenTime` are lifetime counters — they can tell you a track is
 * popular, never that it is current, and a single counter cannot decay.
 *
 * @param songId the id that played (`ytm_<videoId>` for remote tracks).
 * @param artist the artist that played, as recorded at playback time.
 * @param playedAt wall-clock time of the play.
 * @param listenedSeconds how long the listener stayed, updated by the player.
 * @param completed whether the whole track was heard.
 */
data class DailyMixSignal(
    val songId: String,
    val artist: String,
    val playedAt: Long,
    val listenedSeconds: Long = 0L,
    val completed: Boolean = false,
)

/**
 * The Daily Mix ranking model.
 *
 * The mix is built from the documented failure mode the spec calls out: a
 * recommendation shelf that is really a lifetime top-ten. A track with a
 * thousand plays from two years ago is not what the listener wants back now,
 * and a lifetime aggregate has no way to know that. Every number below is
 * therefore a *decayed* sum over the timeline — an engagement's weight halves
 * every [HALF_LIFE_DAYS], so today moves the score far more than last month,
 * and last month far more than last year.
 *
 * Three signals, all from history:
 *  - **track affinity** — how often and how fully this exact recording has
 *    been played, decayed by age.
 *  - **artist affinity** — the same decayed sum rolled up to the artist, which
 *    lets a track the listener has *never* played earn a place because its
 *    artist keeps coming back (a real discovery, not a random one).
 *  - **freshness** — a small nudge for the most recent play of a track, so
 *    today's rotation leads the mix instead of tying with last week's.
 *
 * Ordering is interleaved by artist and capped at [MAX_PER_ARTIST] per artist,
 * so a mix cannot collapse into one catalogue's worth of a single artist.
 *
 * Pure: no Room, no Android, no clock of its own — `now` is passed in, which
 * is exactly what makes the decay testable.
 */
object DailyMixScoring {

    /** An engagement loses half its weight every this many days. */
    const val HALF_LIFE_DAYS = 14.0

    /** No artist may own more than this many slots in one mix. */
    const val MAX_PER_ARTIST = 3

    /** Weight of a recording's own decayed affinity in its score. */
    const val SONG_WEIGHT = 1.0f

    /** Weight of the artist's decayed affinity — a nudge, not the verdict. */
    const val ARTIST_WEIGHT = 0.35f

    /** Extra lift for an unplayed track by an artist the listener keeps playing. */
    const val DISCOVERY_BONUS = 0.6f

    /** How much of the freshness term reaches the final score. */
    private const val FRESHNESS_WEIGHT = 0.1f

    private const val DAY_MS = 24L * 60L * 60L * 1000L

    /** Assumed track length when judging how much of a play was heard. */
    private const val ASSUMED_TRACK_SECONDS = 180f

    /**
     * The decay curve: weight `1.0` at the moment of play, `0.5` at one
     * half-life, `0.25` at two, and so on. Never negative, never zero.
     */
    fun decay(ageDays: Double, halfLifeDays: Double = HALF_LIFE_DAYS): Float {
        if (ageDays <= 0.0) return 1f
        if (halfLifeDays <= 0.0) return 0f
        return 0.5.pow(ageDays / halfLifeDays).toFloat()
    }

    /**
     * Ranks [candidates] into a Daily Mix of at most [limit] tracks.
     *
     * @param candidates every playable track the device knows about.
     * @param signals one entry per recorded play, from the timeline.
     * @param now the reference "present" for the decay calculation.
     * @param excludeIds tracks already queued — the mix extends instead of repeating.
     * @param idOf reads a candidate's stable id.
     * @param artistOf reads a candidate's artist.
     */
    fun <T> rank(
        candidates: List<T>,
        signals: List<DailyMixSignal>,
        now: Long,
        limit: Int,
        excludeIds: Set<String> = emptySet(),
        idOf: (T) -> String,
        artistOf: (T) -> String,
    ): List<T> {
        if (limit <= 0) return emptyList()
        val pool = candidates.filter { idOf(it) !in excludeIds }
        if (pool.isEmpty()) return emptyList()

        // --- decayed history signals -------------------------------------
        val songAffinity = HashMap<String, Float>()
        val artistAffinity = HashMap<String, Float>()
        val lastPlayed = HashMap<String, Long>()
        val playedIds = HashSet<String>()

        for (signal in signals) {
            val idKey = normalize(signal.songId)
            val artistKey = normalize(signal.artist)
            if (idKey.isEmpty() && artistKey.isEmpty()) continue

            val ageDays = (now - signal.playedAt).coerceAtLeast(0L).toDouble() / DAY_MS
            val weight = decay(ageDays) * engagementOf(signal)

            playedIds.add(idKey)
            songAffinity[idKey] = (songAffinity[idKey] ?: 0f) + weight
            if (artistKey.isNotEmpty()) {
                artistAffinity[artistKey] = (artistAffinity[artistKey] ?: 0f) + weight
            }
            lastPlayed[idKey] = maxOf(lastPlayed[idKey] ?: Long.MIN_VALUE, signal.playedAt)
        }

        // --- score every candidate ---------------------------------------
        val dayKey = now / DAY_MS
        val scored = pool.map { song ->
            val idKey = normalize(idOf(song))
            val artistKey = normalize(artistOf(song))

            val trackAffinity = songAffinity[idKey] ?: 0f
            val artistScore = artistAffinity[artistKey] ?: 0f

            // A track with no plays of its own but a returning artist is the
            // mix's discovery slot; a track with no relationship at all gets
            // only the deterministic exploration jitter below.
            val discovery = if (idKey !in playedIds) DISCOVERY_BONUS * artistScore else 0f

            val freshness = lastPlayed[idKey]
                ?.let { decay((now - it).coerceAtLeast(0L).toDouble() / DAY_MS) }
                ?: 0f

            val total = SONG_WEIGHT * trackAffinity +
                ARTIST_WEIGHT * artistScore +
                discovery +
                FRESHNESS_WEIGHT * freshness +
                explorationJitter(dayKey, idKey)

            Scored(song, total)
        }.sortedWith(
            // Stable: equal scores keep the candidate list's own order.
            compareByDescending<Scored<T>> { it.score }.thenBy { idOf(it.song) }
        )

        // --- interleave by artist, capped per artist ----------------------
        val artistCounts = HashMap<String, Int>()
        val selectedIds = HashSet<String>()
        val out = ArrayList<T>(limit)
        var previousArtist: String? = null

        while (out.size < limit) {
            var pick: Scored<T>? = null
            var fallback: Scored<T>? = null

            for (candidate in scored) {
                val idKey = normalize(idOf(candidate.song))
                if (idKey in selectedIds) continue

                val artistKey = normalize(artistOf(candidate.song))
                if ((artistCounts[artistKey] ?: 0) >= MAX_PER_ARTIST) continue

                if (artistKey == previousArtist) {
                    // Hold a same-artist track in reserve; only use it if no
                    // other artist can fill this slot.
                    if (fallback == null) fallback = candidate
                    continue
                }
                pick = candidate
                break
            }

            val chosen = pick ?: fallback ?: break
            val idKey = normalize(idOf(chosen.song))
            val artistKey = normalize(artistOf(chosen.song))

            selectedIds.add(idKey)
            artistCounts[artistKey] = (artistCounts[artistKey] ?: 0) + 1
            previousArtist = artistKey
            out.add(chosen.song)
        }

        return out
    }

    /**
     * How much a single play is worth before decay: a completed listen counts
     * full, a partial one is scaled by how much was actually heard, floored so
     * a deliberate but short listen still counts as a signal.
     */
    private fun engagementOf(signal: DailyMixSignal): Float {
        if (signal.completed) return 1f
        val fraction = signal.listenedSeconds.coerceAtLeast(0L).toFloat() / ASSUMED_TRACK_SECONDS
        return fraction.coerceIn(0.1f, 0.9f)
    }

    /**
     * A small, deterministic, per-day jitter, so the mix breathes day to day
     * without ever being random — the same day and track always score the
     * same, which keeps the ranking unit-testable.
     */
    private fun explorationJitter(dayKey: Long, idKey: String): Float {
        val hash = (dayKey.toString() + idKey).hashCode() and 0x7fffffff
        return (hash % 100) / 1000f
    }

    /** Artist and id comparisons ignore case and surrounding whitespace. */
    private fun normalize(value: String): String = value.trim().lowercase()

    private data class Scored<T>(val song: T, val score: Float)
}
