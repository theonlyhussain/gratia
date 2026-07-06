package com.gratia.music.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gratia.music.ui.theme.GratiaTheme
import com.gratia.music.ui.theme.SpaceGrotesk
import java.util.Calendar

@Composable
fun Header(
    displayName: String,
    avatarPath: String?,
    isDark: Boolean,
    onToggleTheme: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greeting = when (hour) {
        in 0..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        else -> "Good evening"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top // align Top to match the column
    ) {
        // Greeting
        Column(modifier = Modifier.clickable(onClick = onNavigateToProfile)) {
            Text(
                text = greeting,
                fontSize = 13.sp,
                color = GratiaTheme.colors.textSecondary,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "Hi, $displayName",
                fontFamily = SpaceGrotesk,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = GratiaTheme.colors.textPrimary
            )
            Text(
                text = "Ready to listen?",
                fontSize = 12.sp,
                color = GratiaTheme.colors.textSecondary,
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Theme Toggle (Sun/Moon Morph)
            val rotation by animateFloatAsState(
                targetValue = if (isDark) 180f else 0f,
                animationSpec = tween(500),
                label = "theme_rotation"
            )

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(GratiaTheme.colors.surface)
                    .clickable(onClick = onToggleTheme),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isDark) Icons.Rounded.DarkMode else Icons.Rounded.LightMode,
                    contentDescription = "Toggle Theme",
                    modifier = Modifier
                        .size(22.dp)
                        .rotate(rotation),
                    tint = GratiaTheme.colors.accent
                )
            }

            // Settings Icon
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(GratiaTheme.colors.surface)
                    .clickable(onClick = onNavigateToSettings),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    modifier = Modifier.size(24.dp),
                    tint = GratiaTheme.colors.accent
                )
            }
        }
    }
}
