package com.gratia.music.updater

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.gratia.music.GratiaApp

class UpdateCheckWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val updateManager = GratiaApp.instance.updateManager
            updateManager.checkForUpdate(manualCheck = false)
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }
    
    companion object {
        const val WORK_NAME = "UpdateCheckWorker"
    }
}
