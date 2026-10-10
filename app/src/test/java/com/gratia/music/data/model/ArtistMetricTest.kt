package com.gratia.music.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pins the artist metric model: compact counts parse or fail honestly, and a
 * metric's label always names its own type and source (artist spec 2/3).
 */
class ArtistMetricTest {

    @Test
    fun `catalogue compact counts parse to their real values`() {
        assertEquals(1_200_000L, ArtistMetric.parseCompact("1.2M"))
        assertEquals(1_200_000L, ArtistMetric.parseCompact("1.2 M"))
        assertEquals(87_300L, ArtistMetric.parseCompact("87.3K"))
        assertEquals(2_400_000_000L, ArtistMetric.parseCompact("2.4B"))
        assertEquals(1_234L, ArtistMetric.parseCompact("1,234"))
        assertEquals(940L, ArtistMetric.parseCompact("940"))
    }

    @Test
    fun `unparsable or absent counts fail honestly — never a guess`() {
        assertNull(ArtistMetric.parseCompact(null))
        assertNull(ArtistMetric.parseCompact(""))
        assertNull(ArtistMetric.parseCompact("subscribers"))
        assertNull(ArtistMetric.parseCompact("about 1.2M"))
    }

    @Test
    fun `formatting is compact and round-trips`() {
        assertEquals("1.2M", ArtistMetric.formatCompact(1_200_000L))
        assertEquals("87.3K", ArtistMetric.formatCompact(87_300L))
        assertEquals("940", ArtistMetric.formatCompact(940L))
    }

    @Test
    fun `the label names the metric type and its source`() {
        val subscribers = ArtistMetric(7_100_000L, ArtistMetricType.SUBSCRIBERS, "YouTube")
        assertEquals("7.1M subscribers · YouTube", subscribers.label)

        val followers = ArtistMetric(1_300_000L, ArtistMetricType.FOLLOWERS, "Deezer")
        assertEquals("1.3M followers · Deezer", followers.label)
    }
}
