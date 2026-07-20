package com.gratia.music.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gratia.music.GratiaApp
import com.gratia.music.audio.EqPreset
import com.gratia.music.audio.EqualizerManager
import com.gratia.music.ui.theme.GratiaTheme
import com.gratia.music.ui.theme.Inter
import com.gratia.music.ui.theme.JetBrainsMono
import com.gratia.music.ui.theme.SpaceGrotesk

/**
 * Equalizer screen with visual frequency response curve and preset selector.
 *
 * Inspired by Apple Music's clean EQ presentation and Rhythm's band visualization,
 * but with Gratia's own design language: glassmorphism, spring animations,
 * and premium typography.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerScreen(
    onNavigateBack: () -> Unit
) {
    val eqManager = remember { GratiaApp.instance.equalizerManager }
    val enabled by eqManager.enabled.collectAsState()
    val bandLevels by eqManager.bandLevels.collectAsState()
    val bandFrequencies by eqManager.bandFrequencies.collectAsState()
    val numberOfBands by eqManager.numberOfBands.collectAsState()
    val levelRange by eqManager.levelRange.collectAsState()
    val activePresetName by eqManager.activePresetName.collectAsState()
    val isAvailable by eqManager.isAvailable.collectAsState()
    val haptic = LocalHapticFeedback.current

    val spacing = GratiaTheme.spacing
    val colors = GratiaTheme.colors
    val motion = GratiaTheme.motion
    val typography = GratiaTheme.typography

    // Animated opacity for enabled/disabled state
    val contentAlpha by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.4f,
        animationSpec = tween(motion.normal),
        label = "eqAlpha"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
    ) {
        // ── Header ──────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = spacing.base, vertical = spacing.mediumSmall),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = colors.textPrimary
                )
            }
            Text(
                text = "Equalizer",
                style = typography.largeTitle,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f).padding(start = spacing.small)
            )
            IconButton(
                onClick = {
                    eqManager.reset()
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Reset",
                    tint = colors.textSecondary
                )
            }
        }

        // ── Enable Toggle ───────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.mediumLarge, vertical = spacing.small),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Sound Enhancement",
                    style = typography.section,
                    color = colors.textPrimary
                )
                Text(
                    text = if (!isAvailable) "Not available on this device"
                           else if (enabled) activePresetName ?: "Custom"
                           else "Off",
                    style = typography.caption,
                    color = if (enabled) colors.accent else colors.textSecondary
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = { newState ->
                    eqManager.setEnabled(newState)
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                },
                enabled = isAvailable,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = colors.accent,
                    uncheckedThumbColor = colors.textSecondary,
                    uncheckedTrackColor = colors.surface
                )
            )
        }

        Spacer(Modifier.height(spacing.base))

        // ── Frequency Response Curve ────────────────────────────────────
        if (numberOfBands > 0 && bandLevels.isNotEmpty()) {
            FrequencyResponseCurve(
                bandLevels = bandLevels,
                levelRange = levelRange,
                accentColor = colors.accent,
                surfaceColor = colors.surface,
                textSecondary = colors.textSecondary,
                alpha = contentAlpha,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .padding(horizontal = spacing.mediumLarge)
            )
        }

        Spacer(Modifier.height(spacing.mediumLarge))

        // ── Band Sliders ────────────────────────────────────────────────
        if (numberOfBands > 0 && bandLevels.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .padding(horizontal = spacing.base),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                bandLevels.forEachIndexed { index, level ->
                    BandSlider(
                        frequency = bandFrequencies.getOrNull(index) ?: 0,
                        level = level,
                        minLevel = levelRange.first,
                        maxLevel = levelRange.second,
                        enabled = enabled && isAvailable,
                        accentColor = colors.accent,
                        textPrimary = colors.textPrimary,
                        textSecondary = colors.textSecondary,
                        surfaceColor = colors.surface,
                        alpha = contentAlpha,
                        onLevelChanged = { newLevel ->
                            eqManager.setBandLevel(index, newLevel)
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Spacer(Modifier.height(spacing.mediumLarge))

        // ── Preset Chips ────────────────────────────────────────────────
        Text(
            text = "Presets",
            style = typography.section,
            color = colors.textPrimary,
            modifier = Modifier.padding(horizontal = spacing.mediumLarge, vertical = spacing.small)
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = spacing.mediumLarge),
            horizontalArrangement = Arrangement.spacedBy(spacing.small),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(eqManager.presets) { preset ->
                PresetChip(
                    preset = preset,
                    isSelected = activePresetName == preset.name && enabled,
                    enabled = isAvailable,
                    accentColor = colors.accent,
                    surfaceColor = colors.surface,
                    textPrimary = colors.textPrimary,
                    textSecondary = colors.textSecondary,
                    onClick = {
                        eqManager.applyPreset(preset)
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                )
            }
        }

        Spacer(Modifier.height(spacing.heroSmall))
    }
}

// ── Frequency Response Curve ────────────────────────────────────────────
@Composable
private fun FrequencyResponseCurve(
    bandLevels: List<Short>,
    levelRange: Pair<Short, Short>,
    accentColor: Color,
    surfaceColor: Color,
    textSecondary: Color,
    alpha: Float,
    modifier: Modifier = Modifier
) {
    // Animate each band level for smooth transitions
    val animatedLevels = bandLevels.mapIndexed { index, level ->
        animateFloatAsState(
            targetValue = level.toFloat(),
            animationSpec = spring(
                dampingRatio = 0.8f,
                stiffness = Spring.StiffnessLow
            ),
            label = "band$index"
        ).value
    }

    val rangeSpan = (levelRange.second - levelRange.first).toFloat().coerceAtLeast(1f)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val padding = 16.dp.toPx()
        val drawW = w - padding * 2
        val drawH = h - padding * 2

        // Zero line (center)
        val zeroY = padding + drawH * (levelRange.second.toFloat() / rangeSpan)
        drawLine(
            color = textSecondary.copy(alpha = 0.2f),
            start = Offset(padding, zeroY),
            end = Offset(w - padding, zeroY),
            strokeWidth = 1.dp.toPx()
        )

        if (animatedLevels.isEmpty()) return@Canvas

        // Build smooth curve through band points
        val points = animatedLevels.mapIndexed { index, level ->
            val x = padding + (index.toFloat() / (animatedLevels.size - 1).coerceAtLeast(1)) * drawW
            val normalised = (levelRange.second - level) / rangeSpan
            val y = padding + normalised * drawH
            Offset(x, y)
        }

        // Curve path using cubic bezier
        val curvePath = Path().apply {
            if (points.size < 2) return@apply
            moveTo(points.first().x, points.first().y)
            for (i in 0 until points.size - 1) {
                val p0 = points[i]
                val p1 = points[i + 1]
                val cpOffset = (p1.x - p0.x) * 0.4f
                cubicTo(
                    p0.x + cpOffset, p0.y,
                    p1.x - cpOffset, p1.y,
                    p1.x, p1.y
                )
            }
        }

        // Fill gradient under curve
        val fillPath = Path().apply {
            addPath(curvePath)
            lineTo(points.last().x, h)
            lineTo(points.first().x, h)
            close()
        }
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    accentColor.copy(alpha = 0.25f * alpha),
                    accentColor.copy(alpha = 0.02f * alpha)
                )
            )
        )

        // Stroke the curve
        drawPath(
            path = curvePath,
            color = accentColor.copy(alpha = alpha),
            style = Stroke(
                width = 2.5.dp.toPx(),
                cap = StrokeCap.Round
            )
        )

        // Band dots
        points.forEach { point ->
            drawCircle(
                color = accentColor.copy(alpha = alpha),
                radius = 4.dp.toPx(),
                center = point
            )
            drawCircle(
                color = surfaceColor,
                radius = 2.dp.toPx(),
                center = point
            )
        }
    }
}

// ── Band Slider (vertical) ──────────────────────────────────────────────
@Composable
private fun BandSlider(
    frequency: Int,
    level: Short,
    minLevel: Short,
    maxLevel: Short,
    enabled: Boolean,
    accentColor: Color,
    textPrimary: Color,
    textSecondary: Color,
    surfaceColor: Color,
    alpha: Float,
    onLevelChanged: (Short) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val animatedLevel by animateFloatAsState(
        targetValue = level.toFloat(),
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow),
        label = "sliderLevel"
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // dB label
        Text(
            text = "${if (level >= 0) "+" else ""}${level / 100}",
            style = androidx.compose.ui.text.TextStyle(
                fontFamily = JetBrainsMono,
                fontWeight = FontWeight.Medium,
                fontSize = 10.sp,
                letterSpacing = 0.5.sp
            ),
            color = if (enabled) accentColor.copy(alpha = alpha) else textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.height(16.dp)
        )

        // Vertical slider
        Slider(
            value = animatedLevel,
            onValueChange = { newValue ->
                onLevelChanged(newValue.toInt().toShort())
            },
            valueRange = minLevel.toFloat()..maxLevel.toFloat(),
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = if (enabled) accentColor else textSecondary,
                activeTrackColor = if (enabled) accentColor.copy(alpha = 0.7f * alpha) else textSecondary.copy(alpha = 0.3f),
                inactiveTrackColor = surfaceColor
            ),
            modifier = Modifier
                .weight(1f)
                .graphicsLayer {
                    rotationZ = -90f // Vertical orientation
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.5f)
                }
                .width(200.dp)
        )

        // Frequency label
        Text(
            text = formatFrequency(frequency),
            style = androidx.compose.ui.text.TextStyle(
                fontFamily = JetBrainsMono,
                fontWeight = FontWeight.Normal,
                fontSize = 9.sp,
                letterSpacing = 0.3.sp
            ),
            color = textSecondary.copy(alpha = alpha),
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.height(14.dp)
        )
    }
}

// ── Preset Chip ─────────────────────────────────────────────────────────
@Composable
private fun PresetChip(
    preset: EqPreset,
    isSelected: Boolean,
    enabled: Boolean,
    accentColor: Color,
    surfaceColor: Color,
    textPrimary: Color,
    textSecondary: Color,
    onClick: () -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) accentColor else surfaceColor,
        animationSpec = tween(200),
        label = "chipBg"
    )
    val textColor by animateColorAsState(
        targetValue = if (isSelected) Color.White else textPrimary,
        animationSpec = tween(200),
        label = "chipText"
    )
    val chipScale by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0.97f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow),
        label = "chipScale"
    )

    Box(
        modifier = Modifier
            .graphicsLayer { scaleX = chipScale; scaleY = chipScale }
            .shadow(
                elevation = if (isSelected) 8.dp else 0.dp,
                shape = RoundedCornerShape(20.dp),
                spotColor = accentColor.copy(alpha = 0.3f)
            )
            .clip(RoundedCornerShape(20.dp))
            .background(bgColor)
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(
            text = preset.name,
            style = androidx.compose.ui.text.TextStyle(
                fontFamily = Inter,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                fontSize = 13.sp
            ),
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Format Hz to human-readable (e.g. 60, 230, 1K, 4K, 14K). */
private fun formatFrequency(hz: Int): String {
    return when {
        hz >= 1000 -> "${hz / 1000}K"
        else -> "$hz"
    }
}
