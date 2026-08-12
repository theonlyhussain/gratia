package com.gratia.music.player

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ConnectedDevice(
    val id: Int,
    val name: String,
    val type: Int,
    val isCurrent: Boolean
)

class DeviceManager(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _connectedDevices = MutableStateFlow<List<ConnectedDevice>>(emptyList())
    val connectedDevices: StateFlow<List<ConnectedDevice>> = _connectedDevices.asStateFlow()

    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            updateDevices()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            updateDevices()
        }
    }

    fun startListening() {
        audioManager.registerAudioDeviceCallback(audioDeviceCallback, null)
        updateDevices()
    }

    fun stopListening() {
        audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
    }

    fun updateDevices() {
        val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val validTypes = setOf(
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_DEVICE
        )

        // Find highest priority device to mark as "current" (Android routing typically follows this order)
        var currentDeviceId = -1
        
        // Check Bluetooth first
        val btDevice = outputs.firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || it.type == AudioDeviceInfo.TYPE_BLE_HEADSET }
        if (btDevice != null) {
            currentDeviceId = btDevice.id
        } else {
            // Then wired
            val wiredDevice = outputs.firstOrNull { 
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || 
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_USB_HEADSET 
            }
            if (wiredDevice != null) {
                currentDeviceId = wiredDevice.id
            } else {
                // Then speaker
                val speaker = outputs.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                if (speaker != null) {
                    currentDeviceId = speaker.id
                }
            }
        }

        val mapped = outputs.filter { validTypes.contains(it.type) }.map { info ->
            val name = if (info.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) {
                "This phone"
            } else if (info.productName.isNullOrBlank()) {
                getGenericName(info.type)
            } else {
                info.productName.toString()
            }

            ConnectedDevice(
                id = info.id,
                name = name,
                type = info.type,
                isCurrent = info.id == currentDeviceId
            )
        }
        
        // Ensure "This phone" is always listed, even if sometimes getDevices omits it when BT is connected (depending on OEM)
        val finalDevices = if (mapped.none { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }) {
            mapped + ConnectedDevice(
                id = 0,
                name = "This phone",
                type = AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
                isCurrent = currentDeviceId == -1 || currentDeviceId == 0
            )
        } else {
            mapped
        }

        // Sort: Current device first, then bluetooth/wired, then speaker
        _connectedDevices.value = finalDevices.sortedWith(
            compareBy<ConnectedDevice> { !it.isCurrent }
                .thenBy { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                .thenBy { it.name }
        )
    }

    private fun getGenericName(type: Int): String {
        return when (type) {
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLE_HEADSET -> "Bluetooth Device"
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired Headphones"
            AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE -> "USB Audio Device"
            else -> "Audio Device"
        }
    }
}
