package com.gratia.music.provider

import com.gratia.music.provider.ytmusic.innertube.Innertube
import com.gratia.music.provider.ytmusic.innertube.InnertubeParser
import com.gratia.music.provider.ytmusic.innertube.StreamResolver
import com.gratia.music.provider.ytmusic.model.SearchResult
import com.gratia.music.provider.ytmusic.model.Song

class YouTubeMusicProvider : MusicProvider {

    override val type: MusicProviderType = MusicProviderType.YOUTUBE_MUSIC

    override val isAvailable: Boolean = true

    override suspend fun search(query: String, filter: String?, limit: Int): RemoteSearchResponse {
        val response = Innertube.search(query, filter)
        val songs = InnertubeParser.parseSearchSongs(response).take(limit)
        return RemoteSearchResponse(
            query = query,
            tracks = songs.map { it.toRemoteTrack() }
        )
    }

    override suspend fun getTrack(trackId: String): RemoteTrack? {
        return null // YT Music doesn't have a direct "get track" endpoint usually, playback resolution is enough
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
                albums = artistPage.sections.filter { it.title.contains("album", ignoreCase = true) }
                    .flatMap { it.items }.map { it.toRemoteAlbumSummary() },
                singles = artistPage.sections.filter { it.title.contains("single", ignoreCase = true) }
                    .flatMap { it.items }.map { it.toRemoteAlbumSummary() },
                relatedArtists = artistPage.sections.filter { it.title.contains("fans", ignoreCase = true) }
                    .flatMap { it.items }.map { RemoteArtistRef(id = it.browseId, name = it.title) }
            )
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun getAlbum(albumId: String): RemoteAlbum? {
        return try {
            val response = Innertube.browse(albumId)
            val header = InnertubeParser.parseBrowseHeader(response) ?: return null
            val shelf = InnertubeParser.parsePlaylistShelf(response)
            
            RemoteAlbum(
                id = albumId,
                title = header.title,
                artists = listOf(RemoteArtistRef(id = null, name = header.subtitle)),
                artworkUrl = header.thumbnailUrl,
                tracks = shelf?.songs?.map { it.toRemoteTrack() } ?: emptyList()
            )
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun getPlaylist(playlistId: String): RemotePlaylist? {
        return try {
            val response = Innertube.browse(playlistId)
            val header = InnertubeParser.parseBrowseHeader(response) ?: return null
            val shelf = InnertubeParser.parsePlaylistShelf(response)
            val description = InnertubeParser.parseDescription(response)
            
            RemotePlaylist(
                id = playlistId,
                title = header.title,
                author = header.subtitle,
                description = description,
                artworkUrl = header.thumbnailUrl,
                tracks = shelf?.songs?.map { it.toRemoteTrack() } ?: emptyList()
            )
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun getLyrics(trackId: String): RemoteLyrics? {
        return null // To be implemented via a separate lyrics parser if needed
    }

    override suspend fun getRelatedTracks(trackId: String): List<RemoteTrack> {
        return try {
            val response = Innertube.next(trackId)
            val queue = InnertubeParser.parseWatchQueue(response)
            queue.map { it.toRemoteTrack() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun getHome(limit: Int): List<RemoteHomeSection> {
        return try {
            val response = Innertube.browse("FEmusic_home")
            val shelves = InnertubeParser.parseHome(response)
            shelves.map { shelf ->
                RemoteHomeSection(
                    title = shelf.title,
                    tracks = shelf.items.filter { it.videoId != null }.map { it.toRemoteTrackFallback() },
                    collections = shelf.items.filter { it.browseId != null }.map { it.toRemotePlaylistSummary() }
                )
            }.take(limit)
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun resolvePlayback(trackId: String): PlaybackSource? {
        return try {
            val url = StreamResolver.resolve(trackId)
            PlaybackSource(
                videoId = trackId,
                streamUrl = url
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun com.gratia.music.provider.ytmusic.model.ShelfItem.toRemoteAlbumSummary(): RemoteAlbumSummary {
        return RemoteAlbumSummary(
            id = browseId ?: "",
            title = title,
            artists = emptyList(),
            year = subtitle,
            artworkUrl = thumbnailUrl,
            type = if (subtitle.contains("EP") || subtitle.contains("Single")) "Single" else "Album"
        )
    }

    private fun com.gratia.music.provider.ytmusic.model.ShelfItem.toRemotePlaylistSummary(): RemotePlaylistSummary {
        return RemotePlaylistSummary(
            id = browseId ?: "",
            title = title,
            author = subtitle,
            artworkUrl = thumbnailUrl
        )
    }

    private fun com.gratia.music.provider.ytmusic.model.ShelfItem.toRemoteTrackFallback(): RemoteTrack {
        return RemoteTrack(
            id = "ytm_${videoId ?: ""}",
            provider = MusicProviderType.YOUTUBE_MUSIC,
            title = title,
            artists = listOf(RemoteArtistRef(id = null, name = subtitle)),
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
            durationMs = null,
            durationText = durationText,
            artworkUrl = thumbnailUrl,
            videoId = videoId
        )
    }
}
