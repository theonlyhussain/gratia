package com.gratia.music.ui.screens

import com.gratia.music.data.model.SongEntity

/**
 * One candidate for the Top Picks shelf, with where it came from.
 *
 * @param song the catalogue track.
 * @param seedIndex which seed artist produced it (0 = strongest seed).
 * @param rank its position within that seed's own search results (0 = best).
 */
internal data class TopPickCandidate(val song: SongEntity, val seedIndex: Int, val rank: Int)

/**
 * The Top Picks ranking, centralized and documented (spec 8–9).
 *
 * The shelf is built from catalogue searches seeded by the listener's own
 * artists, so the raw answer is ordered by *seed strength* — the first seed's
 * six results all arrive before the second seed's first. Played back in that
 * order the shelf is a wall of one artist, which is the exact "top artist,
 * top artist, top artist" failure the spec forbids.
 *
 * The interleave below is a strict round-robin over the seeds instead: the
 * best pick of seed 0, then the best of seed 1, then the best of seed 2,
 * then the second pick of seed 0, and so on. Every artist the listener has
 * actually been playing gets its strongest candidates near the front of the
 * shelf, and no one seed can fill it alone. Within one seed the catalogue's
 * own relevance order is preserved untouched.
 *
 * Pure and unit-testable: no player, no network, no database.
 */
internal object TopPicksDiversity {

    /**
     * Ranks candidates into the display order for the shelf.
     *
     * @param candidates one batch per seed artist, already in the catalogue's
     *   own relevance order.
     * @param limit the shelf's final length.
     */
    fun rank(candidates: List<List<SongEntity>>, limit: Int = 10): List<SongEntity> {
        val nonEmpty = candidates.filter { it.isNotEmpty() }
        if (nonEmpty.isEmpty()) return emptyList()

        val seen = HashSet<String>()
        val out = ArrayList<SongEntity>(limit)
        var round = 0

        // A round-robin pass walks one candidate from each seed, in seed
        // order, before moving to the next round. Seeds with no candidate
        // left in this round are simply skipped.
        while (out.size < limit) {
            var anyThisRound = false
            for ((seedIndex, batch) in nonEmpty.withIndex()) {
                if (out.size >= limit) break
                val song = batch.getOrNull(round) ?: continue
                anyThisRound = true
                if (seen.add(song.id)) out.add(song)
            }
            if (!anyThisRound) break
            round++
        }
        return out
    }
}
