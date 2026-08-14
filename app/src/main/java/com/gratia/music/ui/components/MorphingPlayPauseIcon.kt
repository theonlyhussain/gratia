package com.gratia.music.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * A highly polished morphing icon that perfectly transforms between
 * a Play triangle and Pause bars using interpolated paths on a Canvas.
 */
@Composable
fun MorphingPlayPauseIcon(
    isPlaying: Boolean,
    color: Color,
    modifier: Modifier = Modifier
) {
    // 0f = Pause (isPlaying = true, so we show Pause to indicate the action)
    // 1f = Play (isPlaying = false, so we show Play)
    val progress by animateFloatAsState(
        targetValue = if (isPlaying) 0f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "playPauseMorph"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // Normalization scale factor so we can use 0..1 values
        val scaleX = w
        val scaleY = h

        // Helper to lerp between two points
        fun lerpOffset(p0x: Float, p0y: Float, p1x: Float, p1y: Float, fraction: Float): Offset {
            val x = p0x + (p1x - p0x) * fraction
            val y = p0y + (p1y - p0y) * fraction
            return Offset(x * scaleX, y * scaleY)
        }

        val path = Path()

        // --- Left Shape (Pause Left Bar -> Play Left Half) ---
        // Pause Left Bar
        val pl1x = 0.15f; val pl1y = 0.15f
        val pl2x = 0.38f; val pl2y = 0.15f
        val pl3x = 0.38f; val pl3y = 0.85f
        val pl4x = 0.15f; val pl4y = 0.85f

        // Play Left Half
        val tl1x = 0.20f; val tl1y = 0.20f
        val tl2x = 0.55f; val tl2y = 0.40f
        val tl3x = 0.55f; val tl3y = 0.60f
        val tl4x = 0.20f; val tl4y = 0.80f

        val L1 = lerpOffset(pl1x, pl1y, tl1x, tl1y, progress)
        val L2 = lerpOffset(pl2x, pl2y, tl2x, tl2y, progress)
        val L3 = lerpOffset(pl3x, pl3y, tl3x, tl3y, progress)
        val L4 = lerpOffset(pl4x, pl4y, tl4x, tl4y, progress)

        path.moveTo(L1.x, L1.y)
        path.lineTo(L2.x, L2.y)
        path.lineTo(L3.x, L3.y)
        path.lineTo(L4.x, L4.y)
        path.close()

        // --- Right Shape (Pause Right Bar -> Play Right Half) ---
        // Pause Right Bar
        val pr1x = 0.62f; val pr1y = 0.15f
        val pr2x = 0.85f; val pr2y = 0.15f
        val pr3x = 0.85f; val pr3y = 0.85f
        val pr4x = 0.62f; val pr4y = 0.85f

        // Play Right Half
        val tr1x = 0.55f; val tr1y = 0.40f
        val tr2x = 0.90f; val tr2y = 0.50f
        val tr3x = 0.90f; val tr3y = 0.50f
        val tr4x = 0.55f; val tr4y = 0.60f

        val R1 = lerpOffset(pr1x, pr1y, tr1x, tr1y, progress)
        val R2 = lerpOffset(pr2x, pr2y, tr2x, tr2y, progress)
        val R3 = lerpOffset(pr3x, pr3y, tr3x, tr3y, progress)
        val R4 = lerpOffset(pr4x, pr4y, tr4x, tr4y, progress)

        path.moveTo(R1.x, R1.y)
        path.lineTo(R2.x, R2.y)
        path.lineTo(R3.x, R3.y)
        path.lineTo(R4.x, R4.y)
        path.close()

        // We use FILL with a slightly rounded stroke to round off the sharp polygon corners
        drawPath(
            path = path,
            color = color,
            style = Fill
        )
        drawPath(
            path = path,
            color = color,
            style = Stroke(
                width = w * 0.15f, // Adds rounded thickness to the shapes
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}
