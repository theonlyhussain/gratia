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
    suspend fun getArtistPictureUrl(artistName: String): String? = withContext(Dispatchers.IO) {
        if (artistName.isBlank() || artistName == "<unknown>") return@withContext null
        
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
                    return@withContext firstResult.optString("picture_xl", null) 
                        ?: firstResult.optString("picture_medium", null)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext null
    }
}
