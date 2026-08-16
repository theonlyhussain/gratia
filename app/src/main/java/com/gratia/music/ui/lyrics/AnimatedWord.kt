package com.gratia.music.ui.lyrics

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.gratia.music.lyrics.LyricWord

/**
 * Apple Music–style animated word.
 *
 * Instead of splitting into individual characters (which caused an ugly
 * horizontal-mask effect), we animate the **entire word** as a single unit:
 *
 * - **Inactive (future):** dimmed to 40 % opacity
 * - **Active (currently being sung):** full opacity, slight upward lift
 * - **Past:** dims back to 55 % opacity so the focus moves forward
 *
 * Performance:
 * - All visual changes (alpha, translationY) are done via `graphicsLayer`
 *   so they don't trigger Compose layout/measure passes. This is critical
 *   for smooth rendering at 120Hz when the playback timer is updating
 *   the current position every frame.
 */
@Composable
fun AnimatedWord(
    word: LyricWord,
    durationMs: Int,
    currentPositionProvider: () -> Long
) {
    // Read the continuously updating time inside a derivedStateOf so that
    // this Composable ONLY recomposes when the actual state changes (future -> active -> past),
    // rather than on every single frame!
    val isActive by remember { 
        derivedStateOf { currentPositionProvider() in word.startMs..word.endMs } 
    }
    val isPast by remember { 
        derivedStateOf { currentPositionProvider() > word.endMs } 
    }

    // Smooth opacity transition — no character splitting, no horizontal mask
    val targetAlpha = when {
        isActive -> 1f
        isPast -> 0.55f
        else -> 0.4f
    }
    val alpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(
            durationMillis = 220,
            easing = androidx.compose.animation.core.FastOutSlowInEasing
        ),
        label = "WordAlpha"
    )

    // Subtle upward lift on the active word (Apple-style "breathing")
    val translateY by animateFloatAsState(
        targetValue = if (isActive) -2f else 0f,
        animationSpec = tween(
            durationMillis = 200,
            easing = androidx.compose.animation.core.FastOutSlowInEasing
        ),
        label = "WordLift"
    )

    Text(
        text = word.text,
        fontSize = 28.sp,
        fontWeight = if (isActive) FontWeight.Bold else FontWeight.ExtraBold,
        color = Color.White,
        lineHeight = 36.sp,
        modifier = Modifier
            .graphicsLayer {
                this.alpha = alpha
                this.translationY = translateY
            }
    )
}
