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

    lateinit var updateManager: com.gratia.music.updater.UpdateManager
        private set

    lateinit var recommendationManager: com.gratia.music.data.repository.RecommendationManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        database = GratiaDatabase.getInstance(this)
        preloadManager = com.gratia.music.player.PreloadManager(this)
        com.gratia.music.data.network.ArtistImageFetcher.init(this)
        
        updateManager = com.gratia.music.updater.UpdateManager(this)
        
        playerManager = PlayerManager(this)
        
        val lyricsRepo = com.gratia.music.data.repository.LyricsRepository(database.lyricsDao())
        lyricsManager = com.gratia.music.lyrics.LyricsManager(playerManager, lyricsRepo)
        
        recommendationManager = com.gratia.music.data.repository.RecommendationManager(database.songDao(), database.listeningEventDao())

        val eqRepo = EqualizerRepository(this)
        equalizerManager = EqualizerManager(eqRepo)
        
        sleepTimerManager = com.gratia.music.player.SleepTimerManager(playerManager)
        
        scheduleUpdateCheck()
    }

    private fun scheduleUpdateCheck() {
        val workRequest = androidx.work.PeriodicWorkRequestBuilder<com.gratia.music.updater.UpdateCheckWorker>(
            24, java.util.concurrent.TimeUnit.HOURS
        ).setConstraints(
            androidx.work.Constraints.Builder()
                .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                .build()
        ).build()
        
        androidx.work.WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            com.gratia.music.updater.UpdateCheckWorker.WORK_NAME,
            androidx.work.ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }

    companion object {
        lateinit var instance: GratiaApp
            private set
    }
}

