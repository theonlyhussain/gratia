package com.gratia.music.lyrics

data class LyricsResult(
    val text: String,
    val isSynced: Boolean,
    val providerName: String
)

interface LyricsProvider {
    val name: String
    suspend fun fetchLyrics(title: String, artist: String, album: String? = null): LyricsResult?
}
