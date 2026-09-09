package com.gratia.music.download

import android.content.Context
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf

class DownloadManager(private val context: Context) {
    
    fun enqueueDownload(songId: String) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
            
        val workRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setConstraints(constraints)
            .setInputData(workDataOf(DownloadWorker.KEY_SONG_ID to songId))
            .build()
            
        WorkManager.getInstance(context).enqueue(workRequest)
    }
}
