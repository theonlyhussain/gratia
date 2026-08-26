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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gratia.music.player.AudioRoute
import com.gratia.music.ui.theme.GratiaTheme
import com.gratia.music.ui.theme.Inter
import com.gratia.music.ui.theme.SpaceGrotesk

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaOutputBottomSheet(
    routes: List<AudioRoute>,
    onRouteSelected: (AudioRoute) -> Unit,
    onDismiss: () -> Unit
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
                text = "Connect",
                fontFamily = SpaceGrotesk,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = GratiaTheme.colors.textPrimary,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(routes) { route ->
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

@Composable
private fun AudioRouteItem(
    route: AudioRoute,
    onClick: () -> Unit
) {
    val isSelected = route.isSelected
    
    val icon = when (route.type) {
        AudioRoute.RouteType.PHONE -> Icons.Default.Smartphone
        AudioRoute.RouteType.BLUETOOTH -> Icons.Default.Bluetooth
        AudioRoute.RouteType.WIRED -> Icons.Default.Headphones
        AudioRoute.RouteType.USB -> Icons.Default.Cable
        AudioRoute.RouteType.CAST -> Icons.Default.Cast
        AudioRoute.RouteType.UNKNOWN -> Icons.Default.Speaker
    }
    
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
            .padding(16.dp),
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
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) Color.White else GratiaTheme.colors.textSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = route.name,
                fontFamily = Inter,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                fontSize = 16.sp,
                color = if (isSelected) GratiaTheme.colors.accent else GratiaTheme.colors.textPrimary
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    fontFamily = Inter,
                    fontSize = 13.sp,
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
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
