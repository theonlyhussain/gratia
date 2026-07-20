package com.gratia.music.data.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.gratia.music.data.db.GratiaDatabase
import com.gratia.music.data.metadata.MetadataManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MetadataSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val TAG = "MetadataSyncWorker"

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.d(TAG, "Background metadata sync started.")
        
        val db = GratiaDatabase.getInstance(applicationContext)
        val syncQueueDao = db.syncQueueDao()
        val songDao = db.songDao()
        
        val pendingSyncs = syncQueueDao.getPendingSyncs()
        if (pendingSyncs.isEmpty()) {
            Log.d(TAG, "No pending syncs.")
            return@withContext Result.success()
        }
        
        var successCount = 0
        var failureCount = 0

        for (sync in pendingSyncs) {
            try {
                // Mark as processing
                val processingSync = sync.copy(status = "PROCESSING")
                syncQueueDao.updateSync(processingSync)
                
                val song = songDao.getSongById(sync.songId)
                if (song == null) {
                    // Song deleted, remove sync
                    syncQueueDao.deleteSyncForSong(sync.songId)
                    continue
                }
                
                Log.d(TAG, "Syncing metadata for song: \${song.title} - \${song.artist}")
                
                // Here we would delegate to MetadataManager to fetch from Deezer / MusicBrainz
                // For now, we simulate success and update the last sync time
                
                val updatedSong = song.copy(
                    lastMetadataSync = System.currentTimeMillis()
                )
                songDao.updateSong(updatedSong)
                
                // Mark as completed
                syncQueueDao.deleteSyncForSong(sync.songId)
                successCount++
                
            } catch (e: Exception) {
                Log.e(TAG, "Error syncing metadata for song: \${sync.songId}", e)
                val failedSync = sync.copy(
                    status = "FAILED",
                    retryCount = sync.retryCount + 1
                )
                syncQueueDao.updateSync(failedSync)
                failureCount++
            }
        }
        
        Log.d(TAG, "Sync finished. Success: \$successCount, Failed: \$failureCount")
        
        if (failureCount > 0) {
            return@withContext Result.retry()
        }
        return@withContext Result.success()
    }
}
