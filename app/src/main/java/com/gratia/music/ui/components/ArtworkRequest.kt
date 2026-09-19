package com.gratia.music.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import coil.request.ImageRequest
import java.io.File

/**
 * Builds the one image request an artwork surface should hold on to.
 *
 * Two things go wrong when a request is built inline inside a composable, and
 * they are the reason this exists.
 *
 * The first is that `ImageRequest` has no `equals`, and neither does the size
 * resolver that `.size()` installs. Coil compares models to decide whether a
 * load can be kept, so two requests built from the same data are never equal —
 * a screen that recomposes is a screen that re-fetches. The now-playing screen
 * recomposes on every position tick, twice a second, so a freshly built request
 * there pushed the painter back through Loading on every tick and settled on
 * Success again after it. Remembering the request on the data is what makes a
 * cover decode once and stay put.
 *
 * The second is the decode size. A request with no size hint decodes at
 * whatever the layout happens to be when the load starts, which for the sleeve
 * is during a scale spring and on the first frame — transient sizes that are
 * smaller than the final one. Naming the size pins it, so the decode is sized
 * for where the artwork ends up rather than where it started.
 *
 * The remote URL is asked for that same size, since a thumbnail response hands
 * out its smallest rendition otherwise. See [artworkAtSize].
 *
 * Returns null when there is nothing to show, so callers can branch on the same
 * value they would have branched on anyway.
 */
@Composable
fun rememberArtworkRequest(
    coverArtPath: String?,
    sizePx: Int,
    crossfadeMs: Int = 300
): ImageRequest? {
    val context = LocalContext.current
    return remember(coverArtPath, sizePx, crossfadeMs) {
        val isHttp = coverArtPath?.startsWith("http") == true
        val data: Any? = when {
            isHttp -> artworkAtSize(coverArtPath, sizePx)
            !coverArtPath.isNullOrBlank() && File(coverArtPath).exists() -> File(coverArtPath)
            else -> null
        }
        data?.let {
            ImageRequest.Builder(context)
                .data(it)
                .size(sizePx)
                .crossfade(crossfadeMs)
                .build()
        }
    }
}
