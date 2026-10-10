package com.gratia.music.lyrics

/**
 * Whether a transient vocal is on screen right now, and how far through it the
 * voice has got — answered entirely from the playback position.
 *
 * This is deliberately pure and stateless. The backing-vocal overlay must be a
 * *question asked of the clock*, never a one-way flag: a `hasShown` boolean
 * would be correct until the listener scrubbed backwards, at which point the
 * vocal could never appear again (spec 16). Seeking back re-arms it, seeking
 * forward dismisses it, and pausing inside its window leaves it exactly where it
 * was — all for free, because nothing is remembered (spec 17).
 *
 * "Transient vocal" covers both the answering lead-out of a line
 * ([LyricLine.background]) and any future timed accent that should appear and
 * then get out of the way; the timing question is identical.
 */
object TransientVocal {

    /**
     * True while [positionMs] lies inside [line]'s own sung window — from its
     * first word (or stamp) to its last.
     *
     * A line with no text is never active, which is how a document that supplied
     * no backing-vocal metadata renders as ordinary lyrics and nothing else
     * (spec 14): there is simply no line to show.
     */
    fun isActive(line: LyricLine, positionMs: Long): Boolean {
        if (line.text.isBlank()) return false
        return positionMs >= line.startMs && positionMs <= windowEnd(line)
    }

    /**
     * When a transient vocal's window closes: its last word's end where it has
     * word timings, its own stated end where the provider gave one, and its
     * stamp where nothing did — WORD first, then LINE, then nothing (spec 15).
     *
     * Deliberately not [LyricLine.computedEndMs], which takes the *later* of a
     * line's end and its words'. That is right for the lead, whose answering
     * voice can hold past the lead's last word, but wrong here: a backing
     * phrase carryied the parent line's stated end, and measuring to it would
     * leave "(a)" on screen for the rest of the line, long after it was sung
     * (spec 8/22). Words first keeps the window to the moment the voice stops.
     */
    private fun windowEnd(line: LyricLine): Long =
        line.words.lastOrNull()?.endMs ?: line.endMs ?: line.timeMs

    /**
     * How far through [line]'s window [positionMs] is, 0..1, clamped.
     *
     * Used for the group-level presence. Where the line carries its own word
     * timings, the per-word fill is drawn by the shared sweep from the same
     * clock, so a word-level backing phrase highlights word by word; where it
     * does not, the phrase is shown whole for its line window — no per-word
     * timing is invented (spec 12/15).
     */
    fun progress(line: LyricLine, positionMs: Long): Float {
        val end = windowEnd(line)
        val span = (end - line.startMs).coerceAtLeast(1L)
        return ((positionMs - line.startMs).toFloat() / span).coerceIn(0f, 1f)
    }
}
