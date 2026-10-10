package com.gratia.music.data.model

/**
 * An artist aggregate for the stats screens.
 *
 * [artworkUrl] is the best-known cover among the artist's listened tracks,
 * carried on the aggregate itself so a remote artist — who has no row in the
 * `songs` table — still shows real artwork instead of initials (spec 59).
 * Null only when no listened track of theirs had a resolvable cover.
 */
data class ArtistListenSummary(
    val artist: String,
    val totalSeconds: Long,
    val artworkUrl: String? = null
)

/**
 * A track aggregate for the stats screens.
 *
 * [artworkUrl] comes from the listening event's own snapshot, so a streamed
 * track that was never persisted to `songs` still renders its cover (spec 60).
 */
data class TrackListenSummary(
    val songId: String,
    val title: String,
    val artist: String,
    val totalSeconds: Long,
    val artworkUrl: String? = null
)
