package com.gratia.music.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the search ordering that was backwards: an exact song match must outrank
 * loosely-matching artists, albums and playlists, so a query like "God's Plan"
 * leads with the track rather than trailing it behind three near-misses.
 */
class SearchRankingTest {

    @Test
    fun punctuationAndCaseDoNotChangeTheMatchKey() {
        assertEquals("god s plan", normalizeForMatch("  God's   Plan!!  "))
    }

    @Test
    fun anExactTitleOutranksAPrefixWhichOutranksAContains() {
        val exact = relevanceScore("God's Plan", "God's Plan", "Drake", null)
        val prefix = relevanceScore("God's Plan", "God's Plan (Remix)", "Drake", null)
        val contains = relevanceScore("God's Plan", "The God's Plan Story", "Drake", null)

        assertEquals(100, exact)
        assertEquals(80, prefix)
        assertTrue(prefix > contains)
    }

    @Test
    fun anExactSongBeatsAnArtistOrAlbumMatch() {
        val songMatch = relevanceScore("God's Plan", "God's Plan", "Drake", "Scorpion")
        val artistMatch = relevanceScore("God's Plan", "Scorpion", "Drake", null)
        val albumMatch = relevanceScore("God's Plan", "Scorpion", "Someone", "God's Plan")

        assertTrue(songMatch > artistMatch)
        assertTrue(songMatch > albumMatch)
    }

    @Test
    fun aTitleContainingTheQueryStillBeatsAnArtistOnlyMatch() {
        // "Songs first" in one assertion: a track that contains the query must
        // not be displaced by an artist whose *name* starts with it.
        val songTitle = relevanceScore("plan", "God's Plan", "Drake", null)
        val artistName = relevanceScore("plan", "Something Else", "Plan B", null)

        assertTrue(songTitle > artistName)
    }
}
