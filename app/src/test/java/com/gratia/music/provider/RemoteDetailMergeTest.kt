package com.gratia.music.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * A partial remote response must never erase known-good detail data (spec
 * V #10/#11). These pin the exact regressions: "Radiohead" → "Unknown Artist",
 * and a valid cover turning null on a later fetch.
 */
class RemoteDetailMergeTest {

    private fun artist(
        id: String = "UC_radiohead",
        name: String = "Radiohead",
        artworkUrl: String? = "https://lh3.googleusercontent.com/cover=w120-h120",
        albums: List<RemoteAlbumSummary> = listOf(RemoteAlbumSummary(id = "a1", title = "OK Computer")),
    ) = RemoteArtist(
        id = id,
        name = name,
        artworkUrl = artworkUrl,
        albums = albums,
    )

    @Test
    fun `a partial refresh does not replace a known name with the placeholder`() {
        val existing = artist()
        val partial = RemoteArtist(id = "UC_radiohead", name = "Unknown Artist", artworkUrl = null)

        val merged = RemoteDetailMerge.mergeArtist(existing, partial)

        assertEquals("Radiohead", merged.name)
    }

    @Test
    fun `a null artwork does not replace a known artwork`() {
        val existing = artist()
        val partial = RemoteArtist(id = "UC_radiohead", name = "Radiohead", artworkUrl = null)

        val merged = RemoteDetailMerge.mergeArtist(existing, partial)

        assertEquals(existing.artworkUrl, merged.artworkUrl)
    }

    @Test
    fun `an empty section does not erase known albums`() {
        val existing = artist()
        val partial = RemoteArtist(id = "UC_radiohead", name = "Radiohead", albums = emptyList())

        val merged = RemoteDetailMerge.mergeArtist(existing, partial)

        assertEquals(1, merged.albums.size)
    }

    @Test
    fun `a real incoming field still wins`() {
        val existing = artist()
        val fresh = RemoteArtist(
            id = "UC_radiohead",
            name = "Radiohead",
            artworkUrl = "https://lh3.googleusercontent.com/cover=w1080-h1080",
        )

        val merged = RemoteDetailMerge.mergeArtist(existing, fresh)

        assertEquals(fresh.artworkUrl, merged.artworkUrl)
    }

    @Test
    fun `a response for a different artist id is not merged onto this one`() {
        // The stale-request guard: artist A answering after artist B is on screen
        // must not fold A's fields into B.
        val existing = artist(id = "UC_radiohead", name = "Radiohead")
        val other = RemoteArtist(id = "UC_other", name = "Portishead")

        val merged = RemoteDetailMerge.mergeArtist(existing, other)

        assertSame(other, merged)
    }

    @Test
    fun `merging into nothing returns the incoming response`() {
        val incoming = artist()
        assertSame(incoming, RemoteDetailMerge.mergeArtist(null, incoming))
    }

    @Test
    fun `album merge keeps artwork when the incoming response has none`() {
        val existing = RemoteAlbum(
            id = "MPREb_okcomputer",
            title = "OK Computer",
            artworkUrl = "https://lh3.googleusercontent.com/album=w544-h544",
            tracks = listOf(RemoteTrack(id = "ytm_x", title = "Airbag", videoId = "x")),
        )
        val partial = RemoteAlbum(
            id = "MPREb_okcomputer",
            title = "OK Computer",
            artworkUrl = null,
            tracks = emptyList(),
        )

        val merged = RemoteDetailMerge.mergeAlbum(existing, partial)

        assertEquals(existing.artworkUrl, merged.artworkUrl)
        assertEquals(1, merged.tracks.size)
    }
}
