package com.gratia.music.data.model

import java.time.LocalDate

data class DailyListeningSummary(
    val dateString: String, // format "YYYY-MM-DD"
    val songsPlayed: Int,
    val listeningSeconds: Long,
    val completedSongs: Int,
    val skips: Int,
    val lyricsOpenedCount: Int,
    val songsAdded: Int
) {
    val date: LocalDate
        get() = try {
            LocalDate.parse(dateString)
        } catch (e: Exception) {
            LocalDate.now()
        }
}
