package com.gratia.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.gratia.music.GratiaApp
import com.gratia.music.data.model.PlaybackHistoryEntity
import com.gratia.music.data.repository.PlaybackHistoryRepository
import com.gratia.music.player.PlayerViewModel
import com.gratia.music.ui.components.AppleLargeTitleHeader
import com.gratia.music.ui.components.CoverArtImage
import com.gratia.music.ui.components.formatListeningDuration
import com.gratia.music.ui.components.GratiaEmptyState
import com.gratia.music.ui.theme.GratiaTheme
import com.gratia.music.ui.theme.Inter
import com.gratia.music.ui.theme.SpaceGrotesk
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * The full listening timeline behind Home's Recently Played shelf (spec 22–24).
 *
 * One chronological list of every recorded playback — online, local and
 * downloaded in one source-aware history, grouped by the days they happened.
 * A row plays its recorded track; a row can be removed; the whole timeline can
 * be cleared with a confirmation that says exactly what clearing does.
 *
 * Clearing this timeline deliberately does NOT touch the listening aggregates
 * in `listening_events` (spec 123): the stats card, Replay and the daily
 * aggregates are measurements of time spent listening, and "remove this play
 * from my timeline" is not a request to rewrite them. The dialog states the
 * policy instead of leaving the listener to guess.
 */
@Composable
fun ListeningHistoryScreen(
    playerViewModel: PlayerViewModel,
    onNavigateBack: () -> Unit
) {
    val historyRepository = remember {
        PlaybackHistoryRepository(GratiaApp.instance.database.playbackHistoryDao())
    }
    val entries by historyRepository.all(limit = 2000).collectAsState(initial = emptyList())
    var showClearDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val bottomInset = com.gratia.music.ui.LocalBottomPadding.current

    // Grouped by real date boundaries, computed once per list change — the
    // grouping is presentation, so it lives here and not in the repository.
    val groups = remember(entries) { groupByDay(entries) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(GratiaTheme.colors.background)
            .statusBarsPadding(),
        contentPadding = PaddingValues(bottom = bottomInset + GratiaTheme.spacing.heroLarge)
    ) {
        item {
            AppleLargeTitleHeader(
                title = "Listening History",
                onBack = onNavigateBack
            )
            Spacer(Modifier.height(8.dp))
        }

        if (entries.isEmpty()) {
            item {
                Spacer(Modifier.height(48.dp))
                GratiaEmptyState(
                    icon = androidx.compose.material.icons.Icons.Default.History,
                    headline = "Nothing played yet",
                    description = "Songs you listen to will show up here, whichever source they come from.",
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else {
            groups.forEach { group ->
                item(key = "hdr_${group.label}") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = group.label,
                            fontFamily = Inter,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = GratiaTheme.colors.textSecondary
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = formatListeningDuration(group.entries.sumOf { it.listenedSeconds }),
                            fontFamily = Inter,
                            fontSize = 12.sp,
                            color = GratiaTheme.colors.textSecondary
                        )
                    }
                }
                itemsIndexed(
                    group.entries,
                    key = { _, entry -> entry.id }
                ) { _, entry ->
                    HistoryRow(
                        entry = entry,
                        onPlay = { playerViewModel.playFromHistory(entry) },
                        onRemove = {
                            scope.launch { historyRepository.delete(entry.id) }
                        }
                    )
                }
            }

            item {
                Spacer(Modifier.height(24.dp))
                TextButton(
                    onClick = { showClearDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Text(
                        "Clear all history",
                        color = GratiaTheme.colors.error,
                        fontFamily = Inter,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = {
                Text(
                    "Clear listening history?",
                    color = GratiaTheme.colors.textPrimary,
                    fontFamily = SpaceGrotesk,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    "This removes every entry from your playback timeline — the Recently Played shelf and this list. Your listening statistics are measurements of real time and stay as they are.",
                    color = GratiaTheme.colors.textSecondary,
                    fontFamily = Inter
                )
            },
            containerColor = GratiaTheme.colors.surface,
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { historyRepository.clear() }
                    showClearDialog = false
                }) {
                    Text("Clear history", color = GratiaTheme.colors.error, fontFamily = Inter, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel", color = GratiaTheme.colors.textPrimary, fontFamily = Inter)
                }
            }
        )
    }
}

/** One playback, one row: snapshot artwork, truthful source, real time. */
@Composable
private fun HistoryRow(
    entry: PlaybackHistoryEntity,
    onPlay: () -> Unit,
    onRemove: () -> Unit
) {
    val sourceLabel = when (entry.source) {
        com.gratia.music.data.model.MusicSource.YOUTUBE_MUSIC.name -> "YouTube Music"
        com.gratia.music.data.model.MusicSource.DOWNLOADED_REMOTE.name -> "Downloaded"
        com.gratia.music.data.model.MusicSource.LOCAL.name -> "Local"
        else -> null
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay)
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CoverArtImage(
            coverArtPath = entry.artworkUrl,
            title = entry.title,
            artist = entry.artist,
            size = 48.dp,
            cornerRadius = 8.dp,
            fontSize = 14.sp
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = entry.title,
                fontFamily = SpaceGrotesk,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = GratiaTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = entry.artist,
                    fontFamily = Inter,
                    fontSize = 12.sp,
                    color = GratiaTheme.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (sourceLabel != null) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "· $sourceLabel",
                        fontFamily = Inter,
                        fontSize = 11.sp,
                        color = GratiaTheme.colors.textSecondary.copy(alpha = 0.8f)
                    )
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Remove from history",
            tint = GratiaTheme.colors.textSecondary.copy(alpha = 0.5f),
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(18.dp))
                .clickable(onClick = onRemove)
                .padding(8.dp)
        )
    }
}

/**
 * Groups plays into real calendar buckets — Today, Yesterday, this week,
 * then plain dates (spec 22). Pure and deterministic.
 */
/** A day's slice of the timeline. */
internal data class HistoryDayGroup(val label: String, val entries: List<PlaybackHistoryEntity>)

internal fun groupByDay(entries: List<PlaybackHistoryEntity>): List<HistoryDayGroup> {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val yesterday = today.minusDays(1)

    val buckets = LinkedHashMap<String, MutableList<Pair<PlaybackHistoryEntity, LocalDate>>>()
    for (entry in entries) {
        val date = Instant.ofEpochMilli(entry.playedAt).atZone(zone).toLocalDate()
        val label = when {
            date == today -> "Today"
            date == yesterday -> "Yesterday"
            date.isAfter(today.minusDays(7)) ->
                date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
            else -> date.format(DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.getDefault()))
        }
        buckets.getOrPut(label) { mutableListOf() }.add(entry to date)
    }

    return buckets.map { (label, list) ->
        HistoryDayGroup(label, list.map { it.first })
    }
}
