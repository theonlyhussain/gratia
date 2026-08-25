package com.gratia.music.data.model

data class ArtistListenSummary(
    val artist: String,
    val totalSeconds: Long
)

data class TrackListenSummary(
    val songId: String,
    val title: String,
    val artist: String,
    val totalSeconds: Long
)
