package com.gratia.music.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the listening-session policy: what counts as heard time, what a flush
 * must write, and what qualifies as a play (stats spec 5–7).
 */
class ListeningSessionTest {

    // ── Position credit ─────────────────────────────────────────────────

    @Test
    fun `a normal step between observations is credited in full`() {
        assertEquals(100L, ListeningSession.creditTick(120_000L, 120_100L))
    }

    @Test
    fun `a seek forward is not credited`() {
        assertEquals(0L, ListeningSession.creditTick(10_000L, 60_000L))
    }

    @Test
    fun `a rewind is not credited`() {
        assertEquals(0L, ListeningSession.creditTick(60_000L, 10_000L))
    }

    @Test
    fun `a frozen position — buffering — credits nothing`() {
        assertEquals(0L, ListeningSession.creditTick(45_000L, 45_000L))
    }

    @Test
    fun `the largest credited jump is the threshold itself`() {
        assertEquals(ListeningSession.MAX_CREDITED_TICK_MS,
            ListeningSession.creditTick(0L, ListeningSession.MAX_CREDITED_TICK_MS))
        assertEquals(0L,
            ListeningSession.creditTick(0L, ListeningSession.MAX_CREDITED_TICK_MS + 1))
    }

    // ── Event writing ───────────────────────────────────────────────────

    @Test
    fun `a completed track writes its row when anything at all was heard`() {
        assertTrue(ListeningSession.shouldWriteEvent(deltaMs = 0L, totalMs = 5_000L, completed = true, skipped = false))
    }

    @Test
    fun `a track that ended with zero listening writes nothing — no false stats`() {
        assertFalse(ListeningSession.shouldWriteEvent(deltaMs = 0L, totalMs = 0L, completed = true, skipped = false))
    }

    @Test
    fun `a pause flush under a second writes no row`() {
        assertFalse(ListeningSession.shouldWriteEvent(deltaMs = 900L, totalMs = 900L, completed = false, skipped = false))
        assertTrue(ListeningSession.shouldWriteEvent(deltaMs = 1_000L, totalMs = 1_000L, completed = false, skipped = false))
    }

    @Test
    fun `an immediate skip writes no row`() {
        assertFalse(ListeningSession.shouldWriteEvent(deltaMs = 0L, totalMs = 0L, completed = false, skipped = true))
    }

    @Test
    fun `a skip after real listening writes its row`() {
        assertTrue(ListeningSession.shouldWriteEvent(deltaMs = 12_000L, totalMs = 12_000L, completed = false, skipped = true))
    }

    // ── Play qualification ──────────────────────────────────────────────

    @Test
    fun `completing a track is one play however short`() {
        assertTrue(ListeningSession.countsAsPlay(completed = true, skipped = false, totalListenedSeconds = 2L))
    }

    @Test
    fun `skipping after the threshold is one play`() {
        assertTrue(ListeningSession.countsAsPlay(completed = false, skipped = true, totalListenedSeconds = ListeningRules.PLAY_COUNT_MIN_SECONDS))
        assertFalse(ListeningSession.countsAsPlay(completed = false, skipped = true, totalListenedSeconds = ListeningRules.PLAY_COUNT_MIN_SECONDS - 1))
    }

    @Test
    fun `a pause or abandoned session is never a play`() {
        assertFalse(ListeningSession.countsAsPlay(completed = false, skipped = false, totalListenedSeconds = 600L))
    }

    // ── Incremental sums ────────────────────────────────────────────────

    @Test
    fun `incremental flushes sum to the truth rather than re-logging totals`() {
        // Simulate: 60s heard, pause-flushed; 30s more heard, then complete.
        // The old accounting wrote 60 then 90 — the deltas write 60 then 30.
        var loggedMs = 0L
        var totalMs = 60_000L
        fun flush(): Long {
            val delta = totalMs - loggedMs
            loggedMs = totalMs
            return delta
        }
        assertEquals(60_000L, flush())
        totalMs = 90_000L
        assertEquals(30_000L, flush())
        assertEquals(totalMs, loggedMs)
    }
}
