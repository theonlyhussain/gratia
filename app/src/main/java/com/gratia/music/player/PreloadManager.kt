package com.gratia.music.player

import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.preload.DefaultPreloadManager
import androidx.media3.exoplayer.source.preload.TargetPreloadStatusControl
import com.gratia.music.data.model.SongEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
class PreloadManager(private val context: Context) {

    companion object {
        private const val TAG = "GratiaPreload"
        private const val PRELOAD_LIMIT = 3
    }

    private var preloadManager: DefaultPreloadManager? = null
    private val targetPreloadStatusControl = PlaylistTargetPreloadStatusControl()
    private val resolverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** The remote track whose stream was last pre-resolved, so queue updates don't re-request it. */
    @Volatile
    private var lastPreResolvedVideoId: String? = null
    
    // The factory that ExoPlayer MUST use to benefit from preloading
    val mediaSourceFactory: MediaSource.Factory

    init {
        // The same chain the player itself reads through, so read-ahead is
        // ranged and correctly dressed too. A preload that fetched open-ended
        // would hold a throttled connection open against the track being
        // listened to, which is worse than not preloading at all.
        val dataSourceFactory = PlaybackDataSources.create(context)
        mediaSourceFactory = DefaultMediaSourceFactory(context).setDataSourceFactory(dataSourceFactory)

        try {
            targetPreloadStatusControl.preloadLimit = PRELOAD_LIMIT
            
            @Suppress("UNCHECKED_CAST")
            val builder = DefaultPreloadManager.Builder(
                context, 
                targetPreloadStatusControl
            )
            builder.setMediaSourceFactory(mediaSourceFactory)
            
            preloadManager = builder.build()
            Log.d(TAG, "Initialized DefaultPreloadManager with limit: $PRELOAD_LIMIT")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize DefaultPreloadManager: ${e.message}")
        }
    }

    /**
     * Updates the preload manager with the current queue and playing index.
     * This will automatically prioritize preloading the upcoming songs.
     */
    fun updateQueue(queue: List<SongEntity>, currentPlayingId: String?) {
        val manager = preloadManager ?: return
        
        val currentIndex = queue.indexOfFirst { it.id == currentPlayingId }.takeIf { it >= 0 } ?: 0
        
        // Only process a small window to prevent ANRs with massive queues (e.g. 5000+ songs)
        val startIndex = currentIndex
        val endIndex = minOf(queue.size, currentIndex + 15)
        val window = queue.subList(startIndex, endIndex)

        // Convert to MediaItems matching what PlayerManager uses
        // Skip songs without a resolved URI (e.g. pending remote tracks)
        window.forEachIndexed { index, song ->
            if (!song.localUri.isNullOrBlank()) {
                val mediaItem = song.toMediaItem()
                val actualIndex = startIndex + index
                manager.add(mediaItem, actualIndex)
            }
        }

        targetPreloadStatusControl.currentPlayingIndex = currentIndex
        manager.setCurrentPlayingIndex(currentIndex)
        
        manager.invalidate()

        preResolveNextRemoteTrack(queue, currentIndex)
    }

    /**
     * Pre-resolves the stream of the *next* queue item when it is a remote
     * track, so its client walk and URL probe are paid before the listener
     * reaches it. Deliberately one item — DefaultPreloadManager cannot hold a
     * MediaItem without a URI, and pre-resolving a whole queue would burn
     * googlevideo URLs that expire before they are played.
     */
    private fun preResolveNextRemoteTrack(queue: List<SongEntity>, currentIndex: Int) {
        val next = queue.getOrNull(currentIndex + 1) ?: return
        if (next.storageProvider == "local" || next.isDownloaded) return
        val videoId = next.providerTrackId ?: next.id.removePrefix("ytm_")
        if (videoId.isBlank() || videoId == lastPreResolvedVideoId) return
        lastPreResolvedVideoId = videoId
        resolverScope.launch {
            try {
                com.gratia.music.GratiaApp.instance.providerManager.resolvePlayback(next, forceFresh = false)
                Log.d(TAG, "Pre-resolved next remote track: $videoId")
            } catch (e: Exception) {
                Log.w(TAG, "Pre-resolve failed for $videoId: ${e.message}")
            }
        }
    }

    fun release() {
        preloadManager?.release()
        preloadManager = null
    }

    private class PlaylistTargetPreloadStatusControl : TargetPreloadStatusControl<Int> {
        var currentPlayingIndex: Int = C.INDEX_UNSET
        var preloadLimit: Int = 3

        override fun getTargetPreloadStatus(rankingData: Int): TargetPreloadStatusControl.PreloadStatus? {
            if (currentPlayingIndex == C.INDEX_UNSET) {
                return null
            }
            
            val distance = rankingData - currentPlayingIndex
            return when {
                // Preload the next few songs up to SOURCE_PREPARED (extractors initialized)
                distance in 1..preloadLimit -> {
                    DefaultPreloadManager.Status(DefaultPreloadManager.Status.STAGE_SOURCE_PREPARED)
                }
                else -> null
            }
        }
    }
}
