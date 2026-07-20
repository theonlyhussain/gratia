package com.gratia.music.audio

/**
 * Gratia EQ preset: a named set of band gain values.
 *
 * @property name  Display name (e.g. "Bass Boost", "Vocal")
 * @property gains Per-band gain in millibels. Length must match the number of bands
 *                 reported by the hardware equalizer (usually 5 or 10).
 *                 Values are interpolated when band counts differ.
 */
data class EqPreset(
    val name: String,
    val gains: List<Short>
) {
    companion object {
        /** Built-in presets normalised to a 5-band equalizer (60 Hz – 14 kHz). */
        val FLAT = EqPreset("Flat", listOf(0, 0, 0, 0, 0))

        val builtIn: List<EqPreset> = listOf(
            FLAT,
            EqPreset("Bass Boost", listOf(600, 400, 0, 0, 0)),
            EqPreset("Bass Reducer", listOf(-600, -400, 0, 0, 0)),
            EqPreset("Treble Boost", listOf(0, 0, 0, 400, 600)),
            EqPreset("Treble Reducer", listOf(0, 0, 0, -400, -600)),
            EqPreset("Vocal", listOf(-200, 200, 500, 300, -100)),
            EqPreset("Rock", listOf(500, 300, -100, 300, 500)),
            EqPreset("Pop", listOf(-100, 200, 500, 200, -100)),
            EqPreset("Jazz", listOf(300, 0, 100, 200, 400)),
            EqPreset("Classical", listOf(400, 200, -100, 200, 400)),
            EqPreset("Hip-Hop", listOf(500, 400, 0, 100, 300)),
            EqPreset("Electronic", listOf(400, 200, 0, 200, 400)),
            EqPreset("R&B", listOf(300, 500, 200, -100, 200)),
            EqPreset("Acoustic", listOf(300, 100, 0, 200, 300)),
            EqPreset("Podcast", listOf(-200, 0, 400, 500, 200)),
            EqPreset("Late Night", listOf(300, 200, 100, -100, -300)),
            EqPreset("Small Speakers", listOf(500, 400, 200, 0, -100)),
            EqPreset("Headphones", listOf(300, 100, -100, 200, 400)),
        )
    }
}
