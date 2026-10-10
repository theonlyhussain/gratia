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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import com.gratia.music.GratiaApp
import com.gratia.music.provider.BrowseCategory
import com.gratia.music.ui.components.AppleLargeTitleHeader
import com.gratia.music.ui.components.GratiaLoadingState
import com.gratia.music.ui.components.GratiaText
import com.gratia.music.ui.components.LARGE_ARTWORK_PX
import com.gratia.music.ui.components.bounceClick
import com.gratia.music.ui.components.rememberArtworkRequest
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
    val providerManager = GratiaApp.instance.providerManager
    val context = androidx.compose.ui.platform.LocalContext.current

    var categories by remember {
        mutableStateOf(providerManager.cachedBrowseCategories().orEmpty())
    }
    // Seeded from the covers already resolved for these categories — in this
    // process or a previous one. The first frame Explore draws therefore has
    // real artwork on it whenever Gratia has ever seen this taxonomy, instead
    // of a grid of gradients waiting on a dozen browse requests.
    var artworkByCategory by remember {
        mutableStateOf(providerManager.cachedCategoryArtwork())
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

        // Warm the artwork index before the taxonomy arrives, so the covers can
        // be applied in the same frame the grid is.
        withContext(Dispatchers.IO) { providerManager.primeCategoryArtwork() }
        artworkByCategory = providerManager.cachedCategoryArtwork()

        val fetched = withContext(Dispatchers.IO) {
            providerManager.getBrowseCategoriesCached(forceRefresh = refreshNonce > 0)
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

    // Real covers, resolved once per category from its own page and kept in
    // memory *and* on disk. Resolution never blocks the grid: cards render with
    // whatever cover is known and fill in as the rest arrive, and each newly
    // resolved cover is preloaded so the next screenful is already warm.
    LaunchedEffect(categories) {
        var preloaded = 0
        categories.forEach { category ->
            if (artworkByCategory[category.id].isNullOrBlank()) {
                val url = withContext(Dispatchers.IO) {
                    providerManager.getCategoryArtwork(category)
                }
                if (!url.isNullOrBlank()) {
                    artworkByCategory = artworkByCategory + (category.id to url)
                }
            }

            val href = artworkByCategory[category.id]
            if (preloaded < ARTWORK_PRELOAD_COUNT && !href.isNullOrBlank()) {
                preloaded++
                preloadCategoryArtwork(context, href)
            }
        }
    }

    val bottomInset = com.gratia.music.ui.LocalBottomPadding.current

    LazyVerticalGrid(
        columns = GridCells.Adaptive(160.dp),
        modifier = Modifier
            .fillMaxSize()
            .background(GratiaTheme.colors.background)
            // Explore draws under the status bar without this — the only tab
            // that did. The background still runs edge to edge; the title and
            // tiles stay clear of the cutout.
            .statusBarsPadding(),
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
                artworkUrl = category.thumbnailUrl ?: artworkByCategory[category.id],
                onClick = {
                    onNavigateToCategory(category.browseId, category.params, category.title)
                }
            )
        }
    }
}

/**
 * One Explore category tile.
 *
 * A real cover when one could be resolved, over a warm placeholder gradient that
 * only shows while that cover is still loading. A bottom scrim keeps the title
 * legible whatever the artwork underneath happens to be.
 */
@Composable
private fun CategoryTile(
    category: BrowseCategory,
    artworkUrl: String?,
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
            .background(Brush.linearGradient(listOf(accent, second)))
            .bounceClick(onClick = onClick),
        contentAlignment = Alignment.BottomStart
    ) {
        if (!artworkUrl.isNullOrBlank()) {
            AsyncImage(
                model = rememberArtworkRequest(artworkUrl, LARGE_ARTWORK_PX, crossfadeMs = 250),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.45f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.65f)
                        )
                    )
            )
        }
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

/**
 * How many covers to warm ahead of the visible grid. Explore's tiles are
 * roughly two-up on a phone, so eight covers is several screenfuls of lead —
 * enough that scrolling meets artwork already in the cache, and few enough that
 * opening Explore never becomes a download queue.
 */
private const val ARTWORK_PRELOAD_COUNT = 8

/**
 * Puts a category cover into Coil's caches without drawing it.
 *
 * Deliberately fire-and-forget on Coil's own dispatcher: a preload is an
 * optimisation, and an optimisation that can throw into a screen's coroutine is
 * not one. The URL is normalised to the size the tile draws at and keyed by
 * asset, so a preload and the tile's own request land on the same cache entry
 * instead of racing each other for two copies.
 */
private fun preloadCategoryArtwork(context: android.content.Context, url: String) {
    val sized = com.gratia.music.ui.components.artworkAtSize(url, LARGE_ARTWORK_PX) ?: return
    val request = ImageRequest.Builder(context)
        .data(sized)
        .size(LARGE_ARTWORK_PX)
        .apply {
            com.gratia.music.ui.components.artworkCacheKey(url)?.let { diskCacheKey(it) }
        }
        .build()
    runCatching { context.imageLoader.enqueue(request) }
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
