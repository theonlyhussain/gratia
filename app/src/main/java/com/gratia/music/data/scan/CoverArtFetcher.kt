package com.gratia.music.data.scan

import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Result from a Deezer track search containing cover art URL and genre.
 */
data class DeezerTrackResult(
    val coverUrl: String?,
    val genre: String?
)

object CoverArtFetcher {
    private const val TAG = "CoverArtFetcher"

    /**
     * Searches Deezer API for the given artist and title.
     * Returns a [DeezerTrackResult] with cover art URL and genre, or null if not found.
     */
    fun searchDeezer(artist: String, title: String): DeezerTrackResult? {
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

                    // Extract cover art
                    val album = firstResult.optJSONObject("album")
                    var coverUrl: String? = null
                    if (album != null) {
                        val coverXl = album.optString("cover_xl", "")
                        if (coverXl.isNotEmpty()) coverUrl = coverXl
                        else {
                            val coverBig = album.optString("cover_big", "")
                            if (coverBig.isNotEmpty()) coverUrl = coverBig
                        }
                    }

                    // Extract genre from album details
                    val genre = fetchGenreFromAlbum(album)

                    return DeezerTrackResult(coverUrl, genre)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch from Deezer: ${e.message}")
        }
        return null
    }

    /**
     * Legacy function for backward compatibility — returns just the cover URL.
     */
    fun searchDeezerArt(artist: String, title: String): String? {
        return searchDeezer(artist, title)?.coverUrl
    }

    /**
     * Fetch genre from album object. Tries the album's genre_id mapping.
     * Falls back to fetching the album details endpoint for genre info.
     */
    private fun fetchGenreFromAlbum(album: JSONObject?): String? {
        if (album == null) return null
        try {
            val albumId = album.optLong("id", 0L)
            if (albumId == 0L) return null

            // Fetch full album details which include genres
            val albumUrl = URL("https://api.deezer.com/album/$albumId")
            val conn = albumUrl.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 5000
            conn.readTimeout = 5000

            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                val albumJson = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                val genres = albumJson.optJSONObject("genres")
                val genreData = genres?.optJSONArray("data")
                if (genreData != null && genreData.length() > 0) {
                    return genreData.getJSONObject(0).optString("name", null)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch genre: ${e.message}")
        }
        return null
    }

    private fun cleanSearchTerm(term: String): String {
        return term.replace(Regex("\\(.*?\\)"), "")
            .replace(Regex("\\[.*?\\]"), "")
            .trim()
    }
}
