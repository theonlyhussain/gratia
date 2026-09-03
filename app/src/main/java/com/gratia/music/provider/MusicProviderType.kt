package com.gratia.music.provider

enum class MusicProviderType(val id: String, val displayName: String) {
    LOCAL("local", "Local Storage"),
    YOUTUBE_MUSIC("youtube_music", "YouTube Music");

    companion object {
        fun fromId(id: String?): MusicProviderType {
            return entries.find { it.id.equals(id, ignoreCase = true) } ?: LOCAL
        }
    }
}
