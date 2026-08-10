package com.gratia.music.lyrics

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class LyricallyProvider : LyricsProvider {
    override val name = "Lyrically"
    private val TAG = "LyricallyProvider"
    private val BASE_URL = "https://lyrics.paxsenix.org/"

    override suspend fun fetchLyrics(title: String, artist: String, album: String?): LyricsResult? = withContext(Dispatchers.IO) {
        try {
            val query = "$title $artist".trim()
            val queryEncoded = URLEncoder.encode(query, "UTF-8")
            val searchUrl = URL("${BASE_URL}netease/search?q=$queryEncoded")

            Log.d(TAG, "Search Requesting: $searchUrl")
            val searchConnection = searchUrl.openConnection() as HttpURLConnection
            searchConnection.requestMethod = "GET"
            searchConnection.setRequestProperty("User-Agent", "Gratia Music Player (https://github.com/theonlyhussain/gratia)")
            searchConnection.connectTimeout = 8000
            searchConnection.readTimeout = 8000

            if (searchConnection.responseCode == 200) {
                val searchResponse = searchConnection.inputStream.bufferedReader().use { it.readText() }
                val jsonArray = JSONArray(searchResponse)
                
                if (jsonArray.length() > 0) {
                    val firstMatch = jsonArray.getJSONObject(0)
                    val trackId = if (firstMatch.has("id") && !firstMatch.isNull("id")) {
                        firstMatch.getString("id")
                    } else if (firstMatch.has("trackId") && !firstMatch.isNull("trackId")) {
                        firstMatch.getString("trackId")
                    } else {
                        null
                    }                    
                    if (trackId != null) {
                        return@withContext fetchLyricsForId(trackId)
                    }
                }
            } else {
                Log.d(TAG, "Search API failed: ${searchConnection.responseCode}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching from Lyrically API", e)
        }
        return@withContext null
    }

    private suspend fun fetchLyricsForId(id: String): LyricsResult? = withContext(Dispatchers.IO) {
        try {
            val urlString = "${BASE_URL}netease/lyrics?id=$id&word=true"
            Log.d(TAG, "Lyrics Requesting: $urlString")
            val connection = URL(urlString).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "Gratia Music Player (https://github.com/theonlyhussain/gratia)")
            connection.connectTimeout = 8000
            connection.readTimeout = 8000

            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                
                val type = json.optString("type")
                val isWordLevel = type.equals("Syllable", ignoreCase = true)
                val hasContent = json.has("content")
                
                // Only return if it has word-level content or ttmlContent we can use, else let LRCLIB handle line-level
                if (isWordLevel && hasContent) {
                    // We return the raw JSON response as the text, which our parser will consume
                    return@withContext LyricsResult(
                        text = response,
                        isSynced = true,
                        providerName = name,
                        isWordLevel = true
                    )
                }
            } else {
                Log.d(TAG, "Lyrics API failed: ${connection.responseCode}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching lyrics for id $id", e)
        }
        return@withContext null
    }
}
