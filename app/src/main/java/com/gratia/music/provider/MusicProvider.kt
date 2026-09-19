package com.gratia.music.provider

interface MusicProvider {
    val type: MusicProviderType
    val isAvailable: Boolean

    suspend fun search(query: String, filter: String? = null, limit: Int = 25): RemoteSearchResponse
    suspend fun getTrack(trackId: String): RemoteTrack?
    suspend fun getArtist(artistId: String): RemoteArtist?
    suspend fun getAlbum(albumId: String): RemoteAlbum?
    suspend fun getPlaylist(playlistId: String): RemotePlaylist?
    suspend fun getLyrics(trackId: String): RemoteLyrics?
    suspend fun getRelatedTracks(trackId: String): List<RemoteTrack>
    suspend fun getHome(limit: Int = 5): List<RemoteHomeSection>
    suspend fun resolvePlayback(trackId: String): PlaybackSource?

    /**
     * The Explore / Moods & genres landing categories. Defaults to none so a
     * provider without a browsable catalogue does not have to pretend.
     */
    suspend fun getBrowseCategories(): List<BrowseCategory> = emptyList()

    /** The shelves behind one Explore category. */
    suspend fun getBrowsePage(category: BrowseCategory): BrowsePage =
        BrowsePage(category = category)
}
