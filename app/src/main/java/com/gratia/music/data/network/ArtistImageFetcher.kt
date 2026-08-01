package com.gratia.music.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object ArtistImageFetcher {
    /**
     * Fetches a high-quality artist picture URL using the Deezer Search API.
     * Deezer requires no API key for basic search.
     */
    private var prefs: android.content.SharedPreferences? = null

    fun init(context: android.content.Context) {
        prefs = context.getSharedPreferences("artist_images", android.content.Context.MODE_PRIVATE)
    }

    suspend fun getArtistPictureUrl(artistName: String): String? = withContext(Dispatchers.IO) {
        if (artistName.isBlank() || artistName == "<unknown>") return@withContext null
        
        val cached = prefs?.getString(artistName, null)
        if (cached != null) {
            return@withContext if (cached == "NO_IMAGE") null else cached
        }
        
        try {
            val encodedName = URLEncoder.encode(artistName, "UTF-8")
            val url = URL("https://api.deezer.com/search/artist?q=$encodedName")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val jsonObject = JSONObject(response)
                val dataArray = jsonObject.optJSONArray("data")
                
                if (dataArray != null && dataArray.length() > 0) {
                    val firstResult = dataArray.getJSONObject(0)
                    // Try to get the highest quality picture, fallback to medium
                    val fetchedUrl = firstResult.optString("picture_xl", null) 
                        ?: firstResult.optString("picture_medium", null)
                    
                    if (fetchedUrl != null) {
                        prefs?.edit()?.putString(artistName, fetchedUrl)?.apply()
                    } else {
                        prefs?.edit()?.putString(artistName, "NO_IMAGE")?.apply()
                    }
                    return@withContext fetchedUrl
                } else {
                    prefs?.edit()?.putString(artistName, "NO_IMAGE")?.apply()
                }
            } else {
                prefs?.edit()?.putString(artistName, "NO_IMAGE")?.apply()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // Don't cache network errors as permanent failures
        }
        return@withContext null
    }
}
