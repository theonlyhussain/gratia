package com.gratia.music.ui.screens

/**
 * How much Home is allowed to pretend it knows the listener.
 *
 * The rebuild rule is that Gratia must never dress up a cold library as
 * personalisation — a fresh install has no "Top Picks for You" and no "Daily
 * Mix", because there is nothing to base one on. Which shelves Home shows is
 * therefore a function of the listening data actually on the device, not of
 * whether the app has been opened before.
 *
 * [FIRST_RUN] is the state until the listener has touched anything at all. It
 * shows discovery and the device's own music, and nothing that claims to be
 * tailored.
 */
internal enum class HomePersonalization {
    /** Nothing has been played or favourited yet. Discovery only. */
    FIRST_RUN,

    /** Some real history exists, but not enough for a mix worth building. */
    EARLY_USE,

    /** Enough plays across enough artists to build a Daily Mix. */
    PERSONALIZED,
}

/**
 * The listening signals Home actually has, which is deliberately a small,
 * countable set rather than a fuzzy "engagement score" — every field here is
 * something the UI can point at on screen.
 *
 * @param playedTracks distinct tracks with a play recorded against them.
 * @param distinctArtists distinct artists among those played tracks.
 * @param favorites tracks the listener has liked, regardless of plays.
 * @param hasRecentHistory whether anything has a last-played timestamp.
 */
internal data class HomeListeningSignals(
    val playedTracks: Int = 0,
    val distinctArtists: Int = 0,
    val favorites: Int = 0,
    val hasRecentHistory: Boolean = false,
)

/**
 * Distinct tracks that must have been played before a Daily Mix is honest to
 * show. Low enough to arrive quickly, high enough that a single play cannot
 * produce a "mix".
 */
internal const val MIN_PERSONALIZED_PLAYS = 12

/**
 * Distinct artists required alongside [MIN_PERSONALIZED_PLAYS] — a mix built
 * from one artist is a discography, not a recommendation.
 */
internal const val MIN_PERSONALIZED_ARTISTS = 4

/**
 * Resolves the state from raw signals. Pure so it can be unit-tested without a
 * device, and so the thresholds live in exactly one place.
 */
internal fun resolveHomePersonalization(signals: HomeListeningSignals): HomePersonalization = when {
    signals.playedTracks >= MIN_PERSONALIZED_PLAYS &&
        signals.distinctArtists >= MIN_PERSONALIZED_ARTISTS -> HomePersonalization.PERSONALIZED

    // Any genuine engagement at all — a play, a like, a history entry —
    // is enough to stop treating the listener as brand new.
    signals.playedTracks > 0 || signals.favorites > 0 || signals.hasRecentHistory ->
        HomePersonalization.EARLY_USE

    else -> HomePersonalization.FIRST_RUN
}
