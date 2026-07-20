package com.gratia.music.ui.components

import android.content.Context
import android.database.ContentObserver
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
import androidx.compose.ui.unit.dp
import com.gratia.music.ui.theme.GratiaTheme

/**
 * In-player volume control mimicking Apple Music's bottom slider.
 * Syncs automatically with system volume via ContentObserver.
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

    // Listen to system volume changes
    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                super.onChange(selfChange)
                currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
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

        AppleSlider(
            value = currentVolume.toFloat(),
            onValueChange = { newValue ->
                val intVal = newValue.toInt()
                currentVolume = intVal
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, intVal, 0)
            },
            valueRange = 0f..maxVolume.toFloat(),
            modifier = Modifier.weight(1f),
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
