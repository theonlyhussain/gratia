package com.gratia.music.provider

data class RemoteArtistRef(
    val id: String?,
    val name: String
)

data class RemoteAlbumRef(
    val id: String?,
    val name: String
)

data class RemoteTrack(
    val id: String,
    val provider: MusicProviderType = MusicProviderType.YOUTUBE_MUSIC,
    val title: String,
    val artists: List<RemoteArtistRef> = emptyList(),
    val album: RemoteAlbumRef? = null,
    val durationMs: Long? = null,
    val durationText: String? = null,
    val artworkUrl: String? = null,
    val videoId: String,
    val isExplicit: Boolean = false
) {
    val artistDisplay: String
        get() = artists.joinToString(", ") { it.name }.ifEmpty { "Unknown Artist" }
}

data class RemoteAlbumSummary(
    val id: String,
    val title: String,
    val artists: List<RemoteArtistRef> = emptyList(),
    val year: String? = null,
    val artworkUrl: String? = null,
    val type: String? = null
)

data class RemoteArtist(
    val id: String,
    val name: String,
    val description: String? = null,
    val subscribers: String? = null,
    val artworkUrl: String? = null,
    val topSongs: List<RemoteTrack> = emptyList(),
    val albums: List<RemoteAlbumSummary> = emptyList(),
    val singles: List<RemoteAlbumSummary> = emptyList(),
    val relatedArtists: List<RemoteArtistRef> = emptyList()
)

data class RemoteAlbum(
    val id: String,
    val title: String,
    val artists: List<RemoteArtistRef> = emptyList(),
    val year: String? = null,
    val artworkUrl: String? = null,
    val trackCount: Int? = null,
    val durationText: String? = null,
    val tracks: List<RemoteTrack> = emptyList()
)

data class RemotePlaylistSummary(
    val id: String,
    val title: String,
    val author: String? = null,
    val artworkUrl: String? = null,
    val itemCount: String? = null
)

data class RemotePlaylist(
    val id: String,
    val title: String,
    val author: String? = null,
    val description: String? = null,
    val artworkUrl: String? = null,
    val trackCount: Int? = null,
    val tracks: List<RemoteTrack> = emptyList()
)

data class RemoteLyrics(
    val videoId: String,
    val text: String,
    val isSynced: Boolean = false,
    val provider: String = "YouTube Music"
)

data class PlaybackSource(
    val videoId: String,
    val streamUrl: String,
    val mimeType: String? = null,
    val durationMs: Long? = null,
    val expiresAtMs: Long? = null,
    val format: String? = null,
    val bitrate: Int? = null
)

data class RemoteHomeSection(
    val title: String,
    val tracks: List<RemoteTrack> = emptyList(),
    val collections: List<RemotePlaylistSummary> = emptyList()
)

data class RemoteSearchResponse(
    val query: String,
    val filter: String? = null,
    val tracks: List<RemoteTrack> = emptyList(),
    val artists: List<RemoteArtistRef> = emptyList(),
    val albums: List<RemoteAlbumSummary> = emptyList(),
    val playlists: List<RemotePlaylistSummary> = emptyList()
)
