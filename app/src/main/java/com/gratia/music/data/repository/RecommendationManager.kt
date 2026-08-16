package com.gratia.music.data.repository

import com.gratia.music.data.dao.ListeningEventDao
import com.gratia.music.data.dao.SongDao
import com.gratia.music.data.model.SongEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.log2
import kotlin.math.max

class RecommendationManager(
    private val songDao: SongDao,
    private val listeningEventDao: ListeningEventDao
) {
    /**
     * Calculates the best recommended song based on the user's actual listening behavior.
     * Uses a multi-signal scoring model.
     */
    suspend fun getRecommendedSong(allSongs: List<SongEntity>): SongEntity? = withContext(Dispatchers.IO) {
        if (allSongs.isEmpty()) return@withContext null

        // We only want to consider songs that the user has interacted with or are fully known metadata
        val candidates = allSongs.filter { it.playCount > 0 || (it.artist != "<unknown>" && it.artist.isNotBlank()) }
        if (candidates.isEmpty()) return@withContext allSongs.randomOrNull()

        var bestScore = -Float.MAX_VALUE
        var bestSong: SongEntity? = null
        val now = System.currentTimeMillis()

        // Artist level affinity tracker
        val artistScores = mutableMapOf<String, Float>()

        // 1. Calculate raw song scores
        val songScores = candidates.map { song ->
            val score = calculateScore(song, now)
            
            // Add to artist affinity
            val artist = song.artist
            if (artist.isNotBlank() && artist != "<unknown>") {
                artistScores[artist] = (artistScores[artist] ?: 0f) + max(0f, score)
            }
            
            song to score
        }

        // 2. Apply artist affinity and exploration
        songScores.forEach { (song, rawScore) ->
            val artistAffinity = if (song.artist.isNotBlank() && song.artist != "<unknown>") {
                artistScores[song.artist] ?: 0f
            } else 0f
            
            // Exploration: small random boost to prevent being permanently stuck
            val randomExplorationBoost = (Math.random() * 5.0).toFloat()
            
            val finalScore = rawScore + (artistAffinity * 0.2f) + randomExplorationBoost
            
            if (finalScore > bestScore) {
                bestScore = finalScore
                bestSong = song
            }
        }

        return@withContext bestSong ?: allSongs.randomOrNull()
    }

    private fun calculateScore(song: SongEntity, now: Long): Float {
        // 1. Listening Duration Score (normalized by song length)
        // If a song is 3 mins (180,000ms), and they listened for 10 hours (36,000,000ms), they love it.
        val durationMs = if (song.durationMs > 0) song.durationMs else 180000L // fallback 3 mins
        val listendDurationScore = (song.totalListenTime.toFloat() / durationMs.toFloat()) * 10f

        // 2. Completion Score
        val completionScore = song.completedCount * 15f

        // 3. Repeat Play Score (Diminishing returns so 1000 plays doesn't break the system)
        val repeatPlayScore = log2(song.playCount.toFloat() + 1f) * 20f

        // 4. Recency Score
        // If played today, huge boost. If played 1 month ago, tiny boost.
        val recencyScore = if (song.lastPlayedAt != null) {
            val daysSincePlayed = (now - song.lastPlayedAt) / (1000L * 60 * 60 * 24)
            when {
                daysSincePlayed == 0L -> 25f
                daysSincePlayed < 3L -> 15f
                daysSincePlayed < 7L -> 10f
                daysSincePlayed < 30L -> 5f
                else -> 0f
            }
        } else {
            0f // Never played
        }

        // 5. Skip Penalties
        // If a song has high skips but also high totalListenTime, it's just played a lot.
        // We estimate "early skips" by seeing if skipCount is high relative to playCount.
        val skipRatio = if (song.playCount > 0) {
            song.skipCount.toFloat() / song.playCount.toFloat()
        } else 0f
        
        val skipPenalty = if (skipRatio > 0.5f) {
            song.skipCount * 10f // Heavy penalty if they skip it more than 50% of the time
        } else {
            song.skipCount * 2f
        }

        return listendDurationScore + completionScore + repeatPlayScore + recencyScore - skipPenalty
    }
}
