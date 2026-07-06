package com.gratia.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
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
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListeningCalendarBottomSheet(
    summaries: List<DailyListeningSummary>,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    // We need the last 30 days
    val today = LocalDate.now()
    val last30Days = (29 downTo 0).map { today.minusDays(it.toLong()) }
    val summaryMap = summaries.associateBy { it.date }
    
    var selectedDate by remember { mutableStateOf(today) }

    val hasData = summaries.any { it.listeningSeconds > 0 }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = GratiaTheme.colors.background,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Text(
                text = "Listening Calendar",
                color = GratiaTheme.colors.textPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(16.dp))

            if (!hasData) {
                Text(
                    text = "No listening history yet.\nPlay songs to start building your 30-day calendar.",
                    color = GratiaTheme.colors.textSecondary,
                    fontSize = 14.sp
                )
            } else {
                // Top stats
                val totalSongs = summaries.sumOf { it.songsPlayed }
                val totalMinutes = summaries.sumOf { it.listeningSeconds } / 60
                val hrs = totalMinutes / 60
                val mins = totalMinutes % 60
                val timeStr = if (hrs > 0) "${hrs}h ${mins}m" else "${mins}m"
                
                // Calculate streak
                var streak = 0
                for (date in last30Days.reversed()) {
                    val s = summaryMap[date]
                    if (s != null && s.listeningSeconds > 0) streak++ else break
                }
                
                // Most active
                val mostActive = summaries.maxByOrNull { it.listeningSeconds }?.date?.dayOfWeek?.name?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "None"

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    StatBox("Songs played", totalSongs.toString())
                    StatBox("Listened", timeStr)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    StatBox("Current streak", "$streak days")
                    StatBox("Most active", mostActive)
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "Last 30 days",
                color = GratiaTheme.colors.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            // Heatmap Grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(6), // 5 rows x 6 columns = 30 days
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(last30Days) { date ->
                    val summary = summaryMap[date]
                    val intensity = getIntensityLevel(summary?.listeningSeconds ?: 0L)
                    val isSelected = date == selectedDate
                    
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(getCalendarColor(intensity))
                            .border(
                                width = if (isSelected) 2.dp else if (intensity == 0) 1.dp else 0.dp,
                                color = if (isSelected) GratiaTheme.colors.accent else if (intensity == 0) GratiaTheme.colors.surfaceHover else Color.Transparent,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { selectedDate = date }
                    ) {
                        // Optional: tiny number for date
                        Text(
                            text = date.dayOfMonth.toString(),
                            color = if (intensity > 2) GratiaTheme.colors.textPrimary else GratiaTheme.colors.textPrimary.copy(alpha = 0.5f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Selected Day Details
            val selectedSummary = summaryMap[selectedDate]
            val formatter = DateTimeFormatter.ofPattern("MMMM d")
            Text(
                text = selectedDate.format(formatter),
                color = GratiaTheme.colors.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            if (selectedSummary == null || selectedSummary.listeningSeconds == 0L) {
                Text(
                    text = "No listening activity on this day.",
                    color = GratiaTheme.colors.textSecondary,
                    fontSize = 14.sp
                )
            } else {
                val dMinutes = selectedSummary.listeningSeconds / 60
                val dHrs = dMinutes / 60
                val dMins = dMinutes % 60
                val dTimeStr = if (dHrs > 0) "${dHrs}h ${dMins}m" else "${dMins}m"
                
                Text(text = "${selectedSummary.songsPlayed} songs played", color = GratiaTheme.colors.textSecondary, fontSize = 14.sp)
                Text(text = "$dTimeStr listened", color = GratiaTheme.colors.textSecondary, fontSize = 14.sp)
                if (selectedSummary.lyricsOpenedCount > 0) {
                    Text(text = "Lyrics opened: ${selectedSummary.lyricsOpenedCount} times", color = GratiaTheme.colors.textSecondary, fontSize = 14.sp)
                }
                if (selectedSummary.songsAdded > 0) {
                    Text(text = "Songs added: ${selectedSummary.songsAdded}", color = GratiaTheme.colors.textSecondary, fontSize = 14.sp)
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Listening stats stay on this device.",
                color = GratiaTheme.colors.textSecondary,
                fontSize = 12.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun StatBox(label: String, value: String) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(GratiaTheme.colors.surface)
            .padding(12.dp)
            .widthIn(min = 140.dp)
    ) {
        Text(text = label, color = GratiaTheme.colors.textSecondary, fontSize = 12.sp)
        Text(text = value, color = GratiaTheme.colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}
