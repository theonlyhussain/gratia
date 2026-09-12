package com.gratia.music.lyrics

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlin.math.abs

/**
 * Lyrics from SimpMusic's community database, keyed on the YouTube video id.
 *
 * That key is what makes it worth having: every other provider matches on
 * title and artist and can hand back a different edit of the same song, which
 * drifts out of sync a verse in. This one is looking up the exact track that
 * is playing.
 *
 * Two caveats, both seen in the wild:
 *  - the host geoblocks some regions outright, answering 403 with a "Access
 *    denied from your region" body rather than a network error, so a miss here
 *    can be permanent for a given user and the chain must carry on past it;
 *  - the rich sync is served HTML-escaped — see [EnhancedLrc.decodeEntities].
 */
object SimpMusicLyrics : LyricsProvider {

    override val name = "SimpMusic"

    private const val BASE = "https://api-lyrics.simpmusic.org/v1/"

    /** Duration slack when the database holds several cuts of one video. */
    private const val DURATION_TOLERANCE_SECONDS = 10

    override suspend fun fetchLyrics(
        title: String,
        artist: String,
        album: String?,
        durationMs: Long?,
        videoId: String?
    ): LyricsResult? = withContext(Dispatchers.IO) {
        if (videoId.isNullOrBlank()) return@withContext null
        val body = lyricsGet(BASE + videoId) ?: return@withContext null
        val response = runCatching { lyricsJson.decodeFromString<Response>(body) }.getOrNull()
        if (response == null || !response.success) return@withContext null

        val seconds = ((durationMs ?: 0L) / 1000).toInt()
        val validTracks = response.data.orEmpty()
            .filter { seconds <= 0 || abs((it.duration ?: 0) - seconds) <= DURATION_TOLERANCE_SECONDS }
            
        val track = validTracks.minByOrNull { abs((it.duration ?: 0) - seconds) }
            ?: return@withContext null

        val durationDiff = abs((track.duration ?: 0) - seconds) * 1000L

        // Word timing first; a line-synced answer from here is no better
        // than LRCLIB's, but it is still better than nothing.
        val wordLines = track.richSyncLyrics?.takeIf { it.isNotBlank() }
            ?.let { EnhancedLrc.parse(it) }
            ?.takeIf { it.isNotEmpty() }
            
        if (wordLines != null) {
            return@withContext LyricsResult(
                text = track.richSyncLyrics ?: "",
                syncLevel = SyncLevel.WORD,
                providerName = name,
                matchConfidence = 95, // High because it's a videoId match!
                durationDifferenceMs = durationDiff
            )
        }
        
        val lineLines = track.syncedLyrics?.takeIf { it.isNotBlank() }
            ?.let { LrcLib.parseLrc(it) }
            ?.takeIf { it.isNotEmpty() }
            
        if (lineLines != null) {
            return@withContext LyricsResult(
                text = track.syncedLyrics ?: "",
                syncLevel = SyncLevel.LINE,
                providerName = name,
                matchConfidence = 95,
                durationDifferenceMs = durationDiff
            )
        }
        
        null
    }

    @Serializable
    internal data class Response(
        val success: Boolean = false,
        val data: List<Track>? = null,
    )

    @Serializable
    internal data class Track(
        val duration: Int? = null,
        val richSyncLyrics: String? = null,
        val syncedLyrics: String? = null,
        val plainLyrics: String? = null,
    )
}
