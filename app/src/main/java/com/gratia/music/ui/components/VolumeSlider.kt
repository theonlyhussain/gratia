package com.gratia.music.ui.components

import android.content.Context
import android.database.ContentObserver
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.gratia.music.ui.theme.GratiaTheme

/**
 * In-player volume control over the real Android media volume.
 *
 * [AudioManager.STREAM_MUSIC] is the one source of truth: the slider reads it,
 * writes it, and follows it while it is visible. Changes from hardware volume
 * buttons arrive through the settings ContentObserver; a small polling fallback
 * covers vendor ROMs whose observer never fires. Both only run while this
 * composable is composed — nothing polls for the whole app.
 *
 * When a Bluetooth route is active, some devices expose a per-device volume;
 * it is read and written in the same way, so the slider reflects what the
 * connected device actually hears.
 */
@Composable
fun VolumeSlider(
    modifier: Modifier = Modifier,
    activeColor: Color = Color.White,
    inactiveColor: Color = Color.White.copy(alpha = 0.3f)
) {
    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }
    var currentVolume by remember { mutableIntStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)) }

    fun readVolume() {
        currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
    }

    // System volume changes — hardware buttons included — land here.
    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                super.onChange(selfChange)
                readVolume()
            }
        }
        context.contentResolver.registerContentObserver(
            Settings.System.CONTENT_URI,
            true,
            observer
        )
        onDispose {
            context.contentResolver.unregisterContentObserver(observer)
        }
    }

    // Fallback for vendor ROMs whose volume observer is unreliable. One read a
    // second while this slider exists; cheaper than a wrong slider.
    DisposableEffect(context) {
        var ticking = true
        val ticker = object : Runnable {
            override fun run() {
                if (!ticking) return
                readVolume()
                Handler(Looper.getMainLooper()).postDelayed(this, 1_000L)
            }
        }
        Handler(Looper.getMainLooper()).postDelayed(ticker, 1_000L)
        onDispose { ticking = false }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = GratiaTheme.spacing.large),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (currentVolume == 0) Icons.Default.VolumeMute else Icons.Default.VolumeDown,
            contentDescription = "Volume Down",
            tint = inactiveColor,
            modifier = Modifier.size(20.dp)
        )

        Spacer(Modifier.width(GratiaTheme.spacing.mediumSmall))

        val percent = if (maxVolume > 0) (currentVolume * 100 / maxVolume) else 0
        AppleSlider(
            value = currentVolume.toFloat(),
            onValueChange = { newValue ->
                val intVal = newValue.toInt()
                currentVolume = intVal
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, intVal, 0)
            },
            valueRange = 0f..maxVolume.toFloat(),
            modifier = Modifier
                .weight(1f)
                .semantics { contentDescription = "Media volume: $percent percent" },
            activeColor = activeColor,
            inactiveColor = inactiveColor
        )

        Spacer(Modifier.width(GratiaTheme.spacing.mediumSmall))

        Icon(
            imageVector = Icons.Default.VolumeUp,
            contentDescription = "Volume Up",
            tint = inactiveColor,
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * Whether the given output route carries its own volume (Bluetooth devices
 * commonly do). Used by the output sheet to say which volume it is moving.
 */
fun AudioDeviceInfo.hasIndependentVolume(): Boolean = when (type) {
    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
    AudioDeviceInfo.TYPE_BLE_HEADSET,
    AudioDeviceInfo.TYPE_BLE_SPEAKER,
    AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
    AudioDeviceInfo.TYPE_WIRED_HEADSET,
    AudioDeviceInfo.TYPE_USB_HEADSET,
    AudioDeviceInfo.TYPE_USB_DEVICE,
    AudioDeviceInfo.TYPE_USB_ACCESSORY -> true
    else -> false
}
