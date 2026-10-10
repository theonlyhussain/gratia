package com.gratia.music.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the Daily Mix's history-driven model: recency decay, engagement
 * weighting, artist discovery and per-artist diversity. Pure JVM tests — the
 * scorer has no Android or database dependency by design.
 */
class DailyMixScoringTest {

    private data class Track(val id: String, val artist: String)

    private val now = 1_700_000_000_000L
    private val dayMs = 24L * 60L * 60L * 1000L

    private fun daysAgo(days: Long) = now - days * dayMs

    private fun play(
        id: String,
        artist: String,
        daysAgo: Long,
        listenedSeconds: Long = 200L,
        completed: Boolean = true,
    ) = DailyMixSignal(
        songId = id,
        artist = artist,
        playedAt = daysAgo(daysAgo),
        listenedSeconds = listenedSeconds,
        completed = completed,
    )

    private fun rank(
        candidates: List<Track>,
        signals: List<DailyMixSignal>,
        limit: Int = 10,
        excludeIds: Set<String> = emptySet(),
    ) = DailyMixScoring.rank(
        candidates = candidates,
        signals = signals,
        now = now,
        limit = limit,
        excludeIds = excludeIds,
        idOf = { it.id },
        artistOf = { it.artist },
    ).map { it.id }

    @Test
    fun `decay halves every half-life`() {
        assertEquals(1f, DailyMixScoring.decay(0.0), 1e-4f)
        assertEquals(0.5f, DailyMixScoring.decay(DailyMixScoring.HALF_LIFE_DAYS), 1e-4f)
        assertEquals(0.25f, DailyMixScoring.decay(DailyMixScoring.HALF_LIFE_DAYS * 2), 1e-4f)
        // Future timestamps cannot score above a play that is happening now.
        assertEquals(1f, DailyMixScoring.decay(-5.0), 1e-4f)
    }

    @Test
    fun `a recent play outranks a stale track with more lifetime plays`() {
        val candidates = listOf(Track("fresh", "a"), Track("stale", "b"))
        val signals = mutableListOf(play("fresh", "a", daysAgo = 0))
        repeat(5) { signals += play("stale", "b", daysAgo = 200) }

        val ranked = rank(candidates, signals)

        assertEquals("fresh", ranked.first())
    }

    @Test
    fun `a completed listen outweighs a partial listen of the same age`() {
        val candidates = listOf(Track("finished", "a"), Track("abandoned", "b"))
        val signals = listOf(
            play("finished", "a", daysAgo = 3, listenedSeconds = 240, completed = true),
            play("abandoned", "b", daysAgo = 3, listenedSeconds = 10, completed = false),
        )

        val ranked = rank(candidates, signals)

        assertEquals("finished", ranked.first())
    }

    @Test
    fun `recency decay favours the track played today over one played last month`() {
        val candidates = listOf(Track("today", "a"), Track("lastMonth", "b"))
        val signals = listOf(
            play("today", "a", daysAgo = 0),
            play("lastMonth", "b", daysAgo = 30),
        )

        assertEquals("today", rank(candidates, signals).first())
    }

    @Test
    fun `an unplayed track by a returning artist is surfaced as a discovery`() {
        val candidates = listOf(Track("newByLoved", "x"), Track("cold", "z"))
        // Two distinct plays by artist "x" — the candidate above has no plays of
        // its own, but its artist is clearly in rotation.
        val signals = listOf(
            play("x-old-1", "x", daysAgo = 1),
            play("x-old-2", "x", daysAgo = 5),
        )

        val ranked = rank(candidates, signals)

        assertEquals("newByLoved", ranked.first())
    }

    @Test
    fun `no artist may exceed the per-artist cap`() {
        val fans = (1..6).map { Track("a$it", "a") }
        val others = listOf(
            Track("b1", "b"), Track("c1", "c"), Track("d1", "d"),
        )
        val signals = fans.map { play(it.id, it.artist, daysAgo = 1) } +
            others.map { play(it.id, it.artist, daysAgo = 2) }

        val ranked = rank(fans + others, signals, limit = 10)

        assertEquals(DailyMixScoring.MAX_PER_ARTIST, ranked.count { it.startsWith("a") })
    }

    @Test
    fun `consecutive picks alternate artists when another artist is available`() {
        val candidates = listOf(
            Track("s1", "a"), Track("s2", "a"), Track("b1", "b"),
        )
        val signals = listOf(
            play("s1", "a", daysAgo = 0),
            play("s2", "a", daysAgo = 12),
            play("b1", "b", daysAgo = 30),
        )

        val ranked = rank(candidates, signals, limit = 3)

        assertEquals("s1", ranked[0])
        assertEquals("b1", ranked[1])
        assertEquals("s2", ranked[2])
    }

    @Test
    fun `excluded ids never appear in the mix`() {
        val candidates = listOf(Track("keep", "a"), Track("skip", "b"))
        val signals = listOf(play("skip", "b", daysAgo = 0), play("keep", "a", daysAgo = 1))

        val ranked = rank(candidates, signals, excludeIds = setOf("skip"))

        assertFalse(ranked.contains("skip"))
        assertTrue(ranked.contains("keep"))
    }

    @Test
    fun `the same inputs always produce the same mix`() {
        val candidates = (1..8).map { Track("t$it", "a${it % 3}") }
        val signals = candidates.mapIndexed { i, t -> play(t.id, t.artist, daysAgo = i.toLong()) }

        val first = rank(candidates, signals)
        val second = rank(candidates, signals)

        assertEquals(first, second)
    }

    @Test
    fun `a cold start still returns an exploration mix up to the limit`() {
        val candidates = (1..5).map { Track("t$it", "a$it") }

        val ranked = rank(candidates, emptyList(), limit = 3)

        assertEquals(3, ranked.size)
        // No relationship at all: the result is a stable, capped selection.
        assertEquals(rank(candidates, emptyList(), limit = 3), ranked)
    }

    @Test
    fun `empty candidates yield an empty mix`() {
        assertTrue(rank(emptyList(), emptyList()).isEmpty())
    }
}
