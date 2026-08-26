package com.gratia.music.player

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import androidx.mediarouter.media.MediaControlIntent
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AudioRoute(
    val id: String,
    val name: String,
    val type: RouteType,
    val isSelected: Boolean,
    val mediaRoute: MediaRouter.RouteInfo? = null
) {
    enum class RouteType {
        PHONE, BLUETOOTH, WIRED, USB, CAST, UNKNOWN
    }
}

class MediaOutputManager(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val mediaRouter by lazy { MediaRouter.getInstance(context) }

    private val _availableRoutes = MutableStateFlow<List<AudioRoute>>(emptyList())
    val availableRoutes: StateFlow<List<AudioRoute>> = _availableRoutes.asStateFlow()

    private val _activeRoute = MutableStateFlow<AudioRoute>(createFallbackPhoneRoute())
    val activeRoute: StateFlow<AudioRoute> = _activeRoute.asStateFlow()

    private val selector = MediaRouteSelector.Builder()
        .addControlCategory(MediaControlIntent.CATEGORY_LIVE_AUDIO)
        .addControlCategory(MediaControlIntent.CATEGORY_REMOTE_PLAYBACK)
        .build()

    private val mediaRouterCallback = object : MediaRouter.Callback() {
        override fun onRouteSelected(router: MediaRouter, route: MediaRouter.RouteInfo, reason: Int) {
            updateState()
        }

        override fun onRouteChanged(router: MediaRouter, route: MediaRouter.RouteInfo) {
            updateState()
        }

        override fun onRouteAdded(router: MediaRouter, route: MediaRouter.RouteInfo) {
            updateState()
        }

        override fun onRouteRemoved(router: MediaRouter, route: MediaRouter.RouteInfo) {
            updateState()
        }
    }

    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            updateState()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            updateState()
        }
    }

    fun startTracking() {
        mediaRouter.addCallback(
            selector,
            mediaRouterCallback,
            MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY
        )
        audioManager.registerAudioDeviceCallback(audioDeviceCallback, Handler(Looper.getMainLooper()))
        updateState()
    }

    fun stopTracking() {
        mediaRouter.removeCallback(mediaRouterCallback)
        audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
    }

    fun selectRoute(route: AudioRoute) {
        route.mediaRoute?.let {
            mediaRouter.selectRoute(it)
        }
    }

    private fun updateState() {
        val hardwareDevices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        
        val hasBluetoothHardware = hardwareDevices.any {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
            it.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
            it.type == AudioDeviceInfo.TYPE_BLE_SPEAKER ||
            it.type == AudioDeviceInfo.TYPE_BLE_BROADCAST
        }

        val hasWiredHardware = hardwareDevices.any {
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET
        }

        val hasUsbHardware = hardwareDevices.any {
            it.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
            (it.type == AudioDeviceInfo.TYPE_USB_DEVICE && !it.productName.toString().contains("Debug", ignoreCase = true))
        }

        val routes = mutableListOf<AudioRoute>()
        val mrRoutes = mediaRouter.routes

        var activeRouteFromMR: AudioRoute? = null
        var phoneRoute: AudioRoute? = null

        for (mrRoute in mrRoutes) {
            val isDefaultOrPhone = mrRoute.isDefault || mrRoute.deviceType == MediaRouter.RouteInfo.DEVICE_TYPE_UNKNOWN && mrRoute.name.toString().contains("Phone", ignoreCase = true)
            
            val type = when {
                mrRoute.deviceType == 3 -> AudioRoute.RouteType.BLUETOOTH
                mrRoute.description?.contains("Bluetooth", ignoreCase = true) == true -> AudioRoute.RouteType.BLUETOOTH
                mrRoute.playbackType == MediaRouter.RouteInfo.PLAYBACK_TYPE_REMOTE -> AudioRoute.RouteType.CAST
                isDefaultOrPhone -> AudioRoute.RouteType.PHONE
                else -> AudioRoute.RouteType.UNKNOWN
            }

            // FILTERING STALE ROUTES: Ensure the specific route's name matches a connected hardware device
            if (type == AudioRoute.RouteType.BLUETOOTH) {
                val isHardwareConnected = hardwareDevices.any { hw ->
                    val hwType = hw.type
                    val isBTHardware = hwType == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                            hwType == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                            hwType == AudioDeviceInfo.TYPE_BLE_SPEAKER ||
                            hwType == AudioDeviceInfo.TYPE_BLE_BROADCAST
                    isBTHardware && (hw.productName?.toString() == mrRoute.name?.toString() || mrRoute.name?.toString()?.contains(hw.productName?.toString() ?: "", ignoreCase = true) == true)
                }
                if (!isHardwareConnected) {
                    continue 
                }
            }

            val audioRoute = AudioRoute(
                id = mrRoute.id,
                name = mrRoute.name,
                type = type,
                isSelected = mrRoute.isSelected,
                mediaRoute = mrRoute
            )

            if (type == AudioRoute.RouteType.PHONE) {
                phoneRoute = audioRoute
            } else if (type != AudioRoute.RouteType.UNKNOWN) {
                routes.add(audioRoute)
            }

            if (mrRoute.isSelected && audioRoute.type != AudioRoute.RouteType.UNKNOWN) {
                activeRouteFromMR = audioRoute
            }
        }

        if (hasWiredHardware && routes.none { it.type == AudioRoute.RouteType.WIRED }) {
            val wiredDevice = hardwareDevices.firstOrNull { it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET }
            routes.add(AudioRoute("wired", wiredDevice?.productName?.toString() ?: "Wired Headphones", AudioRoute.RouteType.WIRED, false, null))
        }

        if (hasUsbHardware && routes.none { it.type == AudioRoute.RouteType.USB }) {
            val usbDevice = hardwareDevices.firstOrNull { it.type == AudioDeviceInfo.TYPE_USB_HEADSET || (it.type == AudioDeviceInfo.TYPE_USB_DEVICE && !it.productName.toString().contains("Debug", ignoreCase = true)) }
            routes.add(AudioRoute("usb", usbDevice?.productName?.toString() ?: "USB Audio", AudioRoute.RouteType.USB, false, null))
        }

        if (phoneRoute == null) {
            phoneRoute = createFallbackPhoneRoute()
        }
        routes.add(0, phoneRoute)

        // If MediaRouter has an active CAST or properly verified BLUETOOTH route, use it.
        // Otherwise, fall back to what hardware is physically connected (Wired > USB > Phone).
        val trueActiveRoute = when {
            activeRouteFromMR?.type == AudioRoute.RouteType.BLUETOOTH -> activeRouteFromMR
            activeRouteFromMR?.type == AudioRoute.RouteType.CAST -> activeRouteFromMR
            hasWiredHardware -> routes.firstOrNull { it.type == AudioRoute.RouteType.WIRED } ?: phoneRoute
            hasUsbHardware -> routes.firstOrNull { it.type == AudioRoute.RouteType.USB } ?: phoneRoute
            else -> phoneRoute
        }

        val updatedRoutes = routes.map { it.copy(isSelected = it.id == trueActiveRoute.id) }.distinctBy { it.id }

        _availableRoutes.value = updatedRoutes
        _activeRoute.value = trueActiveRoute
    }

    private fun createFallbackPhoneRoute(): AudioRoute {
        return AudioRoute(
            id = "phone_fallback",
            name = "This phone",
            type = AudioRoute.RouteType.PHONE,
            isSelected = true,
            mediaRoute = null
        )
    }
}
