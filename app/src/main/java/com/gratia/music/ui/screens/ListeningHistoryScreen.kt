package com.gratia.music.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gratia.music.GratiaApp
import com.gratia.music.data.model.ArtistListenSummary
import com.gratia.music.data.model.DailyListeningSummary
import com.gratia.music.data.model.TrackListenSummary
import com.gratia.music.data.repository.ListeningEventRepository
import com.gratia.music.ui.components.AppleLargeTitleHeader
import com.gratia.music.ui.theme.GratiaTheme
import com.gratia.music.ui.theme.Inter
import com.gratia.music.ui.theme.SpaceGrotesk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.max

enum class TimePeriod(val displayName: String) {
    TODAY("Today"),
    WEEK("Week"),
    MONTH("Month"),
    ALL_TIME("All Time")
}

@Composable
fun ListeningHistoryScreen(
    onNavigateBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val listeningRepo = remember { ListeningEventRepository(GratiaApp.instance.database.listeningEventDao()) }

    var selectedPeriod by remember { mutableStateOf(TimePeriod.TODAY) }
    var totalSeconds by remember { mutableStateOf(0L) }
    var topArtists by remember { mutableStateOf<List<ArtistListenSummary>>(emptyList()) }
    var topTracks by remember { mutableStateOf<List<TrackListenSummary>>(emptyList()) }
    var dailySummaries by remember { mutableStateOf<List<DailyListeningSummary>>(emptyList()) }
    var showClearDialog by remember { mutableStateOf(false) }

    LaunchedEffect(selectedPeriod) {
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val startTimestamp = when (selectedPeriod) {
                TimePeriod.TODAY -> LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                TimePeriod.WEEK -> LocalDate.now().minusDays(6).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                TimePeriod.MONTH -> LocalDate.now().minusDays(29).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                TimePeriod.ALL_TIME -> 0L
            }

            totalSeconds = listeningRepo.getTotalListeningSeconds(startTimestamp, now)
            topArtists = listeningRepo.getTopArtists(startTimestamp, now, limit = 5)
            topTracks = listeningRepo.getTopTracks(startTimestamp, now, limit = 5)
            
            val days = when (selectedPeriod) {
                TimePeriod.TODAY -> 1
                TimePeriod.WEEK -> 7
                TimePeriod.MONTH -> 30
                TimePeriod.ALL_TIME -> 30 // Fallback for charts if needed
            }
            dailySummaries = listeningRepo.getDailySummaries(days)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(GratiaTheme.colors.background)
            .statusBarsPadding(),
        contentPadding = PaddingValues(bottom = GratiaTheme.spacing.heroLarge)
    ) {
        item {
            AppleLargeTitleHeader(
                title = "Listening History",
                onBack = onNavigateBack
            )
            Spacer(Modifier.height(16.dp))
        }

        item {
            // Time Period Selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                TimePeriod.values().forEach { period ->
                    val isSelected = selectedPeriod == period
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isSelected) GratiaTheme.colors.accent else GratiaTheme.colors.surface)
                            .clickable { selectedPeriod = period }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = period.displayName,
                            color = if (isSelected) GratiaTheme.colors.background else GratiaTheme.colors.textPrimary,
                            fontFamily = Inter,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 14.sp
                        )
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }

        if (totalSeconds == 0L) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No listening data yet", fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = GratiaTheme.colors.textPrimary)
                        Spacer(Modifier.height(8.dp))
                        Text("Start listening to build your listening statistics.", fontFamily = Inter, fontSize = 14.sp, color = GratiaTheme.colors.textSecondary, textAlign = TextAlign.Center)
                    }
                }
            }
        } else {
            item {
                // Overview Visualization
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(GratiaTheme.colors.surface)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val hours = totalSeconds / 3600
                        val mins = (totalSeconds % 3600) / 60
                        
                        Text(
                            text = if (hours > 0) "${hours}h ${mins}m" else "${mins}m",
                            fontFamily = SpaceGrotesk,
                            fontWeight = FontWeight.Bold,
                            fontSize = 32.sp,
                            color = GratiaTheme.colors.accent
                        )
                        Text(
                            text = "listened ${selectedPeriod.displayName.lowercase()}",
                            fontFamily = Inter,
                            fontSize = 14.sp,
                            color = GratiaTheme.colors.textSecondary
                        )
                        
                        if (selectedPeriod == TimePeriod.WEEK && dailySummaries.isNotEmpty()) {
                            Spacer(Modifier.height(24.dp))
                            WeekBarChart(dailySummaries = dailySummaries)
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }

            item {
                // Summary Stats
                val daysCount = when(selectedPeriod) {
                    TimePeriod.TODAY -> 1
                    TimePeriod.WEEK -> 7
                    TimePeriod.MONTH -> 30
                    TimePeriod.ALL_TIME -> max(1, dailySummaries.size)
                }
                val avgMins = (totalSeconds / 60) / daysCount
                
                var mostActiveDayStr = "-"
                if (dailySummaries.isNotEmpty()) {
                    val maxDay = dailySummaries.maxByOrNull { it.listeningSeconds }
                    if (maxDay != null && maxDay.listeningSeconds > 0) {
                        mostActiveDayStr = maxDay.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    SummaryCard(title = "Daily Avg", value = "${avgMins}m", modifier = Modifier.weight(1f))
                    SummaryCard(title = "Most Active", value = mostActiveDayStr, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(24.dp))
            }

            if (topArtists.isNotEmpty()) {
                item {
                    Text(
                        "TOP ARTISTS",
                        fontFamily = Inter,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                        color = GratiaTheme.colors.textSecondary,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(GratiaTheme.colors.surface)
                            .padding(vertical = 8.dp)
                    ) {
                        topArtists.forEachIndexed { index, artistSummary ->
                            val percent = if (totalSeconds > 0) ((artistSummary.totalSeconds.toFloat() / totalSeconds) * 100).toInt() else 0
                            val h = artistSummary.totalSeconds / 3600
                            val m = (artistSummary.totalSeconds % 3600) / 60
                            val timeStr = if (h > 0) "${h}h ${m}m" else "${m}m"

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${index + 1}",
                                    fontFamily = SpaceGrotesk,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = GratiaTheme.colors.textSecondary,
                                    modifier = Modifier.width(24.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = artistSummary.artist,
                                        fontFamily = Inter,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp,
                                        color = GratiaTheme.colors.textPrimary
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    LinearProgressIndicator(
                                        progress = { percent / 100f },
                                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                                        color = GratiaTheme.colors.accent,
                                        trackColor = GratiaTheme.colors.background
                                    )
                                }
                                Spacer(Modifier.width(16.dp))
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = timeStr,
                                        fontFamily = SpaceGrotesk,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = GratiaTheme.colors.textPrimary
                                    )
                                    Text(
                                        text = "$percent%",
                                        fontFamily = Inter,
                                        fontSize = 12.sp,
                                        color = GratiaTheme.colors.textSecondary
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }

            if (topTracks.isNotEmpty()) {
                item {
                    Text(
                        "TOP TRACKS",
                        fontFamily = Inter,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                        color = GratiaTheme.colors.textSecondary,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(GratiaTheme.colors.surface)
                            .padding(vertical = 8.dp)
                    ) {
                        topTracks.forEachIndexed { index, trackSummary ->
                            val h = trackSummary.totalSeconds / 3600
                            val m = (trackSummary.totalSeconds % 3600) / 60
                            val timeStr = if (h > 0) "${h}h ${m}m" else "${m}m"

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${index + 1}",
                                    fontFamily = SpaceGrotesk,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = GratiaTheme.colors.textSecondary,
                                    modifier = Modifier.width(24.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = trackSummary.title,
                                        fontFamily = Inter,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp,
                                        color = GratiaTheme.colors.textPrimary,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = trackSummary.artist,
                                        fontFamily = Inter,
                                        fontSize = 12.sp,
                                        color = GratiaTheme.colors.textSecondary,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(Modifier.width(16.dp))
                                Text(
                                    text = timeStr,
                                    fontFamily = SpaceGrotesk,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = GratiaTheme.colors.textPrimary
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(32.dp))
                }
            }
        }

        item {
            // Clear History Button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                TextButton(
                    onClick = { showClearDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Clear Listening History",
                        color = GratiaTheme.colors.error,
                        fontFamily = Inter,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            Spacer(Modifier.height(64.dp))
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = {
                Text(
                    "Clear Listening History?",
                    color = GratiaTheme.colors.textPrimary,
                    fontFamily = SpaceGrotesk,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    "This will remove your listening statistics and listening-based recommendation data. Your music, playlists, profile, and app settings will not be affected.",
                    color = GratiaTheme.colors.textSecondary,
                    fontFamily = Inter
                )
            },
            containerColor = GratiaTheme.colors.surface,
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            listeningRepo.clearHistory()
                            
                            // Re-fetch to clear UI
                            totalSeconds = 0L
                            topArtists = emptyList()
                            topTracks = emptyList()
                            dailySummaries = emptyList()
                        }
                        showClearDialog = false
                    }
                ) {
                    Text("Clear History", color = GratiaTheme.colors.error, fontFamily = Inter, fontWeight = FontWeight.Bold)
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

@Composable
fun SummaryCard(title: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(GratiaTheme.colors.surface)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            fontFamily = SpaceGrotesk,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            color = GratiaTheme.colors.textPrimary
        )
        Text(
            text = title,
            fontFamily = Inter,
            fontSize = 12.sp,
            color = GratiaTheme.colors.textSecondary
        )
    }
}

@Composable
fun WeekBarChart(dailySummaries: List<DailyListeningSummary>) {
    // A simple representation of a bar chart
    val maxSeconds = dailySummaries.maxOfOrNull { it.listeningSeconds } ?: 1L
    
    // Sort so it's chronologically ordered for the week
    val sorted = dailySummaries.sortedBy { it.dateString }
    
    Row(
        modifier = Modifier.fillMaxWidth().height(100.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom
    ) {
        sorted.forEach { summary ->
            val heightFraction = if (maxSeconds > 0) summary.listeningSeconds.toFloat() / maxSeconds else 0f
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
                modifier = Modifier.fillMaxHeight()
            ) {
                Box(
                    modifier = Modifier
                        .width(24.dp)
                        .fillMaxHeight(heightFraction.coerceAtLeast(0.05f))
                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                        .background(GratiaTheme.colors.accent)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = summary.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(3),
                    fontFamily = Inter,
                    fontSize = 10.sp,
                    color = GratiaTheme.colors.textSecondary
                )
            }
        }
    }
}
