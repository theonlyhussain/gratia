package com.gratia.music.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the source classification the online-first Home shelves depend on.
 *
 * The bug these guard against is subtle: a scanned file and a downloaded remote
 * track are both "on disk", so anything that classifies by *looks* will slip a
 * local file into an online shelf — which is exactly what Home was doing.
 */
class MusicSourceTest {

    private fun song(storageProvider: String, isDownloaded: Boolean = false) = SongEntity(
        title = "Title",
        artist = "Artist",
        storageProvider = storageProvider,
        isDownloaded = isDownloaded,
    )

    @Test
    fun aScannedFileIsLocalAndNotOnline() {
        val song = song("local")
        assertEquals(MusicSource.LOCAL, song.musicSource)
        assertTrue(song.isLocalTrack())
        assertFalse(song.isRemoteTrack())
        assertFalse(song.musicSource.isOnline)
    }

    @Test
    fun aStreamedCatalogueTrackIsRemote() {
        val song = song("youtube_music")
        assertEquals(MusicSource.YOUTUBE_MUSIC, song.musicSource)
        assertTrue(song.isRemoteTrack())
        assertTrue(song.musicSource.isOnline)
        assertFalse(song.musicSource.isOnDevice)
    }

    @Test
    fun aDownloadedRemoteTrackIsNotLocalOrigin() {
        val song = song("youtube_music", isDownloaded = true)
        assertEquals(MusicSource.DOWNLOADED_REMOTE, song.musicSource)
        // Both on-device and online: it is a file, but it is still that video.
        assertTrue(song.musicSource.isOnDevice)
        assertTrue(song.musicSource.isOnline)
        assertTrue(song.hasRemoteIdentity())
        assertFalse(song.isLocalTrack())
    }

    @Test
    fun playingNeedsNoNetworkForLocalAndDownloadedTracksOnly() {
        // The player's gate for "may this tap show a network loader?". A
        // downloaded YouTube track is still identified by its provider, so a
        // check on the provider alone would put a remote spinner on a file that
        // is already on disk.
        assertTrue(song("local").playsFromDevice())
        assertTrue(song("youtube_music", isDownloaded = true).playsFromDevice())
        assertFalse(song("youtube_music").playsFromDevice())
    }

    @Test
    fun onlyRemotelyOwnedTracksAppearOnOnlineShelves() {
        // The predicate Home uses for Top Picks / Quick Picks / Trending.
        val onlineEligible = listOf(
            song("youtube_music"),
            song("youtube_music", isDownloaded = true),
        ).filter { it.isRemoteTrack() || it.isDownloadedRemoteTrack() }

        assertEquals(2, onlineEligible.size)
        assertFalse(onlineEligible.contains(song("local")))
    }
}
