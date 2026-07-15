package com.gratia.music.lyrics

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class LRCLIBProvider : LyricsProvider {
    override val name = "LRCLIB"
    private val TAG = "LRCLIBProvider"

    // Optional duration Ms for precise matching
    suspend fun fetchLyricsWithDuration(title: String, artist: String, album: String?, durationMs: Long?): LyricsResult? {
        // Fallback cascade logic according to spec:
        // 1. Artist + Title + Duration (LRCLIB API prefers this order actually, but the query params don't care)
        // Let's use the explicit cascade:
        // 1. Artist + Title + Duration (and album if present)
        var result = tryFetch(title, artist, album, durationMs)
        if (result != null) return result

        Log.d(TAG, "Search 1 failed. Retrying with Artist + Title + Duration (no album).")
        result = tryFetch(title, artist, null, durationMs)
        if (result != null) return result

        Log.d(TAG, "Search 2 failed. Retrying with Title + Artist (flipped) + Duration.")
        result = tryFetch(artist, title, null, durationMs)
        if (result != null) return result

        Log.d(TAG, "Search 3 failed. Retrying with just Title.")
        result = tryFetch(title, "", null, null)
        return result
    }

    override suspend fun fetchLyrics(title: String, artist: String, album: String?): LyricsResult? {
        return fetchLyricsWithDuration(title, artist, album, null)
    }

    private suspend fun tryFetch(title: String, artist: String, album: String?, durationMs: Long?): LyricsResult? = withContext(Dispatchers.IO) {
        try {
            val titleEncoded = URLEncoder.encode(title, "UTF-8")
            val artistEncoded = if (artist.isNotBlank()) URLEncoder.encode(artist, "UTF-8") else ""
            var urlString = "https://lrclib.net/api/get?track_name=$titleEncoded"
            
            if (artistEncoded.isNotBlank()) {
                urlString += "&artist_name=$artistEncoded"
            }
            if (!album.isNullOrBlank()) {
                val albumEncoded = URLEncoder.encode(album, "UTF-8")
                urlString += "&album_name=$albumEncoded"
            }
            if (durationMs != null && durationMs > 0) {
                val durationSec = durationMs / 1000
                urlString += "&duration=$durationSec"
            }

            Log.d(TAG, "Requesting: $urlString")
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "Gratia Music Player (https://github.com/theonlyhussain/gratia)")
            connection.connectTimeout = 8000
            connection.readTimeout = 8000

            val responseCode = connection.responseCode
            if (responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)

                val syncedLyrics = json.optString("syncedLyrics")
                val plainLyrics = json.optString("plainLyrics")

                if (syncedLyrics.isNotBlank()) {
                    return@withContext LyricsResult(syncedLyrics, true, name)
                } else if (plainLyrics.isNotBlank()) {
                    return@withContext LyricsResult(plainLyrics, false, name)
                }
            } else if (responseCode == 404) {
                Log.d(TAG, "API returned 404 Not Found")
                // Expected, we can just return null and let fallback continue
            } else if (responseCode >= 500) {
                Log.e(TAG, "API returned Server Error: $responseCode")
                // Could throw to prevent fallback, but spec says "gracefully recover / never infinite retry"
                // Returning null lets it try fallbacks, but realistically server error affects all queries.
            } else {
                Log.e(TAG, "API returned unexpected code $responseCode")
            }
        } catch (e: java.net.SocketTimeoutException) {
            Log.e(TAG, "Timeout fetching lyrics", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching lyrics", e)
        }
        return@withContext null
    }
}
