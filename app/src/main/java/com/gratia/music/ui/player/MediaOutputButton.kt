package com.gratia.music.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Speaker
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.mediarouter.app.MediaRouteButton
import androidx.mediarouter.media.MediaControlIntent
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import com.gratia.music.ui.theme.Inter

@Composable
fun MediaOutputButton(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val mediaRouter = remember { MediaRouter.getInstance(context) }
    
    // Default selector to pick up standard audio routes (Bluetooth, phone speaker, etc)
    val selector = remember {
        MediaRouteSelector.Builder()
            .addControlCategory(MediaControlIntent.CATEGORY_LIVE_AUDIO)
            .build()
    }

    var currentRouteName by remember { mutableStateOf(mediaRouter.selectedRoute.name) }
    
    // We keep a reference to the MediaRouteButton so we can performClick on it
    var routeButtonRef by remember { mutableStateOf<MediaRouteButton?>(null) }

    DisposableEffect(mediaRouter) {
        val callback = object : MediaRouter.Callback() {
            override fun onRouteSelected(router: MediaRouter, route: MediaRouter.RouteInfo, reason: Int) {
                currentRouteName = route.name
            }
            override fun onRouteChanged(router: MediaRouter, route: MediaRouter.RouteInfo) {
                currentRouteName = mediaRouter.selectedRoute.name
            }
            override fun onRouteAdded(router: MediaRouter, route: MediaRouter.RouteInfo) {
                currentRouteName = mediaRouter.selectedRoute.name
            }
            override fun onRouteRemoved(router: MediaRouter, route: MediaRouter.RouteInfo) {
                currentRouteName = mediaRouter.selectedRoute.name
            }
        }
        
        mediaRouter.addCallback(
            selector,
            callback,
            MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY
        )
        
        // Update initially in case it changed before the callback attached
        currentRouteName = mediaRouter.selectedRoute.name
        
        onDispose {
            mediaRouter.removeCallback(callback)
        }
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(32.dp))
            .background(Color.White.copy(alpha = 0.15f))
            .clickable { 
                routeButtonRef?.showDialog()
            }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Speaker,
            contentDescription = "Connected Device",
            tint = Color.White.copy(alpha = 0.8f),
            modifier = Modifier.size(20.dp)
        )
        
        Spacer(modifier = Modifier.width(8.dp))
        
        Text(
            text = currentRouteName,
            color = Color.White.copy(alpha = 0.9f),
            fontFamily = Inter,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 140.dp)
        )
        
        // Hidden Native MediaRouteButton
        AndroidView(
            factory = { ctx ->
                MediaRouteButton(ctx).apply {
                    routeSelector = selector
                    routeButtonRef = this
                }
            },
            modifier = Modifier.size(0.dp)
        )
    }
}
