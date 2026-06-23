package com.gratia.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gratia.music.data.model.DailyListeningSummary
import com.gratia.music.ui.theme.GratiaTheme
import java.time.LocalDate

@Composable
fun ListeningCalendarPreview(
    summaries: List<DailyListeningSummary>,
    onPreviewClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // We only need the last 7 days for the preview.
    val today = LocalDate.now()
    val last7Days = (6 downTo 0).map { today.minusDays(it.toLong()) }
    
    // Map dates to summaries
    val summaryMap = summaries.associateBy { it.date }
    
    val totalMinutes = summaries.sumOf { it.listeningSeconds } / 60
    val totalSongs = summaries.sumOf { it.songsPlayed }
    
    val hasData = summaries.any { it.listeningSeconds > 0 }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(GratiaTheme.colors.surfaceCard)
            .clickable { onPreviewClick() }
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Listening Calendar",
                    color = GratiaTheme.colors.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                if (hasData) {
                    val hrs = totalMinutes / 60
                    val mins = totalMinutes % 60
                    val timeStr = if (hrs > 0) "${hrs}h ${mins}m" else "${mins}m"
                    Text(
                        text = "Last 7 days • $totalSongs songs • $timeStr listened",
                        color = GratiaTheme.colors.textSecondary,
                        fontSize = 12.sp
                    )
                } else {
                    Text(
                        text = "Last 7 days • Play songs to build your calendar.",
                        color = GratiaTheme.colors.textSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            last7Days.forEach { date ->
                val summary = summaryMap[date]
                val listenedSeconds = summary?.listeningSeconds ?: 0L
                val intensity = getIntensityLevel(listenedSeconds)
                val dayName = date.dayOfWeek.name.take(1) // M, T, W...
                
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(getCalendarColor(intensity))
                            .border(
                                width = if (intensity == 0) 1.dp else 0.dp,
                                color = if (intensity == 0) GratiaTheme.colors.surfaceHover else Color.Transparent,
                                shape = RoundedCornerShape(8.dp)
                            )
                    ) {
                        Text(
                            text = dayName,
                            color = if (intensity > 2) GratiaTheme.colors.textOnDark else GratiaTheme.colors.textPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }
            }
        }
    }
}

fun getIntensityLevel(seconds: Long): Int {
    val minutes = seconds / 60
    return when {
        minutes == 0L -> 0
        minutes in 1..10 -> 1
        minutes in 11..30 -> 2
        minutes in 31..90 -> 3
        else -> 4
    }
}

@Composable
fun getCalendarColor(level: Int): Color {
    return when (level) {
        0 -> GratiaTheme.colors.cotton.copy(alpha = 0.5f)
        1 -> Color(0xFFDCA7A7) // pale warm red
        2 -> GratiaTheme.colors.cherryRed.copy(alpha = 0.7f)
        3 -> GratiaTheme.colors.cherryRed
        4 -> GratiaTheme.colors.maroon
        else -> GratiaTheme.colors.cotton
    }
}
