package com.gratia.music.data.model

import com.gratia.music.provider.MusicProviderType

/**
 * Rebuilds a playable [SongEntity] from a history row's own snapshot.
 *
 * The timeline carries everything a track needs to be played again — remote
 * identity for catalogue entries, the song id for anything with a row in the
 * songs table. This conversion exists so a track that was streamed once and
 * never persisted is still tappable in history, instead of history showing
 * plays that can no longer be played.
 */
fun PlaybackHistoryEntity.toSongEntity(): SongEntity = SongEntity(
    id = songId,
    title = title,
    artist = artist,
    album = album,
    storageProvider = when (source) {
        MusicSource.LOCAL.name -> MusicProviderType.LOCAL.id
        else -> MusicProviderType.YOUTUBE_MUSIC.id
    },
    providerTrackId = videoId,
    artworkUrl = artworkUrl,
    coverArtPath = artworkUrl,
)
