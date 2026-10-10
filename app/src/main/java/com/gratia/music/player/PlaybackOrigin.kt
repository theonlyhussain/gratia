package com.gratia.music.player

/**
 * Where a playback session began.
 *
 * The one authoritative answer to "where did this come from?", attached to the
 * session when it starts and carried with it from then on — not inferred later
 * from whichever screen happens to be on top. The old approach asked the
 * library for the current song's *album* and printed that, which is why a track
 * chosen from Search could be described as being from somewhere it never came
 * from.
 *
 * The label is presentation, not identity: the enum is the model, [label] is
 * only what it is called on screen. Nothing should compare label strings.
 */
enum class PlaybackOrigin(
    /**
     * What the player says when asked where this came from. `null` means the
     * origin is genuinely unknown and the UI should say nothing rather than
     * guess.
     */
    val label: String?,
) {
    SEARCH("Search"),
    HOME("Home"),
    EXPLORE("Explore"),
    ARTIST("Artist"),
    ALBUM("Album"),
    PLAYLIST("Playlist"),

    /**
     * The Daily Mix collection, as a context of its own. The queue header must
     * say "Playing from Daily Mix" — the mix is a collection the listener
     * chose, not a Home shelf they happened to tap a card on, and folding it
     * into HOME is what let the queue describe a mix session as library
     * playback.
     */
    DAILY_MIX("Daily Mix"),

    LIBRARY("Your Library"),
    LOCAL_MUSIC("Local Music"),
    DOWNLOADS("Downloads"),
    FAVORITES("Favorites"),

    /** A track the AutoPlay radio chose, rather than one the listener picked. */
    AUTOPLAY("AutoPlay"),

    /** Nothing knows; the player shows no origin rather than inventing one. */
    UNKNOWN(null),

    ;

    companion object {
        fun fromName(name: String?): PlaybackOrigin =
            entries.firstOrNull { it.name == name } ?: UNKNOWN
    }
}

/**
 * The one formatter every surface uses for a playback context string — the
 * expanded player, the queue and anything else that names where playback came
 * from. `null` for an unknown origin, so a caller simply omits the line rather
 * than printing a placeholder.
 */
fun playbackContextLabel(origin: PlaybackOrigin?): String? =
    (origin ?: PlaybackOrigin.UNKNOWN).label
