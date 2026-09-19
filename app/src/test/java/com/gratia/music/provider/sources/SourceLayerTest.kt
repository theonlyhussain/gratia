/*
 * Copyright (C) 2026 Kushagra Singh / BitChord contributors
 * Modified for Gratia by Hussain / Gratia contributors, 2026
 * Licensed under the GNU General Public License v3.0
 *
 * Tests for the ported BitChord source layer: TrackMatcher (recording identity)
 * and SourceResolver's quality policy (worthSwapping / isBetter). The cases
 * mirror BitChord's own acceptance cases — never match on title alone, never
 * downgrade, 160 Opus does not lose to 96 AAC but does lose to 320 AAC.
 */
package com.gratia.music.provider.sources

import com.gratia.music.provider.RemoteAlbumRef
import com.gratia.music.provider.RemoteArtistRef
import com.gratia.music.provider.RemoteTrack
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceLayerTest {

    private fun track(
        title: String,
        artist: String,
        durationText: String? = null,
        album: String? = null,
        isExplicit: Boolean = false,
    ) = RemoteTrack(
        id = title,
        videoId = title,
        title = title,
        artists = listOf(RemoteArtistRef(id = null, name = artist)),
        album = album?.let { RemoteAlbumRef(id = it, name = it) },
        durationText = durationText,
        isExplicit = isExplicit,
    )

    private fun target(
        title: String,
        artist: String,
        durationSec: Int? = null,
        album: String? = null,
        isVideo: Boolean = false,
    ) = TrackMatcher.Target(
        title = title,
        artist = artist,
        durationSec = durationSec,
        album = album,
        isVideo = isVideo,
    )

    // ── TrackMatcher: identity ────────────────────────────────────────────

    @Test
    fun `same recording with packaging noise matches`() {
        val candidate = track("Paniyon Sa", "Atif Aslam, Tulsi Kumar", "4:12")
        val wanted = target("Paniyon Sa (From \"Satyamev Jayate\")", "Atif Aslam", 252)
        assertNotNull(TrackMatcher.score(candidate, wanted))
    }

    @Test
    fun `cover with same title and no shared artist is refused`() {
        val cover = track("Tum Hi Ho", "Some Other Singer", "4:18")
        val wanted = target("Tum Hi Ho", "Arijit Singh", 258)
        // No shared artist, durations differ by more than the exact-override
        // window — must not match.
        assertNull(TrackMatcher.score(cover, wanted))
    }

    @Test
    fun `live version does not match album cut`() {
        val live = track("Tum Hi Ho (Live)", "Arijit Singh", "4:40")
        val wanted = target("Tum Hi Ho", "Arijit Singh", 258)
        assertNull(TrackMatcher.score(live, wanted))
        // And the other way round.
        assertNull(TrackMatcher.score(track("Tum Hi Ho", "Arijit Singh", "4:18"), target("Tum Hi Ho (Live)", "Arijit Singh", 258)))
    }

    @Test
    fun `duration beyond the limit vetoes the match`() {
        val extended = track("Song", "Artist", "10:02")
        val wanted = target("Song", "Artist", 240)
        assertNull(TrackMatcher.score(extended, wanted))
    }

    @Test
    fun `music video timing drift is allowed with a shared artist`() {
        val video = track("Brown Rang", "Yo Yo Honey Singh", "5:31")
        val wanted = target("Brown Rang", "Yo Yo Honey Singh", 211, isVideo = true)
        assertNotNull(TrackMatcher.score(video, wanted))
    }

    @Test
    fun `album agreement beats a one-second-nearer runtime`() {
        val albumCut = track("Song", "Artist", "4:00", album = "The Album")
        val otherCut = track("Song", "Artist", "3:59", album = "Compilation")
        val wanted = target("Song", "Artist", 240)
        val ranked = TrackMatcher.ranked(listOf(otherCut, albumCut), wanted)
        assertTrue(ranked.first() === albumCut)
    }

    @Test
    fun `explicit flag mismatch vetoes when both sides state it`() {
        val clean = track("Song", "Artist", "4:00", isExplicit = false)
        val wanted = target("Song", "Artist", 240, album = null)
        // wanted.isExplicit is null here (takeIf { it } on false -> null), so
        // this must still match; the veto only fires when both state it.
        assertNotNull(TrackMatcher.score(clean, wanted))
        val explicitWanted = TrackMatcher.Target(
            title = "Song", artist = "Artist", durationSec = 240, isExplicit = true,
        )
        assertNull(TrackMatcher.score(clean, explicitWanted))
    }

    @Test
    fun `conflicting albums without a target album is a conservative miss`() {
        val a = track("Song", "Artist", "4:00", album = "Release One")
        val b = track("Song", "Artist", "4:01", album = "Release Two")
        val wanted = target("Song", "Artist", 240)
        assertTrue(TrackMatcher.hasConflictingAlbums(listOf(a, b), wanted))
    }

    @Test
    fun `severe duration mismatch is detectable`() {
        assertTrue(TrackMatcher.isSevereMismatch(209, 331))
        assertFalse(TrackMatcher.isSevereMismatch(209, 211))
    }

    // ── SourceResolver: quality policy ────────────────────────────────────

    private val opus160 = StreamFormat(codec = "opus", kbps = 160)
    private val aac96 = StreamFormat(codec = "mp4", kbps = 96)
    private val aac320 = StreamFormat(codec = "mp4", kbps = 320)
    private val flac = StreamFormat(codec = "flac")

    @Test
    fun `case A - jiosaavn 96 aac is not worth swapping off youtube opus`() {
        // (It is also refused at the source itself — see MIN_USABLE_KBPS.)
        assertFalse(SourceResolver.worthSwapping(aac96, opus160))
    }

    @Test
    fun `case B - jiosaavn 320 aac is worth swapping off youtube opus`() {
        assertTrue(SourceResolver.worthSwapping(aac320, opus160))
    }

    @Test
    fun `lossless is always worth swapping`() {
        assertTrue(SourceResolver.worthSwapping(flac, opus160))
        assertTrue(SourceResolver.worthSwapping(flac, aac320))
    }

    @Test
    fun `unknown playing floor refuses every lossy candidate`() {
        // A swap that might be a downgrade is worse than no swap at all.
        assertFalse(SourceResolver.worthSwapping(aac320, null))
    }

    @Test
    fun `small gains are not worth a seam in the audio`() {
        assertFalse(SourceResolver.worthSwapping(StreamFormat(codec = "mp4", kbps = 192), opus160))
    }

    @Test
    fun `isBetter ranks lossless over lossy and bitrate within lossy`() {
        assertTrue(SourceResolver.isBetter(flac, aac320))
        assertTrue(SourceResolver.isBetter(aac320, opus160))
        assertFalse(SourceResolver.isBetter(aac96, opus160))
    }

    @Test
    fun `sameRecordingAs demands both runtimes`() {
        assertFalse(SourceResolver.sameRecordingAs(null, 240))
        assertFalse(SourceResolver.sameRecordingAs(240, null))
        assertFalse(SourceResolver.sameRecordingAs(240, 260))
        assertTrue(SourceResolver.sameRecordingAs(240, 241))
    }

    // ── StreamFormat ──────────────────────────────────────────────────────

    @Test
    fun `stream format knows lossless by codec alone`() {
        assertTrue(flac.isLossless == true)
        assertTrue(aac320.isLossless == false)
        // Unknown codec is unknown, not false.
        assertTrue(StreamFormat(kbps = 320).isLossless == null)
    }

    @Test
    fun `summary never claims kbps for lossless`() {
        val summary = StreamFormat(codec = "flac", kbps = 900, sampleRateHz = 44100, bitDepth = 16).summary
        assertFalse(summary.contains("kbps"))
        assertTrue(summary.contains("FLAC"))
        assertTrue(summary.contains("16-bit"))
    }

    @Test
    fun `summary reports bitrate for lossy`() {
        assertTrue(aac320.summary.contains("320 kbps"))
    }
}
