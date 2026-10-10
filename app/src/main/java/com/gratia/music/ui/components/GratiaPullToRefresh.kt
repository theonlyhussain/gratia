package com.gratia.music.ui.components

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.gratia.music.ui.theme.GratiaTheme

/**
 * Gratia's pull-to-refresh.
 *
 * Wraps the platform gesture rather than inventing a drag handler, so the
 * content follows the finger, the threshold is the one Android users already
 * know, and an accidental nudge past a couple of pixels does not start a
 * refresh. What is Gratia's here is only the indicator: the same accent-on-
 * surface treatment as the rest of the app, sitting where the large title
 * leaves room for it.
 *
 * The refreshing state is a *report*, not a gate — the caller keeps rendering
 * its cached content underneath while the spinner turns.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GratiaPullToRefresh(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val state = rememberPullToRefreshState()

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize(),
        state = state,
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = state,
                isRefreshing = isRefreshing,
                modifier = Modifier.align(Alignment.TopCenter),
                containerColor = GratiaTheme.colors.surface,
                color = GratiaTheme.colors.accent
            )
        },
        content = content
    )
}
