package com.gratia.music.provider

/**
 * One rule for folding a remote detail response into what is already known.
 *
 * The artist page's worst bug was not a rendering bug — it was a *merge* bug.
 * A refresh could come back only partially parsed (YouTube's immersive header
 * renders differently run to run), and the screen did `artist = response`, so a
 * null thumbnail erased a good cover and a missing name became "Unknown Artist".
 * The page degraded on its own after a few minutes, which is exactly what an
 * unconditional overwrite of known-good data looks like (spec C/N/O).
 *
 * The rule is: a field the response does not actually carry never wins. Blank
 * strings, the literal placeholder name, and null/blank URLs are treated as
 * "not stated" and leave the existing value in place. Lists merge the same way —
 * an empty section is not evidence that the albums vanished.
 *
 * Pure and dependency-free so the policy is pinned by a unit test rather than by
 * reading a screen (spec V #10).
 */
object RemoteDetailMerge {

    private const val PLACEHOLDER_NAME = "Unknown Artist"

    /** True when [name] is a real credit rather than an absent one. */
    fun isRealName(name: String?): Boolean =
        !name.isNullOrBlank() && !name.trim().equals(PLACEHOLDER_NAME, ignoreCase = true)

    /**
     * Folds [incoming] over [existing], keeping every known-good field the
     * response did not supply. Returns [incoming] unchanged when there is nothing
     * to merge into.
     */
    fun mergeArtist(existing: RemoteArtist?, incoming: RemoteArtist): RemoteArtist {
        if (existing == null) return incoming
        // A response for a different artist id must never be merged onto this
        // one — that would be the stale-request overwrite. Only same-identity
        // responses merge (spec C, stale requests).
        if (existing.id != incoming.id) return incoming
        return incoming.copy(
            name = incoming.name.takeIf { isRealName(it) } ?: existing.name,
            description = incoming.description.validOr(existing.description),
            subscribers = incoming.subscribers.validOr(existing.subscribers),
            monthlyListeners = incoming.monthlyListeners.validOr(existing.monthlyListeners),
            artworkUrl = incoming.artworkUrl.validOr(existing.artworkUrl),
            topSongs = incoming.topSongs.ifEmpty { existing.topSongs },
            albums = incoming.albums.ifEmpty { existing.albums },
            singles = incoming.singles.ifEmpty { existing.singles },
            relatedArtists = incoming.relatedArtists.ifEmpty { existing.relatedArtists },
            songsPlaylistId = incoming.songsPlaylistId.validOr(existing.songsPlaylistId),
        )
    }

    /** As [mergeArtist], for an album detail response. */
    fun mergeAlbum(existing: RemoteAlbum?, incoming: RemoteAlbum): RemoteAlbum {
        if (existing == null) return incoming
        if (existing.id != incoming.id) return incoming
        return incoming.copy(
            title = incoming.title.takeIf { it.isNotBlank() } ?: existing.title,
            artists = incoming.artists.ifEmpty { existing.artists },
            year = incoming.year.validOr(existing.year),
            artworkUrl = incoming.artworkUrl.validOr(existing.artworkUrl),
            trackCount = incoming.trackCount ?: existing.trackCount,
            durationText = incoming.durationText.validOr(existing.durationText),
            tracks = incoming.tracks.ifEmpty { existing.tracks },
        )
    }
}

/** [this] when it states a real value, otherwise [fallback]. */
private fun String?.validOr(fallback: String?): String? =
    this?.takeIf { it.isNotBlank() } ?: fallback
