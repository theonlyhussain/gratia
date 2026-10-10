package com.gratia.music.player

import androidx.media3.common.Player
import com.gratia.music.player.PlayerManager.PlaybackLoadState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The load-state machine's acceptance bar, held as tests.
 *
 * The reported cold-start bug lived in exactly one transition: a track
 * prepared at startup walks IDLE → BUFFERING → READY while paused, and the
 * old rule only resolved the load state from READY *when the previous state
 * was RESOLVING*. A restored local file therefore entered BUFFERING on
 * prepare and never left it — the UI showed the remote network spinner on a
 * local file, indefinitely, until the listener tapped Play (the only event
 * that still overwrote the state). These tests pin the corrected rules in
 * [PlaybackLoadRules]:
 *
 * - READY always derives PLAYING/PAUSED from `playWhenReady`, whatever came
 *   before it.
 * - BUFFERING means "loading" only when the player intends to produce audio;
 *   a paused player that is buffering (the quiet startup prepare, a seek
 *   while paused) presents as paused.
 * - RESOLVING stays owned by the resolver until the player accepts or
 *   rejects the source.
 */
class PlaybackLoadRulesTest {

    // ---- The cold-start regression ------------------------------------------

    @Test
    fun `a restored track that prepared while buffering settles as PAUSED at ready`() {
        // The exact reported sequence: restore → prepare (no play) →
        // STATE_BUFFERING → STATE_READY. The old machine stayed in BUFFERING
        // here, which is what drew the endless spinner on a local file.
        val next = PlaybackLoadRules.onPlaybackStateChanged(
            current = PlaybackLoadState.BUFFERING,
            newState = Player.STATE_READY,
            playWhenReady = false,
        )

        assertEquals(PlaybackLoadState.PAUSED, next)
    }

    @Test
    fun `ready with the player intending to play is PLAYING`() {
        val next = PlaybackLoadRules.onPlaybackStateChanged(
            current = PlaybackLoadState.BUFFERING,
            newState = Player.STATE_READY,
            playWhenReady = true,
        )

        assertEquals(PlaybackLoadState.PLAYING, next)
    }

    @Test
    fun `ready after a resolving walk also derives from playWhenReady`() {
        val paused = PlaybackLoadRules.onPlaybackStateChanged(
            current = PlaybackLoadState.RESOLVING,
            newState = Player.STATE_READY,
            playWhenReady = false,
        )
        val playing = PlaybackLoadRules.onPlaybackStateChanged(
            current = PlaybackLoadState.RESOLVING,
            newState = Player.STATE_READY,
            playWhenReady = true,
        )

        assertEquals(PlaybackLoadState.PAUSED, paused)
        assertEquals(PlaybackLoadState.PLAYING, playing)
    }

    // ---- Buffering is loading only when audio is wanted ----------------------

    @Test
    fun `a paused player that buffers presents as paused, not as a spinner`() {
        // The startup prepare of a restored session, and a seek while
        // paused: the player fetches data, but nothing is about to play.
        val fromIdle = PlaybackLoadRules.onPlaybackStateChanged(
            current = PlaybackLoadState.IDLE,
            newState = Player.STATE_BUFFERING,
            playWhenReady = false,
        )
        val fromPaused = PlaybackLoadRules.onPlaybackStateChanged(
            current = PlaybackLoadState.PAUSED,
            newState = Player.STATE_BUFFERING,
            playWhenReady = false,
        )

        assertEquals(PlaybackLoadState.PAUSED, fromIdle)
        assertEquals(PlaybackLoadState.PAUSED, fromPaused)
    }

    @Test
    fun `a playing player that buffers shows the buffering display`() {
        val next = PlaybackLoadRules.onPlaybackStateChanged(
            current = PlaybackLoadState.PLAYING,
            newState = Player.STATE_BUFFERING,
            playWhenReady = true,
        )

        assertEquals(PlaybackLoadState.BUFFERING, next)
    }

    @Test
    fun `buffering supersedes a resolution because a source now exists`() {
        // playSong clears the player before it starts resolving, so a
        // buffering event during a walk means a source has already been
        // handed over (the stream-refresh path does exactly this). At that
        // point BUFFERING — "holding a source that has not produced audio" —
        // is the truth; only STATE_IDLE leaves the resolver's display alone.
        val next = PlaybackLoadRules.onPlaybackStateChanged(
            current = PlaybackLoadState.RESOLVING,
            newState = Player.STATE_BUFFERING,
            playWhenReady = true,
        )

        assertEquals(PlaybackLoadState.BUFFERING, next)
    }

    // ---- Idle ----------------------------------------------------------------

    @Test
    fun `idle without a resolution or error is idle`() {
        val next = PlaybackLoadRules.onPlaybackStateChanged(
            current = PlaybackLoadState.PAUSED,
            newState = Player.STATE_IDLE,
            playWhenReady = false,
        )

        assertEquals(PlaybackLoadState.IDLE, next)
    }

    @Test
    fun `idle does not steal the resolver's state`() {
        // The player going idle is not the resolver's answer: a walk still in
        // flight keeps the RESOLVING display, which is the one case where the
        // display must not follow the player.
        val next = PlaybackLoadRules.onPlaybackStateChanged(
            current = PlaybackLoadState.RESOLVING,
            newState = Player.STATE_IDLE,
            playWhenReady = false,
        )

        assertNull(next)
    }

    @Test
    fun `ended is owned by the queue logic, not the load display`() {
        val next = PlaybackLoadRules.onPlaybackStateChanged(
            current = PlaybackLoadState.PLAYING,
            newState = Player.STATE_ENDED,
            playWhenReady = false,
        )

        assertNull(next)
    }

    // ---- isPlaying transitions -----------------------------------------------

    @Test
    fun `audio flowing ends every wait except an in-flight resolution`() {
        val playing = PlaybackLoadRules.onIsPlayingChanged(
            current = PlaybackLoadState.PAUSED,
            playing = true,
            playWhenReady = true,
        )
        val resolving = PlaybackLoadRules.onIsPlayingChanged(
            current = PlaybackLoadState.RESOLVING,
            playing = true,
            playWhenReady = true,
        )

        assertEquals(PlaybackLoadState.PLAYING, playing)
        assertNull(resolving)
    }

    @Test
    fun `playback stopping always comes down from PLAYING`() {
        val next = PlaybackLoadRules.onIsPlayingChanged(
            current = PlaybackLoadState.PLAYING,
            playing = false,
            playWhenReady = false,
        )

        assertEquals(PlaybackLoadState.PAUSED, next)
    }

    @Test
    fun `pausing while buffering ends the spinner when the listener paused`() {
        // No new playback-state callback arrives for a pause mid-buffer —
        // the player stays BUFFERING — so without this rule the spinner
        // would outlive the intent to play.
        val next = PlaybackLoadRules.onIsPlayingChanged(
            current = PlaybackLoadState.BUFFERING,
            playing = false,
            playWhenReady = false,
        )

        assertEquals(PlaybackLoadState.PAUSED, next)
    }

    @Test
    fun `suppressed playback keeps the buffering display`() {
        // playWhenReady still true (transient focus loss): audio is wanted
        // and has not come back yet — the buffering display is the truth.
        val next = PlaybackLoadRules.onIsPlayingChanged(
            current = PlaybackLoadState.BUFFERING,
            playing = false,
            playWhenReady = true,
        )

        assertNull(next)
    }
}
