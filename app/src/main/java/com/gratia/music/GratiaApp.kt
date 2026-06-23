package com.gratia.music

import android.app.Application
import com.gratia.music.data.db.GratiaDatabase
import com.gratia.music.player.PlayerManager

class GratiaApp : Application() {

    lateinit var database: GratiaDatabase
        private set

    lateinit var playerManager: PlayerManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        database = GratiaDatabase.getInstance(this)
        playerManager = PlayerManager(this)
    }

    companion object {
        lateinit var instance: GratiaApp
            private set
    }
}
