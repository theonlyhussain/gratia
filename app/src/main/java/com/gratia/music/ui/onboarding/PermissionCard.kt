package com.gratia.music.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gratia.music.ui.theme.GratiaTheme
import com.gratia.music.ui.theme.Inter

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun PermissionCard(
    item: PermissionItem,
    modifier: Modifier = Modifier
) {
    val borderColor = when (item.status) {
        PermissionStatus.GRANTED -> GratiaTheme.colors.accent.copy(alpha = 0.5f)
        PermissionStatus.DENIED, PermissionStatus.PERMANENTLY_DENIED -> Color.Red.copy(alpha = 0.3f)
        PermissionStatus.REQUESTING -> GratiaTheme.colors.textSecondary.copy(alpha = 0.5f)
        PermissionStatus.PENDING -> GratiaTheme.colors.textSecondary.copy(alpha = 0.1f)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(GratiaTheme.colors.surface)
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(GratiaTheme.colors.background),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                tint = GratiaTheme.colors.accent,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                fontFamily = Inter,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                color = GratiaTheme.colors.textPrimary
            )
            Text(
                text = item.description,
                fontFamily = Inter,
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
                color = GratiaTheme.colors.textSecondary,
                lineHeight = 20.sp
            )
            
            if (item.status == PermissionStatus.PERMANENTLY_DENIED) {
                Text(
                    text = "You can enable this later in Android Settings.",
                    fontFamily = Inter,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    color = Color.Red.copy(alpha = 0.8f),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        AnimatedContent(
            targetState = item.status,
            transitionSpec = {
                fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
            },
            label = "status_icon"
        ) { status ->
            when (status) {
                PermissionStatus.GRANTED -> {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Granted",
                        tint = GratiaTheme.colors.accent,
                        modifier = Modifier.size(24.dp)
                    )
                }
                PermissionStatus.REQUESTING -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = GratiaTheme.colors.accent,
                        strokeWidth = 2.dp
                    )
                }
                PermissionStatus.DENIED, PermissionStatus.PERMANENTLY_DENIED -> {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Denied",
                        tint = Color.Red.copy(alpha = 0.8f),
                        modifier = Modifier.size(24.dp)
                    )
                }
                PermissionStatus.PENDING -> {
                    Icon(
                        imageVector = Icons.Default.HourglassEmpty,
                        contentDescription = "Pending",
                        tint = GratiaTheme.colors.textSecondary.copy(alpha = 0.5f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}
