package com.gratia.music.player

/**
 * What counts as a real play.
 *
 * Kept as pure functions, like [PlaybackLoadRules] and [RadioQueue], so the
 * policy is pinned by a unit test rather than by whatever `PlayerManager`
 * happens to do at a call site. The rule it replaces incremented the play count
 * at the moment the listener *tapped* a song, which made "Most Played" a list of
 * everything that had ever been opened, and inflated every recommendation that
 * read play counts (spec 14).
 *
 * A play must be heard:
 *  - a track that reaches its end is always one play, however short;
 *  - a track skipped after [PLAY_COUNT_MIN_SECONDS] of listening is one play;
 *  - a pause is never a play on its own.
 *
 * The count is applied once per finished session, at the point the session's
 * listened time is already known — never at selection time.
 */
object ListeningRules {

    /** How much of a track must be heard before a skipped one counts as a play. */
    const val PLAY_COUNT_MIN_SECONDS = 30L

    /**
     * Whether a finished playback session earns one play.
     *
     * [completed] is a track that ran to the end, [skipped] one the listener
     * moved past. Both false is a pause (or an abandoned session), which does
     * not count.
     */
    fun countsAsPlay(completed: Boolean, skipped: Boolean, listenedSeconds: Long): Boolean =
        completed || (skipped && listenedSeconds >= PLAY_COUNT_MIN_SECONDS)
}
