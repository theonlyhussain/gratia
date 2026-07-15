package com.gratia.music

import android.app.Application
import com.gratia.music.data.db.GratiaDatabase
import com.gratia.music.player.PlayerManager

class GratiaApp : Application() {

    lateinit var database: GratiaDatabase
        private set

    lateinit var playerManager: PlayerManager
        private set

    lateinit var lyricsManager: com.gratia.music.lyrics.LyricsManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        database = GratiaDatabase.getInstance(this)
        playerManager = PlayerManager(this)
        
        val lyricsRepo = com.gratia.music.data.repository.LyricsRepository(database.lyricsDao())
        lyricsManager = com.gratia.music.lyrics.LyricsManager(playerManager, lyricsRepo)
    }

    companion object {
        lateinit var instance: GratiaApp
            private set
    }
}
