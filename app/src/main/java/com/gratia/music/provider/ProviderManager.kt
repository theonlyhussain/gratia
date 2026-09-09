package com.gratia.music.provider

import android.content.Context
import android.util.Log
import com.gratia.music.data.model.SongEntity
import java.util.concurrent.ConcurrentHashMap

class ProviderManager(
    private val context: Context
) {
    companion object {
        private const val TAG = "ProviderManager"
    }

    val youtubeMusicProvider = YouTubeMusicProvider()

    private val providers = mutableMapOf<MusicProviderType, MusicProvider>(
        MusicProviderType.YOUTUBE_MUSIC to youtubeMusicProvider
    )

    // In-memory short-term cache for resolved streaming URLs to avoid duplicate network calls
    // while the stream is active. Key: videoId, Value: CachedSource(source, resolvedAtMs)
    private data class CachedSource(val source: PlaybackSource, val resolvedAtMs: Long)
    private val streamCache = ConcurrentHashMap<String, CachedSource>()

    fun getProvider(type: MusicProviderType): MusicProvider? {
        return providers[type]
    }

    fun registerProvider(type: MusicProviderType, provider: MusicProvider) {
        providers[type] = provider
    }

    /**
     * Resolves playback source for a remote song.
     * Uses in-memory cache if the stream URL was resolved recently and hasn't expired.
     * If forceFresh is true, ignores cache and requests a brand new stream URL.
     */
    suspend fun resolvePlayback(song: SongEntity, forceFresh: Boolean = false): PlaybackSource? {
        val providerType = MusicProviderType.fromId(song.storageProvider)
        if (providerType == MusicProviderType.LOCAL) {
            return null
        }

        val videoId = song.providerTrackId ?: song.id.removePrefix("ytm_")
        if (videoId.isBlank()) {
            Log.e(TAG, "Cannot resolve playback: missing providerTrackId for '${song.title}'")
            return null
        }

        val now = System.currentTimeMillis()
        if (!forceFresh) {
            val cached = streamCache[videoId]
            if (cached != null) {
                // Check if expiresAtMs is known and still valid (with 5-minute safety margin)
                val expiresAt = cached.source.expiresAtMs
                val isExpired = if (expiresAt != null) {
                    now >= (expiresAt - 5 * 60 * 1000)
                } else {
                    // Default fallback: cache for max 4 hours
                    (now - cached.resolvedAtMs) > (4 * 3600 * 1000)
                }

                if (!isExpired) {
                    Log.d(TAG, "Using cached streaming URL for $videoId")
                    return cached.source
                } else {
                    Log.d(TAG, "Cached streaming URL expired for $videoId, re-resolving...")
                    streamCache.remove(videoId)
                }
            }
        } else {
            streamCache.remove(videoId)
        }

        val provider = getProvider(providerType) ?: run {
            Log.e(TAG, "No provider registered for $providerType")
            return null
        }

        val freshSource = provider.resolvePlayback(videoId)
        if (freshSource != null) {
            streamCache[videoId] = CachedSource(freshSource, now)
        }
        return freshSource
    }

    fun invalidateStreamCache(videoId: String) {
        streamCache.remove(videoId)
    }

    suspend fun resolveForDownload(song: SongEntity, maxKbps: Int): PlaybackSource? {
        val providerType = MusicProviderType.fromId(song.storageProvider)
        if (providerType == MusicProviderType.LOCAL) {
            return null
        }
        val videoId = song.providerTrackId ?: song.id.removePrefix("ytm_")
        if (videoId.isBlank()) return null
        
        return if (providerType == MusicProviderType.YOUTUBE_MUSIC) {
            try {
                val stream = com.gratia.music.provider.ytmusic.innertube.StreamResolver.resolveForDownload(videoId, maxKbps)
                PlaybackSource(videoId, stream.url)
            } catch (e: Exception) {
                Log.e(TAG, "resolveForDownload failed", e)
                null
            }
        } else {
            // Other providers might not support specific quality downloads yet, fallback to standard resolve
            getProvider(providerType)?.resolvePlayback(videoId)
        }
    }

    fun clearCache() {
        streamCache.clear()
    }
}
