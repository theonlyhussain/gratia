package com.gratia.music.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gratia.music.player.AudioRoute
import com.gratia.music.ui.components.VolumeSlider
import com.gratia.music.ui.theme.GratiaTheme
import com.gratia.music.ui.theme.Inter
import com.gratia.music.ui.theme.SpaceGrotesk

/**
 * The audio output panel: the active device with its real system volume, the
 * pipeline line the player already earns honestly, and the other routes.
 *
 * The volume is [android.media.AudioManager.STREAM_MUSIC] — read, written and
 * observed, never a private 0..100 number that happens to look like one. The
 * pipeline line is composed from what the decoder reports; when the player has
 * nothing measured to show, the line is omitted rather than decorated.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaOutputBottomSheet(
    routes: List<AudioRoute>,
    onRouteSelected: (AudioRoute) -> Unit,
    onDismiss: () -> Unit,
    pipelineSummary: String? = null,
    upgraded: Boolean = false,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = GratiaTheme.colors.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = GratiaTheme.spacing.large)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Audio output",
                fontFamily = SpaceGrotesk,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = GratiaTheme.colors.textPrimary,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            val active = routes.firstOrNull { it.isSelected }

            // The device actually receiving audio, and the fact of it — the
            // panel opens with the real volume under it, not a fake scale.
            if (active != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(GratiaTheme.shapes.medium)
                        .background(GratiaTheme.colors.accent.copy(alpha = 0.10f))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val icon = routeIcon(active.type)
                    Surface(
                        shape = CircleShape,
                        color = GratiaTheme.colors.accent,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = active.name,
                            fontFamily = Inter,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            color = GratiaTheme.colors.textPrimary,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "Playing here",
                            fontFamily = Inter,
                            fontSize = 12.sp,
                            color = GratiaTheme.colors.textSecondary
                        )
                    }
                }

                Spacer(Modifier.height(6.dp))
                VolumeSlider(
                    activeColor = GratiaTheme.colors.accent,
                    inactiveColor = GratiaTheme.colors.textSecondary
                )
            }

            // The audio pipeline, in measured facts only. "Upgraded" is only
            // passed when the player actually swapped streams — see
            // PlayerManager.isUpgradedStream.
            if (pipelineSummary != null) {
                Spacer(Modifier.height(4.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(GratiaTheme.shapes.medium)
                        .background(GratiaTheme.colors.surfaceHover)
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = "Audio Pipeline",
                        fontFamily = Inter,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = GratiaTheme.colors.textSecondary
                    )
                    Text(
                        text = if (upgraded) "Quality upgraded · $pipelineSummary" else pipelineSummary,
                        fontFamily = Inter,
                        fontSize = 13.sp,
                        color = GratiaTheme.colors.textPrimary,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            if (routes.size > 1) {
                Text(
                    text = "Other devices",
                    fontFamily = Inter,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = GratiaTheme.colors.textSecondary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(routes.filter { !it.isSelected }) { route ->
                        AudioRouteItem(
                            route = route,
                            onClick = {
                                onRouteSelected(route)
                                onDismiss()
                            }
                        )
                    }
                }
            }
        }
    }
}

private fun routeIcon(type: AudioRoute.RouteType) = when (type) {
    AudioRoute.RouteType.PHONE -> Icons.Default.Smartphone
    AudioRoute.RouteType.BLUETOOTH -> Icons.Default.Bluetooth
    AudioRoute.RouteType.WIRED -> Icons.Default.Headphones
    AudioRoute.RouteType.USB -> Icons.Default.Cable
    AudioRoute.RouteType.CAST -> Icons.Default.Cast
    AudioRoute.RouteType.UNKNOWN -> Icons.Default.Speaker
}

@Composable
private fun AudioRouteItem(
    route: AudioRoute,
    onClick: () -> Unit
) {
    val isSelected = route.isSelected

    val subtitle = when (route.type) {
        AudioRoute.RouteType.PHONE -> "Current device"
        AudioRoute.RouteType.BLUETOOTH -> "Bluetooth"
        AudioRoute.RouteType.WIRED -> "Wired"
        AudioRoute.RouteType.USB -> "USB Audio"
        AudioRoute.RouteType.CAST -> "Cast"
        AudioRoute.RouteType.UNKNOWN -> ""
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GratiaTheme.shapes.medium)
            .background(if (isSelected) GratiaTheme.colors.accent.copy(alpha = 0.15f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (isSelected) GratiaTheme.colors.accent else GratiaTheme.colors.surfaceHover),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = routeIcon(route.type),
                contentDescription = null,
                tint = if (isSelected) Color.White else GratiaTheme.colors.textSecondary,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = route.name,
                fontFamily = Inter,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                fontSize = 15.sp,
                color = if (isSelected) GratiaTheme.colors.accent else GratiaTheme.colors.textPrimary
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    fontFamily = Inter,
                    fontSize = 12.sp,
                    color = GratiaTheme.colors.textSecondary,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Selected",
                tint = GratiaTheme.colors.accent,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
