package com.gratia.music.lyrics

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * LyricsifyProvider uses the iTunes Search API to resolve an Apple Music track ID,
 * then fetches the raw TTML lyrics from the Paxsenix Apple Music Lyrics API.
 */
class LyricsifyProvider : LyricsProvider {
    override val name = "Lyricsify"
    private val TAG = "LyricsifyProvider"

    override suspend fun fetchLyrics(
        title: String,
        artist: String,
        album: String?,
        durationMs: Long?,
        videoId: String?
    ): LyricsResult? = withContext(Dispatchers.IO) {
        try {
            // 1. Resolve Apple Music track ID via iTunes Search API
            val query = "$title $artist".trim()
            val queryEncoded = URLEncoder.encode(query, "UTF-8")
            val searchUrl = URL("https://itunes.apple.com/search?term=$queryEncoded&limit=5&entity=song")
            
            Log.d(TAG, "Search Requesting: $searchUrl")
            val searchConnection = searchUrl.openConnection() as HttpURLConnection
            searchConnection.requestMethod = "GET"
            searchConnection.setRequestProperty("User-Agent", "Gratia Music Player (https://github.com/theonlyhussain/gratia)")
            searchConnection.connectTimeout = 8000
            searchConnection.readTimeout = 8000

            var trackId: Long? = null

            if (searchConnection.responseCode == 200) {
                val searchResponse = searchConnection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(searchResponse)
                val results = json.optJSONArray("results")
                
                if (results != null && results.length() > 0) {
                    val firstMatch = results.getJSONObject(0)
                    trackId = firstMatch.optLong("trackId", -1L).takeIf { it != -1L }
                }
            } else {
                Log.w(TAG, "iTunes Search API failed: ${searchConnection.responseCode}")
            }

            if (trackId == null) {
                Log.d(TAG, "No Apple Music track ID found for: $title - $artist")
                return@withContext null
            }

            // 2. Fetch TTML lyrics from Paxsenix API
            val lyricsUrl = URL("https://lyrics.paxsenix.org/apple-music/lyrics?id=$trackId&ttml=true")
            Log.d(TAG, "Lyrics Requesting: $lyricsUrl")
            
            val connection = lyricsUrl.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "Gratia Music Player (https://github.com/theonlyhussain/gratia)")
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                
                // If it successfully returns TTML content (which should start with <tt or <?xml)
                if (response.isNotBlank() && (response.contains("<tt") || response.contains("ttm:"))) {
                    return@withContext LyricsResult(
                        text = response,
                        syncLevel = SyncLevel.SYLLABLE, // TTML usually provides syllable/word sync
                        providerName = name,
                        matchConfidence = 85,
                        durationDifferenceMs = 0L 
                    )
                }
            } else {
                Log.w(TAG, "Paxsenix API failed: ${connection.responseCode}")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error fetching from LyricsifyProvider", e)
        }
        
        return@withContext null
    }
}
