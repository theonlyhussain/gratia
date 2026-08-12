package com.gratia.music.data.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object WikipediaFetcher {
    private const val TAG = "WikipediaFetcher"
    private var prefs: android.content.SharedPreferences? = null

    fun init(context: android.content.Context) {
        prefs = context.getSharedPreferences("wiki_bios", android.content.Context.MODE_PRIVATE)
    }

    suspend fun getArtistBiography(artistName: String): String? = withContext(Dispatchers.IO) {
        if (artistName.isBlank() || artistName == "<unknown>") return@withContext null
        
        val cached = prefs?.getString(artistName, null)
        if (cached != null) {
            return@withContext if (cached == "NO_BIO") null else cached
        }

        // We will try standard name, if it fails or is disambiguation, we try with " (musician)"
        var bio = fetchSummary(artistName)
        
        if (bio == null || isDisambiguation(bio)) {
            bio = fetchSummary("$artistName (musician)")
        }
        
        if (bio == null || isDisambiguation(bio)) {
            bio = fetchSummary("$artistName (band)")
        }

        if (bio != null && !isDisambiguation(bio)) {
            prefs?.edit()?.putString(artistName, bio)?.apply()
            return@withContext bio
        } else {
            prefs?.edit()?.putString(artistName, "NO_BIO")?.apply()
            return@withContext null
        }
    }

    private fun isDisambiguation(text: String): Boolean {
        val lower = text.lowercase()
        return lower.contains("may refer to:") || lower.contains("disambiguation")
    }

    private suspend fun fetchSummary(query: String): String? {
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val urlString = "https://en.wikipedia.org/w/api.php?action=query&prop=extracts&exintro=true&explaintext=true&titles=$encodedQuery&format=json&redirects=1"
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "Gratia Music Player (https://github.com/theonlyhussain/gratia)")
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(responseText)
                val queryObj = json.optJSONObject("query")
                val pages = queryObj?.optJSONObject("pages") ?: return null
                
                val firstKey = pages.keys().next()
                if (firstKey == "-1") return null // Page missing
                
                val page = pages.getJSONObject(firstKey)
                val extract = page.optString("extract", "").trim()
                
                if (extract.isNotEmpty()) {
                    return extract
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch biography from Wikipedia: ${e.message}")
        }
        return null
    }
}
