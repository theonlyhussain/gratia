package com.gratia.music.data.scan

import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object CoverArtFetcher {
    private const val TAG = "CoverArtFetcher"

    /**
     * Searches Deezer API for the given artist and title.
     * Returns the URL of the cover art, or null if not found.
     */
    fun searchDeezerArt(artist: String, title: String): String? {
        try {
            val cleanTitle = cleanSearchTerm(title)
            val cleanArtist = cleanSearchTerm(artist)

            if (cleanTitle.isEmpty()) return null

            val query = if (cleanArtist.lowercase() == "unknown artist" || cleanArtist.isEmpty()) {
                "track:\"$cleanTitle\""
            } else {
                "artist:\"$cleanArtist\" track:\"$cleanTitle\""
            }

            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val urlString = "https://api.deezer.com/search?q=$encodedQuery&limit=1"
            
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(responseText)
                val dataArray = json.optJSONArray("data")
                if (dataArray != null && dataArray.length() > 0) {
                    val firstResult = dataArray.getJSONObject(0)
                    val album = firstResult.optJSONObject("album")
                    if (album != null) {
                        val coverXl = album.optString("cover_xl", "")
                        if (coverXl.isNotEmpty()) return coverXl
                        val coverBig = album.optString("cover_big", "")
                        if (coverBig.isNotEmpty()) return coverBig
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch from Deezer: ${e.message}")
        }
        return null
    }

    private fun cleanSearchTerm(term: String): String {
        return term.replace(Regex("\\(.*?\\)"), "")
            .replace(Regex("\\[.*?\\]"), "")
            .trim()
    }
}
