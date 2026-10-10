package com.gratia.music.player

import com.gratia.music.data.model.SongEntity

/**
 * The pure model of queue tiers — the rules [PlayerManager.appendAutoplaySuggestions]
 * and the queue screen implement, separated from the player so they can be
 * tested without a Media3 controller (the same move [RadioQueue] made).
 *
 * Three tiers, and only three:
 *
 *  **CURRENT** — the row playing now.
 *  **CONTEXT** — the collection the listener pressed play on: a Daily Mix, an
 *  album, a playlist, a search session. These rows exist because of where
 *  playback started.
 *  **AUTOPLAY** — rows a recommendation engine generated because the context
 *  was running out. Marked as such the moment they are appended.
 *
 * What is deliberately *not* a tier: downloaded, local, streamed. Storage is
 * a playback property — it decides where bytes come from, never where a row
 * sits in the queue. A downloaded track inside a Daily Mix is still a mix
 * track; the resolver may read it from disk, and its tier does not change.
 */
object QueueTiers {

    /**
     * How many of the rows at the head of [queue] belong to the context: the
     * run from the top of the queue to the first generated row. With no
     * generated rows present, the whole queue is context — that is exactly
     * what a freshly started Daily Mix is.
     */
    fun contextCount(queue: List<SongEntity>, autoplayIds: Set<String> = emptySet()): Int {
        val firstGenerated = queue.indexOfFirst { it.id in autoplayIds }
        return if (firstGenerated == -1) queue.size else firstGenerated
    }

    /**
     * Split [queue] into its context rows and its generated rows, using the
     * player's [autoplayIds] set as the mark of generation.
     */
    fun splitByTier(
        queue: List<SongEntity>,
        autoplayIds: Set<String>,
    ): Split = Split(
        context = queue.filter { it.id !in autoplayIds },
        autoplay = queue.filter { it.id in autoplayIds },
    )

    /**
     * The append discipline: from [candidates], keep only the rows that are
     * genuinely new — not queued, not already offered this session, and not
     * removed by the listener. This is the rule that keeps a refill batch
     * from reintroducing what the queue already holds or what the listener
     * discarded.
     */
    fun dedupForAppend(
        candidates: List<SongEntity>,
        queuedIds: Set<String>,
        playedIds: Set<String>,
        removedIds: Set<String>,
    ): List<SongEntity> = candidates.filter { song ->
        val id = song.id
        id.isNotBlank() && id !in queuedIds && id !in playedIds && id !in removedIds
    }

    /** The two visible regions of a queue, split by tier. */
    data class Split(
        val context: List<SongEntity>,
        val autoplay: List<SongEntity>,
    )
}
