package com.gratia.music.utils

object ArtistParser {

    /**
     * Parses a raw artist string (e.g., "Alan Walker, Sabrina Carpenter & Farruko")
     * into a list of individual artist names.
     */
    fun parseArtists(rawArtistString: String?): List<String> {
        if (rawArtistString.isNullOrBlank()) return emptyList()

        // Replace common delimiters with a standard delimiter, e.g., "|||"
        var parsed = rawArtistString
            .replace(Regex("(?i)\\s+feat\\.?\\s+"), "|||")
            .replace(Regex("(?i)\\s+ft\\.?\\s+"), "|||")
            .replace(Regex("(?i)\\s+featuring\\s+"), "|||")
            .replace(Regex("\\s+&\\s+"), "|||")
            .replace(Regex("(?i)\\s+and\\s+"), "|||")
            .replace(Regex("\\s*,\\s*"), "|||")
            .replace(Regex("\\s*;\\s*"), "|||")
            .replace(Regex("\\s*/\\s*"), "|||")

        return parsed.split("|||")
            .map { it.trim() }
            .filter { it.isNotEmpty() && it.lowercase() != "unknown" }
            .distinct()
    }

    /**
     * Determines the primary artist of a song.
     */
    fun getPrimaryArtist(song: com.gratia.music.data.model.SongEntity): String {
        val parsed = parseArtists(song.artist)
        return parsed.firstOrNull() ?: song.artist
    }
}
