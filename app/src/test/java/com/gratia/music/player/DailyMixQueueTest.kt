package com.gratia.music.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Daily Mix queue contract, held as tests against the pure tier model.
 *
 * The reported bug was: tap the Daily Mix, open the queue, and find
 * downloaded/local library tracks sitting in it that the mix never contained,
 * with no way to tell context from suggestions. The root cause lived in how
 * the extension batch was appended — a full-queue rewrite through an API that
 * also copied the existing tail — so the fix is the pure rule
 * [QueueTiers.contextEndsBefore] plus the append discipline exercised below.
 *
 * These tests hold the semantics: the mix's rows are CONTEXT, generated rows
 * are AUTOPLAY, downloaded status is never a queue property, and the queue
 * header names the mix.
 */
class DailyMixQueueTest {

    private fun mixTrack(n: Int) = com.gratia.music.data.model.SongEntity(
        id = "ytm_mix_$n",
        title = "Mix song $n",
        artist = "Mix artist",
        storageProvider = "youtube_music",
        providerTrackId = "mix_$n",
    )

    /** The same recording, but with a finished download attached. */
    private fun downloaded(song: com.gratia.music.data.model.SongEntity) =
        song.copy(isDownloaded = true, downloadPath = "/data/downloads/${song.providerTrackId}.m4a")

    @Test
    fun `queue opens with the mix tracks in mix order`() {
        val mix = listOf(mixTrack(1), mixTrack(2), mixTrack(3))
        // Playing the mix seeds the queue with the mix itself — never with
        // whatever else the library happens to hold. No generated rows yet,
        // so the whole queue is context.
        assertEquals(3, QueueTiers.contextCount(mix))
    }

    @Test
    fun `a downloaded mix track keeps its context tier`() {
        // Storage is a playback property, not a queue property: resolving
        // Creep from a downloaded file must not reclassify it out of the mix.
        val mix = listOf(downloaded(mixTrack(1)), mixTrack(2))
        assertEquals(2, QueueTiers.contextCount(mix))
    }

    @Test
    fun `generated suggestions start after the last context row`() {
        val mix = listOf(mixTrack(1), mixTrack(2), mixTrack(3))
        val suggestions = listOf(mixTrack(4), mixTrack(5))
        val queue = mix + suggestions
        val autoplayIds = suggestions.mapTo(HashSet()) { it.id }

        val split = QueueTiers.splitByTier(queue, autoplayIds)
        // Everything the mix brought is context; the generated pair is not.
        assertEquals(listOf("ytm_mix_1", "ytm_mix_2", "ytm_mix_3"), split.context.map { it.id })
        assertEquals(listOf("ytm_mix_4", "ytm_mix_5"), split.autoplay.map { it.id })
    }

    @Test
    fun `an appended batch never duplicates rows already queued`() {
        // The append discipline: from a candidate list that overlaps the
        // queue, only genuinely new rows survive — which is what stopped the
        // old full-queue rewrite from duplicating the mix on every extension.
        val queued = listOf(mixTrack(1), mixTrack(2), mixTrack(3))
        val played = HashSet<String>()
        val removed = HashSet<String>()
        val candidates = listOf(mixTrack(3), mixTrack(4), mixTrack(2), mixTrack(5))

        val appended = QueueTiers.dedupForAppend(
            candidates,
            queuedIds = queued.mapTo(HashSet()) { it.id },
            playedIds = played,
            removedIds = removed,
        )
        assertEquals(listOf("ytm_mix_4", "ytm_mix_5"), appended.map { it.id })
    }

    @Test
    fun `a removed song is not re-suggested by a later batch`() {
        val queued = listOf(mixTrack(1))
        val removed = hashSetOf("ytm_mix_2")
        val candidates = listOf(mixTrack(2), mixTrack(3))

        val appended = QueueTiers.dedupForAppend(
            candidates,
            queuedIds = queued.mapTo(HashSet()) { it.id },
            playedIds = HashSet(),
            removedIds = removed,
        )
        // mix_2 was removed by hand; the batch containing it may only bring
        // the rows the listener did not throw away.
        assertEquals(listOf("ytm_mix_3"), appended.map { it.id })
    }

    @Test
    fun `the queue header names the mix, not the library`() {
        // The context label for a mix session must say Daily Mix. The old
        // session labelled itself "Home", which told the listener nothing
        // about which collection they were inside.
        assertEquals("Daily Mix", PlaybackOrigin.DAILY_MIX.label)
        assertNotEquals(PlaybackOrigin.HOME, PlaybackOrigin.DAILY_MIX)
    }

    private fun assertNotEquals(a: Any, b: Any) {
        assertTrue("Expected $a to differ from $b", a != b)
    }
}
