package com.gratia.music.ui.screens

import com.gratia.music.provider.RemoteArtistRef

/**
 * Search relevance, as one reusable component rather than sorting logic spread
 * through the results composable (spec A).
 *
 * Two separate questions, deliberately kept apart:
 *
 *  - [relevanceScore] ranks a *row* against the query, across titles, credits and
 *    albums — the ordering the mixed "All" list depends on.
 *  - [artistNameScore] asks specifically how strongly an artist's *name* matches,
 *    which is what decides whether the Artists shelf steps above Songs.
 *
 * Keeping them separate is the fix for the artist-first requirement: an exact
 * track title and an exact artist name are different signals with different
 * consequences, and collapsing them into one number is what made a query like
 * "Radiohead" lead with unrelated songs.
 *
 * Nothing here scrapes a second service or hardcodes a name; every signal comes
 * from the catalogue response already in hand.
 */

/**
 * Relevance of a candidate row to the query, highest first.
 *
 * The ordering the "All" results depend on: an exact song title outranks a
 * prefix, a prefix outranks a contains, and only after titles do artist and
 * album matches count. This keeps "God's Plan" (exact title) above "God's Plan
 * (Remix)" albums and unrelated playlists.
 */
internal fun relevanceScore(query: String, title: String, artist: String, album: String?): Int {
    val q = normalizeForMatch(query)
    if (q.isBlank()) return 0
    val t = normalizeForMatch(title)
    val a = normalizeForMatch(artist)
    val al = normalizeForMatch(album.orEmpty())
    return when {
        t == q -> 100
        t.startsWith(q) -> 80
        t.contains(q) -> 60
        a == q -> 50
        a.startsWith(q) -> 45
        a.contains(q) -> 40
        al == q -> 30
        al.contains(q) -> 20
        else -> 10
    }
}

/**
 * How strongly an artist's *name* matches the query, on its own scale.
 *
 * Exact matches score highest, then prefix, then whole-word containment; a
 * merely incidental substring is a weak signal and scores low, so "radio"
 * surfaces "Radiohead" but not a band that happens to contain "radio" inside a
 * longer word. Case, punctuation and separators are normalised away, so
 * `"radiohead"`, `"Radiohead"` and `"radiohead "` are the same query.
 */
internal fun artistNameScore(query: String, name: String): Int {
    val q = normalizeForMatch(query)
    if (q.isBlank()) return 0
    val n = normalizeForMatch(name)
    if (n.isBlank()) return 0
    val qTokens = q.split(' ').filter { it.isNotBlank() }
    val nTokens = n.split(' ').filter { it.isNotBlank() }
    return when {
        n == q -> 100
        n.startsWith(q) -> 85
        q in nTokens || n in qTokens -> 75
        qTokens.isNotEmpty() && qTokens.all { it in nTokens } -> 70
        n.contains(q) -> 40
        else -> 0
    }
}

/**
 * True when the query is plainly an artist name *and* the catalogue agreed — a
 * strong artist-name match exists, so the Artists shelf is surfaced above Songs
 * (spec A, the "Radiohead" case).
 *
 * Deliberately conservative: a query that only loosely resembles an artist name
 * (the "Creep" case) leaves the default Songs-first order, and instead relies on
 * the associated-artist boost to float the right artist to the front of the
 * Artists shelf (spec A, the "Creep" case).
 */
internal fun isArtistFirstQuery(query: String, artists: List<RemoteArtistRef>): Boolean {
    if (normalizeForMatch(query).isBlank()) return false
    return artists.any { artistNameScore(query, it.name) >= 85 }
}

/**
 * Case- and punctuation-insensitive comparison key.
 *
 * Separators collapse to single spaces first, so `"God's   Plan!!"` and
 * `"god s plan"` produce the same key.
 */
internal fun normalizeForMatch(s: String): String =
    s.lowercase()
        .replace(Regex("[^a-z0-9 ]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
