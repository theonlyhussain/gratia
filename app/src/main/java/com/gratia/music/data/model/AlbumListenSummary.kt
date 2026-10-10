package com.gratia.music.data.model

/**
 * An album aggregate for the Replay ranking — extended with the artwork of its
 * best-known track (spec 100). The event table carries song ids only, so the
 * artwork is resolved through the album's top track at query time; an album
 * with no resolvable cover simply has none, and the row falls back to the
 * initials treatment rather than a blank or a guess.
 */
data class AlbumListenSummary(
    val name: String,
    val artist: String?,
    val totalSeconds: Long,
    val artworkUrl: String? = null
)
