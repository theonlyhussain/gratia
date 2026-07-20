package com.gratia.music

import android.app.Application
import com.gratia.music.audio.EqualizerManager
import com.gratia.music.audio.EqualizerRepository
import com.gratia.music.data.db.GratiaDatabase
import com.gratia.music.player.PlayerManager

class GratiaApp : Application() {

    lateinit var database: GratiaDatabase
        private set

    lateinit var playerManager: PlayerManager
        private set

    lateinit var lyricsManager: com.gratia.music.lyrics.LyricsManager
        private set

    lateinit var equalizerManager: EqualizerManager
        private set

    lateinit var sleepTimerManager: com.gratia.music.player.SleepTimerManager
        private set

    lateinit var preloadManager: com.gratia.music.player.PreloadManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        database = GratiaDatabase.getInstance(this)
        preloadManager = com.gratia.music.player.PreloadManager(this)
        
        playerManager = PlayerManager(this)
        
        val lyricsRepo = com.gratia.music.data.repository.LyricsRepository(database.lyricsDao())
        lyricsManager = com.gratia.music.lyrics.LyricsManager(playerManager, lyricsRepo)

        val eqRepo = EqualizerRepository(this)
        equalizerManager = EqualizerManager(eqRepo)
        
        sleepTimerManager = com.gratia.music.player.SleepTimerManager(playerManager)
    }

    companion object {
        lateinit var instance: GratiaApp
            private set
    }
}

