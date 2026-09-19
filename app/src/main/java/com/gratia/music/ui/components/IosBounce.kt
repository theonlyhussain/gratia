package com.gratia.music.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * iOS-style rubber banding for a scrollable, in place of Android's own
 * overscroll.
 *
 * Android's effect is applied by the platform and is drawn inside the
 * scrollable's own bounds — a glow, or on Android 12 and up a stretch of the
 * list. What iOS does instead is move the whole surface: the page keeps
 * following the finger past its end, against a resistance that stiffens the
 * further it is pulled, and springs back when it is let go. Nothing is drawn,
 * the layout itself gives.
 *
 * That is what this container provides. When the list has nothing left to
 * consume during a drag, the leftover is spent pulling the content instead, at
 * [RUBBER_BAND] of the finger's own travel, damped further the closer the pull
 * gets to [maxStretch]. A drag back the other way gives the stretch up before
 * the list starts to move again, so the two never fight. On release the pull is
 * handed to a spring that carries it home.
 *
 * Compose's own overscroll is switched off inside this container, because the
 * two would otherwise both act on the same drag and the page would travel twice
 * as far as the finger.
 */
@OptIn(ExperimentalFoundationApi::class)
@Suppress("DEPRECATION")
@Composable
fun IosBounce(
    isScrolling: () -> Boolean,
    modifier: Modifier = Modifier,
    maxStretch: Dp = MAX_STRETCH,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    // Guarded rather than trusted: a zero limit would divide the resistance
    // curve by nothing and hand the spring a NaN to settle from.
    val limitPx = with(density) { maxStretch.toPx() }.coerceAtLeast(1f)
    val stretch = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    // The finger leaving the screen is what hands the pull to the spring.
    LaunchedEffect(Unit) {
        snapshotFlow { isScrolling() }.collect { scrolling ->
            if (!scrolling && stretch.value != 0f) {
                stretch.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        dampingRatio = SPRING_DAMPING,
                        stiffness = SPRING_STIFFNESS
                    )
                )
            }
        }
    }

    val connection = remember(limitPx) {
        object : NestedScrollConnection {

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                val held = stretch.value
                if (held == 0f) return Offset.Zero

                // A drag back towards the list gives the stretch up first. Only
                // what is left over after that reaches the list, so the page
                // never moves and relaxes at the same time.
                val towards = when {
                    held > 0f && available.y < 0f -> -available.y
                    held < 0f && available.y > 0f -> available.y
                    else -> return Offset.Zero
                }
                val given = minOf(abs(held), towards)
                scope.launch { stretch.snapTo(held - given * if (held > 0f) 1f else -1f) }
                return Offset(0f, if (held > 0f) -given else given)
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                // Anything left over means the list had nowhere to put it: the
                // finger is past the end and the page starts to give.
                if (source != NestedScrollSource.UserInput || available.y == 0f) return Offset.Zero
                val held = stretch.value
                val room = 1f - (abs(held) / limitPx).coerceIn(0f, 1f)
                val pulled = held + available.y * RUBBER_BAND * room
                scope.launch { stretch.snapTo(pulled.coerceIn(-limitPx, limitPx)) }
                // Reported as consumed: the drag has been answered, just not by
                // the list.
                return Offset(0f, available.y)
            }
        }
    }

    CompositionLocalProvider(LocalOverscrollConfiguration provides null) {
        Box(
            modifier = modifier
                .nestedScroll(connection)
                .graphicsLayer { translationY = stretch.value }
        ) {
            content()
        }
    }
}

/** Convenience overloads so a caller can hand over the state it already has. */

@Composable
fun IosBounce(
    state: LazyListState,
    modifier: Modifier = Modifier,
    maxStretch: Dp = MAX_STRETCH,
    content: @Composable () -> Unit
) = IosBounce(
    isScrolling = { state.isScrollInProgress },
    modifier = modifier,
    maxStretch = maxStretch,
    content = content
)

@Composable
fun IosBounce(
    state: ScrollState,
    modifier: Modifier = Modifier,
    maxStretch: Dp = MAX_STRETCH,
    content: @Composable () -> Unit
) = IosBounce(
    isScrolling = { state.isScrollInProgress },
    modifier = modifier,
    maxStretch = maxStretch,
    content = content
)

/** How far a page may be pulled past its end before it stops giving. */
private val MAX_STRETCH = 110.dp

/**
 * How much of the finger's own travel the page takes while it is being pulled.
 *
 * Under one, so the page visibly lags the finger. It is the lag that reads as
 * resistance; a page that tracked the finger exactly would read as the list
 * simply being longer.
 */
private const val RUBBER_BAND = 0.55f

/** Underdamped, so the page settles back with a little overshoot. */
private const val SPRING_DAMPING = 0.62f
private const val SPRING_STIFFNESS = Spring.StiffnessMediumLow
