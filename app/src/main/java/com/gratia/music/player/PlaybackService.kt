package com.gratia.music.player

import android.content.Intent
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.ForwardingPlayer
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.gratia.music.GratiaApp
import com.gratia.music.player.transition.GratiaPlayerEngine
import com.gratia.music.player.transition.TransitionController
import androidx.core.app.ServiceCompat

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
    private var playerEngine: GratiaPlayerEngine? = null
    private var transitionController: TransitionController? = null

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "PlaybackService.onCreate()")
        
        val engine = GratiaPlayerEngine(this).apply {
            initialize()
        }
        playerEngine = engine
        
        val masterPlayer = engine.masterPlayer

        // Attach the equalizer to ExoPlayer's audio session
        val audioSessionId = engine.getAudioSessionId()
        if (audioSessionId != C.AUDIO_SESSION_ID_UNSET && audioSessionId != 0) {
            Log.d(TAG, "PlaybackService: attaching EQ to session $audioSessionId")
            GratiaApp.instance.equalizerManager.attachToSession(audioSessionId)
        }

        // Listen for audio session changes (e.g. after codec re-init)
        masterPlayer.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                Log.d(TAG, "PlaybackService: audio session changed to $audioSessionId")
                if (audioSessionId != C.AUDIO_SESSION_ID_UNSET && audioSessionId != 0) {
                    GratiaApp.instance.equalizerManager.attachToSession(audioSessionId)
                }
            }
        })
            
        val wrappedMasterPlayer = wrapPlayer(masterPlayer)
        mediaSession = MediaSession.Builder(this, wrappedMasterPlayer)
            .build()
            
        engine.addPlayerSwapListener { newPlayer ->
            Log.d(TAG, "Player swapped during transition. Updating MediaSession.")
            val wrappedNewPlayer = wrapPlayer(newPlayer)
            mediaSession?.player = wrappedNewPlayer
            
            // Need to re-listen for session ID changes on the new player
            newPlayer.addListener(object : Player.Listener {
                override fun onAudioSessionIdChanged(audioSessionId: Int) {
                    Log.d(TAG, "PlaybackService: audio session changed to $audioSessionId (after swap)")
                    if (audioSessionId != C.AUDIO_SESSION_ID_UNSET && audioSessionId != 0) {
                        GratiaApp.instance.equalizerManager.attachToSession(audioSessionId)
                    }
                }
            })
            
            // Also update EQ immediately just in case
            val newSessionId = engine.getAudioSessionId()
            if (newSessionId != C.AUDIO_SESSION_ID_UNSET && newSessionId != 0) {
                GratiaApp.instance.equalizerManager.attachToSession(newSessionId)
            }
        }
        
        transitionController = TransitionController(engine)
        transitionController?.initialize()
        
        Log.d(TAG, "PlaybackService: GratiaPlayerEngine + MediaSession created")
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }
    
    private fun wrapPlayer(player: Player): Player {
        return object : ForwardingPlayer(player) {
            override fun getAvailableCommands(): Player.Commands {
                return super.getAvailableCommands().buildUpon()
                    .add(Player.COMMAND_SEEK_TO_NEXT)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                    .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .build()
            }
            
            override fun hasNextMediaItem(): Boolean = true
            override fun hasPreviousMediaItem(): Boolean = true
            
            override fun seekToNextMediaItem() {
                Log.d(TAG, "seekToNextMediaItem intercepted from MediaSession")
                GratiaApp.instance.playerManager.nextSong()
            }
            
            override fun seekToPreviousMediaItem() {
                Log.d(TAG, "seekToPreviousMediaItem intercepted from MediaSession")
                GratiaApp.instance.playerManager.prevSong()
            }

            override fun seekToNext() {
                Log.d(TAG, "seekToNext intercepted from MediaSession")
                GratiaApp.instance.playerManager.nextSong()
            }

            override fun seekToPrevious() {
                Log.d(TAG, "seekToPrevious intercepted from MediaSession")
                GratiaApp.instance.playerManager.prevSong()
            }
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "PlaybackService.onDestroy()")
        mediaSession?.run {
            player.release()
            release()
        }
        transitionController?.release()
        playerEngine?.release()
        mediaSession = null
        transitionController = null
        playerEngine = null
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0 ||
            player.playbackState == Player.STATE_ENDED) {
            Log.d(TAG, "PlaybackService.onTaskRemoved(): stopping (player idle/ended)")
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        } else {
            Log.d(TAG, "PlaybackService.onTaskRemoved(): keeping alive (still playing)")
        }
    }
}

