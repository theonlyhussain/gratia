package com.gratia.music.provider

import android.util.Log
import com.gratia.music.provider.ytmusic.innertube.Innertube
import com.gratia.music.provider.ytmusic.innertube.InnertubeParser
import com.gratia.music.provider.ytmusic.innertube.StreamResolver
import com.gratia.music.provider.ytmusic.model.BrowseCard
import com.gratia.music.provider.ytmusic.model.BrowseType
import com.gratia.music.provider.ytmusic.model.SearchFilter
import com.gratia.music.provider.ytmusic.model.SearchResult
import com.gratia.music.provider.ytmusic.model.Song
import com.gratia.music.provider.ytmusic.model.durationMillis

class YouTubeMusicProvider : MusicProvider {

    companion object {
        private const val TAG = "RemoteProvider"

        /**
         * YouTube Music's "Moods & genres" page — the taxonomy Explore is built
         * from. Each tile on it carries its own `params` token, which is what
         * actually addresses a single mood or genre.
         */
        private const val MOODS_AND_GENRES = "FEmusic_moods_and_genres"
    }

    override val type: MusicProviderType = MusicProviderType.YOUTUBE_MUSIC

    override val isAvailable: Boolean = true

    override suspend fun search(query: String, filter: String?, limit: Int): RemoteSearchResponse {
        // The UI names its filter ("songs", "artists", …); the search endpoint
        // wants InnerTube's base64 `params` token naming the filter category.
        // Sent raw, an unknown string is ignored and the response silently
        // degrades to the "All" layout — so the mapping happens here, where
        // both vocabularies live.
        val params = when (filter?.lowercase()) {
            "songs" -> SearchFilter.SONGS.params
            "albums" -> SearchFilter.ALBUMS.params
            "artists" -> SearchFilter.ARTISTS.params
            "playlists" -> SearchFilter.PLAYLISTS.params
            else -> null
        }
        val response = Innertube.search(query, params)
        val results = InnertubeParser.parseSearch(response)

        val tracks = results.filterIsInstance<SearchResult.Track>()
            .map { it.song.toRemoteTrack() }
            .take(limit)

        // A filter other than songs answers with browse rows only, and even
        // the unfiltered "All" layout leads with one. Carrying them as their
        // own sections is what lets the search screen offer artist and album
        // navigation instead of flattening everything into song rows.
        val artists = results.filterIsInstance<SearchResult.Browse>()
            .filter { it.item.type == BrowseType.ARTIST && !it.item.browseId.isBlankId() }
            .map { RemoteArtistRef(id = it.item.browseId, name = it.item.title) }
            .distinctBy { it.id }
            .take(limit)

        val albums = results.filterIsInstance<SearchResult.Browse>()
            .filter { it.item.type == BrowseType.ALBUM && !it.item.browseId.isBlankId() }
            .map { item ->
                RemoteAlbumSummary(
                    id = item.item.browseId,
                    title = item.item.title,
                    artists = artistRefsFromSubtitle(item.item.subtitle),
                    year = item.item.subtitle.yearFromSubtitle(),
                    artworkUrl = item.item.thumbnailUrl,
                    type = "Album"
                )
            }
            .distinctBy { it.id }
            .take(limit)

        val playlists = results.filterIsInstance<SearchResult.Browse>()
            .filter { it.item.type == BrowseType.PLAYLIST && !it.item.browseId.isBlankId() }
            .map { item ->
                RemotePlaylistSummary(
                    id = item.item.browseId,
                    title = item.item.title,
                    author = item.item.subtitle,
                    artworkUrl = item.item.thumbnailUrl
                )
            }
            .distinctBy { it.id }
            .take(limit)

        return RemoteSearchResponse(
            query = query,
            filter = filter,
            tracks = tracks,
            artists = artists,
            albums = albums,
            playlists = playlists
        )
    }

    override suspend fun getTrack(trackId: String): RemoteTrack? {
        // YT Music has no direct "get track" browse endpoint; search for the
        // exact id — the catalogue track (not a video upload) comes back as
        // the first song row.
        return try {
            val response = Innertube.search(trackId, SearchFilter.SONGS.params)
            InnertubeParser.parseSearchSongs(response)
                .firstOrNull { it.videoId == trackId }
                ?.toRemoteTrack()
        } catch (e: Exception) {
            Log.w(TAG, "getTrack($trackId) failed: ${e.message}")
            null
        }
    }

    override suspend fun getArtist(artistId: String): RemoteArtist? {
        return try {
            val response = Innertube.browse(artistId)
            val artistPage = InnertubeParser.parseArtistPage(response)
            RemoteArtist(
                id = artistId,
                name = artistPage.name ?: "Unknown Artist",
                description = artistPage.description,
                subscribers = artistPage.subscriberCountText,
                artworkUrl = artistPage.thumbnailUrl,
                topSongs = artistPage.songs.map { it.toRemoteTrack() },
                albums = artistPage.sections
                    .filter { it.title.contains("album", ignoreCase = true) &&
                        !it.title.contains("single", ignoreCase = true) }
                    .flatMap { it.items }.map { it.toRemoteAlbumSummary() },
                singles = artistPage.sections
                    .filter { it.title.contains("single", ignoreCase = true) }
                    .flatMap { it.items }.map { it.toRemoteAlbumSummary() },
                relatedArtists = artistPage.sections
                    .filter { it.title.contains("fans", ignoreCase = true) }
                    .flatMap { it.items }.map { RemoteArtistRef(id = it.browseId, name = it.title) },
                // The "Top songs" shelf header links the playlist holding the
                // artist's complete song list — the landing page itself only
                // carries the first handful.
                songsPlaylistId = artistPage.moreSongsBrowseId
            )
        } catch (e: Exception) {
            Log.w(TAG, "getArtist($artistId) failed: ${e.message}")
            null
        }
    }

    /**
     * The artist's full song list, one page at a time, from the playlist the
     * artist page links ([RemoteArtist.songsPlaylistId]). Never derived from a
     * search for the artist's name — that is incomplete and unreliable by
     * construction.
     *
     * @param songsPlaylistId the browse id of the artist songs playlist.
     * @param continuation the token from the previous [RemotePage], or null
     *   for the first page.
     */
    suspend fun getArtistSongs(
        songsPlaylistId: String,
        continuation: String? = null
    ): RemotePage<RemoteTrack> {
        val response = if (continuation != null) {
            Innertube.browseContinuation(continuation)
        } else {
            Innertube.browse(songsPlaylistId)
        }
        val shelfPage = InnertubeParser.parsePlaylistShelf(response)
        val songs = shelfPage?.songs ?: InnertubeParser.collectSongsDeep(response)
        return RemotePage(
            items = songs.map { it.toRemoteTrack() },
            continuation = shelfPage?.continuation ?: InnertubeParser.continuationToken(response)
        ).also { Log.d(TAG, "getArtistSongs items=${it.items.size} more=${it.continuation != null}") }
    }

    /**
     * The artist's albums or singles, one page at a time, from the artist
     * page's own carousel section — the browse endpoint, not a search.
     *
     * @param type "album" or "single"; anything else is treated as albums.
     */
    suspend fun getArtistAlbums(
        artistId: String,
        type: String = "album",
        continuation: String? = null
    ): RemotePage<RemoteAlbumSummary> {
        val response = if (continuation != null) {
            Innertube.browseContinuation(continuation)
        } else {
            Innertube.browse(artistId)
        }
        val artistPage = InnertubeParser.parseArtistPage(response)
        val wanted = if (type.equals("single", ignoreCase = true)) "single" else "album"
        val unwanted = if (wanted == "single") "album" else "single"
        val items = artistPage.sections
            .filter { it.title.contains(wanted, ignoreCase = true) &&
                !it.title.contains(unwanted, ignoreCase = true) }
            .flatMap { it.items }
            .map { it.toRemoteAlbumSummary() }
        return RemotePage(
            items = items,
            // Carousels page through the section-list continuation, when one exists.
            continuation = InnertubeParser.continuationToken(response)
        )
    }

    override suspend fun getAlbum(albumId: String): RemoteAlbum? {
        return try {
            val response = Innertube.browse(albumId)
            val header = InnertubeParser.parseBrowseHeader(response) ?: return null

            // Album pages put their tracks in the playlist shelf. The shelf
            // parser scopes itself to the *secondary* column / continuation
            // envelope and answers null for anything not playlist-shaped —
            // which an album page is — so fall back to the page credit walk.
            // collectSongsDeep reads the album header once and credits every
            // row with it, which is exactly right for a release.
            val shelfPage = InnertubeParser.parsePlaylistShelf(response)
            val songs = shelfPage?.songs ?: InnertubeParser.collectSongsDeep(response)

            if (songs.isEmpty()) {
                Log.w(TAG, "getAlbum($albumId): header parsed but no tracks found")
            }

            // The header subtitle is the credit line ("Artist • 2023"), so
            // split the artist out instead of printing the whole line.
            val subtitle = header.subtitle
            RemoteAlbum(
                id = albumId,
                title = header.title,
                artists = artistRefsFromSubtitle(subtitle),
                year = subtitle.yearFromSubtitle(),
                artworkUrl = header.thumbnailUrl,
                tracks = songs.map { it.toRemoteTrack() }
            )
        } catch (e: Exception) {
            Log.w(TAG, "getAlbum($albumId) failed: ${e.message}")
            null
        }
    }

    override suspend fun getPlaylist(playlistId: String): RemotePlaylist? {
        return try {
            val response = Innertube.browse(playlistId)
            val header = InnertubeParser.parseBrowseHeader(response) ?: return null
            val shelfPage = InnertubeParser.parsePlaylistShelf(response)
            val songs = shelfPage?.songs ?: InnertubeParser.collectSongsDeep(response)
            val description = InnertubeParser.parseDescription(response)

            RemotePlaylist(
                id = playlistId,
                title = header.title,
                author = header.subtitle,
                description = description,
                artworkUrl = header.thumbnailUrl,
                tracks = songs.map { it.toRemoteTrack() },
                continuation = shelfPage?.continuation
            )
        } catch (e: Exception) {
            Log.w(TAG, "getPlaylist($playlistId) failed: ${e.message}")
            null
        }
    }

    override suspend fun getLyrics(trackId: String): RemoteLyrics? {
        return null // Gratia's own lyrics-provider pipeline owns lyrics
    }

    override suspend fun getRelatedTracks(trackId: String): List<RemoteTrack> {
        return try {
            val response = Innertube.next(trackId)
            InnertubeParser.parseWatchQueue(response).map { it.toRemoteTrack() }
        } catch (e: Exception) {
            Log.w(TAG, "getRelatedTracks($trackId) failed: ${e.message}")
            emptyList()
        }
    }

    override suspend fun getHome(limit: Int): List<RemoteHomeSection> {
        return try {
            val response = Innertube.browse("FEmusic_home")
            InnertubeParser.parseHome(response).map { shelf ->
                RemoteHomeSection(
                    title = shelf.title,
                    tracks = shelf.items.filter { !it.videoId.isNullOrBlank() }.map { it.toRemoteTrackFallback() },
                    collections = shelf.items.filter { !it.browseId.isNullOrBlank() }.map { it.toRemotePlaylistSummary() }
                )
            }.take(limit)
        } catch (e: Exception) {
            Log.w(TAG, "getHome failed: ${e.message}")
            emptyList()
        }
    }

    override suspend fun getBrowseCategories(): List<BrowseCategory> {
        return try {
            val response = Innertube.browse(MOODS_AND_GENRES)
            InnertubeParser.parseCategories(response).map { ref ->
                BrowseCategory(
                    id = ref.id,
                    title = ref.title,
                    browseId = ref.browseId,
                    params = ref.params
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "getBrowseCategories failed: ${e.message}")
            emptyList()
        }
    }

    override suspend fun getBrowsePage(category: BrowseCategory): BrowsePage {
        return try {
            val response = Innertube.browse(category.browseId, category.params)
            val sections = InnertubeParser.parseBrowseShelves(response)
                .map { shelf ->
                    BrowseSection(
                        title = shelf.title,
                        entries = shelf.cards.mapNotNull(::toBrowseEntry)
                    )
                }
                .filter { it.entries.isNotEmpty() }
            BrowsePage(category = category, sections = sections)
        } catch (e: Exception) {
            Log.w(TAG, "getBrowsePage(${category.id}) failed: ${e.message}")
            BrowsePage(category = category)
        }
    }

    /**
     * Maps a parser-level browse card onto the domain entry the UI routes on.
     * Returns null for nothing — the card type is total — but the signature is
     * nullable so it can be handed straight to `mapNotNull` alongside the
     * other classifiers.
     */
    private fun toBrowseEntry(card: BrowseCard): BrowseEntry = when (card) {
        is BrowseCard.Track ->
            BrowseEntry.Track(card.song.toRemoteTrack())
        is BrowseCard.Album ->
            BrowseEntry.Album(
                RemoteAlbumSummary(
                    id = card.browseId,
                    title = card.title,
                    artists = artistRefsFromSubtitle(card.subtitle),
                    year = card.subtitle.yearFromSubtitle(),
                    artworkUrl = card.thumbnailUrl,
                    type = "Album"
                )
            )
        is BrowseCard.Artist ->
            BrowseEntry.Artist(
                RemoteArtistRef(
                    id = card.browseId,
                    name = card.name,
                    artworkUrl = card.thumbnailUrl
                )
            )
        is BrowseCard.Playlist ->
            BrowseEntry.Playlist(
                RemotePlaylistSummary(
                    id = card.browseId,
                    title = card.title,
                    author = InnertubeParser.artistFromSubtitle(card.subtitle),
                    artworkUrl = card.thumbnailUrl
                )
            )
    }

    override suspend fun resolvePlayback(trackId: String): PlaybackSource? {
        return try {
            val stream = StreamResolver.resolveFull(trackId)
            PlaybackSource(
                videoId = trackId,
                streamUrl = stream.url,
                mimeType = stream.mimeType,
                format = stream.downloadExtension,
                bitrate = stream.kbps
                // expiresAtMs deliberately unset: the resolver holds its own
                // short-lived URL cache, and guessing a longer validity here
                // would let ProviderManager serve a stream Google has retired.
            )
        } catch (e: Exception) {
            Log.w(TAG, "resolvePlayback($trackId) failed: ${e.message}")
            null
        }
    }

    // ---- Subtitle helpers ----------------------------------------------------

    /** A shelf card without a browse id cannot be navigated to. */
    private fun String.isBlankId(): Boolean = isBlank()

    /**
     * The artist credit out of a release subtitle, which reads
     * "Artist • 2023" or "Album • Artist • 2023" — the display line, not a
     * structured artist ref, so the id is unknown here and filled in later by
     * the pages that do carry one.
     */
    private fun artistRefsFromSubtitle(subtitle: String): List<RemoteArtistRef> {
        val segments = subtitle.split("•").map { it.trim() }.filter { it.isNotBlank() }
        val names = segments.filterNot { segment ->
            segment.matches(YEAR_REGEX) ||
                segment.equals("album", ignoreCase = true) ||
                segment.equals("single", ignoreCase = true) ||
                segment.equals("ep", ignoreCase = true)
        }
        return names.take(1).map { RemoteArtistRef(id = null, name = it) }
    }

    private fun String.yearFromSubtitle(): String? =
        split("•").map { it.trim() }.firstOrNull { it.matches(YEAR_REGEX) }

    private val YEAR_REGEX = Regex("""\d{4}""")

    // ---- Mappers --------------------------------------------------------------

    private fun com.gratia.music.provider.ytmusic.model.ShelfItem.toRemoteAlbumSummary(): RemoteAlbumSummary {
        return RemoteAlbumSummary(
            id = browseId ?: "",
            title = title,
            artists = artistRefsFromSubtitle(subtitle),
            year = subtitle.yearFromSubtitle(),
            artworkUrl = thumbnailUrl,
            type = if (subtitle.contains("EP", ignoreCase = true) || subtitle.contains("Single", ignoreCase = true)) "Single" else "Album"
        )
    }

    private fun com.gratia.music.provider.ytmusic.model.ShelfItem.toRemotePlaylistSummary(): RemotePlaylistSummary {
        return RemotePlaylistSummary(
            id = browseId ?: "",
            title = title,
            author = InnertubeParser.artistFromSubtitle(subtitle),
            artworkUrl = thumbnailUrl
        )
    }

    private fun com.gratia.music.provider.ytmusic.model.ShelfItem.toRemoteTrackFallback(): RemoteTrack {
        return RemoteTrack(
            id = "ytm_$videoId",
            provider = MusicProviderType.YOUTUBE_MUSIC,
            title = title,
            artists = listOf(RemoteArtistRef(id = null, name = InnertubeParser.artistFromSubtitle(subtitle))),
            album = null,
            durationMs = null,
            durationText = null,
            artworkUrl = thumbnailUrl,
            videoId = videoId ?: ""
        )
    }

    private fun Song.toRemoteTrack(): RemoteTrack {
        return RemoteTrack(
            id = "ytm_$videoId",
            provider = MusicProviderType.YOUTUBE_MUSIC,
            title = title,
            artists = listOf(RemoteArtistRef(id = artistId, name = artist)),
            album = albumId?.let { RemoteAlbumRef(id = it, name = albumName ?: "") },
            durationMs = durationMillis(),
            durationText = durationText,
            artworkUrl = thumbnailUrl,
            videoId = videoId,
            isExplicit = false
        )
    }
}
