package com.gratia.music.data.metadata

import com.gratia.music.data.dao.SongDao
import com.gratia.music.data.dao.SyncQueueDao
import com.gratia.music.data.model.SyncQueueEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class MetadataManager(
    private val songDao: SongDao,
    private val syncQueueDao: SyncQueueDao
) {

    private val REFRESH_INTERVAL_MS = TimeUnit.DAYS.toMillis(30)

    /**
     * Checks if a song requires a metadata refresh.
     * If so, queues it in the SyncQueue.
     */
    suspend fun checkAndQueueSync(songId: String) = withContext(Dispatchers.IO) {
        val song = songDao.getSongById(songId) ?: return@withContext
        
        val now = System.currentTimeMillis()
        val isStale = (now - song.lastMetadataSync) > REFRESH_INTERVAL_MS
        val isMissingCriticalData = song.album.isNullOrBlank() || song.releaseDate.isNullOrBlank()
        
        if (isStale || isMissingCriticalData) {
            val existingSync = syncQueueDao.getSyncBySongId(songId)
            if (existingSync == null) {
                syncQueueDao.insertSync(
                    SyncQueueEntity(songId = songId, status = "QUEUED")
                )
            }
        }
    }
}
