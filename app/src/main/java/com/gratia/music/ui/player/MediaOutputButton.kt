package com.gratia.music.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gratia.music.player.AudioRoute
import com.gratia.music.player.MediaOutputManager
import com.gratia.music.ui.LocalPlayerViewModel
import com.gratia.music.ui.theme.Inter

@Composable
fun MediaOutputButton(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    // Create and track MediaOutputManager
    val mediaOutputManager = remember { MediaOutputManager(context) }

    // The player's measured audio facts, for the sheet's pipeline line. Composed
    // from the decoder-reported format only — never a resolver's claims. The
    // button lives inside the expanded player, so the local is always provided.
    val playerViewModel = LocalPlayerViewModel.current
    val audioFormat = playerViewModel.audioFormat.collectAsState().value
    val isUpgraded = playerViewModel.isUpgradedStream.collectAsState().value
    val sourceLabel = remember(audioFormat, isUpgraded) {
        audioFormat?.let { fmt ->
            val codec = when (fmt.mimeType) {
                "audio/flac" -> "FLAC"
                "audio/alac" -> "ALAC"
                "audio/mp4a-latm" -> "AAC"
                "audio/mpeg" -> "MP3"
                "audio/ogg", "audio/vorbis" -> "OGG"
                "audio/opus" -> "Opus"
                "audio/x-wav" -> "WAV"
                else -> fmt.mimeType?.substringAfter("audio/")?.uppercase() ?: "Audio"
            }
            val khz = fmt.sampleRate / 1000f
            val rate = if (fmt.sampleRate > 0) " · ${if (khz % 1 == 0f) "${khz.toInt()}" else "$khz"} kHz" else ""
            val bits = if (fmt.bitDepth > 0) " · ${fmt.bitDepth}-bit" else ""
            "$codec$bits$rate"
        }
    }

    DisposableEffect(mediaOutputManager) {
        mediaOutputManager.startTracking()
        onDispose {
            mediaOutputManager.stopTracking()
        }
    }

    val activeRoute by mediaOutputManager.activeRoute.collectAsState()
    val availableRoutes by mediaOutputManager.availableRoutes.collectAsState()

    var showBottomSheet by remember { mutableStateOf(false) }

    val icon = when (activeRoute.type) {
        AudioRoute.RouteType.PHONE -> Icons.Default.Smartphone
        AudioRoute.RouteType.BLUETOOTH -> Icons.Default.Bluetooth
        AudioRoute.RouteType.WIRED -> Icons.Default.Headphones
        AudioRoute.RouteType.USB -> Icons.Default.Cable
        AudioRoute.RouteType.CAST -> Icons.Default.Cast
        AudioRoute.RouteType.UNKNOWN -> Icons.Default.Speaker
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(32.dp))
            .background(Color.White.copy(alpha = 0.15f))
            .clickable {
                showBottomSheet = true
            }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = "Connected Device",
            tint = Color.White.copy(alpha = 0.8f),
            modifier = Modifier.size(20.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = activeRoute.name,
            color = Color.White.copy(alpha = 0.9f),
            fontFamily = Inter,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
    }

    if (showBottomSheet) {
        // The pipeline line is composed in the expanded player's own terms —
        // measured codec and sample rate, and the upgrade flag only when the
        // player really swapped streams.
        MediaOutputBottomSheet(
            routes = availableRoutes,
            onRouteSelected = { route ->
                mediaOutputManager.selectRoute(route)
            },
            onDismiss = { showBottomSheet = false },
            pipelineSummary = sourceLabel,
            upgraded = isUpgraded,
        )
    }
}
