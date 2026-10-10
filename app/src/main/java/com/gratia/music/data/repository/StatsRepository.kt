package com.gratia.music.data.repository

import com.gratia.music.GratiaApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The one authoritative summariser of listening history (spec 64/99/144).
 *
 * Both the Home stats card and the full Replay screen read this — a number on
 * Home and the same number on Replay are the same query, never two
 * calculations that can drift. Every value is an aggregate of recorded
 * listening events in Room (`listening_events`), each of which was written by
 * the player's own pause/skip/complete accounting of *actual playback time*
 * — never wall-clock, never UI visibility. Nothing here invents data: an
 * empty period summarises to zeros and empty lists, and the UI is responsible
 * for its honest empty state (spec 56/134–138).
 *
 * Period boundaries use the device's local time zone (spec 106).
 */
object StatsRepository {

    /** A period's full summary, all values real or zero. */
    data class StatsSummary(
        val totalSeconds: Long,
        val playCount: Long,
        val distinctSongs: Int,
        val distinctArtists: Int,
        val distinctAlbums: Int,
        val topSongs: List<TrackStat>,
        val topArtists: List<ArtistStat>,
        val topAlbums: List<AlbumStat>,
        val topGenres: List<GenreStat>,
        val peakHour: Int?,
        val biggestDay: DayStat?,
    )

    data class TrackStat(val songId: String, val title: String, val artist: String, val totalSeconds: Long, val plays: Long, val artworkUrl: String? = null)
    data class ArtistStat(val name: String, val totalSeconds: Long, val artworkUrl: String? = null)
    data class AlbumStat(val name: String, val totalSeconds: Long, val artworkUrl: String? = null)
    data class GenreStat(val name: String, val totalSeconds: Long)
    data class DayStat(val dateString: String, val totalSeconds: Long)

    /** This month, local time. */
    fun monthStart(): Long =
        java.time.LocalDate.now().withDayOfMonth(1)
            .atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()

    /** This year, local time. */
    fun yearStart(): Long =
        java.time.LocalDate.now().withDayOfYear(1)
            .atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()

    /** Today, local time. */
    fun todayStart(): Long =
        java.time.LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()

    /**
     * This week, local time — weeks start Monday (ISO), matching how the rest
     * of the period helpers derive their bounds from [LocalDate] in the
     * device's own zone, so a week boundary is a real midnight, not a UTC
     * truncation (stats spec 9).
     */
    fun weekStart(): Long =
        java.time.LocalDate.now().with(java.time.DayOfWeek.MONDAY)
            .atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()

    /**
     * Summarises recorded listening in [start, end]. Runs on IO; the queries
     * are indexed SQL aggregates over `listening_events`, not full scans in
     * Kotlin (spec 139/140).
     */
    suspend fun summary(start: Long, end: Long): StatsSummary = withContext(Dispatchers.IO) {
        val db = GratiaApp.instance.database
        val eventDao = db.listeningEventDao()
        val songDao = db.songDao()

        val totalSeconds = eventDao.getTotalListeningSeconds(start, end) ?: 0L

        val topTrackRows = eventDao.getTopTracks(start, end, 50)
        val topArtistRows = eventDao.getTopArtists(start, end, 10)
        val topAlbumRows = eventDao.getTopAlbums(start, end, 10)

        // Every aggregate is read from the event's own snapshot (spec 12/13/58),
        // so a streamed track that never reached the `songs` table still counts
        // and still carries its cover. `plays` is derived from listened time at
        // the same 30-second unit the rest of the app treats as a play.
        val topSongs = topTrackRows.map { row ->
            TrackStat(row.songId, row.title, row.artist, row.totalSeconds, row.totalSeconds / 30, row.artworkUrl)
        }

        // Genre is the one signal the event does not carry — it lives on the
        // `songs` row (a scanned/local metadata field). Resolved best-effort:
        // a track with no row or no genre contributes nothing rather than a
        // fabricated label (spec 78/79).
        val songById = topTrackRows.mapNotNull { songDao.getSongById(it.songId) }.associateBy { it.id }
        val genres = topTrackRows.mapNotNull { row ->
            songById[row.songId]?.genre?.takeIf { it.isNotBlank() }?.let { it to row.totalSeconds }
        }.groupBy({ it.first }, { it.second })
            .map { (name, seconds) -> GenreStat(name, seconds.sum()) }
            .sortedByDescending { it.totalSeconds }
            .take(8)

        // Artist and album aggregates arrive with their own artwork, resolved
        // from the listening events rather than a second songs-table lookup
        // (spec 59/60).
        val topArtists = topArtistRows.map { ArtistStat(it.artist, it.totalSeconds, it.artworkUrl) }
        val albums = topAlbumRows.map { AlbumStat(it.name, it.totalSeconds, it.artworkUrl) }

        val daily = eventDao.getDailySummariesSince(start)
            .filter { it.dateString.let { d -> d >= dayString(start) } }
        val biggest = daily.filter { it.listeningSeconds > 0 }
            .maxByOrNull { it.listeningSeconds }
            ?.let { DayStat(it.dateString, it.listeningSeconds) }

        val playCount = eventDao.getCompletedPlayCount(start, end)

        // Confidence gates (spec 105/106). "Most active around 9 AM" claimed off
        // one accidental play is a guess dressed as an insight; the hour only
        // becomes an insight when enough actual listening stands behind it, and
        // the same bar applies to the biggest-day callout.
        val insightConfident = totalSeconds >= MIN_INSIGHT_TOTAL_SECONDS &&
            playCount >= MIN_INSIGHT_PLAYS
        val peakHour = if (insightConfident) eventDao.peakListeningHour(start, end) else null
        val confidentBiggest = biggest?.takeIf { insightConfident }

        StatsSummary(
            totalSeconds = totalSeconds,
            playCount = playCount,
            distinctSongs = eventDao.getDistinctSongCount(start, end).toInt(),
            distinctArtists = topArtistRows.size,
            distinctAlbums = albums.size,
            topSongs = topSongs,
            topArtists = topArtists,
            topAlbums = albums,
            topGenres = genres,
            peakHour = peakHour,
            biggestDay = confidentBiggest,
        )
    }

    /** Minimum listening behind an insight before the app is willing to say it. */
    private const val MIN_INSIGHT_TOTAL_SECONDS = 900L

    /** Minimum completed plays behind an insight. */
    private const val MIN_INSIGHT_PLAYS = 3L

    private fun dayString(epochMs: Long): String =
        java.time.Instant.ofEpochMilli(epochMs).atZone(java.time.ZoneId.systemDefault())
            .toLocalDate().toString()
}
