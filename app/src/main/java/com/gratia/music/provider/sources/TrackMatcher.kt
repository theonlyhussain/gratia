/*
 * Copyright (C) 2026 Kushagra Singh / Gratia contributors
 * Modified for Gratia by Hussain / Gratia contributors, 2026
 * Licensed under the GNU General Public License v3.0
 *
 * Source: Gratia data/sources/TrackMatcher.kt.
 * https://github.com/kushagrasinghx/Gratia (GPLv3)
 *
 * Modifications for Gratia: the model the matcher judges is Gratia's
 * [RemoteTrack]; documentation and matching logic carried over unchanged.
 */
package com.gratia.music.provider.sources

import com.gratia.music.provider.RemoteTrack
import java.util.Locale
import kotlin.math.abs

/**
 * Decides whether one catalogue's track is the same recording as another's,
 * and what to ask that catalogue for in the first place.
 *
 * Split out of [SourceResolver] because it is the only part of the source
 * layer that is a judgement rather than plumbing, and because both of its
 * failure modes are silent:
 *
 *  - **Too loose** and the wrong recording plays under the right title — a
 *    cover, a remix, an hour-long loop — with nothing on screen to say so.
 *  - **Too strict** and the source the user enabled is quietly never used.
 *
 * The way out of both is to be explicit about *what part of a title carries
 * identity*. Services disagree constantly about the packaging — the film a
 * song is from, the "Official Audio" tag, which of the credited singers make
 * it into the title — and agree about the recording underneath. So the title
 * is taken apart into three pieces:
 *
 *  - [TitleParts.words], the title proper. Must agree exactly.
 *  - [TitleParts.versions], the words that mean *a different take* — remix,
 *    live, acoustic. Must agree exactly, in both directions: "Song (Live)"
 *    is not "Song", and neither is the other way round.
 *  - [TitleParts.context], everything else thrown away with the brackets.
 *    Never a veto, only a tie-break.
 *
 * Artist and duration are then checked against that: a shared credit is
 * required when both sides name one, and a runtime far from the one asked for
 * rules a candidate out however well its title reads.
 */
object TrackMatcher {

    /** The recording being looked for, as much of it as the queue knows. */
    data class Target(
        val title: String,
        val artist: String = "",
        /** Runtime in whole seconds; null when the queue row never carried one. */
        val durationSec: Int? = null,
        /** Release name, when the queue knows it. Used to separate catalogue collisions. */
        val album: String? = null,
        /** Explicit/clean edition, null when the originating catalogue did not say. */
        val isExplicit: Boolean? = null,
        /** Music-video timing includes visual intros/outros that catalogue audio omits. */
        val isVideo: Boolean = false,
    )

    fun targetOf(track: RemoteTrack) = Target(
        title = track.title,
        artist = track.artistDisplay,
        durationSec = track.durationText.secondsOf(),
        album = track.album?.name,
        isExplicit = track.isExplicit.takeIf { it },
        isVideo = false,
    )

    // ── Asking ──────────────────────────────────────────────────────────────

    /**
     * What to put to a source's search box, best query first.
     *
     * The raw title is deliberately *not* one of them. A YouTube title carries
     * the packaging — `Paniyon Sa (From "Satyamev Jayate")` — and handing that
     * verbatim to a catalogue that lists the track as `Paniyon Sa` is asking it
     * to match on words it has never stored.
     *
     * Two queries, not one: the second drops the artist, for the catalogues
     * that credit a track to the composer or the film rather than the singer.
     */
    fun queries(target: Target): List<String> {
        val title = searchableTitle(target.title, target.artist)
        if (title.isBlank()) return emptyList()
        val artist = primaryArtist(target.artist)
        if (artist.isBlank()) return listOf(title)
        return listOf("$title $artist", title)
    }

    /** The title with the packaging taken off, version markers kept. */
    internal fun searchableTitle(title: String, artist: String = ""): String =
        parseTitle(title, artist).let { (it.words + it.versions).joinToString(" ") }

    /** The first credited artist — who a catalogue is most likely to file the track under. */
    internal fun primaryArtist(artist: String): String =
        artist.lowercase(Locale.ROOT).split(ARTIST_SEPARATORS).firstOrNull()?.trim().orEmpty()

    /** Whether both credits name at least one of the same artists. */
    internal fun sharesArtist(wanted: String, got: String): Boolean {
        val want = artistNames(wanted)
        val have = artistNames(got)
        return want.isNotEmpty() && have.isNotEmpty() &&
            want.any { w -> have.any { h -> sameArtist(w, h) } }
    }

    // ── Judging ─────────────────────────────────────────────────────────────

    /**
     * The best of [candidates] that is genuinely [target], or null if none is.
     *
     * Best, not first — a search routinely answers with the single, the album
     * cut, a sped-up edit and a karaoke version, and taking the first
     * acceptable one lets somebody else's search ranking decide which copy
     * plays.
     */
    fun best(candidates: List<RemoteTrack>, target: Target): RemoteTrack? =
        ranked(candidates, target).firstOrNull()

    /**
     * Every candidate that really is [target], most confident first.
     *
     * The whole list rather than the winner, because identity is not the only
     * question worth asking of it: two catalogues can both genuinely hold a
     * recording and offer it at different qualities, and that choice belongs
     * to [SourceResolver], which knows what was asked for.
     */
    fun ranked(candidates: List<RemoteTrack>, target: Target): List<RemoteTrack> =
        candidates
            .mapNotNull { candidate -> score(candidate, target)?.let { candidate to it } }
            // A runtime-identical cover is only a last-resort explanation for
            // catalogues crediting the same master differently. If this search
            // also returned anything carrying the requested artist, there is
            // no reason to keep the different-artist rows in contention.
            .let { scored ->
                val credited = scored.filter { (candidate, _) ->
                    artistScore(target.artist, candidate.artistDisplay) != null
                }
                credited.ifEmpty { scored }
            }
            .sortedByDescending { it.second }
            .map { it.first }

    /**
     * How confident this is the same recording, or null when it is not one.
     *
     * Null is the common answer and the safe one: it costs a source its turn,
     * and the next source — ultimately YouTube, which by definition has the
     * track — still plays what the user asked for.
     */
    fun score(candidate: RemoteTrack, target: Target): Int? {
        val wanted = parseTitle(target.title, target.artist)
        val got = parseTitle(candidate.title, candidate.artistDisplay)
        if (wanted.core.isEmpty() || got.core.isEmpty()) return null
        if (wanted.core != got.core) return null
        // Direction matters both ways round: asking for the album cut must not
        // land on the live take, and asking for the live take must not land on
        // the album cut.
        if (wanted.versions != got.versions) return null

        val creditedArtist = artistScore(target.artist, candidate.artistDisplay)
        val duration = durationScore(
            target.durationSec,
            candidate.durationText.secondsOf(),
            allowVideoDrift = creditedArtist != null,
        ) ?: return null
        val artist = creditedArtist
            // The credits don't merely differ in spelling, they name different
            // people — and sometimes that is because they are describing the
            // same recording from different ends of it (composer credit on
            // one service, singer credit on another). What breaks the tie is
            // length: two recordings that share an exact title and agree on
            // their runtime to the second are the same master. An exact
            // runtime is allowed to stand in for a shared credit, and *only*
            // an exact one. It still scores below a genuine credit match.
            ?: CREDITS_DISAGREE.takeIf {
                !target.isVideo && withinSeconds(candidate, target, CREDIT_OVERRIDE_SEC)
            }
            ?: return null
        val explicit = explicitScore(target.isExplicit, candidate.isExplicit.takeIf { it }) ?: return null
        return BASE + artist + duration + albumScore(target.album, candidate.album?.name) + explicit +
            contextScore(wanted, got)
    }

    /**
     * Whether otherwise valid rows describe more than one release while the
     * requested track gives us no release with which to choose between them.
     *
     * JioSaavn has catalogue collisions where title and artist are identical
     * but the audio is not. Picking the runtime-nearest row is unsafe there:
     * a different recording can be only a second nearer than the wanted one.
     * The caller uses this as a conservative source miss and leaves the track
     * on YouTube instead of guessing.
     */
    fun hasConflictingAlbums(candidates: List<RemoteTrack>, target: Target): Boolean {
        if (!target.album.isNullOrBlank()) return false
        val comparable = if (target.durationSec != null) {
            candidates.filter { withinSeconds(it, target, DURATION_LIMIT_SEC) }
        } else {
            candidates
        }
        if (target.durationSec != null && comparable.isEmpty()) return false
        return comparable.mapNotNull { albumKey(it.album?.name) }.distinct().size > 1
    }

    /**
     * Resolves a JioSaavn release collision only when one candidate is plainly
     * more specifically credited than every other close-duration candidate.
     */
    fun uniquelyMostCreditedCloseMatch(candidates: List<RemoteTrack>, target: Target): RemoteTrack? {
        val close = candidates.filter { withinSeconds(it, target, DURATION_LIMIT_SEC) }
        if (close.size < 2) return null
        val ranked = close.map { it to artistNames(it.artistDisplay).size }
        val topCredits = ranked.maxOfOrNull { it.second } ?: return null
        if (topCredits < 2) return null
        val winners = ranked.filter { it.second == topCredits }.map { it.first }
        return winners.singleOrNull()
    }

    /**
     * Whether [candidate] states a runtime, and one within [seconds] of
     * [target]'s. Both halves are requirements — an unstated runtime is a
     * candidate that cannot be checked.
     */
    fun withinSeconds(candidate: RemoteTrack, target: Target, seconds: Int): Boolean {
        val wanted = target.durationSec ?: return false
        val got = candidate.durationText.secondsOf() ?: return false
        return abs(wanted - got) <= seconds
    }

    /** Kept for callers that only want a yes or no — and for tests. */
    fun matches(
        candidate: RemoteTrack,
        title: String,
        artist: String,
        durationSec: Int? = null,
    ): Boolean = score(candidate, Target(title, artist, durationSec)) != null

    // ── Title ───────────────────────────────────────────────────────────────

    /**
     * A title split into the part that is the recording's identity and the
     * parts that are the listing's.
     */
    internal data class TitleParts(
        /** The title proper, lowercased, one entry per word. */
        val words: List<String>,
        /** [words] with everything but letters and digits removed — what identity is compared on. */
        val core: String,
        /** Markers that mean a different take of the same song: `remix`, `live`, `acoustic`. */
        val versions: Set<String>,
        /** Words dropped with the packaging. A hint for scoring, never a veto. */
        val context: Set<String>,
    )

    internal fun parseTitle(raw: String, artist: String = ""): TitleParts {
        val versions = sortedSetOf<String>()
        val context = mutableSetOf<String>()
        var text = raw.lowercase(Locale.ROOT).replace("&", " and ")

        // Bracketed asides, innermost first: "(From "Satyamev Jayate")",
        // "[Official Audio]", "(Live at Wembley)".
        repeat(BRACKET_PASSES) {
            if (!BRACKETED.containsMatchIn(text)) return@repeat
            text = BRACKETED.replace(text) { match ->
                classify(match.groupValues[1], versions, context)
                " "
            }
        }
        // An unbalanced bracket — a title truncated mid-aside — takes the rest
        // of the line with it rather than leaving half an aside in the core.
        text.indexOfFirst { it == '(' || it == '[' }.takeIf { it >= 0 }?.let { open ->
            classify(text.substring(open), versions, context)
            text = text.substring(0, open)
        }

        // Dash- and pipe-separated tails. The head is normally the title, but
        // the "Artist - Title" upload convention inverts that, so a head that
        // is just the artist's name hands over to the tail instead of eating it.
        repeat(DASH_PASSES) {
            val dash = DASH.find(text) ?: return@repeat
            val head = text.substring(0, dash.range.first)
            val tail = text.substring(dash.range.last + 1)
            text = if (isArtistName(head, artist)) {
                classify(head, versions, context)
                tail
            } else {
                classify(tail, versions, context)
                head
            }
        }

        // A feat. credit belongs to the artist field wherever a catalogue
        // chooses to print it.
        text = text.replace(FEATURING, " ")

        var words = text.split(WORD_SPLIT)
            .map { it.replace(NON_ALNUM, "") }
            .filter { it.isNotEmpty() && it !in JOINING_WORDS }
        // "Paniyon Sa Full Song", "Tum Hi Ho Audio" — an upload's trailing
        // label, printed without brackets to hang it on. Never stripped down
        // to nothing: a track really called "Song" keeps its name.
        while (words.size > 1 && words.last() in TRAILING_NOISE) {
            words = words.dropLast(1)
        }

        return TitleParts(
            words = words,
            core = words.joinToString(""),
            versions = versions,
            context = context,
        )
    }

    /**
     * Files one dropped segment under [versions] or [context].
     *
     * A segment naming a take — `Remix`, `Live at Wembley`, `Slowed + Reverb` —
     * is identity and is kept. Everything else is packaging. The phrases in
     * [NEUTRAL_SEGMENTS] are the exceptions that read like takes and aren't.
     */
    private fun classify(
        segment: String,
        versions: MutableSet<String>,
        context: MutableSet<String>,
    ) {
        val words = segment.split(WORD_SPLIT)
            .map { it.replace(NON_ALNUM, "") }
            .filter { it.isNotEmpty() }
        if (words.isEmpty()) return
        if (words.joinToString("") in NEUTRAL_SEGMENTS) return
        val marks = words.filter { it in VERSION_WORDS }
        if (marks.isNotEmpty()) {
            versions += marks
            return
        }
        context += words.filter { it.length > 2 && it !in NOISE_WORDS }
    }

    /** Whether [text] is nothing but (part of) [artist] — the "Artist - Title" upload shape. */
    private fun isArtistName(text: String, artist: String): Boolean {
        if (artist.isBlank()) return false
        val words = text.split(WORD_SPLIT).map { it.replace(NON_ALNUM, "") }.filter { it.isNotEmpty() }
        if (words.isEmpty()) return false
        val credited = artist.lowercase(Locale.ROOT).split(WORD_SPLIT)
            .map { it.replace(NON_ALNUM, "") }
            .filter { it.isNotEmpty() }
            .toSet()
        return words.all { it in credited }
    }

    // ── Artist ──────────────────────────────────────────────────────────────

    /**
     * Points for the credit agreeing, or null when it disagrees.
     *
     * A side with no credit at all scores zero rather than failing: there is
     * nothing to disagree with, and the title has already had to match exactly.
     */
    private fun artistScore(wanted: String, got: String): Int? {
        val want = artistNames(wanted)
        val have = artistNames(got)
        if (want.isEmpty() || have.isEmpty()) return 0
        val shared = sharesArtist(wanted, got)
        if (!shared) return null
        return if (want == have) ARTIST_EXACT else ARTIST_SHARED
    }

    /**
     * The credited artists, each as its own list of words.
     *
     * Words rather than one run-together string, so that containment is
     * checked on whole names: "Queen" is inside "Queensrÿche" as text and is
     * not one of its artists.
     */
    internal fun artistNames(value: String): Set<List<String>> = value
        .lowercase(Locale.ROOT)
        .split(ARTIST_SEPARATORS)
        .map { name ->
            name.split(WORD_SPLIT)
                .map { it.replace(NON_ALNUM, "") }
                .filter { it.length > 1 }
        }
        .filter { it.isNotEmpty() }
        .toSet()

    private fun sameArtist(a: List<String>, b: List<String>) = runOf(a, b) || runOf(b, a)

    /** Whether [outer] contains [inner] as a run of whole words. */
    private fun runOf(outer: List<String>, inner: List<String>): Boolean {
        if (inner.isEmpty() || inner.size > outer.size) return false
        return (0..outer.size - inner.size).any { at ->
            outer.subList(at, at + inner.size) == inner
        }
    }

    // ── Duration ────────────────────────────────────────────────────────────

    /**
     * Points for the runtimes agreeing, or null when they are too far apart to
     * be the same recording. Only consulted when both sides state a runtime.
     */
    private fun durationScore(wanted: Int?, got: Int?, allowVideoDrift: Boolean = false): Int? {
        if (wanted == null || got == null) return 0
        val drift = abs(wanted - got)
        return when {
            drift > DURATION_LIMIT_SEC && allowVideoDrift && drift <= VIDEO_DURATION_LIMIT_SEC -> 0
            drift > DURATION_LIMIT_SEC -> null
            drift <= DURATION_TIGHT_SEC -> DURATION_TIGHT
            else -> DURATION_LOOSE
        }
    }

    // ── Album ───────────────────────────────────────────────────────────────

    /**
     * Exact release agreement is deliberately stronger than the difference
     * between a tight and a loose runtime. A disagreement is not a veto:
     * compilations and reissues can contain the same master.
     */
    private fun albumScore(wanted: String?, got: String?): Int {
        val want = albumKey(wanted) ?: return 0
        val have = albumKey(got) ?: return 0
        return if (want == have) ALBUM_EXACT else 0
    }

    /**
     * Clean and uncensored editions can be otherwise metadata-identical. When
     * both catalogues state the flag they must agree; an unknown source is not
     * rejected because it has made no contradictory claim.
     */
    private fun explicitScore(wanted: Boolean?, got: Boolean?): Int? = when {
        wanted == null || got == null -> 0
        wanted != got -> null
        else -> EXPLICIT_EXACT
    }

    /** Punctuation, spacing and a trailing edition label are catalogue formatting, not release identity. */
    private fun albumKey(value: String?): String? {
        var text = value?.lowercase(Locale.ROOT)?.trim().orEmpty()
        if (text.isEmpty()) return null
        repeat(BRACKET_PASSES) { text = BRACKETED.replace(text, " ") }
        val words = text.split(WORD_SPLIT)
            .map { it.replace(NON_ALNUM, "") }
            .filter { it.isNotEmpty() && it !in ALBUM_NOISE_WORDS }
        return words.joinToString("").takeIf { it.isNotEmpty() }
    }

    /** "3:45" or "1:02:03" as whole seconds; null for anything else. */
    internal fun secondsOf(text: String?): Int? {
        val parts = text?.trim()?.split(':')?.takeIf { it.size in 2..3 } ?: return null
        val numbers = parts.map { it.trim().toIntOrNull() ?: return null }
        return numbers.fold(0) { total, part -> total * 60 + part }.takeIf { it > 0 }
    }

    private fun String?.secondsOf(): Int? = TrackMatcher.secondsOf(this)

    // ── Context ─────────────────────────────────────────────────────────────

    /** A nudge when both listings mention the same film or album in their asides. */
    private fun contextScore(wanted: TitleParts, got: TitleParts): Int =
        if (wanted.context.any { it in got.context }) CONTEXT_SHARED else 0

    // ── Weights ─────────────────────────────────────────────────────────────

    /** Everything that reaches scoring has already matched on title and version. */
    private const val BASE = 100
    private const val ARTIST_EXACT = 25
    private const val ARTIST_SHARED = 10

    /** Carried by a match the runtime vouched for rather than the credit. A penalty, not a pass. */
    private const val CREDITS_DISAGREE = -30

    /** How exactly two runtimes must agree before that is allowed to stand in for a shared credit. */
    private const val CREDIT_OVERRIDE_SEC = 2
    private const val DURATION_TIGHT = 40
    private const val DURATION_LOOSE = 15
    private const val ALBUM_EXACT = 35
    private const val EXPLICIT_EXACT = 20
    private const val CONTEXT_SHARED = 20

    /** Within this many seconds is the same master, allowing for trimmed silence. */
    private const val DURATION_TIGHT_SEC = 3

    /**
     * Past this, two tracks sharing a title are not sharing a recording.
     * Wide enough for a fade or an intro a service trims differently, narrow
     * enough to rule out an extended cut or a full-album upload.
     */
    const val DURATION_LIMIT_SEC = 30

    /**
     * Whether two runtimes differ by more than [DURATION_LIMIT_SEC], meaning
     * they cannot be the same recording.
     */
    fun isSevereMismatch(expectedSec: Int?, actualSec: Int?): Boolean {
        if (expectedSec == null || actualSec == null) return false
        return abs(actualSec - expectedSec) > DURATION_LIMIT_SEC
    }

    /** Visual intros/outros can make the video substantially longer than its audio master. */
    private const val VIDEO_DURATION_LIMIT_SEC = 90

    private const val BRACKET_PASSES = 3
    private const val DASH_PASSES = 3

    private val BRACKETED = Regex("""[(\[]([^()\[\]]*)[)\]]""")
    private val DASH = Regex("""\s+[-–—|]+\s+""")
    private val FEATURING = Regex("""\b(feat|ft|featuring|with)\b.*""")
    private val WORD_SPLIT = Regex("""[\s.·]+""")
    private val NON_ALNUM = Regex("""[^a-z0-9]""")
    private val ARTIST_SEPARATORS =
        Regex("""\s*(?:[,&/;·|]|\band\b|\bx\b|\bvs\.?\b|\bfeat\.?\b|\bft\.?\b|\bfeaturing\b|\bwith\b)\s*""")

    /**
     * What makes a listing a different recording rather than a different
     * listing of the same one. A title carrying one of these on one side only
     * is refused outright.
     */
    private val VERSION_WORDS = setOf(
        "remix", "remixes", "rmx", "refix", "flip", "bootleg", "mashup", "medley",
        "live", "concert", "unplugged", "acoustic", "instrumental", "karaoke",
        // A stem is not the song.
        "vocals", "vocal", "acapella", "acappella", "backing", "stems", "stem",
        "cover", "demo", "reprise", "remake", "rework", "extended", "edit",
        "version", "mix", "dub", "vip", "session", "sessions",
        "sped", "slowed", "reverb", "nightcore", "lofi", "orchestral", "symphonic",
        "part", "pt", "chapter",
    )

    /**
     * Asides that read like a version and describe the ordinary release —
     * without this, "Song (Album Version)" and "Song" would be two different
     * recordings.
     */
    private val NEUTRAL_SEGMENTS = setOf(
        "albumversion", "originalversion", "originalmix", "singleversion",
        "radioversion", "radioedit", "stereoversion", "monoversion",
        "studioversion", "fullversion", "standardversion", "explicitversion",
        "deluxeversion", "originaltrack",
    )

    /** Packaging words, worth nothing as a tie-break because everything has them. */
    private val NOISE_WORDS = setOf(
        "official", "video", "audio", "lyrics", "lyric", "lyrical", "visualizer",
        "song", "songs", "full", "music", "the", "and", "from", "feat", "ft",
        "featuring", "with", "new", "latest", "free", "download", "remaster",
        "remastered", "explicit", "clean", "bonus", "track", "deluxe", "original",
        "album", "single", "hd", "hq", "4k", "mp3",
    )

    private val ALBUM_NOISE_WORDS = setOf(
        "album", "deluxe", "edition", "expanded", "remaster", "remastered",
        "version", "explicit", "clean", "bonus", "anniversary",
    )

    /** Trailing labels an upload hangs on a title with no brackets to hold them. */
    private val TRAILING_NOISE = setOf(
        "song", "songs", "video", "audio", "lyrics", "lyric", "lyrical",
        "official", "full", "hd", "hq", "4k", "mp3", "ost", "soundtrack",
    )

    /** Dropped from the core so that "Jack and Jill" and "Jack & Jill" are one title. */
    private val JOINING_WORDS = setOf("and")
}

