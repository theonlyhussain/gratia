package com.gratia.music.lyrics



/**
 * Rhythm lyrics song search result
 */
data class RhythmLyricsSearchResult(
    val id: String,
    val songName: String?,
    val artistName: String?,
    val albumName: String?,
    val artwork: String?,
    val releaseDate: String?,
    val duration: Long?,
    val isrc: String?,
    val url: String?,
    val contentRating: String?,
    val albumId: String?
)

/**
 * Rhythm lyrics response containing word-by-word synchronized lyrics
 */
data class RhythmLyricsResponse(
    val info: String?,
    val type: String?, // "Syllable" for word-by-word
    val content: List<RhythmLyricsLine>?,
    val ttmlContent: String?,
    val source: String?,
    val track: RhythmLyricsTrackInfo?
)

/**
 * Represents a line of lyrics with word-level synchronization
 */
data class RhythmLyricsLine(
    val text: List<RhythmLyricsWord>?,
    val background: Boolean?,
    val backgroundText: List<String>?,
    val oppositeTurn: Boolean?,
    val timestamp: Long?, // Line start timestamp in milliseconds
    val endtime: Long?, // Line end timestamp in milliseconds
    val endIsImplicit: Boolean? = null
)

/**
 * Represents a single word or syllable with precise timing
 */
data class RhythmLyricsWord(
    val text: String,
    val part: Boolean?, // true if this is part of a split word (syllable)
    val timestamp: Long, // Word start timestamp in milliseconds
    val endtime: Long // Word end timestamp in milliseconds
)

/**
 * Track information from Rhythm lyrics source
 */
data class RhythmLyricsTrackInfo(
    val albumName: String?,
    val artistName: String?,
    val name: String?,
    val releaseDate: String?,
    val hasLyrics: Boolean?,
    val hasTimeSyncedLyrics: Boolean?
)

/**
 * Represents a generic track search result from various Lyrically API search endpoints (Spotify, NetEase, QQ, Kugou, YouTube).
 */
data class RhythmLyricsGenericSearchResult(
    val trackId: String?,
    val id: String?,
    val videoId: String?,
    val songmid: String?,
    val hash: String?,
    val name: String?,
    val title: String?,
    val artistName: String?,
    val author: String?,
    val artist: String?
) {
    fun getCanonicalId(): String? {
        return trackId ?: id ?: videoId ?: songmid ?: hash
    }
    
    fun getCanonicalName(): String? {
        return name ?: title
    }
    
    fun getCanonicalArtist(): String? {
        return artistName ?: author ?: artist
    }
}
