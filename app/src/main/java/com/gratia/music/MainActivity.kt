package com.gratia.music

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.gratia.music.ui.GratiaAppRoot
import com.gratia.music.ui.theme.GratiaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val context = LocalContext.current
            val settings = remember { com.gratia.music.data.SettingsDataStore(context) }
            val themeOption by settings.themeOptionFlow.collectAsState(initial = com.gratia.music.data.ThemeOption.SYSTEM)
            val accentOption by settings.accentColorOptionFlow.collectAsState(initial = com.gratia.music.data.AccentColorOption.DEFAULT)
            val isOled by settings.oledThemeEnabledFlow.collectAsState(initial = false)

            GratiaTheme(
                themeOption = themeOption,
                accentOption = accentOption,
                isOledThemeEnabled = isOled
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    GratiaAppRoot()
                }
            }
        }
    }
}