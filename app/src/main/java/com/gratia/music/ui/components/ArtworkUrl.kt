package com.gratia.music.ui.components

/**
 * Asks a remote thumbnail host for the size the artwork is actually drawn at.
 *
 * Google's image hosts serve thumbnails at whatever size the URL names — the
 * `=w120-h120-l90-rj` tail of a YouTube Music cover is a *request*, not a
 * description, and the same asset is available at any size up to the original.
 * A response that lists its renditions by ascending size therefore hands out a
 * 120px square for a track, and that is the copy that ends up on the now-playing
 * screen: a search-result thumbnail blown up to the width of the display, which
 * is what "the artwork looks bad" turns out to be.
 *
 * So each surface asks for what it draws, exactly as it would from an image
 * loader's own size hint. Rows ask for a couple of hundred pixels, the player
 * asks for a thousand, and nothing downloads a megapixel cover to paint a
 * 48dp square.
 *
 * A no-op for anything that isn't one of those hosts, for local files and
 * content URIs, and for a URL that carries no size token at all — so it is safe
 * to put in front of every piece of artwork in the app.
 */
fun artworkAtSize(url: String?, pixels: Int): String? {
    if (url.isNullOrBlank()) return url
    if (!url.startsWith("http")) return url
    if (IMAGE_HOSTS.none { url.contains(it) }) return url

    val target = pixels.coerceIn(MIN_PIXELS, MAX_PIXELS)

    // The usual shape: `=w544-h544-l90-rj`. Both axes are set together because
    // the crop is square on every surface that shows cover art, and a mismatch
    // between them makes the host letterbox rather than fill.
    SIZE_PAIR.find(url)?.let { match ->
        return url.replaceRange(match.range, "=w$target-h$target")
    }

    // The older single-axis shape: `=s544-c`. Only the size is set; the `-c`
    // crop flag and anything after it is left exactly as it was.
    SIZE_SINGLE.find(url)?.let { match ->
        return url.replaceRange(match.range, "=s$target")
    }

    return url
}

/**
 * The resolution a URL currently asks for, if it names one.
 *
 * Used to decide whether a request is an upgrade or a downgrade: the size
 * token already in a URL is the *floor*, never the ceiling, so a surface that
 * asks for more than the URL names gets a sharper rendition and a surface
 * that asks for less never silently throws away pixels it already had.
 */
fun artworkTokenPixels(url: String?): Int? {
    if (url.isNullOrBlank()) return null

    SIZE_PAIR.find(url)?.let { match ->
        val width = WIDTH.find(match.value)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val height = HEIGHT.find(match.value)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        return maxOf(width, height).takeIf { it > 0 }
    }
    SIZE_SINGLE.find(url)?.let { match ->
        return match.value.removePrefix("=s").toIntOrNull()?.takeIf { it > 0 }
    }
    return null
}

/**
 * Returns [url] asked for at whichever is larger: the size it already names, or
 * [resolvedPixels] — the size the image will actually be drawn at.
 *
 * This is the one rule that keeps artwork sharp everywhere. A URL that arrived
 * with a 120px thumbnail token (`=w120-h120`) is a 120px request no matter how
 * large the surface drawing it, which is why a search thumbnail blown up to a
 * full-width header looked bad. Raising the token to the drawn size fixes the
 * source rather than upscaling the blur, and because it only ever raises, an
 * explicit small size (a 48dp row) never accidentally downloads a megapixel
 * cover.
 *
 * A no-op for local paths, content URIs and hosts that do not honour a size
 * token, so it is safe to place in front of every image in the app.
 */
fun upgradedArtworkUrl(url: String?, resolvedPixels: Int): String? {
    if (url.isNullOrBlank()) return url
    if (!url.startsWith("http")) return url
    if (IMAGE_HOSTS.none { url.contains(it) }) return url

    val existing = artworkTokenPixels(url)
    val desired = if (resolvedPixels > 0) resolvedPixels else (existing ?: DEFAULT_ARTWORK_PX)
    val target = maxOf(existing ?: 0, desired)
    return artworkAtSize(url, target)
}

/**
 * The cache identity of a remote cover: the asset itself, with the size request
 * removed.
 *
 * [artworkAtSize] turns one asset into as many URLs as there are surfaces that
 * draw it — a row asks for a few hundred pixels, a page header for a thousand,
 * the player for more. Those are the same picture, and a loader keyed on the raw URL treats
 * them as three separate downloads. Stripping the size token leaves one key per
 * asset, so the bytes are fetched once and re-decoded to whatever size each
 * surface actually needs.
 *
 * Memory cache keys stay size-aware (two sizes should not evict each other),
 * which is why this is applied to the disk cache only. Non-resizable URLs —
 * local files, content URIs, hosts that do not honour a size token — are
 * returned unchanged and behave exactly as before.
 */
fun artworkCacheKey(url: String?): String? {
    if (url.isNullOrBlank()) return url
    if (!url.startsWith("http")) return url
    if (IMAGE_HOSTS.none { url.contains(it) }) return url

    var base: String = url ?: return url
    SIZE_PAIR.find(base)?.let { base = base.replaceRange(it.range, "") }
    SIZE_SINGLE.find(base)?.let { base = base.replaceRange(it.range, "") }
    return base
}

/**
 * The smallest rendition worth fetching: below this the host is being asked
 * for a thumbnail it will upscale itself. Everything else follows the surface.
 */
private const val MIN_PIXELS = 120

/**
 * The largest rendition the image hosts actually serve — YouTube Music covers
 * top out around 2260px, so asking for more is a wasted request, not a sharper
 * image.
 */
private const val MAX_PIXELS = 2260

/** The hosts that serve resizable artwork. `ggpht` is the older alias. */
private val IMAGE_HOSTS = listOf("googleusercontent.com", "ggpht.com")

private val SIZE_PAIR = Regex("""=w\d+-h\d+""")
private val SIZE_SINGLE = Regex("""=s\d+""")
private val WIDTH = Regex("""w(\d+)""")
private val HEIGHT = Regex("""h(\d+)""")

/** What the now-playing sleeve is drawn at, before it collapses into a header. */
const val PLAYER_ARTWORK_PX = 1440

/** Page headers and large cards. */
const val LARGE_ARTWORK_PX = 1080

/** Rows, list items and the mini player. */
const val ROW_ARTWORK_PX = 320

/**
 * The fallback when a surface draws artwork without naming a size at all —
 * large enough to look sharp wherever it lands, small enough not to dominate
 * a download.
 */
const val DEFAULT_ARTWORK_PX = 1080
