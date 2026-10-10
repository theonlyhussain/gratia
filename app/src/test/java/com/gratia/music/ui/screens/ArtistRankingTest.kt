package com.gratia.music.ui.screens

import com.gratia.music.provider.RemoteArtistRef
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The artist-first ranking (spec A / V #1). A query that is plainly an artist's
 * name must surface that artist above unrelated songs — without hardcoding any
 * name, and without turning a song query like "Creep" artist-first.
 */
class ArtistRankingTest {

    private val radiohead = RemoteArtistRef(id = "UC_radiohead", name = "Radiohead")

    @Test
    fun `an exact artist name query is artist-first`() {
        assertTrue(isArtistFirstQuery("Radiohead", listOf(radiohead)))
    }

    @Test
    fun `case, trailing space and punctuation do not change the decision`() {
        assertTrue(isArtistFirstQuery("  radiohead ", listOf(radiohead)))
        assertTrue(isArtistFirstQuery("RADIOHEAD!!", listOf(radiohead)))
    }

    @Test
    fun `a song query is not artist-first even when the artist is related`() {
        // "Creep" returns Radiohead as an associated artist, but the query is not
        // their name — so Songs stay first and the artist is merely floated up
        // in the Artists shelf (spec A).
        assertFalse(isArtistFirstQuery("Creep", listOf(radiohead)))
    }

    @Test
    fun `an unrelated query is not artist-first`() {
        assertFalse(isArtistFirstQuery("OK Computer", listOf(radiohead)))
    }

    @Test
    fun `exact score beats prefix beats incidental substring`() {
        val exact = artistNameScore("Radiohead", "Radiohead")
        val prefix = artistNameScore("Radio", "Radiohead")
        val incidental = artistNameScore("radio", "Pararadio")

        assertTrue(exact > prefix)
        assertTrue(prefix > incidental)
    }
}
