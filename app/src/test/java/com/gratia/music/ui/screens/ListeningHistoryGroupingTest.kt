package com.gratia.music.ui.screens

import com.gratia.music.data.model.PlaybackHistoryEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * Pins the history screen's date grouping (spec 22): real calendar buckets,
 * deterministic labels, chronological order preserved within a bucket.
 */
class ListeningHistoryGroupingTest {

    private val zone: ZoneId = ZoneId.systemDefault()

    private fun entry(id: String, playedAt: Long) = PlaybackHistoryEntity(
        id = id,
        songId = "song_$id",
        title = "t$id",
        artist = "a$id",
        source = "YOUTUBE_MUSIC",
        playedAt = playedAt,
    )

    private fun at(date: LocalDate, hour: Int): Long =
        date.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `groups by today, yesterday and plain dates`() {
        val today = LocalDate.now(zone)
        val yesterday = today.minusDays(1)
        val threeDaysAgo = today.minusDays(3)

        val groups = groupByDay(
            listOf(
                entry("a", at(today, 9)),
                entry("b", at(yesterday, 14)),
                entry("c", at(threeDaysAgo, 20)),
            )
        )

        assertEquals(listOf("Today", "Yesterday", threeDaysAgo.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault())), groups.map { it.label })
        assertEquals(listOf("a"), groups[0].entries.map { it.id })
        assertEquals(listOf("b"), groups[1].entries.map { it.id })
        assertEquals(listOf("c"), groups[2].entries.map { it.id })
    }

    @Test
    fun `entries older than a week carry the full date with year`() {
        val today = LocalDate.now(zone)
        val old = today.minusDays(30)

        val groups = groupByDay(listOf(entry("old", at(old, 12))))

        val expected = old.format(java.time.format.DateTimeFormatter.ofPattern("MMMM d, yyyy", java.util.Locale.getDefault()))
        assertEquals(expected, groups.single().label)
    }

    @Test
    fun `same-day entries land in one bucket in the order given`() {
        val today = LocalDate.now(zone)
        val groups = groupByDay(
            listOf(
                entry("first", at(today, 8)),
                entry("second", at(today, 11)),
                entry("third", at(today, 20)),
            )
        )
        assertEquals(1, groups.size)
        assertEquals(listOf("first", "second", "third"), groups.single().entries.map { it.id })
    }

    @Test
    fun `empty history groups to nothing`() {
        assertEquals(0, groupByDay(emptyList()).size)
    }
}
