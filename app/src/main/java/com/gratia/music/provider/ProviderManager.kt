package com.gratia.music.provider

import android.content.Context
import android.util.Log
import com.gratia.music.data.model.SongEntity
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

class ProviderManager(
    private val context: Context
) {
    companion object {
        private const val TAG = "ProviderManager"

        /**
         * How long a Home page stays usable. Long enough that switching tabs
         * does not re-ask the endpoint, short enough that "New releases" is
         * not last week's list.
         */
        private const val HOME_CACHE_TTL_MS = 15 * 60 * 1000L
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
     *
     * @param reason why a fresh resolution is being forced — "403" for a stream
     *   refused mid-play, "manual" otherwise. A 403 also drops the resolver's own
     *   short-lived URL cache for the track: a refresh that replays the dead URL
     *   isn't a refresh.
     */
    suspend fun resolvePlayback(song: SongEntity, forceFresh: Boolean = false, reason: String = "manual"): PlaybackSource? {
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
        if (forceFresh) {
            Log.d(TAG, "STREAM_REFRESH reason=$reason videoId=$videoId")
            if (reason == "403") {
                com.gratia.music.provider.ytmusic.innertube.StreamResolver.forget(videoId)
            }
            streamCache.remove(videoId)
        } else {
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

    // ---- Home catalog cache --------------------------------------------------

    /**
     * Home's shelves come from an unofficial endpoint that is happy to answer
     * slowly and unhappy about being asked often, so the last good page is held
     * here and served while a refresh happens behind it. Home paints instantly
     * on the second visit instead of opening on a spinner every time.
     */
    private data class CachedHome(val sections: List<RemoteHomeSection>, val at: Long)

    @Volatile
    private var homeCache: CachedHome? = null

    private val homeLock = Mutex()

    /** The last good Home feed, if any — for painting before a refresh lands. */
    fun cachedHome(): List<RemoteHomeSection>? = homeCache?.sections

    /**
     * Home shelves: from memory when fresh enough, otherwise from the network.
     *
     * A refresh that fails on its way to an empty answer deliberately does not
     * clear [homeCache] — a flaky endpoint is not a reason to blank a screen
     * that was working. Only a non-empty fresh page replaces the cached one.
     */
    suspend fun getHomeCached(limit: Int = 8, forceRefresh: Boolean = false): List<RemoteHomeSection> {
        val cached = homeCache
        if (!forceRefresh && cached != null && isFresh(cached.at)) return cached.sections

        return homeLock.withLock {
            val current = homeCache
            if (!forceRefresh && current != null && isFresh(current.at)) {
                return@withLock current.sections
            }

            val fresh = try {
                youtubeMusicProvider.getHome(limit)
            } catch (e: Exception) {
                Log.w(TAG, "getHome failed: ${e.message}")
                emptyList()
            }

            if (fresh.isNotEmpty()) {
                homeCache = CachedHome(fresh, System.currentTimeMillis())
                fresh
            } else {
                // Stale beats blank: keep serving the last good page.
                current?.sections ?: emptyList()
            }
        }
    }

    private fun isFresh(resolvedAtMs: Long): Boolean =
        System.currentTimeMillis() - resolvedAtMs < HOME_CACHE_TTL_MS

    // ---- Explore categories cache -------------------------------------------

    private data class CachedCategories(val items: List<BrowseCategory>, val at: Long)

    @Volatile
    private var categoriesCache: CachedCategories? = null

    private val categoriesLock = Mutex()

    /** The last good Explore taxonomy, if any — for painting before a refresh lands. */
    fun cachedBrowseCategories(): List<BrowseCategory>? = categoriesCache?.items

    /**
     * Explore categories: from memory when fresh, otherwise from the network.
     * The taxonomy changes slowly, and the same stale-beats-blank rule as
     * [getHomeCached] applies — a failed refresh must not empty a screen that
     * was working.
     */
    suspend fun getBrowseCategoriesCached(forceRefresh: Boolean = false): List<BrowseCategory> {
        val cached = categoriesCache
        if (!forceRefresh && cached != null && isFresh(cached.at)) return cached.items

        return categoriesLock.withLock {
            val current = categoriesCache
            if (!forceRefresh && current != null && isFresh(current.at)) {
                return@withLock current.items
            }

            val fresh = try {
                youtubeMusicProvider.getBrowseCategories()
            } catch (e: Exception) {
                Log.w(TAG, "getBrowseCategories failed: ${e.message}")
                emptyList()
            }

            if (fresh.isNotEmpty()) {
                categoriesCache = CachedCategories(fresh, System.currentTimeMillis())
                fresh
            } else {
                current?.items ?: emptyList()
            }
        }
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
