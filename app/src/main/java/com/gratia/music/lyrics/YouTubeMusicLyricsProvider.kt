package com.gratia.music.lyrics

import android.util.Log
import com.gratia.music.GratiaApp

class YouTubeMusicLyricsProvider : LyricsProvider {

    override val name: String = "YouTube Music"

    override suspend fun fetchLyrics(
        title: String,
        artist: String,
        album: String?,
        durationMs: Long?,
        videoId: String?
    ): LyricsResult? {
        // Prefer direct lookup if videoId is known
        if (!videoId.isNullOrBlank()) {
            return fetchLyricsForVideo(videoId)
        }
        
        // Fallback search-based lyrics if videoId is not known directly
        return try {
            val query = "$title $artist"
            val searchRes = GratiaApp.instance.providerManager.youtubeMusicProvider.search(query, filter = "songs", limit = 1)
            val vId = searchRes.tracks.firstOrNull()?.videoId ?: return null
            fetchLyricsForVideo(vId)
        } catch (e: Exception) {
            Log.e("YTM_Lyrics", "fetchLyrics error", e)
            null
        }
    }

    suspend fun fetchLyricsForVideo(videoId: String): LyricsResult? {
        return try {
            val remoteLyrics = GratiaApp.instance.providerManager.youtubeMusicProvider.getLyrics(videoId)
            if (remoteLyrics != null && remoteLyrics.text.isNotBlank()) {
                LyricsResult(
                    text = remoteLyrics.text,
                    syncLevel = if (remoteLyrics.isSynced) SyncLevel.LINE else SyncLevel.UNSYNCED,
                    providerName = "YouTube Music",
                    matchConfidence = 95, // High because it's videoId mapped usually
                    durationDifferenceMs = 0L
                )
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("YTM_Lyrics", "fetchLyricsForVideo error", e)
            null
        }
    }
}
