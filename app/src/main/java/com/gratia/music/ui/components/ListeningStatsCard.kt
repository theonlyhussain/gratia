package com.gratia.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gratia.music.data.model.ArtistListenSummary
import com.gratia.music.ui.theme.GratiaTheme
import com.gratia.music.ui.theme.Inter

@Composable
fun ListeningStatsCard(
    totalListeningSeconds: Long,
    topArtists: List<ArtistListenSummary>,
    modifier: Modifier = Modifier
) {
    if (totalListeningSeconds == 0L && topArtists.isEmpty()) return

    val hours = totalListeningSeconds / 3600
    val minutes = (totalListeningSeconds % 3600) / 60
    
    val timeString = if (hours > 0) {
        "${hours}h ${minutes}m"
    } else {
        "${minutes}m"
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)),
        color = GratiaTheme.colors.surface,
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        imageVector = Icons.Default.Equalizer,
                        contentDescription = null,
                        tint = GratiaTheme.colors.accent,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        "Listening Stats",
                        fontFamily = Inter,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = GratiaTheme.colors.textPrimary
                    )
                }
                Text(
                    "Today",
                    fontFamily = Inter,
                    fontSize = 12.sp,
                    color = GratiaTheme.colors.textSecondary
                )
            }
            
            Spacer(Modifier.height(20.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Time Listened",
                        fontFamily = Inter,
                        fontSize = 13.sp,
                        color = GratiaTheme.colors.textSecondary
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        timeString,
                        fontFamily = Inter,
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp,
                        color = GratiaTheme.colors.textPrimary
                    )
                }
                
                if (topArtists.isNotEmpty()) {
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                        Text(
                            "Top Artist",
                            fontFamily = Inter,
                            fontSize = 13.sp,
                            color = GratiaTheme.colors.textSecondary
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            topArtists.first().artist,
                            fontFamily = Inter,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = GratiaTheme.colors.textPrimary,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }
            }
            
            if (topArtists.size > 1) {
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    topArtists.drop(1).take(2).forEach { artistObj ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = GratiaTheme.colors.background,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                artistObj.artist,
                                fontFamily = Inter,
                                fontSize = 12.sp,
                                color = GratiaTheme.colors.textSecondary,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}
