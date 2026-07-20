package com.gratia.music.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import com.gratia.music.ui.theme.GratiaTheme
import kotlin.math.max
import kotlin.math.min

/**
 * Apple Music-style collapsible header that smoothly transitions from
 * a large prominent heading into a compact toolbar as the user scrolls.
 *
 * Usage:
 * ```
 * val listState = rememberLazyListState()
 * CollapsingHeader(
 *     listState = listState,
 *     expandedContent = { progress ->
 *         // Large header content — progress goes from 1.0 (fully expanded) to 0.0 (collapsed)
 *     },
 *     collapsedContent = {
 *         // Compact toolbar content — always visible
 *     },
 *     trailingActions = {
 *         // Action buttons (sort, settings, etc.)
 *     }
 * )
 * LazyColumn(state = listState) { ... }
 * ```
 *
 * @param listState The LazyListState to observe for scroll offset
 * @param collapseThresholdDp How many dp of scroll before fully collapsed (default 120dp)
 * @param expandedContent Content shown when expanded, receives collapse progress (1.0 = expanded, 0.0 = collapsed)
 * @param collapsedContent Compact toolbar content always visible
 * @param trailingActions Slot for action buttons on the right side of the collapsed bar
 */
@Composable
fun CollapsingHeader(
    listState: LazyListState,
    modifier: Modifier = Modifier,
    collapseThresholdDp: Dp = 120.dp,
    expandedContent: @Composable (progress: Float) -> Unit,
    collapsedContent: @Composable () -> Unit = {},
    trailingActions: @Composable RowScope.() -> Unit = {}
) {
    val density = LocalDensity.current
    val collapseThresholdPx = with(density) { collapseThresholdDp.toPx() }

    // Calculate raw scroll offset
    val scrollOffset by remember {
        derivedStateOf {
            val firstVisibleItem = listState.firstVisibleItemIndex
            val firstVisibleOffset = listState.firstVisibleItemScrollOffset
            if (firstVisibleItem == 0) {
                firstVisibleOffset.toFloat()
            } else {
                // If we've scrolled past the first item, treat as fully collapsed
                collapseThresholdPx
            }
        }
    }

    // Raw progress: 1.0 = fully expanded, 0.0 = fully collapsed
    val rawProgress = (1f - (scrollOffset / collapseThresholdPx).coerceIn(0f, 1f))

    // Smooth the progress with spring physics for that Apple-feel
    val progress by animateFloatAsState(
        targetValue = rawProgress,
        animationSpec = spring(
            dampingRatio = 0.85f,
            stiffness = Spring.StiffnessMedium
        ),
        label = "collapseProgress"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(GratiaTheme.colors.background)
            .statusBarsPadding()
    ) {
        // ── Collapsed bar (always visible) ──────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = GratiaTheme.spacing.mediumLarge,
                    vertical = GratiaTheme.spacing.small
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Collapsed title — fades in as header collapses
            Box(
                modifier = Modifier
                    .weight(1f)
                    .alpha((1f - progress).coerceIn(0f, 1f))
            ) {
                collapsedContent()
            }

            // Trailing actions — always visible
            Row(
                horizontalArrangement = Arrangement.spacedBy(GratiaTheme.spacing.small),
                verticalAlignment = Alignment.CenterVertically,
                content = trailingActions
            )
        }

        // ── Expanded content (fades/scales away on collapse) ────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    // Scale down slightly as it collapses
                    val scale = 0.92f + (0.08f * progress)
                    scaleX = scale
                    scaleY = scale
                    alpha = progress
                    // Slide up as it collapses
                    translationY = -(1f - progress) * 24f * density.density
                }
                .padding(
                    horizontal = GratiaTheme.spacing.mediumLarge,
                    vertical = lerp(0.dp, GratiaTheme.spacing.mediumSmall, progress)
                )
        ) {
            if (progress > 0.01f) {
                expandedContent(progress)
            }
        }
    }
}
