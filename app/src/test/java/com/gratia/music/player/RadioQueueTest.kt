package com.gratia.music.player

import com.gratia.music.data.model.SongEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The acceptance bar for Infinity Play, held as tests.
 *
 * The reported bug was precise: search a song, tap result #1, switch Infinity
 * Play on, and get search result #2 instead of anything the radio had to say.
 * That is a statement about *queue composition*, so it is testable without a
 * player, a network or a database — which is why the rules live in
 * [RadioQueue] as pure functions and are pinned down here.
 */
class RadioQueueTest {

    private fun remote(videoId: String, title: String = "remote-$videoId") = SongEntity(
        id = "ytm_$videoId",
        title = title,
        artist = "Artist",
        storageProvider = "youtube_music",
        providerTrackId = videoId,
    )

    private fun local(id: String) = SongEntity(
        id = id,
        title = "local-$id",
        artist = "Artist",
        storageProvider = "local",
    )

    // ---- The regression: Infinity Play must reach the radio -----------------

    @Test
    fun `search result two is not what plays after enabling infinity play`() {
        // The listener's flow: a search returns several songs, they tap the
        // first, and only then do they switch Infinity Play on.
        val search1 = remote("v1")
        val search2 = remote("v2")
        val search3 = remote("v3")
        val context = listOf(search1, search2, search3)

        // Result #1 is playing; nothing was queued by hand.
        val currentIndex = 0
        val explicit = emptySet<String>()
        val generated = mutableSetOf<String>()

        // The radio answers with related tracks, seed first as it usually does.
        val suggestions = RadioQueue.filterSuggestions(
            seedVideoId = "v1",
            candidates = listOf(search1, remote("r1"), remote("r2")),
            alreadyKnown = context.map { it.id }.toSet(),
        )

        val insertAt = RadioQueue.insertionIndex(context, currentIndex, generated, explicit)
        val queue = buildList {
            addAll(context.subList(0, insertAt))
            addAll(suggestions)
            addAll(context.subList(insertAt, context.size))
        }

        // What the player advances onto.
        val next = queue[currentIndex + 1]

        assertEquals("v1's related track must follow it", "r1", next.providerTrackId)
        assertEquals("radio order must be preserved", "r2", queue[currentIndex + 2].providerTrackId)
        assertTrue(
            "the rest of the search results must not have jumped ahead of the radio",
            next.id != search2.id && next.id != search3.id,
        )
    }

    // ---- Rule 1: what the listener queued by hand wins ----------------------

    @Test
    fun `explicitly queued tracks stay ahead of suggestions`() {
        // seed → manual A → manual B, then Infinity Play is switched on.
        val seed = remote("seed")
        val a = remote("a")
        val b = remote("b")
        val context = listOf(seed, a, b)
        val explicit = setOf(a.id, b.id)

        val insertAt = RadioQueue.insertionIndex(
            queue = context,
            currentIndex = 0,
            generatedIds = emptySet(),
            explicitIds = explicit,
        )

        assertEquals("suggestions land after manual B, not after the seed", 3, insertAt)

        val withRadio = context.subList(0, insertAt) +
            listOf(remote("r1"), remote("r2"))
        assertEquals(
            listOf("seed", "a", "b", "r1", "r2"),
            withRadio.map { it.providerTrackId },
        )
    }

    @Test
    fun `add to queue lands before the generated run`() {
        val seed = remote("seed")
        val r1 = remote("r1")
        val r2 = remote("r2")
        val generated = setOf(r1.id, r2.id)
        val queue = listOf(seed, r1, r2)

        val boundary = RadioQueue.boundary(queue, generated)
        assertEquals(1, boundary)

        // What addToQueue does with that index.
        val after = queue.toMutableList().apply { add(boundary, remote("manual")) }
        assertEquals(
            listOf("seed", "manual", "r1", "r2"),
            after.map { it.providerTrackId },
        )
    }

    // ---- Rule 2: successive batches stay one contiguous run -----------------

    @Test
    fun `a later batch extends the run rather than splitting it`() {
        val seed = remote("seed")
        val r1 = remote("r1")
        val generated = setOf(r1.id)
        val queue = listOf(seed, r1)

        val insertAt = RadioQueue.insertionIndex(queue, currentIndex = 0, generated, emptySet())
        assertEquals(2, insertAt)
    }

    // ---- Rule 3: nothing is ever suggested twice ----------------------------

    @Test
    fun `the seed is filtered out of its own radio answer`() {
        val filtered = RadioQueue.filterSuggestions(
            seedVideoId = "seed",
            candidates = listOf(remote("seed"), remote("r1")),
            alreadyKnown = setOf("ytm_seed"),
        )
        assertEquals(listOf("r1"), filtered.map { it.providerTrackId })
    }

    @Test
    fun `already queued and already suggested tracks are filtered out`() {
        val filtered = RadioQueue.filterSuggestions(
            seedVideoId = "seed",
            candidates = listOf(remote("queued"), remote("seen"), remote("fresh")),
            alreadyKnown = setOf("ytm_queued", "ytm_seen"),
        )
        assertEquals(listOf("fresh"), filtered.map { it.providerTrackId })
    }

    @Test
    fun `duplicates inside one radio answer collapse`() {
        val filtered = RadioQueue.filterSuggestions(
            seedVideoId = "seed",
            candidates = listOf(remote("r1"), remote("r1"), remote("r2")),
            alreadyKnown = emptySet(),
        )
        assertEquals(listOf("r1", "r2"), filtered.map { it.providerTrackId })
    }

    // ---- Remote-only: a local file can never continue a radio ---------------

    @Test
    fun `local files are never part of a radio continuation`() {
        val filtered = RadioQueue.filterSuggestions(
            seedVideoId = "seed",
            candidates = listOf(local("local-1"), remote("r1")),
            alreadyKnown = emptySet(),
        )
        assertEquals(listOf("r1"), filtered.map { it.providerTrackId })
    }

    @Test
    fun `a local track cannot seed a radio`() {
        assertNull(RadioQueue.seedVideoIdOf(local("local-1")))
        assertNull(RadioQueue.seedVideoIdOf(null))
        assertEquals("v1", RadioQueue.seedVideoIdOf(remote("v1")))
    }

    @Test
    fun `a remote row missing its provider id still seeds from its own id`() {
        val row = SongEntity(
            id = "ytm_abc",
            title = "t",
            artist = "a",
            storageProvider = "youtube_music",
            providerTrackId = null,
        )
        assertEquals("abc", RadioQueue.seedVideoIdOf(row))
    }

    // ---- Refill: before the queue empties, never after ----------------------

    @Test
    fun `refill triggers while suggestions are still ahead`() {
        assertTrue(RadioQueue.needsRefill(0))
        assertTrue(RadioQueue.needsRefill(RadioQueue.REFILL_THRESHOLD))
        assertFalse(RadioQueue.needsRefill(RadioQueue.REFILL_THRESHOLD + 1))
        assertFalse(RadioQueue.needsRefill(RadioQueue.BATCH_TARGET))
    }

    @Test
    fun `remaining suggestions count only what is ahead`() {
        val seed = remote("seed")
        val a = remote("a")
        val r1 = remote("r1")
        val r2 = remote("r2")
        val generated = setOf(r1.id, r2.id)
        // A suggestion already played sits *before* the current track.
        val queue = listOf(seed, r1, a, r2)

        // Only r2 is still ahead of the listener, whichever position they are at.
        assertEquals(1, RadioQueue.remainingSuggestions(queue, currentIndex = 1, generated))
        assertEquals(1, RadioQueue.remainingSuggestions(queue, currentIndex = 2, generated))
        assertEquals(0, RadioQueue.remainingSuggestions(queue, currentIndex = 3, generated))
    }

    // ---- The boundary survives the queue being rearranged -------------------

    @Test
    fun `boundary is read from the queue so a reorder cannot invalidate it`() {
        val seed = remote("seed")
        val r1 = remote("r1")
        val r2 = remote("r2")
        val generated = setOf(r1.id, r2.id)

        assertEquals(1, RadioQueue.boundary(listOf(seed, r1, r2), generated))
        // Same rows in a different order: the generated run still begins at the
        // first generated row, not at a remembered offset.
        assertEquals(1, RadioQueue.boundary(listOf(seed, r2, r1), generated))
        // With a manually queued row in between, the boundary moves with it.
        assertEquals(2, RadioQueue.boundary(listOf(seed, remote("manual"), r1, r2), generated))
        // No suggestions left.
        assertEquals(1, RadioQueue.boundary(listOf(seed), generated))
        assertTrue(RadioQueue.isInsideUserQueue(listOf(seed, r1), 0, generated))
        assertFalse(RadioQueue.isInsideUserQueue(listOf(seed, r1), 1, generated))
    }

    // ---- The queue screen's two sections -----------------------------------

    @Test
    fun `upcoming entries split into user queue and autoplay sections`() {
        val seed = remote("seed")
        val manualA = remote("mA")
        val manualB = remote("mB")
        val r1 = remote("r1")
        val r2 = remote("r2")
        val generated = setOf(r1.id, r2.id)

        // current → manual A → manual B → radio r1 → radio r2
        val queue = listOf(seed, manualA, manualB, r1, r2)

        val (user, auto) = RadioQueue.upcomingByTier(queue, currentIndex = 0, generated)
        assertEquals(listOf(manualA, manualB), user)
        assertEquals(listOf(r1, r2), auto)
    }

    @Test
    fun `row dragged into the user run stays in the user section`() {
        val seed = remote("seed")
        val manual = remote("m")
        val r1 = remote("r1")
        val r2 = remote("r2")
        val generated = setOf(r1.id, r2.id)

        // The listener dragged r2 between the seed and the manual track. That
        // is their queue and their order; the split follows the arrangement.
        val queue = listOf(seed, r2, manual, r1)

        val (user, auto) = RadioQueue.upcomingByTier(queue, currentIndex = 0, generated)
        assertEquals(listOf(manual), user)
        assertEquals(listOf(r2, r1), auto)
    }

    @Test
    fun `no suggestions means an empty autoplay section`() {
        val seed = remote("seed")
        val manual = remote("m")
        val queue = listOf(seed, manual)

        val (user, auto) = RadioQueue.upcomingByTier(queue, currentIndex = 0, setOf())
        assertEquals(listOf(manual), user)
        assertTrue(auto.isEmpty())
    }

    @Test
    fun `nothing ahead of the listener splits to nothing`() {
        val seed = remote("seed")
        val r1 = remote("r1")
        val generated = setOf(r1.id)
        val queue = listOf(seed, r1)

        val (user, auto) = RadioQueue.upcomingByTier(queue, currentIndex = 1, generated)
        assertTrue(user.isEmpty())
        assertTrue(auto.isEmpty())

        // Out-of-range index behaves the same way rather than throwing.
        val (user2, auto2) = RadioQueue.upcomingByTier(queue, currentIndex = 9, generated)
        assertTrue(user2.isEmpty())
        assertTrue(auto2.isEmpty())
    }
}
