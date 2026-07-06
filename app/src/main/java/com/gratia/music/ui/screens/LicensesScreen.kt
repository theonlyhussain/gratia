package com.gratia.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gratia.music.ui.theme.GratiaTheme
import com.gratia.music.ui.theme.Inter
import com.gratia.music.ui.theme.SpaceGrotesk

@Composable
fun LicensesScreen(
    onNavigateBack: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GratiaTheme.colors.background)
            .statusBarsPadding()
    ) {
        // Header
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconButton(
                onClick = onNavigateBack,
                modifier = Modifier
                    .size(36.dp)
                    .border(1.dp, GratiaTheme.colors.glassBorder, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    modifier = Modifier.size(16.dp),
                    tint = GratiaTheme.colors.textSecondary
                )
            }
            Text(
                text = "Open-Source Licenses",
                fontFamily = SpaceGrotesk,
                fontWeight = FontWeight.SemiBold,
                fontSize = 20.sp,
                color = GratiaTheme.colors.textPrimary
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Main statement
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = androidx.compose.ui.graphics.Color.White,
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = androidx.compose.ui.graphics.SolidColor(GratiaTheme.colors.glassBorder)
                ),
                shadowElevation = 4.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Gratia License",
                        fontFamily = Inter,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = GratiaTheme.colors.textPrimary
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Gratia is licensed under the GNU General Public License v3 (GPLv3). This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for details.",
                        fontFamily = Inter,
                        fontSize = 12.sp,
                        color = GratiaTheme.colors.textSecondary,
                        lineHeight = 18.sp
                    )
                }
            }

            // Attributions Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = androidx.compose.ui.graphics.Color.White,
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = androidx.compose.ui.graphics.SolidColor(GratiaTheme.colors.glassBorder)
                ),
                shadowElevation = 4.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Attributions & References",
                        fontFamily = Inter,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = GratiaTheme.colors.textPrimary
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "This project includes or adapts open-source components and implementation ideas from GPLv3-licensed Android music player projects, including Retro Music Player. We are grateful to the original developers for their contributions to the open-source Android music ecosystem.\n\n" +
                               "Lyrics animation behavior is adapted from akshayjadhav4/lyrics-animation, modified and written in native Kotlin and Jetpack Compose.",
                        fontFamily = Inter,
                        fontSize = 12.sp,
                        color = GratiaTheme.colors.textSecondary,
                        lineHeight = 18.sp
                    )
                }
            }

            // Third-party libraries info
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = androidx.compose.ui.graphics.Color.White,
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = androidx.compose.ui.graphics.SolidColor(GratiaTheme.colors.glassBorder)
                ),
                shadowElevation = 4.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Third-Party Components",
                        fontFamily = Inter,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = GratiaTheme.colors.textPrimary
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Gratia uses several open-source libraries:\n" +
                               "• Android Jetpack (Compose, Architecture Components, Room, Navigation, DataStore) - Apache 2.0\n" +
                               "• Android Media3 (ExoPlayer, Session) - Apache 2.0\n" +
                               "• Kotlin Standard Library & Coroutines - Apache 2.0\n" +
                               "• Coil (Image Loading) - Apache 2.0\n" +
                               "• Gson / JSON - Apache 2.0",
                        fontFamily = Inter,
                        fontSize = 12.sp,
                        color = GratiaTheme.colors.textSecondary,
                        lineHeight = 18.sp
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
