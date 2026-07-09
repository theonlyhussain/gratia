package com.gratia.music.player

import android.content.Intent
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * Foreground service for background audio playback.
 * Provides media notification controls.
 * 
 * Lifecycle: Created by the system when a MediaController connects.
 * Destroyed when stopSelf() is called or the system kills it.
 * PlayerManager handles reconnection if this service restarts.
 */
class PlaybackService : MediaSessionService() {

    companion object {
        private const val TAG = "GratiaPlayer"
    }

    private var mediaSession: MediaSession? = null
    private var exoPlayer: ExoPlayer? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "PlaybackService.onCreate()")
        
        exoPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
            
        mediaSession = MediaSession.Builder(this, exoPlayer!!)
            .build()
        
        Log.d(TAG, "PlaybackService: ExoPlayer + MediaSession created")
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        Log.d(TAG, "PlaybackService.onDestroy()")
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        exoPlayer = null
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0 ||
            player.playbackState == Player.STATE_ENDED) {
            Log.d(TAG, "PlaybackService.onTaskRemoved(): stopping (player idle/ended)")
            stopSelf()
        } else {
            Log.d(TAG, "PlaybackService.onTaskRemoved(): keeping alive (still playing)")
        }
    }
}
