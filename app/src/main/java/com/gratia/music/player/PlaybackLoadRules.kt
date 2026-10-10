package com.gratia.music.player

import androidx.media3.common.Player
import com.gratia.music.player.PlayerManager.PlaybackLoadState

/**
 * The load-state rules, as pure functions.
 *
 * The state machine these implement lives in [PlayerManager] — but the
 * transitions themselves are decision rules, and decision rules are where the
 * cold-start bug lived: `STATE_READY` only resolved the load state when the
 * previous state was RESOLVING, so a track prepared at startup (prepare →
 * BUFFERING → READY, paused) entered BUFFERING and never left it. The UI read
 * that as an endless remote loading spinner on a local file, and only the
 * listener's Play tap — `onIsPlayingChanged` — ever cleared it.
 *
 * The corrected rule set derives every state from the player itself, exactly
 * as the "Media3 is the source of truth" requirement asks:
 *
 * ```text
 * STATE_READY     → PLAYING if playWhenReady else PAUSED   (always, not only from RESOLVING)
 * STATE_BUFFERING → BUFFERING if the player intends to play; a paused
 *                   player that is buffering is PAUSED, not a spinner.
 *                   This one supersedes RESOLVING (a source exists by now)
 * STATE_IDLE      → IDLE (unless the resolver owns the state or an error does)
 * ```
 *
 * [PlaybackLoadState] is nested in [PlayerManager] because every existing UI
 * reader names it that way; loading this object never initialises the
 * manager, so these functions stay unit-testable without a player.
 */
object PlaybackLoadRules {

    /**
     * The next load state for a Media3 playback-state change, or `null` to
     * keep the current one.
     */
    fun onPlaybackStateChanged(
        current: PlaybackLoadState,
        newState: Int,
        playWhenReady: Boolean,
    ): PlaybackLoadState? = when (newState) {
        // A prepared source is the player's own fact. Whatever came before it
        // — a resolving walk, prepare-time buffering, a restored pause — the
        // state now follows playWhenReady. This is the transition the old
        // machine refused to make from BUFFERING, which is what left a
        // prepared, paused local track showing a loading spinner forever.
        Player.STATE_READY -> if (playWhenReady) PlaybackLoadState.PLAYING else PlaybackLoadState.PAUSED

        // Data is being fetched for a source the player already holds —
        // which is what makes this BUFFERING rather than RESOLVING, and why a
        // buffering event supersedes an in-flight resolution's display
        // (playSong clears the player before resolving, so a buffering event
        // during a walk means a source has already been handed over). It is
        // "loading" only when the player intends to produce audio though: a
        // paused track preparing — the restored session's quiet prepare, a
        // seek while paused — must present as paused, not as a network loader.
        Player.STATE_BUFFERING ->
            if (playWhenReady) PlaybackLoadState.BUFFERING else PlaybackLoadState.PAUSED

        // Idle without an error is the player at rest between commands; with
        // one, the error path owns the state. RESOLVING stays: the resolver
        // is still walking, and the player going idle is not its answer.
        Player.STATE_IDLE ->
            if (current == PlaybackLoadState.RESOLVING) null else PlaybackLoadState.IDLE

        // End-of-track belongs to the queue logic (advance/repeat/stop), not
        // to the load display.
        else -> null
    }

    /**
     * The next load state for an is-playing change, or `null` to keep the
     * current one.
     */
    fun onIsPlayingChanged(
        current: PlaybackLoadState,
        playing: Boolean,
        playWhenReady: Boolean,
    ): PlaybackLoadState? = when {
        // Audio is actually flowing. RESOLVING is not overwritten: the
        // resolver owns the state until the player accepts or rejects the
        // source, and isPlaying alone does not say which track the audio
        // belongs to.
        playing -> if (current == PlaybackLoadState.RESOLVING) null else PlaybackLoadState.PLAYING

        // Playback stopped. A PLAYING state must always come down with it —
        // that is the pair the play/pause glyph is derived from. A BUFFERING
        // state comes down too when the listener paused while the player was
        // mid-buffer: no new playback-state callback arrives for that pause
        // (the player stays BUFFERING), and without this the spinner would
        // outlive the intent to play. A pause while playWhenReady is still
        // true (suppression) keeps the buffering display — audio is wanted
        // and has not come back yet.
        !playing && current == PlaybackLoadState.PLAYING -> PlaybackLoadState.PAUSED
        !playing && current == PlaybackLoadState.BUFFERING && !playWhenReady -> PlaybackLoadState.PAUSED

        else -> null
    }
}
