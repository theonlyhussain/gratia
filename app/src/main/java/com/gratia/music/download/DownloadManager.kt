package com.gratia.music.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Gateway for downloading remote music.
 *
 * Dedupe is structural rather than advisory: each recording's download runs as
 * a uniquely-named WorkManager chain — [workNameFor] — so enqueueing the same
 * track twice *joins* the running work instead of racing it. A chain that
 * already finished is only re-run when [DownloadWorker] finds no file behind
 * it (the user removed the download), so tapping Download again on a
 * downloaded track is a no-op, not a second copy.
 *
 * Identity is [SongEntity.downloadKey] — the remote recording id, never a
 * title/artist pair — which is what keeps two different recordings that share
 * a display name from colliding and a download from overwriting an unrelated
 * local file.
 */
class DownloadManager(
    private val context: Context,
    private val stateHolder: DownloadStateHolder,
) {

    /** The Android download-notification channel, created once on first use. */
    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Downloads",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Progress of music downloads"
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }
    }

    /** Whether the app may post a notification at all (API 33+ runtime grant). */
    fun canPostNotifications(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }

    /** Enqueues (or joins) the download for the track identified by [key]. */
    fun enqueueDownload(key: String) {
        ensureNotificationChannel()
        val settings = com.gratia.music.data.SettingsDataStore(context)
        // Wi-Fi-only is a user setting the download honours at enqueue time:
        // the constraint is the enforcement, not a suggestion (spec 37).
        kotlinx.coroutines.CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val wifiOnly = settings.wifiOnlyDownloadsFlow.first()
            val request = OneTimeWorkRequestBuilder<DownloadWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(
                            if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
                        )
                        .build()
                )
                .setInputData(Data.Builder().putString(DownloadWorker.KEY_DOWNLOAD_KEY, key).build())
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                workNameFor(key),
                ExistingWorkPolicy.KEEP,
                request,
            )
        }
    }

    /**
     * Removes a download: deletes the file and clears the persisted state.
     * The worker never runs for a removed download — the file check in
     * [DownloadWorker] re-enqueues nothing — so this is terminal until the
     * user asks again.
     *
     * Safe to call from the UI layer: file deletion and the Room update run
     * on the worker-free path in [DownloadWorker.scheduleRemoval], dispatched
     * on IO here so callers stay synchronous.
     */
    fun removeDownload(key: String, songId: String) {
        WorkManager.getInstance(context).cancelUniqueWork(workNameFor(key))
        stateHolder.clear(key)
        removalScope.launch { DownloadWorker.scheduleRemoval(context, songId) }
    }

    private val removalScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO
    )

    /** Live progress for [key], surfaced for progress rows. */
    fun progressFor(key: String): Flow<DownloadState> =
        stateHolder.states.map { it[key] ?: DownloadState.NotDownloaded }

    /** Posts the per-track download notification. Truthful or indeterminate (spec 44). */
    fun notifyProgress(key: String, title: String, artist: String, progress: Float?) {
        if (!canPostNotifications()) return
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(com.gratia.music.R.drawable.ic_stat_gratia)
            .setContentTitle(title)
            .setContentText(if (progress != null) "Downloading… ${(progress * 100).toInt()}%" else "Downloading…")
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
        if (progress != null) {
            builder.setProgress(100, (progress * 100).toInt(), false)
        } else {
            builder.setProgress(0, 0, true)
        }
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_BASE + key.hashCode(), builder.build())
    }

    fun notifyComplete(key: String, title: String, artist: String) {
        if (!canPostNotifications()) return
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(com.gratia.music.R.drawable.ic_stat_gratia)
            .setContentTitle(title)
            .setContentText("Downloaded")
            .setOngoing(false)
            .setAutoCancel(true)
        val id = NOTIFICATION_ID_BASE + key.hashCode()
        NotificationManagerCompat.from(context).notify(id, builder.build())
    }

    fun notifyFailed(key: String, title: String, artist: String, message: String) {
        if (!canPostNotifications()) return
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(com.gratia.music.R.drawable.ic_stat_gratia)
            .setContentTitle(title)
            .setContentText("Download failed — $message")
            .setOngoing(false)
            .setAutoCancel(true)
        val id = NOTIFICATION_ID_BASE + key.hashCode()
        NotificationManagerCompat.from(context).notify(id, builder.build())
    }

    fun dismissNotification(key: String) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_BASE + key.hashCode())
    }

    companion object {
        private const val CHANNEL_ID = "gratia_downloads"
        private const val NOTIFICATION_ID_BASE = 4700
        fun workNameFor(key: String) = "download_$key"
    }
}
