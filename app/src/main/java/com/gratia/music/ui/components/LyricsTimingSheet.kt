package com.gratia.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gratia.music.player.PlayerViewModel
import com.gratia.music.ui.theme.GratiaTheme
import com.gratia.music.ui.theme.Inter
import com.gratia.music.ui.theme.SpaceGrotesk
import kotlin.math.abs
import kotlin.math.roundToInt

/** The slider spans ±5 seconds — wider drift than any real recording mismatch needs. */
private const val MAX_OFFSET_MS = 5000f
/** One tap of − / + is 100ms, the smallest shift that is audible but not coarse. */
private const val STEP_MS = 100L

/**
 * The lyric synchronization control, reached from the player's three-dot menu.
 *
 * What it is: a display-side offset applied on top of the parsed lyric
 * timeline — `lyricTime + offset` — leaving the stored lyrics document exactly
 * as the provider delivered it. The offset persists per recording (keyed by
 * the lyrics row's songId, which for remote tracks is the stable `ytm_<videoId>`
 * identity), so returning to the same song restores it, and a different song
 * never inherits it.
 *
 * What it replaces: an estimated-timing toggle that answered "this provider's
 * timings are wrong" by pretending to re-derive them. They cannot be
 * re-derived — only shifted, by a listener who can hear the mismatch.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsTimingSheet(
    playerViewModel: PlayerViewModel,
    onDismiss: () -> Unit,
) {
    val currentLyrics by playerViewModel.currentLyrics.collectAsState()
    val view = LocalView.current
    val haptics = GratiaTheme.haptics

    val savedOffset = currentLyrics?.offsetMs ?: 0L
    var sliderValue by remember(currentLyrics?.songId, savedOffset) {
        mutableFloatStateOf(savedOffset.toFloat().coerceIn(-MAX_OFFSET_MS, MAX_OFFSET_MS))
    }

    fun commit(value: Long) {
        val clamped = value.coerceIn(-MAX_OFFSET_MS.toLong(), MAX_OFFSET_MS.toLong())
        sliderValue = clamped.toFloat()
        // Update the adjusted timeline immediately — the active lyric line moves
        // the moment this lands, which is the whole point of live adjustment.
        playerViewModel.updateLyricsOffset(clamped)
        haptics.light(view)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = GratiaTheme.colors.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 8.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(GratiaTheme.colors.textSecondary.copy(alpha = 0.4f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 40.dp)
        ) {
            Text(
                text = "Lyrics timing",
                fontFamily = SpaceGrotesk,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = GratiaTheme.colors.textPrimary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Shifts when each line lights up. Saves for this song only.",
                fontFamily = Inter,
                fontSize = 13.sp,
                color = GratiaTheme.colors.textSecondary
            )

            Spacer(Modifier.height(28.dp))

            // Current offset, signed and unmissable.
            Text(
                text = "%+d ms".format(sliderValue.roundToInt()),
                fontFamily = com.gratia.music.ui.theme.JetBrainsMono,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                color = if (sliderValue.roundToInt() == 0) GratiaTheme.colors.textSecondary else GratiaTheme.colors.accent,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Spacer(Modifier.height(20.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "−5s",
                    fontFamily = Inter,
                    fontSize = 12.sp,
                    color = GratiaTheme.colors.textSecondary
                )
                androidx.compose.material3.Slider(
                    value = sliderValue,
                    onValueChange = { sliderValue = it },
                    onValueChangeFinished = { commit(sliderValue.roundToInt().toLong()) },
                    valueRange = -MAX_OFFSET_MS..MAX_OFFSET_MS,
                    colors = SliderDefaults.colors(
                        thumbColor = GratiaTheme.colors.accent,
                        activeTrackColor = GratiaTheme.colors.accent,
                        inactiveTrackColor = GratiaTheme.colors.surfaceHover
                    ),
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "+5s",
                    fontFamily = Inter,
                    fontSize = 12.sp,
                    color = GratiaTheme.colors.textSecondary
                )
            }

            Spacer(Modifier.height(20.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                // − / + steppers: fine control without trying to land a slider
                // on an exact round number with a thumb.
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedIconButton(
                        onClick = { commit((sliderValue.roundToInt().toLong()) - STEP_MS) },
                        modifier = Modifier.size(52.dp)
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Earlier by 100 milliseconds")
                    }
                    OutlinedIconButton(
                        onClick = { commit((sliderValue.roundToInt().toLong()) + STEP_MS) },
                        modifier = Modifier.size(52.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Later by 100 milliseconds")
                    }
                }

                // Reset only when there is something to reset.
                if (sliderValue.roundToInt() != 0) {
                    TextButton(
                        onClick = { commit(0L) },
                        colors = ButtonDefaults.textButtonColors(contentColor = GratiaTheme.colors.textSecondary)
                    ) {
                        Icon(
                            Icons.Default.RestartAlt,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Reset", fontFamily = Inter)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // The only honest explanation of direction: which way the words
            // move relative to the music, without pretending to know whether
            // the recording or the provider is at fault.
            Text(
                text = when {
                    sliderValue > 0f -> "Words appear later, to catch up with the music"
                    sliderValue < 0f -> "Words appear earlier"
                    else -> "Unchanged"
                },
                fontFamily = Inter,
                fontSize = 12.sp,
                color = GratiaTheme.colors.textSecondary,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = {
                    commit(sliderValue.roundToInt().toLong())
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = GratiaTheme.colors.accent),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Done",
                    fontFamily = Inter,
                    fontWeight = FontWeight.SemiBold,
                    color = androidx.compose.ui.graphics.Color.White
                )
            }
        }
    }
}
