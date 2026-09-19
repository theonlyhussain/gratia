package com.gratia.music.provider

import com.gratia.music.data.model.SongEntity
import com.gratia.music.provider.ytmusic.model.durationMillis

object RemoteTrackMapper {
    /**
     * Converts a RemoteTrack to SongEntity so it can seamlessly live in Gratia's
     * existing queue, player, history, and UI systems without changing models.
     */
    fun toSongEntity(remote: RemoteTrack): SongEntity {
        val videoId = remote.videoId.ifEmpty { remote.id }
        return SongEntity(
            id = "ytm_$videoId",
            title = remote.title,
            artist = remote.artistDisplay,
            album = remote.album?.name,
            // A row's duration is a display string ("3:45") — anything that has
            // to *reason* about the length (LRCLIB exact lyric lookup keyed on
            // duration) needs it as a quantity, so convert here rather than
            // losing it. Zero when the row didn't state one.
            durationMs = remote.durationMs ?: remote.durationText.durationMillis(),
            storageProvider = MusicProviderType.YOUTUBE_MUSIC.id,
            providerTrackId = videoId,
            providerArtistId = remote.artists.firstOrNull()?.id,
            providerAlbumId = remote.album?.id,
            artworkUrl = remote.artworkUrl,
            coverArtPath = remote.artworkUrl, // Coil loads HTTP artwork URIs natively
            coverSource = "youtube_music",
            explicit = remote.isExplicit,
            localUri = null // Streaming URL is dynamically resolved at playback time
        )
    }

    /**
     * Converts a SongEntity back to RemoteTrack if needed.
     */
    fun toRemoteTrack(song: SongEntity): RemoteTrack {
        val videoId = song.providerTrackId ?: song.id.removePrefix("ytm_")
        return RemoteTrack(
            id = videoId,
            provider = MusicProviderType.fromId(song.storageProvider),
            title = song.title,
            artists = listOf(RemoteArtistRef(id = song.providerArtistId, name = song.artist)),
            album = if (!song.album.isNullOrBlank()) RemoteAlbumRef(id = song.providerAlbumId, name = song.album) else null,
            durationMs = song.durationMs,
            artworkUrl = song.artworkUrl ?: song.coverArtPath,
            videoId = videoId,
            isExplicit = song.explicit
        )
    }
}
