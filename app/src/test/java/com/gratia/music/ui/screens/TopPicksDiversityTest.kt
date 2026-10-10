package com.gratia.music.ui.screens

import com.gratia.music.data.model.SongEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the Top Picks interleave (spec 8–9): three seed artists must not be
 * allowed to collapse into a wall of the first one.
 */
class TopPicksDiversityTest {

    private fun song(id: String) = SongEntity(id = id, title = "t$id", artist = "a$id")

    @Test
    fun `round robins across seed artists instead of stacking the first seed`() {
        val batches = listOf(
            listOf("s1a", "s1b", "s1c", "s1d").map(::song),
            listOf("s2a", "s2b", "s2c", "s2d").map(::song),
            listOf("s3a", "s3b", "s3c", "s3d").map(::song),
        )

        val ranked = TopPicksDiversity.rank(batches, limit = 10)

        // First three are one from each seed, in seed order.
        assertEquals("s1a", ranked[0].id)
        assertEquals("s2a", ranked[1].id)
        assertEquals("s3a", ranked[2].id)
        // Second round follows before any seed's third pick.
        assertEquals("s1b", ranked[3].id)
        assertEquals("s2b", ranked[4].id)
        // No seed owns a run of more than one position.
        ranked.zipWithNext().forEach { (a, b) ->
            assertTrue(a.id.take(2) != b.id.take(2))
        }
    }

    @Test
    fun `single seed still fills the shelf in relevance order`() {
        val batches = listOf(
            listOf("only1", "only2", "only3", "only4").map(::song),
        )
        val ranked = TopPicksDiversity.rank(batches, limit = 10)
        assertEquals(listOf("only1", "only2", "only3", "only4"), ranked.map { it.id })
    }

    @Test
    fun `skips exhausted seeds without stalling the round`() {
        val batches = listOf(
            listOf("a1").map(::song),
            listOf("b1", "b2", "b3").map(::song),
        )
        val ranked = TopPicksDiversity.rank(batches, limit = 5)
        assertEquals(listOf("a1", "b1", "b2", "b3"), ranked.map { it.id })
    }

    @Test
    fun `deduplicates a track the catalogue returned for two seeds`() {
        val shared = song("shared")
        val batch1 = listOf(song("x1"), song("x2"), shared)
        val batch2 = listOf(shared, song("y2"))
        val ranked = TopPicksDiversity.rank(listOf(batch1, batch2), limit = 10)
        assertEquals(1, ranked.count { it.id == "shared" })
    }

    @Test
    fun `empty input yields empty shelf`() {
        assertTrue(TopPicksDiversity.rank(emptyList(), limit = 10).isEmpty())
        assertTrue(TopPicksDiversity.rank(listOf(emptyList()), limit = 10).isEmpty())
    }
}
