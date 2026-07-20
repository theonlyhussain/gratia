package com.gratia.music.data.metadata

import com.gratia.music.data.model.SongEntity

class SongIdentityEngine {

    /**
     * Determines if two songs are conceptually the same track based on a fallback priority:
     * 1. ISRC
     * 2. Title + Artist + Duration
     * 3. Fingerprint (Future)
     * 4. Title + Artist
     */
    fun isSameSong(songA: SongEntity, songB: SongEntity): Boolean {
        // Priority 1: ISRC
        if (!songA.isrc.isNullOrBlank() && !songB.isrc.isNullOrBlank()) {
            return songA.isrc == songB.isrc
        }

        // Priority 2: Title + Artist + Duration (within 5 seconds)
        if (songA.title.equals(songB.title, ignoreCase = true) &&
            songA.artist.equals(songB.artist, ignoreCase = true)) {
            
            val durationDiff = Math.abs(songA.durationMs - songB.durationMs)
            if (durationDiff < 5000) {
                return true
            }
        }

        // Fallback: Title + Artist
        if (songA.title.equals(songB.title, ignoreCase = true) &&
            songA.artist.equals(songB.artist, ignoreCase = true)) {
            return true
        }

        return false
    }
}
