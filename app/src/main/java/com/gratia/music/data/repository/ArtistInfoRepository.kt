package com.gratia.music.data.repository

import android.util.Log
import com.gratia.music.data.network.WikipediaFetcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import com.gratia.music.GratiaApp
import com.gratia.music.data.SettingsDataStore

data class ArtistInfo(
    val name: String,
    val pictureUrl: String?,
    val fanCount: Int,
    val isVerified: Boolean,
    val biography: String?
)

data class ContributorInfo(
    val id: Long,
    val name: String,
    val pictureUrl: String?,
    val role: String
)

object ArtistInfoRepository {
    private const val TAG = "ArtistInfoRepository"

    suspend fun getArtistInfo(artistName: String): ArtistInfo? = withContext(Dispatchers.IO) {
        if (artistName.isBlank() || artistName == "<unknown>") return@withContext null

        val context = GratiaApp.instance.applicationContext
        val cacheDir = File(context.filesDir, "artist_info_cache")
        if (!cacheDir.exists()) cacheDir.mkdirs()
        
        val safeName = artistName.replace(Regex("[^a-zA-Z0-9.-]"), "_")
        val cacheFile = File(cacheDir, "$safeName.json")
        
        var cachedInfo: ArtistInfo? = null
        if (cacheFile.exists()) {
            try {
                val json = JSONObject(cacheFile.readText())
                cachedInfo = ArtistInfo(
                    name = json.optString("name", artistName),
                    pictureUrl = if (json.has("pictureUrl") && !json.isNull("pictureUrl")) json.getString("pictureUrl") else null,
                    fanCount = json.optInt("fanCount", 0),
                    isVerified = json.optBoolean("isVerified", false),
                    biography = if (json.has("biography") && !json.isNull("biography")) json.getString("biography") else null
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse cache: ${e.message}")
            }
        }

        val settings = SettingsDataStore(context)
        val onlineEnabled = settings.onlineDataEnabledFlow.first()
        
        if (!onlineEnabled) {
            return@withContext cachedInfo
        }

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

                    val info = ArtistInfo(
                        name = name,
                        pictureUrl = pictureXl,
                        fanCount = nbFan,
                        isVerified = isVerified,
                        biography = biography
                    )
                    
                    try {
                        val json = JSONObject().apply {
                            put("name", info.name)
                            put("pictureUrl", info.pictureUrl)
                            put("fanCount", info.fanCount)
                            put("isVerified", info.isVerified)
                            put("biography", info.biography)
                        }
                        cacheFile.writeText(json.toString())
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to save cache: ${e.message}")
                    }
                    return@withContext info
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch artist info: ${e.message}")
        }
        return@withContext cachedInfo
    }

    suspend fun getTrackContributors(title: String, artist: String): List<ContributorInfo> = withContext(Dispatchers.IO) {
        if (title.isBlank() || artist.isBlank()) return@withContext emptyList()
        val contributors = mutableListOf<ContributorInfo>()
        try {
            val cleanTitle = title.replace(Regex("(?i)\\s*\\(.*?\\)"), "").trim()
            val cleanArtist = artist.split(",")[0].split("&")[0].trim()
            
            val query = "artist:\"$cleanArtist\" track:\"$cleanTitle\""
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val searchUrl = "https://api.deezer.com/search?q=$encodedQuery&limit=1"
            
            val url = URL(searchUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "Mozilla/5.0")
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(responseText)
                val dataArray = json.optJSONArray("data")
                
                if (dataArray != null && dataArray.length() > 0) {
                    val trackId = dataArray.getJSONObject(0).optLong("id")
                    
                    val trackUrl = URL("https://api.deezer.com/track/$trackId")
                    val trackConnection = trackUrl.openConnection() as HttpURLConnection
                    trackConnection.requestMethod = "GET"
                    trackConnection.setRequestProperty("User-Agent", "Mozilla/5.0")
                    trackConnection.connectTimeout = 5000
                    trackConnection.readTimeout = 5000
                    
                    if (trackConnection.responseCode == HttpURLConnection.HTTP_OK) {
                        val trackResponseText = trackConnection.inputStream.bufferedReader().use { it.readText() }
                        val trackJson = JSONObject(trackResponseText)
                        val contributorsArray = trackJson.optJSONArray("contributors")
                        
                        if (contributorsArray != null) {
                            for (i in 0 until contributorsArray.length()) {
                                val cObj = contributorsArray.getJSONObject(i)
                                val id = cObj.optLong("id")
                                val name = cObj.optString("name", "")
                                val role = cObj.optString("role", "Unknown")
                                val pictureXl = cObj.optString("picture_xl", "").takeIf { it.isNotEmpty() }
                                    ?: cObj.optString("picture_medium", "").takeIf { it.isNotEmpty() }
                                
                                if (name.isNotBlank()) {
                                    contributors.add(ContributorInfo(id, name, pictureXl, role))
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch track contributors: ${e.message}")
        }
        return@withContext contributors
    }

    suspend fun updateLocalBiography(artistName: String, biography: String?) = withContext(Dispatchers.IO) {
        val context = GratiaApp.instance.applicationContext
        val cacheDir = File(context.filesDir, "artist_info_cache")
        if (!cacheDir.exists()) cacheDir.mkdirs()
        
        val safeName = artistName.replace(Regex("[^a-zA-Z0-9.-]"), "_")
        val cacheFile = File(cacheDir, "$safeName.json")
        
        val json = if (cacheFile.exists()) {
            try { JSONObject(cacheFile.readText()) } catch (e: Exception) { JSONObject() }
        } else JSONObject()
        
        json.put("name", artistName)
        if (biography == null) {
            json.put("biography", JSONObject.NULL)
        } else {
            json.put("biography", biography)
        }
        
        cacheFile.writeText(json.toString())
    }
}
