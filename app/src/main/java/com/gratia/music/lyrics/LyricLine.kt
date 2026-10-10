package com.gratia.music.lyrics

/**
 * One word of a line, with the stretch of the song it is sung over.
 *
 * Apple's TTML splits long words into syllables; those are merged back into
 * whole words on the way in, so [startMs] is the first syllable's start and
 * [endMs] the last one's end. Whole words are what the sweep needs — a
 * highlight that ran across "e" and "nough" separately reads as a stutter.
 */
data class LyricWord(val startMs: Long, val endMs: Long, val text: String)

/**
 * One synced line. [timeMs] is when it starts; a line that is not sung is an
 * instrumental stretch — LRC files mark those with a bare timestamp, and files
 * with no stamp to spare name the stretch in the text instead. Both read the
 * same way here; see [isGap].
 *
 * [words] is populated only by the providers that carry word-level timing
 * (BetterLyrics, LyricsPlus, SimpMusic's rich sync). LRCLIB has none, so a
 * line from there highlights whole; see [isWordSynced].
 *
 * [sungUntilMs] is the line's own end where a line-synced provider states one,
 * which is what lets an interlude be told apart from a slowly sung line.
 *
 * [background] is the answering vocal — the "(ooh)" or the echoed half-phrase
 * a second voice sings over the lead. It is a line in its own right, with its
 * own stamp and its own words, because that is what it is: it starts partway
 * through the line it answers and routinely runs past the *next* line's stamp.
 * Run into [text] it dragged the sweep along with it, and the cursor — which
 * takes the last line whose stamp has passed — moved on before the bracket had
 * been sung, so the tail of the line was skipped. Kept apart it draws
 * underneath the lead on its own clock. Never nested: a background line's own
 * [background] is always null.
 */
data class LyricLine(
    val startMs: Long,
    val text: String,
    val words: List<LyricWord> = emptyList(),
    val endMs: Long? = null,
    val background: LyricLine? = null,
    val translation: String? = null,
    val romanization: String? = null,
    val voiceTag: String? = null
) {
    val timeMs: Long get() = startMs

    /**
     * Whether this row is a break rather than something sung — either nothing
     * written after the stamp, or the stretch named in place of the words (see
     * [isBreakText]).
     *
     * The two arrive from different places and are the same row by the time it
     * is drawn: the break, counting itself out. Anything deciding what to do
     * with a line — sweep it, split a backing vocal off it, follow it — asks
     * this rather than looking at [text].
     */
    val isGap: Boolean get() = text.isEmpty() || text.isBreakText()

    val isWordSynced: Boolean get() = words.isNotEmpty()

    /**
     * Whether anything actually told us when the singing stops, rather than
     * only when it starts. Word timings carry it, and so does a provider that
     * stamps the line's own end ([endMs]).
     *
     * The distance to the next line's stamp is *not* evidence of an end: that
     * distance is the line's own slot, and on a line-synced source it is
     * routinely ten seconds for a line sung over all ten of them.
     */
    val hasKnownEnd: Boolean get() = words.isNotEmpty() || endMs != null

    /**
     * When the last word finishes — or the line's own end where the provider
     * gave one, or [timeMs] when nothing did. Check [hasKnownEnd] before
     * reading a silence out of this.
     *
     * The answering vocal counts: it is still this line being sung, and it
     * regularly holds a note past the lead's last word. Measured without it, a
     * break would be found in the middle of a line that is still going.
     */
    val computedEndMs: Long
        get() {
            val lead = words.lastOrNull()?.endMs ?: endMs ?: timeMs
            return maxOf(lead, background?.computedEndMs ?: lead)
        }

    /**
     * How far through the line the singing has got, 0..1, as a fractional
     * index into [text]. The sweep reveals up to this character.
     *
     * Within a word it interpolates across that word's own span, so a held
     * note draws slowly and a rattled-off one snaps. Whitespace between two
     * words is credited to the gap between them: it fills as the singer moves
     * on rather than jumping ahead of the next word's first letter.
     */
    fun revealedChars(positionMs: Long): Float {
        if (words.isEmpty()) return if (positionMs >= timeMs) text.length.toFloat() else 0f
        var offset = 0
        words.forEachIndexed { index, word ->
            // Where this word sits in [text]. Built by walking rather than
            // searching, so a word repeated in the line still lines up.
            val start = text.indexOf(word.text, offset).takeIf { it >= 0 } ?: offset
            val end = start + word.text.length
            if (positionMs < word.startMs) return start.toFloat()
            if (positionMs < word.endMs) {
                val span = (word.endMs - word.startMs).coerceAtLeast(1L)
                val through = (positionMs - word.startMs).toFloat() / span
                return start + through * word.text.length
            }
            // Past this word: the trailing space fills over the pause before
            // the next one, so the highlight keeps creeping instead of resting
            // on the word's last letter.
            val next = words.getOrNull(index + 1)
            if (next != null && positionMs < next.startMs) {
                val gapStart = text.indexOf(next.text, end).takeIf { it >= 0 } ?: end
                val pause = (next.startMs - word.endMs).coerceAtLeast(1L)
                val through = (positionMs - word.endMs).toFloat() / pause
                return end + through * (gapStart - end)
            }
            offset = end
        }
        return text.length.toFloat()
    }

    /**
     * How much bloom the word being sung has earned, 0..1.
     *
     * Two things decide it. How long the word is held sets the ceiling — a
     * note carried for a second swells, a word rattled off in a tenth of one
     * barely registers, which is the difference between a glow that belongs to
     * the singing and a lamp dragged along under the text. Then an envelope
     * across the word's own span rises as it lands and eases off as it goes,
     * so each word blooms and lets go rather than the light being on
     * throughout and stepping between brightnesses at every boundary.
     *
     * Zero between words and after the last one, which is what keeps the
     * pauses dark and costs nothing to draw.
     */
    fun glowIntensity(positionMs: Long): Float {
        val word = words.firstOrNull { positionMs < it.endMs } ?: return 0f
        if (positionMs < word.startMs) return 0f

        val held = (word.endMs - word.startMs).coerceAtLeast(1L)
        val through = ((positionMs - word.startMs).toFloat() / held).coerceIn(0f, 1f)
        val envelope = when {
            through < GLOW_ATTACK -> through / GLOW_ATTACK
            through > 1f - GLOW_RELEASE -> (1f - through) / GLOW_RELEASE
            else -> 1f
        }
        val pace = ((held - GLOW_FAST_MS).toFloat() / (GLOW_SLOW_MS - GLOW_FAST_MS))
            .coerceIn(0f, 1f)
        return (GLOW_FLOOR + (1f - GLOW_FLOOR) * pace) * envelope.coerceIn(0f, 1f)
    }
}

/**
 * Whether a line names a break instead of singing one.
 *
 * Files that have no empty stamp to spare still say "instrumental here", and
 * they say it with the line's own text: a glyph standing in for the music, or a
 * bracketed note naming the stretch. Unrecognised, such a line is drawn as a
 * lyric and read as one — "(instrumental)" scrolling past in the verse's own
 * type as though it were sung.
 *
 * It has to be the whole line, and nothing but it. A lyric that mentions the
 * word, or carries a note glyph in the middle of the phrase, is a lyric, so
 * this errs towards leaving text alone.
 *
 * Empty text is not a break *here*: [LyricLine.isGap] handles the file that
 * wrote a bare stamp, and the two are read separately so neither has to know
 * about the other.
 */
internal fun String.isBreakText(): Boolean {
    val trimmed = trim()
    if (trimmed.isEmpty()) return false
    return MUSIC_GLYPHS_ONLY.matches(trimmed) || NAMED_BREAK.matches(trimmed)
}

/** A line that is nothing but note glyphs — `♪`, `♪ ♪`, `♫♫♫`. */
private val MUSIC_GLYPHS_ONLY = Regex("""^[\s♪♫♩♬𝄞🎵🎶🎼]+$""")

/**
 * A line that is nothing but a bracketed note naming the stretch.
 *
 * Square and round brackets, and the full-width forms of both, because the CJK
 * files write `（間奏）`. Only the names that mean the break and nothing else,
 * and only on their own: a note with a word after it — `(guitar solo, fade
 * out)`, `(instrumental x2)` — is left as text, because a file that wrote that
 * much about the stretch wrote a line about it.
 */
private val NAMED_BREAK = Regex(
    """^[\[(（【]\s*(instrumental|instrumental\s+break|music|music\s+break|interlude|solo|guitar\s+solo|間奏|间奏)\s*[)\]）】]$""",
    RegexOption.IGNORE_CASE
)

/** A word this short is patter; it gets [GLOW_FLOOR] and no more. */
private const val GLOW_FAST_MS = 130L

/** A word held this long gets the full bloom. */
private const val GLOW_SLOW_MS = 800L

/** What the quickest words still get, so patter doesn't go completely flat. */
private const val GLOW_FLOOR = 0.22f

/** Share of a word's span spent coming up, and going back down. */
private const val GLOW_ATTACK = 0.18f
private const val GLOW_RELEASE = 0.38f
