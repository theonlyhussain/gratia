package com.gratia.music.lyrics

enum class SyncLevel(val priority: Int) {
    SYLLABLE(4),
    WORD(3),
    LINE(2),
    UNSYNCED(1),
    NONE(0)
}

data class LyricsResult(
    val text: String,
    val syncLevel: SyncLevel,
    val providerName: String,
    val matchConfidence: Int,
    val durationDifferenceMs: Long
)

interface LyricsProvider {
    val name: String
    suspend fun fetchLyrics(
        title: String, 
        artist: String, 
        album: String? = null,
        durationMs: Long? = null,
        videoId: String? = null
    ): LyricsResult?
}
