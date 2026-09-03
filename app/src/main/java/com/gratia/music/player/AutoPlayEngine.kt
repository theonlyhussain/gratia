package com.gratia.music.player

import android.util.Log
import com.gratia.music.data.model.SongEntity
import com.gratia.music.data.repository.SongRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AutoPlayEngine(private val repository: SongRepository) {

    companion object {
        private const val TAG = "AutoPlayEngine"
    }

    suspend fun generateNextTrack(
        action: QueueAction,
        currentSong: SongEntity,
        currentQueueIds: List<String>
    ): SongEntity? = withContext(Dispatchers.IO) {
        
        Log.d(TAG, "generateNextTrack: processing action $action for current song '${currentSong.title}' (provider=${currentSong.storageProvider})")
        
        val recentHistory = currentQueueIds.takeLast(15) // Prevent playing recent tracks again

        // For YouTube Music tracks, use related/recommendation provider content
        if (currentSong.storageProvider == "youtube_music") {
            try {
                val videoId = currentSong.providerTrackId ?: currentSong.id.removePrefix("ytm_")
                if (videoId.isNotBlank()) {
                    val related = com.gratia.music.GratiaApp.instance.providerManager.youtubeMusicProvider.getRelatedTracks(videoId)
                    val candidate = related.firstOrNull { rem ->
                        rem.videoId != videoId &&
                        !recentHistory.contains("ytm_${rem.videoId}") &&
                        !recentHistory.contains(rem.videoId)
                    }
                    if (candidate != null) {
                        val mapped = com.gratia.music.provider.RemoteTrackMapper.toSongEntity(candidate)
                        Log.d(TAG, "Generated remote related track: '${mapped.title}' by ${mapped.artist}")
                        return@withContext mapped
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to get remote related track, falling back to local: ${e.message}")
            }
        }

        val MAX_ATTEMPTS = 5
        var candidate: SongEntity? = null
        
        for (i in 0 until MAX_ATTEMPTS) {
            candidate = when (action) {
                QueueAction.MAINTAIN_VIBE -> {
                    val genre = currentSong.genre
                    if (!genre.isNullOrBlank()) {
                        repository.getRandomSongByGenre(genre)
                    } else {
                        repository.getRandomSong()
                    }
                }
                QueueAction.PENALIZE_VIBE -> {
                    val genre = currentSong.genre
                    val artist = currentSong.artist
                    if (!genre.isNullOrBlank()) {
                        repository.getRandomSongByGenreExcludingArtist(genre, artist)
                    } else {
                        repository.getRandomSongExcludingArtist(artist)
                    }
                }
                QueueAction.RADICAL_SHIFT -> {
                    val genre = currentSong.genre
                    if (!genre.isNullOrBlank()) {
                        repository.getRandomSongExcludingGenre(genre)
                    } else {
                        repository.getRandomSong()
                    }
                }
                QueueAction.WILDCARD_TRACK -> {
                    repository.getRandomSong()
                }
            }
            
            if (candidate != null && !recentHistory.contains(candidate.id) && candidate.id != currentSong.id) {
                break
            }
            // Keep looping if duplicate
        }

        if (candidate != null) {
            Log.d(TAG, "Generated track: '${candidate.title}' by ${candidate.artist} (Action: $action)")
        } else {
            Log.d(TAG, "Failed to generate track.")
        }
        
        return@withContext candidate
    }
}
