package com.gratia.music.player

import android.content.Context
import androidx.mediarouter.media.MediaControlIntent
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ConnectedDevice(
    val id: String,
    val name: String,
    val type: Int, // MediaRouter DEVICE_TYPE or Custom DEVICE_TYPE_BLUETOOTH
    val isCurrent: Boolean,
    val routeInfo: MediaRouter.RouteInfo? = null
) {
    companion object {
        const val DEVICE_TYPE_BLUETOOTH = 3
    }
}

class DeviceManager(private val context: Context) {

    private val mediaRouter = MediaRouter.getInstance(context)
    private val routeSelector = MediaRouteSelector.Builder()
        .addControlCategory(MediaControlIntent.CATEGORY_LIVE_AUDIO)
        .build()

    private val _connectedDevices = MutableStateFlow<List<ConnectedDevice>>(emptyList())
    val connectedDevices: StateFlow<List<ConnectedDevice>> = _connectedDevices.asStateFlow()

    private val mediaRouterCallback = object : MediaRouter.Callback() {
        override fun onRouteAdded(router: MediaRouter, route: MediaRouter.RouteInfo) {
            updateDevices()
        }

        override fun onRouteRemoved(router: MediaRouter, route: MediaRouter.RouteInfo) {
            updateDevices()
        }

        override fun onRouteChanged(router: MediaRouter, route: MediaRouter.RouteInfo) {
            updateDevices()
        }

        override fun onRouteSelected(router: MediaRouter, route: MediaRouter.RouteInfo, reason: Int) {
            updateDevices()
        }

        override fun onRouteUnselected(router: MediaRouter, route: MediaRouter.RouteInfo, reason: Int) {
            updateDevices()
        }
    }

    fun startListening() {
        mediaRouter.addCallback(
            routeSelector,
            mediaRouterCallback,
            MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY or MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN
        )
        updateDevices()
    }

    fun stopListening() {
        mediaRouter.removeCallback(mediaRouterCallback)
    }

    fun updateDevices() {
        val routes = mediaRouter.routes

        val allMapped = mutableListOf<ConnectedDevice>()

        routes.forEach { route ->
            if (route.matchesSelector(routeSelector) || route.isDefault || route.isBluetooth) {
                // Determine a user-friendly name
                val rawName = route.name ?: ""
                val name = if (route.isDefault && rawName.equals("Phone", ignoreCase = true)) {
                    "This phone"
                } else {
                    rawName
                }

                // If deviceType is unknown but it's a bluetooth route, mark it as bluetooth
                val type = if (route.isBluetooth && route.deviceType == MediaRouter.RouteInfo.DEVICE_TYPE_UNKNOWN) {
                    ConnectedDevice.DEVICE_TYPE_BLUETOOTH
                } else {
                    route.deviceType
                }

                allMapped.add(
                    ConnectedDevice(
                        id = route.id,
                        name = name.takeIf { it.isNotBlank() } ?: "Unknown Device",
                        type = type,
                        isCurrent = route.isSelected,
                        routeInfo = route
                    )
                )
            }
        }

        // Deduplicate
        val uniqueDevices = allMapped.distinctBy { it.id }

        // Sort: Current device first, then phone speaker, then alphabetical
        _connectedDevices.value = uniqueDevices.sortedWith(
            compareBy<ConnectedDevice> { !it.isCurrent }
                .thenBy { it.name != "This phone" }
                .thenBy { it.name }
        )
    }
}
