package com.gratia.music.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Launch
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gratia.music.ui.theme.GratiaTheme
import com.gratia.music.ui.theme.Inter
import com.gratia.music.ui.theme.SpaceGrotesk

@Composable
fun AboutScreen(
    onNavigateBack: () -> Unit,
    onNavigateToLicenses: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GratiaTheme.colors.cotton)
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
                    tint = GratiaTheme.colors.textMuted
                )
            }
            Text(
                text = "About Gratia",
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
            // App info card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = androidx.compose.ui.graphics.Color.White,
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = androidx.compose.ui.graphics.SolidColor(GratiaTheme.colors.glassBorder)
                ),
                shadowElevation = 4.dp
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Gratia",
                        fontFamily = SpaceGrotesk,
                        fontWeight = FontWeight.Bold,
                        fontSize = 28.sp,
                        color = GratiaTheme.colors.cherryRed
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Version 0.1.0-alpha",
                        fontFamily = Inter,
                        fontSize = 12.sp,
                        color = GratiaTheme.colors.textMuted
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "A private, bring-your-own-storage Android music app, built with a custom design system and premium kinetic lyrics engine.",
                        fontFamily = Inter,
                        fontSize = 13.sp,
                        color = GratiaTheme.colors.textSecondary,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
            }

            // Developer card
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
                        text = "Developer",
                        fontFamily = Inter,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = GratiaTheme.colors.textPrimary
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Hussain Shaikh",
                        fontFamily = SpaceGrotesk,
                        fontWeight = FontWeight.Medium,
                        fontSize = 16.sp,
                        color = GratiaTheme.colors.textPrimary
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/theonlyhussain"))
                                context.startActivity(intent)
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "GitHub Profile",
                            fontFamily = Inter,
                            fontSize = 13.sp,
                            color = GratiaTheme.colors.textSecondary
                        )
                        Icon(
                            imageVector = Icons.Default.Launch,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = GratiaTheme.colors.textMuted
                        )
                    }
                }
            }

            // Open source licenses card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = androidx.compose.ui.graphics.Color.White,
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = androidx.compose.ui.graphics.SolidColor(GratiaTheme.colors.glassBorder)
                ),
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToLicenses() }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = GratiaTheme.colors.cherryRed,
                        modifier = Modifier.size(20.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Open-source licenses",
                            fontFamily = Inter,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = GratiaTheme.colors.textPrimary
                        )
                        Text(
                            text = "Legal notices, code attributions, and disclaimers",
                            fontFamily = Inter,
                            fontSize = 11.sp,
                            color = GratiaTheme.colors.textMuted
                        )
                    }
                }
            }
        }
    }
}
