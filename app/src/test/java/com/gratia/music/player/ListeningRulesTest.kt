package com.gratia.music.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Play counts must come from listening, not from tapping (spec 14).
 *
 * The old behaviour incremented `songs.playCount` inside `playSong`, so opening
 * a track — then immediately closing it — counted as a play, and "Most Played"
 * filled with tracks that were never heard. These tests pin the replacement
 * policy in [ListeningRules].
 */
class ListeningRulesTest {

    @Test
    fun `a track that runs to the end is always a play`() {
        assertTrue(ListeningRules.countsAsPlay(completed = true, skipped = false, listenedSeconds = 0L))
        assertTrue(ListeningRules.countsAsPlay(completed = true, skipped = false, listenedSeconds = 5L))
    }

    @Test
    fun `a skipped track counts only after a meaningful listen`() {
        assertFalse(ListeningRules.countsAsPlay(completed = false, skipped = true, listenedSeconds = 5L))
        assertTrue(
            ListeningRules.countsAsPlay(
                completed = false,
                skipped = true,
                listenedSeconds = ListeningRules.PLAY_COUNT_MIN_SECONDS,
            )
        )
    }

    @Test
    fun `tapping a song is not a play`() {
        // The selection itself earns nothing: no completed, no skipped, zero
        // heard seconds — exactly the state right after playSong.
        assertFalse(ListeningRules.countsAsPlay(completed = false, skipped = false, listenedSeconds = 0L))
    }

    @Test
    fun `a pause is never its own play, however long it lasted`() {
        val anHour = 3_600L
        assertFalse(ListeningRules.countsAsPlay(completed = false, skipped = false, listenedSeconds = anHour))
    }
}
