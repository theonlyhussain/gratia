package com.gratia.music

import android.os.Bundle
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
import androidx.fragment.app.FragmentActivity
import com.gratia.music.ui.GratiaAppRoot
import com.gratia.music.ui.theme.GratiaTheme

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // One splash, and it is the system's: the Gratia-branded launch screen
        // (Theme.Gratia.Splash) is held until the app is actually ready to draw
        // its first frame. There is no second, in-app logo overlay any more.
        val splashScreen = installSplashScreen()
        var appReady = false
        splashScreen.setKeepOnScreenCondition { !appReady }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val context = LocalContext.current
            val settings = remember { com.gratia.music.data.SettingsDataStore(context) }
            val themeOption by settings.themeOptionFlow.collectAsState(initial = com.gratia.music.data.ThemeOption.SYSTEM)
            val isOled by settings.oledThemeEnabledFlow.collectAsState(initial = false)

            GratiaTheme(
                themeOption = themeOption,
                isOledThemeEnabled = isOled
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    GratiaAppRoot(onReady = { appReady = true })
                }
            }
        }
    }
}