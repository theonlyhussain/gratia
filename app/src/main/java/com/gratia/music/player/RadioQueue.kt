package com.gratia.music.player

import com.gratia.music.data.model.SongEntity

/**
 * The rules of the AutoPlay continuation, separated from the machinery.
 *
 * Infinity Play is not "play the next queue item". It is a *radio*: the track
 * the listener is hearing is handed to the remote catalogue, which answers with
 * what it thinks belongs next, and that answer is filtered into suggestions and
 * placed in the queue according to three rules, in this order:
 *
 *  1. What the listener explicitly queued wins. "Play next" and "Add to queue"
 *     are instructions, and no suggestion may ever jump in front of one.
 *  2. Otherwise a suggestion follows the track being heard. A list a song was
 *     played *from* — search results, a shelf, an artist page — is a context,
 *     not a queue: tapping one result and switching Infinity Play on means the
 *     radio continues, not that the other nineteen results were requested.
 *  3. Everything already queued or already suggested is never suggested again.
 *
 * The queue therefore reads:
 *
 * ```
 *   … context before …            history, so Previous still works
 *   current
 *   … explicit tracks …           rule 1
 *   … AutoPlay suggestions …      rule 2
 *   … rest of the context …       unreached while the radio keeps answering
 * ```
 *
 * Every decision here is a pure function so it can be tested without a player,
 * a network or a database. The failure this replaces — Infinity Play behaving
 * like "next track" and never reaching the radio — was a logic failure, and
 * logic is what tests should pin down.
 */
object RadioQueue {

    /**
     * How few remaining suggestions trigger the next batch.
     *
     * Not zero on purpose: a refill started when the queue is empty starts it
     * *after* the last suggestion has already ended, which is heard as silence
     * between songs. Fetching while three are still ahead pays the network cost
     * behind the music instead of in front of it.
     */
    const val REFILL_THRESHOLD = 3

    /**
     * How many suggestions to ask for at a time. The radio's answer is usually
     * longer than this; taking a slice keeps the queue scannable and leaves the
     * rest of the answer for the next refill.
     */
    const val BATCH_TARGET = 12

    /**
     * A ceiling on generated suggestions held at once. AutoPlay is meant to run
     * indefinitely, but an unbounded queue would grow for as long as the app is
     * open — memory nobody looks at, and a queue nobody can scroll.
     */
    const val MAX_PENDING = 40

    /** Whether the remaining suggestion count warrants a refill. */
    fun needsRefill(remaining: Int): Boolean = remaining <= REFILL_THRESHOLD

    /**
     * Turns a radio answer into the suggestions worth queueing.
     *
     * @param seedVideoId the track the radio was asked about. The answer
     *   conventionally opens with the seed itself, and replaying the song that
     *   is already playing is not a suggestion.
     * @param candidates the catalogue's answer, in its own order — relevance
     *   order, so it is preserved rather than shuffled.
     * @param alreadyKnown every identity that must not be suggested: the user's
     *   queue, the current track, anything generated this session, and anything
     *   played recently.
     */
    fun filterSuggestions(
        seedVideoId: String?,
        candidates: List<SongEntity>,
        alreadyKnown: Set<String>,
    ): List<SongEntity> {
        val out = ArrayList<SongEntity>(candidates.size)
        val seen = HashSet<String>(candidates.size)

        for (candidate in candidates) {
            // AutoPlay's suggestions are remote by definition. A local file has
            // no video id to continue a radio from, so it can never be part of
            // one — inserting one would end the chain at the next track.
            if (candidate.storageProvider == "local") continue

            val videoId = candidate.providerTrackId?.takeIf { it.isNotBlank() }
                ?: candidate.id.removePrefix("ytm_").takeIf { it.isNotBlank() }
                ?: continue

            if (videoId == seedVideoId) continue
            if (candidate.id in alreadyKnown) continue
            if (videoId in alreadyKnown) continue
            if (!seen.add(candidate.id)) continue

            out.add(candidate)
            if (out.size >= BATCH_TARGET) break
        }

        return out
    }

    /**
     * The radio seed for a track: its video id, or null when the track cannot
     * seed a radio at all (local playback, or a malformed remote row).
     */
    fun seedVideoIdOf(song: SongEntity?): String? {
        song ?: return null
        if (song.storageProvider == "local") return null
        return song.providerTrackId?.takeIf { it.isNotBlank() }
            ?: song.id.removePrefix("ytm_").takeIf { it.isNotBlank() && it != song.id }
    }

    /**
     * Where the generated suggestions begin in [queue], or `queue.size` when
     * there are none.
     *
     * Derived from identity rather than kept as an index, because the queue is
     * reordered, has rows removed and has songs inserted into it. An index held
     * in a field would survive the first drag and be wrong after it; a boundary
     * computed from the queue it describes cannot drift.
     */
    fun boundary(queue: List<SongEntity>, generatedIds: Set<String>): Int {
        val first = queue.indexOfFirst { it.id in generatedIds }
        return if (first < 0) queue.size else first
    }

    /** How many generated suggestions are still ahead of the listener. */
    fun remainingSuggestions(
        queue: List<SongEntity>,
        currentIndex: Int,
        generatedIds: Set<String>,
    ): Int {
        if (currentIndex < 0 || currentIndex >= queue.size) return 0
        var count = 0
        for (i in (currentIndex + 1) until queue.size) {
            if (queue[i].id in generatedIds) count++
        }
        return count
    }

    /**
     * Where a fresh batch of suggestions belongs.
     *
     * After the current track, and after every track the listener explicitly
     * queued — never in front of either. When suggestions already exist this is
     * simply the end of them, so successive batches stay one contiguous run.
     */
    fun insertionIndex(
        queue: List<SongEntity>,
        currentIndex: Int,
        generatedIds: Set<String>,
        explicitIds: Set<String>,
    ): Int {
        val lastGenerated = queue.indexOfLast { it.id in generatedIds }
        if (lastGenerated >= 0) return lastGenerated + 1

        val afterCurrent = (currentIndex + 1).coerceIn(0, queue.size)
        val lastExplicit = queue.indexOfLast { it.id in explicitIds }
        return if (lastExplicit >= afterCurrent) lastExplicit + 1 else afterCurrent
    }

    /** Whether a queue position is still inside the listener's own tracks. */
    fun isInsideUserQueue(queue: List<SongEntity>, currentIndex: Int, generatedIds: Set<String>): Boolean {
        if (currentIndex < 0) return false
        return currentIndex < boundary(queue, generatedIds)
    }

    /**
     * The upcoming entries split the way the queue screen shows them: what the
     * listener queued first, then what the radio generated (spec 13/68).
     *
     * A suggestion can appear in the middle of the user's run only if the
     * listener dragged it there themselves — that is their queue and their
     * order, so it stays where they put it. The sections therefore follow the
     * queue's own arrangement rather than assuming two clean blocks.
     */
    fun upcomingByTier(
        queue: List<SongEntity>,
        currentIndex: Int,
        generatedIds: Set<String>,
    ): Pair<List<SongEntity>, List<SongEntity>> {
        if (currentIndex < 0 || currentIndex >= queue.size) return emptyList<SongEntity>() to emptyList()
        val user = ArrayList<SongEntity>()
        val auto = ArrayList<SongEntity>()
        for (i in (currentIndex + 1) until queue.size) {
            if (queue[i].id in generatedIds) auto.add(queue[i]) else user.add(queue[i])
        }
        return user to auto
    }
}
