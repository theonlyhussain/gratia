package com.gratia.music.player

/**
 * The arithmetic behind one track's listening session (stats spec 5–7).
 *
 * A listening event must be earned by audio the listener actually heard, so
 * the session is measured off the player's own position — never wall clock:
 *
 *  - **Buffering is not listening.** The position only advances while the
 *    player reports it is playing, so stalled time credits nothing.
 *  - **A seek is not listening.** A position jump larger than
 *    [MAX_CREDITED_TICK_MS] is the listener moving, not the track playing;
 *    the jump itself credits zero and only the audio after it counts.
 *  - **Time is written incrementally.** Each flush records only what was
 *    heard since the previous one, so a pause/complete sequence sums to the
 *    truth instead of re-writing the running total (the old accounting
 *    re-logged the cumulative figure on every pause, inflating every
 *    aggregate by the number of pauses taken).
 *
 * Kept as pure functions, like [ListeningRules], so the policy is pinned by
 * a unit test rather than by whatever the player happens to do at a call site.
 */
object ListeningSession {

    /**
     * The largest position jump between two 100 ms observations that is still
     * credited as listening. Anything bigger is a seek (or a player swap);
     * the gap is not audio anyone heard.
     */
    const val MAX_CREDITED_TICK_MS = 2_000L

    /** A flush that carries less new listening than this writes no event row. */
    const val MIN_EVENT_DELTA_MS = 1_000L

    /** How often unlogged listening is persisted so a force-close loses almost nothing. */
    const val PERIODIC_FLUSH_MS = 30_000L

    /**
     * How much of the distance between two position observations is real
     * listening: a normal 100 ms step in full, a seek or rewind not at all.
     */
    fun creditTick(lastPositionMs: Long, newPositionMs: Long): Long {
        val delta = newPositionMs - lastPositionMs
        return if (delta in 1..MAX_CREDITED_TICK_MS) delta else 0L
    }

    /**
     * Whether this flush earns an event row.
     *
     * A [completed] track always writes its row while anything at all was
     * heard — the completed row is what qualifies the play (spec 7) — but a
     * track that ended with zero credited listening (seeked straight to the
     * end) writes nothing: it was never listened to.
     */
    fun shouldWriteEvent(deltaMs: Long, totalMs: Long, completed: Boolean, skipped: Boolean): Boolean = when {
        completed -> totalMs > 0
        else -> deltaMs >= MIN_EVENT_DELTA_MS
    }

    /**
     * Whether the finished session counts as one play — the project's existing
     * rule ([ListeningRules]): heard to the end, or moved past after a
     * meaningful listen. A pause or an abandoned session never qualifies.
     */
    fun countsAsPlay(completed: Boolean, skipped: Boolean, totalListenedSeconds: Long): Boolean =
        ListeningRules.countsAsPlay(completed, skipped, totalListenedSeconds)
}
