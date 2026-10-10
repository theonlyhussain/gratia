package com.gratia.music.data.model

/**
 * One artist metric, typed (artist spec 2/3).
 *
 * Artists surface different numbers on different platforms and they are not
 * interchangeable: YouTube subscribers, Deezer followers, "monthly listeners"
 * and play/view counters are different measurements from different sources.
 * A previous build rendered a Deezer *followers* count under the heading
 * "Monthly Listeners" — a different metric from a different platform, shown
 * as if it were the same number the artist page carries. Every metric now
 * travels with its [type] and [source] so a surface can only ever describe a
 * number as what it actually is.
 *
 * When the value or its meaning is uncertain, UI omits it — never invents
 * one and never relabels one.
 */
data class ArtistMetric(
    /** The raw value, in whatever unit the source counts in. */
    val value: Long,
    val type: ArtistMetricType,
    /** The catalogue that measured it, e.g. "YouTube", "Deezer". */
    val source: String,
    val fetchedAt: Long = System.currentTimeMillis(),
) {
    /**
     * The honest label: metric type first, source named, no substitution —
     * "7.1M subscribers · YouTube", "1.3M followers · Deezer".
     */
    val label: String
        get() = "${formatCompact(value)} ${type.displayName} · $source"

    companion object {
        /**
         * Parses a catalogue's compact count ("1.2M", "87.3K", "1,234") into a
         * Long. Null when the text is not a recognisable count — an unparsable
         * value is omitted downstream, never guessed at (artist spec 3/37).
         */
        fun parseCompact(text: String?): Long? {
            if (text.isNullOrBlank()) return null
            val cleaned = text.trim().replace(",", "").uppercase()
            val match = Regex("^([\\d.]+)\\s*([KMB]?)$").find(cleaned) ?: return null
            val number = match.groupValues[1].toDoubleOrNull() ?: return null
            val multiplier = when (match.groupValues[2]) {
                "K" -> 1_000L
                "M" -> 1_000_000L
                "B" -> 1_000_000_000L
                else -> 1L
            }
            return (number * multiplier).toLong()
        }

        /** "1200000" → "1.2M", "87300" → "87.3K", "940" → "940". */
        fun formatCompact(value: Long): String = when {
            value >= 1_000_000_000 -> trim(value / 1_000_000_000.0) + "B"
            value >= 1_000_000 -> trim(value / 1_000_000.0) + "M"
            value >= 1_000 -> trim(value / 1_000.0) + "K"
            else -> value.toString()
        }

        private fun trim(d: Double): String {
            val rounded = (d * 10).toLong() / 10.0
            return if (rounded % 1.0 == 0.0) rounded.toLong().toString() else rounded.toString()
        }
    }
}

/** The metric kinds the supported catalogues actually state. */
enum class ArtistMetricType(val displayName: String) {
    FOLLOWERS("followers"),
    SUBSCRIBERS("subscribers"),
    MONTHLY_LISTENERS("monthly listeners"),
    LISTENERS("listeners"),
    VIEWS("views"),
    STREAMS("streams"),
}
