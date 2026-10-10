package com.gratia.music

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.gratia.music.audio.EqualizerManager
import com.gratia.music.audio.EqualizerRepository
import com.gratia.music.data.db.GratiaDatabase
import com.gratia.music.player.PlayerManager
import com.gratia.music.ui.components.ArtworkSizeMapper
import kotlinx.coroutines.launch

class GratiaApp : Application(), ImageLoaderFactory {

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

    lateinit var providerManager: com.gratia.music.provider.ProviderManager
        private set

    lateinit var downloadManager: com.gratia.music.download.DownloadManager
        private set

    lateinit var downloadStateHolder: com.gratia.music.download.DownloadStateHolder
        private set

    lateinit var playbackHistoryRepository: com.gratia.music.data.repository.PlaybackHistoryRepository
        private set

    /**
     * Whether a MediaStore scan has already run in this process. Home used to
     * re-scan on every visit; the walk is gated on this now. Not persisted —
     * a fresh process should scan once.
     */
    @Volatile
    var hasScannedLocalMusicThisProcess: Boolean = false

    /** Process-lifetime scope for one-shot application-level work. */
    private val appScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO
    )

    override fun onCreate() {
        super.onCreate()
        instance = this
        database = GratiaDatabase.getInstance(this)
        preloadManager = com.gratia.music.player.PreloadManager(this)
        com.gratia.music.data.network.ArtistImageFetcher.init(this)
        // Before any screen can ask: the remote catalogue's offline handling
        // reads this on first composition.
        com.gratia.music.data.network.NetworkMonitor.initialize(this)
        
        providerManager = com.gratia.music.provider.ProviderManager(this)
        val settingsDataStore = com.gratia.music.data.SettingsDataStore(this)

        updateManager = com.gratia.music.updater.UpdateManager(this)
        
        downloadStateHolder = com.gratia.music.download.DownloadStateHolder()
        downloadManager = com.gratia.music.download.DownloadManager(this, downloadStateHolder)

        playbackHistoryRepository = com.gratia.music.data.repository.PlaybackHistoryRepository(
            database.playbackHistoryDao()
        )
        
        playerManager = PlayerManager(this)
        
        val lyricsRepo = com.gratia.music.data.repository.LyricsRepository(database.lyricsDao())
        lyricsManager = com.gratia.music.lyrics.LyricsManager(playerManager, lyricsRepo)
        
        recommendationManager = com.gratia.music.data.repository.RecommendationManager(
            database.songDao(),
            database.listeningEventDao(),
            database.playbackHistoryDao()
        )

        val eqRepo = EqualizerRepository(this)
        equalizerManager = EqualizerManager(eqRepo)
        
        sleepTimerManager = com.gratia.music.player.SleepTimerManager(playerManager)

        // Updates are manual now. This app used to enqueue a periodic update
        // check on every cold start, which is why simply opening Gratia could
        // start a background update flow nobody asked for. The schedule is
        // retired here rather than only stopped from being created: a device
        // that installed an earlier build still has the periodic work on disk,
        // and WorkManager would happily keep running it after the upgrade.
        com.gratia.music.updater.UpdateCheckWorker.cancelLegacySchedule(this)

        // Obsolete preferences (the Smart Update toggle and its onboarding flag)
        // are dropped once, so a stale "enabled" value from an older install can
        // never resurrect a feature that no longer exists.
        appScope.launch {
            settingsDataStore.clearObsoleteUpdatePreferences()
        }
    }

    /**
     * Every image in the app goes through this loader, and it exists for one
     * rule: a cover URL is asked for at the size it is actually drawn at.
     * [ArtworkSizeMapper] rewrites the provider's thumbnail size token to the
     * size Coil resolved for the request, so a surface that forgot to size its
     * own request still gets a sharp rendition instead of a stretched
     * thumbnail.
     */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components { add(ArtworkSizeMapper(), String::class.java) }
            .build()

    companion object {
        lateinit var instance: GratiaApp
            private set
    }
}

