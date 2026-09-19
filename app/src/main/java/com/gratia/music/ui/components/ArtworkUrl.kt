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

/** Anything smaller would be a downgrade; anything larger, a waste of a download. */
private const val MIN_PIXELS = 120
private const val MAX_PIXELS = 1280

/** The hosts that serve resizable artwork. `ggpht` is the older alias. */
private val IMAGE_HOSTS = listOf("googleusercontent.com", "ggpht.com")

private val SIZE_PAIR = Regex("""=w\d+-h\d+""")
private val SIZE_SINGLE = Regex("""=s\d+""")

/** What the now-playing sleeve is drawn at, before it collapses into a header. */
const val PLAYER_ARTWORK_PX = 1080

/** Page headers and large cards. */
const val LARGE_ARTWORK_PX = 544

/** Rows, list items and the mini player. */
const val ROW_ARTWORK_PX = 240
