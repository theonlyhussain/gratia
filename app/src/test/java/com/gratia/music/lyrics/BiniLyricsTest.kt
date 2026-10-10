package com.gratia.music.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the wire schema of the ported BiniLyrics provider.
 *
 * The provider's whole value is that it can report an ISRC for the recording it
 * matched, so the mapping from the API's snake_case fields onto [BiniLyrics.Hit]
 * is the part worth testing without a network: get `lyrics_url` or `isrc` wrong
 * and the second request — and the recording identity — silently disappears.
 */
class BiniLyricsTest {

    @Test
    fun aHitDecodesEveryFieldTheSecondRequestNeeds() {
        val body = """
            {
              "total": 1,
              "source": "HIT-EXACT",
              "results": [
                {
                  "track_name": "Dracula",
                  "artist_name": "Tame Impala",
                  "album_name": "Dracula",
                  "duration": 205,
                  "isrc": "AUUM72500001",
                  "timing_type": "word",
                  "lyricsUrl": "https://storage.binimum.org/ttml/abc"
                }
              ]
            }
        """.trimIndent()

        val response = lyricsJson.decodeFromString<BiniLyrics.Response>(body)
        val hit = response.results?.firstOrNull()

        assertEquals(1, response.total)
        assertEquals("HIT-EXACT", response.source)
        assertEquals("Dracula", hit?.trackName)
        assertEquals("Tame Impala", hit?.artistName)
        assertEquals("Dracula", hit?.albumName)
        assertEquals(205, hit?.duration)
        assertEquals("AUUM72500001", hit?.isrc)
        assertEquals("word", hit?.timingType)
        assertEquals("https://storage.binimum.org/ttml/abc", hit?.lyricsUrl)
    }

    @Test
    fun aSpotifyShapedResultWithoutExtrasStillDecodes() {
        // The endpoint is lenient about which fields it returns; only the URL
        // and (optionally) the ISRC matter to the flow. Note the URL key is
        // camelCase in the source's schema (no @SerialName on that field).
        val response = lyricsJson.decodeFromString<BiniLyrics.Response>(
            """{"results":[{"track_name":"Song","artist_name":"Artist","lyricsUrl":"https://x/y"}]}""",
        )
        val hit = response.results?.firstOrNull()
        assertEquals("https://x/y", hit?.lyricsUrl)
        assertNull(hit?.isrc)
        assertNull(hit?.duration)
    }

    @Test
    fun anEmptyResultSetIsNoHit() {
        val response = lyricsJson.decodeFromString<BiniLyrics.Response>("""{"total":0,"results":[]}""")
        assertTrue(response.results.isNullOrEmpty())
    }
}
