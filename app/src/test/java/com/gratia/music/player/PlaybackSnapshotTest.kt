package com.gratia.music.player

import com.gratia.music.data.model.SongEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The cold-start restoration contract, held as tests.
 *
 * The reported bug: play a local MP3, close the app, reopen it — and the
 * local file presented itself as if it were being fetched from YouTube
 * (indefinite loading spinner, Play required before anything happened). The
 * fix has three parts, and these tests hold two of them as pure rules:
 *
 * 1. [PlaybackSnapshot] — the persisted state is the *whole* session (full
 *    rows, position, origin, autoplay tier), so nothing is rediscovered and
 *    restoration never depends on Room holding every row.
 * 2. [restorableSourceOf] — the source decides the path exactly once:
 *    LOCAL → local URI (recorded path as the stable fallback),
 *    DOWNLOADED → downloaded file (remote resolution only if the file is
 *    genuinely gone), REMOTE → deferred resolution. A source that is gone
 *    is [RestorableSource.Unavailable], never silently re-resolved.
 *
 * (The third part — the load-state machine that used to get stuck on
 * BUFFERING — is held in [PlaybackLoadRulesTest].)
 */
class PlaybackSnapshotTest {

    private fun localSong(
        localUri: String? = "content://media/external/audio/media/42",
        storagePath: String? = "/storage/emulated/0/Music/song.mp3",
    ) = SongEntity(
        id = "local-uuid-from-path",
        title = "Local song",
        artist = "Local artist",
        album = "Local album",
        storageProvider = "local",
        localUri = localUri,
        storagePath = storagePath,
        durationMs = 180_000L,
        coverArtPath = "/data/user/0/com.gratia.music/covers/local-uuid-from-path.jpg",
    )

    private fun remoteSong(videoId: String = "abc123") = SongEntity(
        id = "ytm_$videoId",
        title = "Remote song",
        artist = "Remote artist",
        storageProvider = "youtube_music",
        providerTrackId = videoId,
        artworkUrl = "https://example.com/art.jpg",
    )

    private fun downloadedSong(videoId: String = "dwn1") = SongEntity(
        id = "ytm_$videoId",
        title = "Downloaded song",
        artist = "Remote artist",
        storageProvider = "youtube_music",
        providerTrackId = videoId,
        isDownloaded = true,
        downloadPath = "/data/user/0/com.gratia.music/downloads/$videoId.m4a",
    )

    // ---- The persisted snapshot is the whole session ------------------------

    @Test
    fun `snapshot round-trips the complete session`() {
        val snapshot = PlaybackSnapshot(
            currentSong = localSong(),
            queue = listOf(localSong(), remoteSong(), downloadedSong()),
            positionMs = 95_000L,
            origin = PlaybackOrigin.LOCAL_MUSIC,
            autoplayIds = setOf("ytm_r1", "ytm_r2"),
            savedAtMs = 1_726_000_000_000L,
        )

        val decoded = PlaybackSnapshot.decode(snapshot.encode())

        assertEquals(snapshot, decoded)
    }

    @Test
    fun `the restored local track keeps its source identity and artwork`() {
        // The old persistence stored only the row id and re-looked the song
        // up in Room. The snapshot itself must carry what "this is a local
        // file" means: the provider, the MediaStore URI, the file path and
        // the artwork, so a restart never has to ask YouTube for anything.
        val song = localSong()
        val decoded = PlaybackSnapshot.decode(
            PlaybackSnapshot(currentSong = song, queue = listOf(song), positionMs = 10L).encode()
        )

        assertNotNull(decoded)
        val restored = decoded!!.currentSong!!
        assertEquals("local", restored.storageProvider)
        assertEquals(song.localUri, restored.localUri)
        assertEquals(song.storagePath, restored.storagePath)
        assertEquals(song.coverArtPath, restored.coverArtPath)
    }

    @Test
    fun `decode is null for absent or malformed state`() {
        assertNull(PlaybackSnapshot.decode(null))
        assertNull(PlaybackSnapshot.decode(""))
        assertNull(PlaybackSnapshot.decode("not json at all"))
    }

    @Test
    fun `decode tolerates fields a newer build wrote`() {
        val fromNewerBuild =
            """{"currentSong":{"id":"x","title":"t","artist":"a","someFutureField":7},""" +
                """"queue":[],"positionMs":5,"origin":"LOCAL_MUSIC"}"""

        val decoded = PlaybackSnapshot.decode(fromNewerBuild)

        assertNotNull(decoded)
        assertEquals("x", decoded!!.currentSong?.id)
        assertEquals(5L, decoded.positionMs)
    }

    @Test
    fun `an empty snapshot decodes to a cleared player`() {
        val decoded = PlaybackSnapshot.decode(
            PlaybackSnapshot(currentSong = null, queue = emptyList()).encode()
        )

        assertNotNull(decoded)
        assertNull(decoded!!.currentSong)
        assertTrue(decoded.queue.isEmpty())
    }

    // ---- The source decides the restoration path ----------------------------

    @Test
    fun `a local track restores from its MediaStore URI`() {
        val source = restorableSourceOf(
            song = localSong(),
            localUriUsable = true,
            downloadFileExists = false,
        )

        assertEquals(
            "content://media/external/audio/media/42",
            (source as RestorableSource.OnDevice).mediaUri,
        )
    }

    @Test
    fun `a local track with a stale URI falls back to its recorded file path`() {
        // A re-scan can change the MediaStore id while the file is still on
        // disk. The recorded path is the stable identity for exactly this.
        val source = restorableSourceOf(
            song = localSong(localUri = "content://media/external/audio/media/999"),
            localUriUsable = false,
            downloadFileExists = false,
        )

        assertEquals(
            "/storage/emulated/0/Music/song.mp3",
            (source as RestorableSource.OnDevice).mediaUri,
        )
    }

    @Test
    fun `a local track whose file is gone is unavailable, not remote`() {
        // The one truthfully dead end. Falling silently into the remote
        // resolver here would replace the listener's own file with whatever
        // a search happens to return — worse than showing nothing.
        val source = restorableSourceOf(
            song = localSong(localUri = "content://media/external/audio/media/999", storagePath = null),
            localUriUsable = false,
            downloadFileExists = false,
        )

        assertEquals(RestorableSource.Unavailable, source)
    }

    @Test
    fun `a downloaded track restores from its downloaded file`() {
        val source = restorableSourceOf(
            song = downloadedSong(),
            localUriUsable = false,
            downloadFileExists = true,
        )

        assertEquals(
            "/data/user/0/com.gratia.music/downloads/dwn1.m4a",
            (source as RestorableSource.OnDevice).mediaUri,
        )
    }

    @Test
    fun `a downloaded track whose file was deleted falls back to remote resolution`() {
        val source = restorableSourceOf(
            song = downloadedSong(),
            localUriUsable = false,
            downloadFileExists = false,
        )

        assertEquals(RestorableSource.NeedsNetwork, source)
    }

    @Test
    fun `a remote track resolves remotely`() {
        val source = restorableSourceOf(
            song = remoteSong(),
            localUriUsable = true, // irrelevant for a streaming track
            downloadFileExists = false,
        )

        assertEquals(RestorableSource.NeedsNetwork, source)
    }
}
