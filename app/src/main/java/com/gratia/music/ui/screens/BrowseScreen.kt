package com.gratia.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gratia.music.GratiaApp
import com.gratia.music.provider.BrowseCategory
import com.gratia.music.ui.components.AppleLargeTitleHeader
import com.gratia.music.ui.components.GratiaLoadingState
import com.gratia.music.ui.components.GratiaText
import com.gratia.music.ui.components.bounceClick
import com.gratia.music.ui.theme.GratiaTheme
import com.gratia.music.ui.theme.SpaceGrotesk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Explore — the online discovery surface.
 *
 * Deliberately not a local genre screen: the categories are YouTube Music's own
 * Moods & genres taxonomy, fetched rather than hard-coded, and tapping one opens
 * a real catalogue page. Local music browsing lives in the Library, where it
 * belongs (the rebuild's Phase 37).
 */
@Composable
fun BrowseScreen(
    onNavigateToCategory: (browseId: String, params: String?, title: String) -> Unit = { _, _, _ -> }
) {
    var categories by remember {
        mutableStateOf(GratiaApp.instance.providerManager.cachedBrowseCategories().orEmpty())
    }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var isOffline by remember { mutableStateOf(false) }
    var refreshNonce by remember { mutableStateOf(0) }

    // Re-keyed on connectivity so Explore fills itself in again as soon as the
    // network returns.
    val isOnline by com.gratia.music.data.network.NetworkMonitor.isOnline.collectAsState()

    LaunchedEffect(refreshNonce, isOnline) {
        if (!isOnline) {
            // Explore is entirely remote, so with no network there is nothing
            // to draw — and a spinner would be a lie. Only surfaced when there
            // is no cached taxonomy to keep showing.
            isOffline = categories.isEmpty()
            isLoading = false
            return@LaunchedEffect
        }

        isLoading = true
        error = null
        isOffline = false
        val fetched = withContext(Dispatchers.IO) {
            GratiaApp.instance.providerManager.getBrowseCategoriesCached(forceRefresh = refreshNonce > 0)
        }
        if (fetched.isEmpty()) {
            // An empty Explore is an endpoint problem, not an empty catalogue —
            // saying so is the difference between "offline" and "broken".
            error = "Couldn't reach YouTube Music. Check your connection and try again."
        } else {
            categories = fetched
        }
        isLoading = false
    }

    val bottomInset = com.gratia.music.ui.LocalBottomPadding.current

    LazyVerticalGrid(
        columns = GridCells.Adaptive(160.dp),
        modifier = Modifier
            .fillMaxSize()
            .background(GratiaTheme.colors.background),
        contentPadding = PaddingValues(
            start = 24.dp,
            end = 24.dp,
            bottom = bottomInset + GratiaTheme.spacing.heroLarge
        ),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                AppleLargeTitleHeader(title = "Explore")
                if (categories.isNotEmpty()) {
                    Text(
                        text = "Moods, genres, eras and moments",
                        fontFamily = com.gratia.music.ui.theme.Inter,
                        fontSize = 14.sp,
                        color = GratiaTheme.colors.textSecondary
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        if (isLoading && categories.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    GratiaLoadingState(message = "Loading categories…")
                }
            }
        }

        if (categories.isEmpty() && (isOffline || error != null)) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    GratiaText(
                        text = if (isOffline) "You're offline" else error ?: "Couldn't load Explore",
                        style = GratiaTheme.typography.body,
                        color = GratiaTheme.colors.textPrimary
                    )
                    if (isOffline) {
                        Spacer(Modifier.height(4.dp))
                        GratiaText(
                            text = "Explore needs a connection. Your library and downloads still work.",
                            style = GratiaTheme.typography.caption,
                            color = GratiaTheme.colors.textSecondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { refreshNonce++ }) {
                        Text(
                            text = "Retry",
                            color = GratiaTheme.colors.accent,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        items(categories, key = { it.id }) { category ->
            CategoryTile(
                category = category,
                onClick = {
                    onNavigateToCategory(category.browseId, category.params, category.title)
                }
            )
        }
    }
}

/** A coloured tile standing in for a category's cover — YouTube ships no art for these. */
@Composable
private fun CategoryTile(
    category: BrowseCategory,
    onClick: () -> Unit
) {
    val hash = kotlin.math.abs(category.id.hashCode())
    val accent = CATEGORY_PALETTE[hash % CATEGORY_PALETTE.size]
    val second = CATEGORY_PALETTE[(hash / CATEGORY_PALETTE.size) % CATEGORY_PALETTE.size]

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(112.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.linearGradient(listOf(accent, second))
            )
            .bounceClick(onClick = onClick),
        contentAlignment = Alignment.BottomStart
    ) {
        Text(
            text = category.title,
            fontFamily = SpaceGrotesk,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(14.dp)
        )
    }
}

/** Warm, album-cover-ish tones that read against white text. */
private val CATEGORY_PALETTE = listOf(
    Color(0xFF810100), // cherry red
    Color(0xFF630102), // maroon
    Color(0xFFA65D03), // warm amber
    Color(0xFF8B4513), // saddle brown
    Color(0xFF4A2020), // dark wine
    Color(0xFF7A3B1E), // copper
    Color(0xFF1F4E5F), // deep teal
    Color(0xFF3B2A5A), // dusk violet
)
