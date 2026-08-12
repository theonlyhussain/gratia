package com.gratia.music.data.repository

import android.util.Log
import com.gratia.music.data.network.WikipediaFetcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class ArtistInfo(
    val name: String,
    val pictureUrl: String?,
    val fanCount: Int,
    val isVerified: Boolean,
    val biography: String?
)

object ArtistInfoRepository {
    private const val TAG = "ArtistInfoRepository"

    suspend fun getArtistInfo(artistName: String): ArtistInfo? = withContext(Dispatchers.IO) {
        if (artistName.isBlank() || artistName == "<unknown>") return@withContext null

        try {
            val encodedQuery = URLEncoder.encode(artistName, "UTF-8")
            val urlString = "https://api.deezer.com/search/artist?q=$encodedQuery&limit=1"
            
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
                    
                    val name = firstResult.optString("name", artistName)
                    val pictureXl = firstResult.optString("picture_xl", "")
                        .takeIf { it.isNotEmpty() }
                        ?: firstResult.optString("picture_medium", "")
                            .takeIf { it.isNotEmpty() }
                    val nbFan = firstResult.optInt("nb_fan", 0)
                    
                    // We consider an artist "verified" if they have a non-trivial amount of fans on Deezer
                    val isVerified = nbFan > 1000 
                    
                    // Fetch biography from Wikipedia
                    val biography = WikipediaFetcher.getArtistBiography(name)

                    return@withContext ArtistInfo(
                        name = name,
                        pictureUrl = pictureXl,
                        fanCount = nbFan,
                        isVerified = isVerified,
                        biography = biography
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch artist info: ${e.message}")
        }
        return@withContext null
    }
}
