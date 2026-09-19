package com.gratia.music.provider

data class RemoteArtistRef(
    val id: String?,
    val name: String,
    /**
     * Artist picture, when the surface that produced the ref had one — Explore
     * and search cards do, a track row's credit line does not.
     */
    val artworkUrl: String? = null
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
    val relatedArtists: List<RemoteArtistRef> = emptyList(),
    /**
     * Playlist browse id holding the artist's full song list — the artist page
     * links it from its "Top songs" shelf header, whose first ~5 rows are all
     * the landing page itself carries. Fetched by [YouTubeMusicProvider.getArtistSongs]
     * rather than derived from a search, which is incomplete and unreliable.
     */
    val songsPlaylistId: String? = null
)

/** One page of a paged remote list, plus the token for the next page — null once exhausted. */
data class RemotePage<T>(
    val items: List<T>,
    val continuation: String?
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
    val tracks: List<RemoteTrack> = emptyList(),
    /** Token for the next page of tracks — null once exhausted. */
    val continuation: String? = null
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

/**
 * A tappable category on the Explore landing page.
 *
 * [browseId] names the page and [params] names the section within it — a mood
 * or genre is addressed by the pair, not by the id alone.
 */
data class BrowseCategory(
    val id: String,
    val title: String,
    val browseId: String,
    val params: String? = null
)

/**
 * A card inside a [BrowseSection], kept typed so the UI can route it: albums
 * open album pages, artists open artist pages, playlists open playlist pages,
 * and tracks go straight into the unified playback system.
 */
sealed interface BrowseEntry {
    data class Track(val track: RemoteTrack) : BrowseEntry
    data class Album(val album: RemoteAlbumSummary) : BrowseEntry
    data class Artist(val artist: RemoteArtistRef) : BrowseEntry
    data class Playlist(val playlist: RemotePlaylistSummary) : BrowseEntry

    /** Stable identity for list keys — entry type plus the underlying id. */
    val stableId: String
        get() = when (this) {
            is Track -> "t:${track.videoId}"
            is Album -> "a:${album.id}"
            is Artist -> "r:${artist.id}"
            is Playlist -> "p:${playlist.id}"
        }
}

/** One independently scrollable shelf of a browsed category page. */
data class BrowseSection(
    val title: String,
    val entries: List<BrowseEntry> = emptyList()
)

/**
 * The shelves behind one Explore category. Not necessarily complete: Explore
 * pages are continuation-backed and a page reports what it actually holds.
 */
data class BrowsePage(
    val category: BrowseCategory,
    val sections: List<BrowseSection> = emptyList()
)
