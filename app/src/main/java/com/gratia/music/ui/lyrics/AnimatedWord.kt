package com.gratia.music.ui.lyrics

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
 * The transitions use a 220 ms ease-out curve, producing a smooth "glow"
 * that naturally follows the music without any flickering or karaoke artifacts.
 */
@Composable
fun AnimatedWord(
    word: LyricWord,
    durationMs: Int,
    currentPositionMs: Long
) {
    val isActive = currentPositionMs in word.startMs..word.endMs
    val isPast = currentPositionMs > word.endMs

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
        targetValue = if (isActive) -1.5f else 0f,
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
        modifier = Modifier
            .alpha(alpha)
            .offset(y = translateY.dp)
    )
}
