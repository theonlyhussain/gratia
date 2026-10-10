package com.gratia.music.ui.components

import coil.map.Mapper
import coil.request.Options
import coil.size.pxOrElse

/**
 * The app-wide answer to "every image should be high quality".
 *
 * Screens hand Coil a cover URL exactly as the provider wrote it, and those
 * URLs carry the provider's *thumbnail* size token — a YouTube Music cover
 * arrives as `...=w120-h120-l90-rj`. Asking for that URL gets a 120px square,
 * whatever the surface then draws it at, so any place that forgot to size its
 * request shows a thumbnail stretched over a card or a full-width header.
 *
 * Rather than audit every `AsyncImage` in the app forever, this rewrites the
 * URL inside the loader, using the size Coil has already resolved for the
 * request. A 48dp row resolves to a small size and is left asking for what it
 * draws; a full-screen player resolves to something near the display width and
 * gets a sharp rendition. [upgradedArtworkUrl] only ever raises the token, so an
 * explicit small size never accidentally pulls a megapixel cover.
 *
 * Only plain `String` data is touched. Local files, content URIs, `File`
 * wrappers and anything on a host that does not honour a size token fall
 * through unchanged, so the mapper is safe to install globally.
 */
class ArtworkSizeMapper : Mapper<String, String> {

    override fun map(data: String, options: Options): String? {
        if (!data.startsWith("http")) return null

        // `pxOrElse(0)` is 0 for an undefined dimension, which is what Coil
        // reports for an unsized ("original") request — exactly the case that
        // should fall back to the app-wide default.
        val size = options.size
        val resolved = maxOf(
            size.width.pxOrElse { 0 },
            size.height.pxOrElse { 0 }
        ).takeIf { it > 0 } ?: DEFAULT_ARTWORK_PX

        val upgraded = upgradedArtworkUrl(data, resolved)
        // Returning null means "no change" to Coil, which is cheaper than
        // handing back an identical string and letting it re-parse.
        return if (upgraded != null && upgraded != data) upgraded else null
    }
}
