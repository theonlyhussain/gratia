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
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gratia.music.ui.theme.JetBrainsMono

/**
 * Custom premium progress bar — replaces Material Slider entirely.
 *
 * Design details:
 * - Very thin track (3dp)
 * - Soft rounded ends (StrokeCap.Round)
 * - Animated progress fill
 * - Large invisible touch area (48dp height for accessibility)
 * - Thumb: only appears while dragging (animated fade-in, 14dp circle)
 * - Time labels: small, muted, monospaced (JetBrains Mono)
 *
 * Interaction:
 * - Drag to seek
 * - Tap to seek
 * - Emits onDragStart / onDragEnd so parent can react
 *   (e.g., scale artwork down, pause background animation)
 */
@Composable
fun GratiaProgressBar(
    progress: Float,
    currentTimeMs: Long,
    durationMs: Long,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    trackColor: Color = Color.White.copy(alpha = 0.12f),
    activeColor: Color = Color.White,
    thumbColor: Color = Color.White,
    onDragStart: () -> Unit = {},
    onDragEnd: () -> Unit = {}
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(progress) }

    // Display progress: use drag value when dragging, otherwise animated real progress
    val displayProgress = if (isDragging) dragProgress else progress

    // Thumb opacity — only visible when dragging
    val thumbAlpha by animateFloatAsState(
        targetValue = if (isDragging) 1f else 0f,
        animationSpec = tween(200),
        label = "thumbAlpha"
    )

    // Thumb scale — pops in when dragging
    val thumbScale by animateFloatAsState(
        targetValue = if (isDragging) 1f else 0.4f,
        animationSpec = tween(200),
        label = "thumbScale"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp)
    ) {
        // Track + thumb area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp) // Large touch area
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val newProgress = (offset.x / size.width).coerceIn(0f, 1f)
                        onSeek(newProgress)
                    }
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
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
                val trackHeight = 3.dp.toPx()
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

                // Thumb — only when dragging (animated fade + scale)
                if (thumbAlpha > 0.01f) {
                    val thumbRadius = 7.dp.toPx() * thumbScale
                    val thumbX = activeWidth.coerceIn(thumbRadius, trackWidth - thumbRadius)

                    // Thumb glow
                    drawCircle(
                        color = thumbColor.copy(alpha = thumbAlpha * 0.3f),
                        radius = thumbRadius * 2f,
                        center = Offset(thumbX, trackY)
                    )

                    // Thumb
                    drawCircle(
                        color = thumbColor.copy(alpha = thumbAlpha),
                        radius = thumbRadius,
                        center = Offset(thumbX, trackY)
                    )
                }
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

            Text(
                text = formatTimePlayer(displayTime),
                fontSize = 10.sp,
                fontWeight = FontWeight.Normal,
                fontFamily = JetBrainsMono,
                color = Color.White.copy(alpha = 0.4f)
            )
            Text(
                text = formatTimePlayer(durationMs),
                fontSize = 10.sp,
                fontWeight = FontWeight.Normal,
                fontFamily = JetBrainsMono,
                color = Color.White.copy(alpha = 0.4f)
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
