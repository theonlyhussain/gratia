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

    override suspend fun fetchLyrics(title: String, artist: String, album: String?): LyricsResult? {
        // Fallback cascade logic:
        // 1. Title + Artist + Album
        // 2. Title + Artist
        // 3. Artist + Title (Sometimes reverses help)

        var result = tryFetch(title, artist, album)
        if (result != null) return result

        Log.d(TAG, "Search with album failed. Retrying with just title and artist.")
        result = tryFetch(title, artist, null)
        if (result != null) return result

        Log.d(TAG, "Search with title/artist failed. Retrying with artist/title flipped.")
        result = tryFetch(artist, title, null)
        return result
    }

    private suspend fun tryFetch(title: String, artist: String, album: String?): LyricsResult? = withContext(Dispatchers.IO) {
        try {
            val titleEncoded = URLEncoder.encode(title, "UTF-8")
            val artistEncoded = URLEncoder.encode(artist, "UTF-8")
            var urlString = "https://lrclib.net/api/get?track_name=$titleEncoded&artist_name=$artistEncoded"
            
            if (!album.isNullOrBlank()) {
                val albumEncoded = URLEncoder.encode(album, "UTF-8")
                urlString += "&album_name=$albumEncoded"
            }

            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "Gratia Music Player (https://github.com/theonlyhussain/gratia)")
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)

                val syncedLyrics = json.optString("syncedLyrics")
                val plainLyrics = json.optString("plainLyrics")

                if (syncedLyrics.isNotBlank()) {
                    return@withContext LyricsResult(syncedLyrics, true, name)
                } else if (plainLyrics.isNotBlank()) {
                    return@withContext LyricsResult(plainLyrics, false, name)
                }
            } else {
                Log.e(TAG, "API returned code ${connection.responseCode}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching lyrics", e)
        }
        return@withContext null
    }
}
