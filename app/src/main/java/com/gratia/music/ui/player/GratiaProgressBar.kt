package com.gratia.music.ui.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.gratia.music.ui.components.GratiaText
import com.gratia.music.ui.theme.GratiaTheme
import com.gratia.music.ui.theme.JetBrainsMono

/**
 * Custom premium progress bar — mimics Apple Music style.
 *
 * Design details:
 * - Very thin track
 * - Thumb is always visible (small circle), grows slightly when dragging
 * - Time labels: small, muted below the bar
 */
@Composable
fun GratiaProgressBar(
    progress: Float,
    currentTimeMs: Long,
    durationMs: Long,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    trackColor: Color = Color.White.copy(alpha = 0.2f),
    activeColor: Color = Color.White,
    thumbColor: Color = Color.White,
    onDragStart: () -> Unit = {},
    onDragEnd: () -> Unit = {}
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(progress) }

    val motion = GratiaTheme.motion
    val haptics = GratiaTheme.haptics
    val view = androidx.compose.ui.platform.LocalView.current

    // Display progress: use drag value when dragging, otherwise animated real progress
    val displayProgress = if (isDragging) dragProgress else progress

    // Thumb scale — small normally, pops in larger when dragging
    val thumbScale by animateFloatAsState(
        targetValue = if (isDragging) 1.2f else 0.8f,
        animationSpec = tween(motion.fast, easing = motion.standardEasing),
        label = "thumbScale"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = GratiaTheme.spacing.large) // 32dp
    ) {
        // Track + thumb area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp) // Large touch area
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val newProgress = (offset.x / size.width).coerceIn(0f, 1f)
                        haptics.light(view)
                        onSeek(newProgress)
                    }
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            haptics.medium(view)
                            dragProgress = (offset.x / size.width).coerceIn(0f, 1f)
                            onDragStart()
                        },
                        onDragEnd = {
                            onSeek(dragProgress)
                            isDragging = false
                            onDragEnd()
                        },
                        onDragCancel = {
                            isDragging = false
                            onDragEnd()
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            dragProgress = (dragProgress + dragAmount / size.width).coerceIn(0f, 1f)
                        }
                    )
                }
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val trackHeight = 4.dp.toPx()
                val trackY = center.y
                val trackWidth = size.width
                val cornerRadius = trackHeight / 2f

                // Inactive track
                drawRoundRect(
                    color = trackColor,
                    topLeft = Offset(0f, trackY - trackHeight / 2f),
                    size = Size(trackWidth, trackHeight),
                    cornerRadius = CornerRadius(cornerRadius, cornerRadius)
                )

                // Active track
                val activeWidth = trackWidth * displayProgress
                if (activeWidth > 0f) {
                    drawRoundRect(
                        color = activeColor,
                        topLeft = Offset(0f, trackY - trackHeight / 2f),
                        size = Size(activeWidth, trackHeight),
                        cornerRadius = CornerRadius(cornerRadius, cornerRadius)
                    )
                }

                // Always visible thumb
                val baseThumbRadius = 6.dp.toPx()
                val thumbRadius = baseThumbRadius * thumbScale
                val thumbX = activeWidth.coerceIn(thumbRadius, trackWidth - thumbRadius)

                if (isDragging) {
                    // Thumb glow when dragging
                    drawCircle(
                        color = thumbColor.copy(alpha = 0.3f),
                        radius = thumbRadius * 2f,
                        center = Offset(thumbX, trackY)
                    )
                }

                // Thumb
                drawCircle(
                    color = thumbColor,
                    radius = thumbRadius,
                    center = Offset(thumbX, trackY)
                )
            }
        }

        // Time labels
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 0.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val displayTime = if (isDragging) {
                (dragProgress * durationMs).toLong()
            } else {
                currentTimeMs
            }

            GratiaText(
                text = formatTimePlayer(displayTime),
                style = GratiaTheme.typography.caption.copy(fontFamily = JetBrainsMono),
                color = Color.White.copy(alpha = 0.5f)
            )
            
            // In Apple Music, remaining time is often shown with a negative sign (e.g. -2:30). 
            // We can just show duration or remaining time. Let's show remaining time for a more authentic feel if we want,
            // but the original app shows duration. Let's stick to duration or remaining. Let's do remaining.
            val remainingTime = durationMs - displayTime
            GratiaText(
                text = "-${formatTimePlayer(remainingTime)}",
                style = GratiaTheme.typography.caption.copy(fontFamily = JetBrainsMono),
                color = Color.White.copy(alpha = 0.5f)
            )
        }
    }
}

/** Format milliseconds as m:ss — monospaced friendly. */
fun formatTimePlayer(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSecs = ms / 1000
    val mins = totalSecs / 60
    val secs = totalSecs % 60
    return "$mins:${secs.toString().padStart(2, '0')}"
}

