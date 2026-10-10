package com.gratia.music.data.cache

import android.content.Context
import android.util.Log
import com.gratia.music.GratiaApp
import com.gratia.music.provider.MusicProviderType
import com.gratia.music.provider.RemoteAlbumSummary
import com.gratia.music.provider.RemoteArtist
import com.gratia.music.provider.RemoteArtistRef
import com.gratia.music.provider.RemoteTrack
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * The artist detail page, cached on disk.
 *
 * Opening an artist the listener has visited before must not start from an
 * empty screen: the catalogue's answer from the last visit is kept as JSON in
 * the app cache directory, rendered immediately, and refreshed over the
 * network behind it. Cache is deliberately *cache* — cacheDir, never
 * filesDir — so the system may reclaim it under pressure and a cleared cache
 * simply costs one cold load again.
 *
 * The DTO mirrors [RemoteArtist] rather than making the remote models
 * serializable: the wire models are the provider's vocabulary, and a
 * persistence concern should not reach into them.
 */
class ArtistCache(context: Context = GratiaApp.instance) {

    companion object {
        private const val TAG = "ArtistCache"
        private const val DIR = "artist_pages"
    }

    private val dir: File = File(context.cacheDir, DIR)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** The cached page for [channelId], or null when never visited (or unreadable). */
    fun get(channelId: String): RemoteArtist? {
        val file = fileFor(channelId)
        if (!file.exists()) return null
        return try {
            val dto = json.decodeFromString<ArtistCacheDto>(file.readText())
            dto.toModel().also {
                Log.d(TAG, "ARTIST_CACHE_HIT id=$channelId ageMs=${System.currentTimeMillis() - dto.cachedAt}")
            }
        } catch (e: Exception) {
            // A corrupt or stale-schema entry is the same as no entry: drop it
            // rather than surfacing a parse error the listener can do nothing about.
            Log.w(TAG, "ARTIST_CACHE_READ_FAILED id=$channelId ${e.message}")
            file.delete()
            null
        }
    }

    /** Stores [artist] as the cached page for its id. Failures are logged, never thrown. */
    fun put(artist: RemoteArtist) {
        try {
            dir.mkdirs()
            fileFor(artist.id).writeText(json.encodeToString(ArtistCacheDto.serializer(), artist.toDto()))
            Log.d(TAG, "ARTIST_CACHE_WRITE id=${artist.id} songs=${artist.topSongs.size} albums=${artist.albums.size}")
        } catch (e: Exception) {
            Log.w(TAG, "ARTIST_CACHE_WRITE_FAILED id=${artist.id} ${e.message}")
        }
    }

    private fun fileFor(channelId: String): File {
        // Channel ids are alphanumeric; still, whatever reaches the file system
        // is sanitized so a hostile id cannot walk out of the directory.
        val safe = channelId.replace(Regex("[^A-Za-z0-9_-]"), "_")
        return File(dir, "$safe.json")
    }
}

// ── Persistence shapes ───────────────────────────────────────────────────────

@Serializable
private data class ArtistCacheDto(
    val id: String,
    val name: String,
    val description: String? = null,
    val subscribers: String? = null,
    val monthlyListeners: String? = null,
    val artworkUrl: String? = null,
    val songsPlaylistId: String? = null,
    val topSongs: List<TrackDto> = emptyList(),
    val albums: List<ShelfDto> = emptyList(),
    val singles: List<ShelfDto> = emptyList(),
    val relatedArtists: List<RefDto> = emptyList(),
    val cachedAt: Long = 0,
)

@Serializable
private data class TrackDto(
    val id: String,
    val title: String,
    val artistName: String,
    val artistId: String? = null,
    val albumId: String? = null,
    val albumName: String? = null,
    val durationMs: Long? = null,
    val durationText: String? = null,
    val artworkUrl: String? = null,
    val videoId: String,
    val isExplicit: Boolean = false,
)

@Serializable
private data class ShelfDto(
    val id: String,
    val title: String,
    val artistName: String? = null,
    val year: String? = null,
    val artworkUrl: String? = null,
    val type: String? = null,
)

@Serializable
private data class RefDto(
    val id: String? = null,
    val name: String,
    val artworkUrl: String? = null,
)

// ── Mapping ──────────────────────────────────────────────────────────────────

private fun RemoteArtist.toDto() = ArtistCacheDto(
    id = id,
    name = name,
    description = description,
    subscribers = subscribers,
    monthlyListeners = monthlyListeners,
    artworkUrl = artworkUrl,
    songsPlaylistId = songsPlaylistId,
    cachedAt = System.currentTimeMillis(),
    topSongs = topSongs.map { it.toDto() },
    albums = albums.map { it.toDto() },
    singles = singles.map { it.toDto() },
    relatedArtists = relatedArtists.map { RefDto(id = it.id, name = it.name, artworkUrl = it.artworkUrl) },
)

private fun RemoteTrack.toDto() = TrackDto(
    id = id,
    title = title,
    artistName = artistDisplay,
    artistId = artists.firstOrNull()?.id,
    albumId = album?.id,
    albumName = album?.name,
    durationMs = durationMs,
    durationText = durationText,
    artworkUrl = artworkUrl,
    videoId = videoId,
    isExplicit = isExplicit,
)

private fun RemoteAlbumSummary.toDto() = ShelfDto(
    id = id,
    title = title,
    artistName = artists.firstOrNull()?.name,
    year = year,
    artworkUrl = artworkUrl,
    type = type,
)

private fun ArtistCacheDto.toModel() = RemoteArtist(
    id = id,
    name = name,
    description = description,
    subscribers = subscribers,
    monthlyListeners = monthlyListeners,
    artworkUrl = artworkUrl,
    songsPlaylistId = songsPlaylistId,
    topSongs = topSongs.map { it.toModel() },
    albums = albums.map { it.toModel() },
    singles = singles.map { it.toModel() },
    relatedArtists = relatedArtists.map { RemoteArtistRef(id = it.id, name = it.name, artworkUrl = it.artworkUrl) },
)

private fun TrackDto.toModel() = RemoteTrack(
    id = id,
    provider = MusicProviderType.YOUTUBE_MUSIC,
    title = title,
    artists = listOf(RemoteArtistRef(id = artistId, name = artistName)),
    album = albumId?.let { RemoteAlbumRefDto(it, albumName ?: "") },
    durationMs = durationMs,
    durationText = durationText,
    artworkUrl = artworkUrl,
    videoId = videoId,
    isExplicit = isExplicit,
)

/** Local alias so the mapping file needn't import the shared model's name ambiguously. */
private typealias RemoteAlbumRefDto = com.gratia.music.provider.RemoteAlbumRef

private fun ShelfDto.toModel() = RemoteAlbumSummary(
    id = id,
    title = title,
    artists = artistName?.let { listOf(RemoteArtistRef(id = null, name = it)) } ?: emptyList(),
    year = year,
    artworkUrl = artworkUrl,
    type = type,
)
