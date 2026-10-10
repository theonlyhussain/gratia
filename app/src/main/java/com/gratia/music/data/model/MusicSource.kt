package com.gratia.music.data.model

import com.gratia.music.provider.MusicProviderType

/**
 * Where a track actually comes from.
 *
 * Gratia carries one [SongEntity] for every kind of music it can play — a file
 * found on the device, a track streamed from YouTube Music, and a remote track
 * that has been downloaded for offline use. The three are *not*
 * interchangeable, and the bugs that come from treating them as such are
 * subtle: a downloaded remote track looks local (it is a file), and a scanned
 * local file looks like it was "added" because it is new to the database.
 *
 * Naming the source explicitly means a shelf can declare what it will accept
 * rather than depending on accidental type assumptions:
 *
 * ```kotlin
 * Top Picks for You  → REMOTE only
 * On Device          → LOCAL + DOWNLOADED_REMOTE
 * ```
 *
 * A track's source is a pure function of fields already on [SongEntity]
 * ([SongEntity.storageProvider], [SongEntity.isDownloaded]); no new persisted
 * state is introduced.
 */
enum class MusicSource {
    /** A file on the device that Gratia did not download — the user's own music. */
    LOCAL,

    /** A YouTube Music catalogue track, streamed on demand. */
    YOUTUBE_MUSIC,

    /**
     * A YouTube Music track the user downloaded. It is a local file *and* it
     * keeps its remote identity ([SongEntity.providerTrackId]); it must never
     * be mistaken for local-original music, but it is legitimately on-device.
     */
    DOWNLOADED_REMOTE;

    /** True when this source belongs on an online discovery shelf. */
    val isOnline: Boolean
        get() = this == YOUTUBE_MUSIC || this == DOWNLOADED_REMOTE

    /** True when this source belongs on an on-device shelf. */
    val isOnDevice: Boolean
        get() = this == LOCAL || this == DOWNLOADED_REMOTE
}

/**
 * Classifies a song by where its bytes (and its identity) come from.
 *
 * Order matters: a downloaded track is checked *before* the provider, because
 * a download keeps its remote [SongEntity.storageProvider] but is no longer a
 * stream. Local files are whatever is not remote.
 */
val SongEntity.musicSource: MusicSource
    get() {
        val provider = MusicProviderType.fromId(storageProvider)
        return when {
            provider == MusicProviderType.LOCAL -> MusicSource.LOCAL
            isDownloaded -> MusicSource.DOWNLOADED_REMOTE
            else -> MusicSource.YOUTUBE_MUSIC
        }
    }

/** A streamable remote catalogue track — the only thing an online shelf may show. */
fun SongEntity.isRemoteTrack(): Boolean =
    musicSource == MusicSource.YOUTUBE_MUSIC

/** A file the user owns that Gratia did not fetch. */
fun SongEntity.isLocalTrack(): Boolean =
    musicSource == MusicSource.LOCAL

/** A remote track downloaded for offline use — remote identity, local bytes. */
fun SongEntity.isDownloadedRemoteTrack(): Boolean =
    musicSource == MusicSource.DOWNLOADED_REMOTE

/**
 * Whether this track is playing under a remote provider identity, which is what
 * the player/lyrics pipeline keys on (the stable YouTube `videoId`). True for a
 * downloaded remote track too — it is still that video.
 */
fun SongEntity.hasRemoteIdentity(): Boolean =
    musicSource.isOnline

/**
 * Whether playing this track needs no network at all.
 *
 * True for a file the phone already held and for a remote track Gratia has
 * downloaded — in both cases the bytes are on the device, so the tap resolves
 * to a `prepare()` and nothing else.
 *
 * The player uses this to decide whether a tap may show a network loader. It is
 * the one place that question is answered: a downloaded YouTube track is still
 * a YouTube track by identity, so anything keying off the provider alone would
 * put a remote spinner on a file that is already local, and a two-second false
 * wait on music the listener can hear instantly is worse than no feedback at
 * all.
 */
fun SongEntity.playsFromDevice(): Boolean =
    musicSource.isOnDevice
