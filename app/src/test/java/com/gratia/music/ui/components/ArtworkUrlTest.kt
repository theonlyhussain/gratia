package com.gratia.music.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pins the one rule that keeps artwork sharp: a URL is asked for at the size it
 * is actually drawn at, and never below the size it already names.
 */
class ArtworkUrlTest {

    private val cover = "https://lh3.googleusercontent.com/abc=w120-h120-l90-rj"

    @Test
    fun `reads a pair size token`() {
        assertEquals(120, artworkTokenPixels(cover))
        assertEquals(544, artworkTokenPixels("https://lh3.googleusercontent.com/x=w544-h544"))
    }

    @Test
    fun `reads a single size token`() {
        assertEquals(300, artworkTokenPixels("https://lh3.ggpht.com/x=s300-c"))
    }

    @Test
    fun `no token is null`() {
        assertNull(artworkTokenPixels("https://lh3.googleusercontent.com/abc"))
        assertNull(artworkTokenPixels("/storage/emulated/0/cover.jpg"))
        assertNull(artworkTokenPixels(null))
    }

    @Test
    fun `raises a thumbnail token to the drawn size`() {
        assertEquals(
            "https://lh3.googleusercontent.com/abc=w1080-h1080-l90-rj",
            upgradedArtworkUrl(cover, 1080)
        )
    }

    @Test
    fun `never downgrades below the size a url already names`() {
        // A 544px asset drawn in a 320px row keeps its 544px rendition.
        assertEquals(
            "https://lh3.googleusercontent.com/x=w544-h544",
            upgradedArtworkUrl("https://lh3.googleusercontent.com/x=w544-h544", ROW_ARTWORK_PX)
        )
    }

    @Test
    fun `upgrades a pair token with a single-token request`() {
        assertEquals(
            "https://lh3.ggpht.com/x=s1080-c",
            upgradedArtworkUrl("https://lh3.ggpht.com/x=s100-c", 1080)
        )
    }

    @Test
    fun `leaves non-host and local urls untouched`() {
        assertEquals(
            "https://example.com/a.jpg",
            upgradedArtworkUrl("https://example.com/a.jpg", 1080)
        )
        assertEquals(
            "/storage/emulated/0/cover.jpg",
            upgradedArtworkUrl("/storage/emulated/0/cover.jpg", 1080)
        )
        assertNull(upgradedArtworkUrl(null, 1080))
    }

    @Test
    fun `at-size clamps an oversized request to the host ceiling`() {
        assertEquals(
            "https://lh3.googleusercontent.com/abc=w2260-h2260-l90-rj",
            artworkAtSize(cover, 99_999)
        )
    }

    @Test
    fun `cache key strips the size token so one asset is one entry`() {
        assertEquals(
            "https://lh3.googleusercontent.com/abc-l90-rj",
            artworkCacheKey(cover)
        )
        assertEquals(
            "https://lh3.ggpht.com/x-c",
            artworkCacheKey("https://lh3.ggpht.com/x=s100-c")
        )
    }

    @Test
    fun `cache key leaves non-host urls unchanged`() {
        assertEquals("https://example.com/a.jpg", artworkCacheKey("https://example.com/a.jpg"))
    }
}
