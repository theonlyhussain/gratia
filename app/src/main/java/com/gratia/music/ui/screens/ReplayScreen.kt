package com.gratia.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.gratia.music.GratiaApp
import com.gratia.music.data.model.SongEntity
import com.gratia.music.data.model.TrackListenSummary
import com.gratia.music.data.repository.StatsRepository
import com.gratia.music.player.PlayerViewModel
import com.gratia.music.player.PlaybackOrigin
import androidx.compose.foundation.clickable
import com.gratia.music.ui.components.AppleSectionHeader
import com.gratia.music.ui.components.CoverArtImage
import com.gratia.music.ui.components.GratiaText
import com.gratia.music.ui.components.formatListeningDuration
import com.gratia.music.ui.components.rememberArtworkRequest
import com.gratia.music.ui.theme.GratiaTheme
import com.gratia.music.ui.theme.Inter
import com.gratia.music.ui.theme.SpaceGrotesk
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

/**
 * Which range of recorded history the Replay screen is showing (spec 62).
 * Every period filters persisted events by real local-timezone timestamps —
 * nothing here falls back to session state or "currently loaded rows"
 * (stats spec 9).
 */
enum class ReplayPeriod(val label: String) {
    TODAY("Today"),
    WEEK("This week"),
    MONTH("This month"),
    YEAR("This year"),
    ALL("All time");

    /** Local-timezone boundaries; ALL spans everything recorded (spec 106). */
    fun bounds(): Pair<Long, Long> = when (this) {
        TODAY -> StatsRepository.todayStart() to Long.MAX_VALUE
        WEEK -> StatsRepository.weekStart() to Long.MAX_VALUE
        MONTH -> StatsRepository.monthStart() to Long.MAX_VALUE
        YEAR -> StatsRepository.yearStart() to Long.MAX_VALUE
        ALL -> 0L to Long.MAX_VALUE
    }
}

/**
 * The full Listening Stats / Replay screen (spec 60–105).
 *
 * Everything on it is an aggregate of recorded listening events — the same
 * [StatsRepository] the Home card reads, so the two can never disagree. The
 * hero background is the period's #1 song artwork, soft-washed and scrimmed;
 * if there is no #1, there is no hero artwork and the screen says so instead
 * of inventing one (spec 134–138). One vertical scroll; ranked lists are
 * rows, not nested scrollers (spec 93).
 */
@Composable
fun ReplayScreen(
    playerViewModel: PlayerViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToAlbum: (String) -> Unit = {}
) {
    var period by remember { mutableStateOf(ReplayPeriod.MONTH) }
    var summary by remember { mutableStateOf<StatsRepository.StatsSummary?>(null) }
    var topSongRow by remember { mutableStateOf<SongRowData?>(null) }
    // songId → row for artwork in the ranked lists, prefetched with the
    // summary — no synchronous loading inside composition (spec 95/96).
    var songsById by remember { mutableStateOf<Map<String, SongEntity>>(emptyMap()) }
    val songRepo = remember { com.gratia.music.data.repository.SongRepository(GratiaApp.instance.database.songDao()) }

    // Recomputed per period switch; the queries are indexed aggregates, so a
    // switch is a few SQL reads rather than a history walk (spec 139/140).
    LaunchedEffect(period) {
        val (start, end) = period.bounds()
        val s = StatsRepository.summary(start, end)
        summary = s
        val ids = s.topSongs.map { it.songId }.toSet()
        songsById = ids.associateWith { songRepo.getSongById(it) }.filterValues { it != null }
            .mapValues { it.value!! }
        topSongRow = s.topSongs.firstOrNull()?.let { t ->
            SongRowData(
                TrackListenSummary(t.songId, t.title, t.artist, t.totalSeconds, t.artworkUrl),
                songsById[t.songId],
            )
        }
    }

    val headerPeriod = when (period) {
        ReplayPeriod.TODAY -> java.time.LocalDate.now().dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
        ReplayPeriod.WEEK -> "Week of ${java.time.LocalDate.now().with(java.time.DayOfWeek.MONDAY).format(java.time.format.DateTimeFormatter.ofPattern("MMMM d"))}"
        ReplayPeriod.MONTH -> "${java.time.LocalDate.now().month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${java.time.Year.now().value}"
        ReplayPeriod.YEAR -> "${java.time.Year.now().value}"
        ReplayPeriod.ALL -> "All time"
    }

    Box(Modifier.fillMaxSize().background(GratiaTheme.colors.background)) {
        // ── Palette-derived hero backdrop (spec 101): the #1 song's cover is
        // sampled for its colours and the gradient is built from those under a
        // scrim — the artwork is not the literal background (spec 102).
        // The #1 track's cover, preferring the songs row and falling back to the
        // event's own snapshot — a streamed track has no row, so the snapshot
        // is the only place its artwork exists (spec 60).
        val heroArt = topSongRow?.song?.let { it.artworkUrl ?: it.coverArtPath }
            ?: topSongRow?.track?.artworkUrl
        if (heroArt != null) {
            AsyncImage(
                model = rememberArtworkRequest(heroArt, 640, crossfadeMs = 400),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp)
                    .graphicsLayer { alpha = 0.55f }
                    .blur(28.dp)
            )
            // The scrim that keeps the white foreground readable is theme-aware:
            // light mode's washed-out backdrop needs a deeper wash for the same
            // contrast the dark scrim delivers on black (stats spec 13).
            val scrimTopAlpha = if (GratiaTheme.colors.isDark) 0.25f else 0.42f
            val scrimMidAlpha = if (GratiaTheme.colors.isDark) 0.40f else 0.58f
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(320.dp)
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = scrimTopAlpha),
                            0.6f to Color.Black.copy(alpha = scrimMidAlpha),
                            1f to GratiaTheme.colors.background,
                        )
                    )
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            // Nav-safe: the system gesture inset plus the mini player height
            // both claim the foot of this screen (spec 107/108).
            contentPadding = PaddingValues(
                bottom = 48.dp + com.gratia.music.ui.LocalBottomPadding.current
            )
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = if (heroArt != null) Color.White else GratiaTheme.colors.textPrimary
                        )
                    }
                    Column {
                        Text(
                            "Listening Stats",
                            fontFamily = SpaceGrotesk,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = if (heroArt != null) Color.White else GratiaTheme.colors.textPrimary
                        )
                        Text(
                            headerPeriod,
                            fontFamily = Inter,
                            fontSize = 13.sp,
                            color = if (heroArt != null) Color.White.copy(alpha = 0.75f) else GratiaTheme.colors.textSecondary
                        )
                    }
                }
            }

            // ── Period chips ────────────────────────────────────────────────
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ReplayPeriod.entries.forEach { p ->
                        val selected = p == period
                        Text(
                            p.label,
                            fontFamily = Inter,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 13.sp,
                            color = if (selected) Color.White else GratiaTheme.colors.textSecondary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    if (selected) GratiaTheme.colors.accent
                                    else GratiaTheme.colors.surface
                                )
                                .clickable { period = p }
                                .padding(horizontal = 14.dp, vertical = 7.dp)
                        )
                    }
                }
            }

            val s = summary
            if (s == null) {
                item { Box(Modifier.height(200.dp)) }
            } else if (s.totalSeconds == 0L) {
                // Real empty state — nothing measured in this period (spec 56/102).
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        GratiaText(
                            "Nothing listened to in this period yet.",
                            style = GratiaTheme.typography.body,
                            color = GratiaTheme.colors.textSecondary
                        )
                    }
                }
            } else {
                // ── Hero summary (spec 87) ─────────────────────────────────
                item {
                    Column(Modifier.padding(horizontal = 24.dp)) {
                        Text(
                            formatListeningDuration(s.totalSeconds),
                            fontFamily = SpaceGrotesk,
                            fontWeight = FontWeight.Bold,
                            fontSize = 44.sp,
                            lineHeight = 48.sp,
                            color = if (heroArt != null) Color.White else GratiaTheme.colors.textPrimary
                        )
                        Text(
                            "${s.playCount} play${if (s.playCount == 1L) "" else "s"} · ${s.distinctSongs} song${if (s.distinctSongs == 1) "" else "s"} · $headerPeriod",
                            fontFamily = Inter,
                            fontSize = 14.sp,
                            color = if (heroArt != null) Color.White.copy(alpha = 0.8f) else GratiaTheme.colors.textSecondary
                        )
                        Spacer(Modifier.height(20.dp))
                        if (heroArt != null) {
                            Text(
                                "TOP SONG",
                                fontFamily = Inter,
                                fontSize = 11.sp,
                                letterSpacing = 1.5.sp,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                            Text(
                                topSongRow?.track?.title ?: "",
                                fontFamily = SpaceGrotesk,
                                fontWeight = FontWeight.Bold,
                                fontSize = 22.sp,
                                color = Color.White
                            )
                            Text(
                                topSongRow?.track?.artist ?: "",
                                fontFamily = Inter,
                                fontSize = 14.sp,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }
                        if (s.topArtists.isNotEmpty()) {
                            Spacer(Modifier.height(14.dp))
                            Text(
                                "TOP ARTIST",
                                fontFamily = Inter,
                                fontSize = 11.sp,
                                letterSpacing = 1.5.sp,
                                color = if (heroArt != null) Color.White.copy(alpha = 0.7f) else GratiaTheme.colors.textSecondary
                            )
                            Text(
                                s.topArtists.first().name,
                                fontFamily = SpaceGrotesk,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = if (heroArt != null) Color.White else GratiaTheme.colors.textPrimary
                            )
                        }
                    }
                }

                // ── Top songs ───────────────────────────────────────────────
                item {
                    Spacer(Modifier.height(28.dp))
                    AppleSectionHeader(title = "Top Songs")
                }
                val topSongs = s.topSongs.take(10)
                items(topSongs.size, key = { "song_${topSongs[it].songId}" }) { i ->
                    val t = topSongs[i]
                    ReplayRankRow(
                        rank = i + 1,
                        title = t.title,
                        subtitle = t.artist,
                        trailing = formatListeningDuration(t.totalSeconds),
                        coverArtPath = songsById[t.songId]?.coverArtPath ?: t.artworkUrl,
                    )
                }

                // ── Top artists ─────────────────────────────────────────────
                item {
                    Spacer(Modifier.height(20.dp))
                    AppleSectionHeader(title = "Top Artists")
                }
                val topArtists = s.topArtists.take(10)
                items(topArtists.size, key = { "artist_${topArtists[it].name}" }) { i ->
                    val a = topArtists[i]
                    ReplayRankRow(
                        rank = i + 1,
                        title = a.name,
                        subtitle = null,
                        trailing = formatListeningDuration(a.totalSeconds),
                        coverArtPath = a.artworkUrl,
                    )
                }

                // ── Top albums ──────────────────────────────────────────────
                if (s.topAlbums.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(20.dp))
                        AppleSectionHeader(title = "Top Albums")
                    }
                    val topAlbums = s.topAlbums.take(10)
                    items(topAlbums.size, key = { "album_${topAlbums[it].name}" }) { i ->
                        val al = topAlbums[i]
                        ReplayRankRow(
                            rank = i + 1,
                            title = al.name,
                            subtitle = al.artworkUrl?.let { null },
                            trailing = formatListeningDuration(al.totalSeconds),
                            coverArtPath = al.artworkUrl,
                        )
                    }
                }

                // ── Genres — only real metadata, else omitted (spec 78/79) ──
                if (s.topGenres.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(20.dp))
                        AppleSectionHeader(title = "Top Genres")
                    }
                    val topGenres = s.topGenres
                    items(topGenres.size, key = { "genre_${topGenres[it].name}" }) { i ->
                        val g = topGenres[i]
                        ReplayRankRow(
                            rank = i + 1,
                            title = g.name,
                            subtitle = null,
                            trailing = formatListeningDuration(g.totalSeconds),
                            coverArtPath = null,
                        )
                    }
                }

                // ── Listening pattern (spec 80–82) ──────────────────────────
                val patternLine = buildString {
                    s.peakHour?.let { h ->
                        append("You listen most around ${hourLabel(h)}.")
                        s.biggestDay?.let { d ->
                            // The actual calendar date, year included — a month
                            // name is not a day (spec 104/192).
                            append(" Biggest day was ${prettyDayWithYear(d.dateString)}.")
                        }
                    }
                }
                if (patternLine.isNotBlank()) {
                    item {
                        Spacer(Modifier.height(24.dp))
                        Text(
                            patternLine,
                            fontFamily = Inter,
                            fontSize = 14.sp,
                            color = GratiaTheme.colors.textSecondary,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                    }
                }
            }
        }
    }
}

/** One ranked row of the Replay lists (spec 89–92). */
@Composable
private fun ReplayRankRow(
    rank: Int,
    title: String,
    subtitle: String?,
    trailing: String,
    coverArtPath: String?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "$rank",
            fontFamily = SpaceGrotesk,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = GratiaTheme.colors.textSecondary,
            modifier = Modifier.width(24.dp)
        )
        CoverArtImage(
            coverArtPath = coverArtPath,
            title = title,
            artist = subtitle ?: "",
            size = 46.dp,
            cornerRadius = 6.dp,
            fontSize = 14.sp
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontFamily = SpaceGrotesk,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = GratiaTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    fontFamily = Inter,
                    fontSize = 12.sp,
                    color = GratiaTheme.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Text(
            trailing,
            fontFamily = Inter,
            fontSize = 13.sp,
            color = GratiaTheme.colors.textSecondary
        )
    }
}

/** Loads the top song's row for hero artwork, off the snapshot. */
private data class SongRowData(
    val track: TrackListenSummary,
    val song: SongEntity?,
)

private fun hourLabel(hour: Int): String {
    val h = ((hour % 24) + 24) % 24
    return when {
        h == 0 -> "midnight"
        h < 12 -> "$h AM"
        h == 12 -> "noon"
        else -> "${h - 12} PM"
    }
}

private fun prettyDayWithYear(dateString: String): String = runCatching {
    val d = java.time.LocalDate.parse(dateString)
    "${d.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${d.dayOfMonth}, ${d.year}"
}.getOrDefault(dateString)
