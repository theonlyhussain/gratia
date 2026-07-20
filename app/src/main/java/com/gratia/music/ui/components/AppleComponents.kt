package com.gratia.music.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.gratia.music.ui.theme.GratiaTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.material3.IconButton
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material.icons.automirrored.filled.ArrowBack

@Composable
fun AppleLargeTitleHeader(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    action: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                IconButton(onClick = onBack, modifier = Modifier.size(36.dp).padding(end = 8.dp)) {
                    Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack, "Back")
                }
            }
            Text(
                text = title,
                style = GratiaTheme.typography.largeTitle,
                color = GratiaTheme.colors.textPrimary
            )
        }
        if (action != null) {
            action()
        }
    }
}

@Composable
fun AppleSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = GratiaTheme.typography.title,
            color = GratiaTheme.colors.textPrimary
        )
        if (action != null) {
            action()
        }
    }
}

@Composable
fun AppleListRow(
    title: String,
    subtitle: String? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = "Navigate",
            tint = GratiaTheme.colors.textSecondary.copy(alpha = 0.5f),
            modifier = Modifier.size(20.dp)
        )
    },
    onClick: () -> Unit,
    showDivider: Boolean = true,
    isDestructive: Boolean = false
) {
    val interactionSource = remember { MutableInteractionSource() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.material3.ripple(color = GratiaTheme.colors.textSecondary),
                onClick = onClick
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leadingContent != null) {
                leadingContent()
                Spacer(modifier = Modifier.width(16.dp))
            }
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = GratiaTheme.typography.body,
                    color = if (isDestructive) GratiaTheme.colors.error else GratiaTheme.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = GratiaTheme.typography.caption,
                        color = GratiaTheme.colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            
            if (trailingContent != null) {
                Spacer(modifier = Modifier.width(12.dp))
                trailingContent()
            }
        }
        
        if (showDivider) {
            val startPadding = if (leadingContent != null) 24.dp + 40.dp /* assuming 24dp icon + 16dp spacer */ else 24.dp
            Divider(
                modifier = Modifier.padding(start = startPadding),
                color = GratiaTheme.colors.textSecondary.copy(alpha = 0.15f),
                thickness = 0.5.dp
            )
        }
    }
}

@Composable
fun AppleSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    modifier: Modifier = Modifier,
    activeColor: Color = GratiaTheme.colors.accent,
    inactiveColor: Color = GratiaTheme.colors.textSecondary.copy(alpha = 0.3f),
    thumbColor: Color = Color.White
) {
    var isDragging by remember { mutableStateOf(false) }
    val haptics = GratiaTheme.haptics
    val view = LocalView.current

    val heightScale by animateFloatAsState(
        targetValue = if (isDragging) 1.8f else 1.0f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
        label = "sliderHeight"
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(24.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { offset ->
                        isDragging = true
                        haptics.light(view)
                        val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                        onValueChange(valueRange.start + fraction * (valueRange.endInclusive - valueRange.start))
                        tryAwaitRelease()
                        isDragging = false
                    }
                )
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { isDragging = true; haptics.light(view) },
                    onDragEnd = { isDragging = false },
                    onDragCancel = { isDragging = false },
                    onDrag = { change, _ ->
                        change.consume()
                        val fraction = (change.position.x / size.width).coerceIn(0f, 1f)
                        onValueChange(valueRange.start + fraction * (valueRange.endInclusive - valueRange.start))
                    }
                )
            },
        contentAlignment = Alignment.CenterStart
    ) {
        val fraction = ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
        
        // Background track
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp * heightScale)
                .clip(CircleShape)
                .background(inactiveColor)
        )
        
        // Active track
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .height(4.dp * heightScale)
                .clip(CircleShape)
                .background(activeColor)
        )
    }
}
