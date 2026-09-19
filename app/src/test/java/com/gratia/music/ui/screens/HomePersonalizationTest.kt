package com.gratia.music.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the Home personalisation thresholds, which are the rule that stops
 * Gratia showing a fake "Daily Mix" to a listener who has just installed it.
 */
class HomePersonalizationTest {

    @Test
    fun noDataAtAllIsFirstRun() {
        assertEquals(
            HomePersonalization.FIRST_RUN,
            resolveHomePersonalization(HomeListeningSignals()),
        )
    }

    @Test
    fun aFavoriteAloneIsEnoughToLeaveFirstRun() {
        assertEquals(
            HomePersonalization.EARLY_USE,
            resolveHomePersonalization(HomeListeningSignals(favorites = 1)),
        )
    }

    @Test
    fun aSinglePlayIsEarlyUseNotPersonalized() {
        assertEquals(
            HomePersonalization.EARLY_USE,
            resolveHomePersonalization(
                HomeListeningSignals(playedTracks = 1, distinctArtists = 1, hasRecentHistory = true),
            ),
        )
    }

    @Test
    fun manyPlaysFromOneArtistIsStillNotPersonalized() {
        // A mix built from a single artist is a discography, not a recommendation.
        assertEquals(
            HomePersonalization.EARLY_USE,
            resolveHomePersonalization(
                HomeListeningSignals(playedTracks = 50, distinctArtists = 1, hasRecentHistory = true),
            ),
        )
    }

    @Test
    fun manyArtistsWithTooFewPlaysIsStillNotPersonalized() {
        assertEquals(
            HomePersonalization.EARLY_USE,
            resolveHomePersonalization(
                HomeListeningSignals(playedTracks = 3, distinctArtists = 3, hasRecentHistory = true),
            ),
        )
    }

    @Test
    fun enoughPlaysAcrossEnoughArtistsBecomesPersonalized() {
        assertEquals(
            HomePersonalization.PERSONALIZED,
            resolveHomePersonalization(
                HomeListeningSignals(
                    playedTracks = MIN_PERSONALIZED_PLAYS,
                    distinctArtists = MIN_PERSONALIZED_ARTISTS,
                    hasRecentHistory = true,
                ),
            ),
        )
    }
}
