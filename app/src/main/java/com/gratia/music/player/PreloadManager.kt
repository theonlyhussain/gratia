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

@OptIn(UnstableApi::class)
class PreloadManager(private val context: Context) {

    companion object {
        private const val TAG = "GratiaPreload"
        private const val PRELOAD_LIMIT = 3
    }

    private var preloadManager: DefaultPreloadManager? = null
    private val targetPreloadStatusControl = PlaylistTargetPreloadStatusControl()
    
    // The factory that ExoPlayer MUST use to benefit from preloading
    val mediaSourceFactory: MediaSource.Factory

    init {
        val dataSourceFactory = DefaultDataSource.Factory(context)
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
        val mediaItems = window.map { song ->
            song.toMediaItem()
        }

        mediaItems.forEachIndexed { index, mediaItem ->
            val actualIndex = startIndex + index
            manager.add(mediaItem, actualIndex)
        }

        targetPreloadStatusControl.currentPlayingIndex = currentIndex
        manager.setCurrentPlayingIndex(currentIndex)
        
        manager.invalidate()
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
