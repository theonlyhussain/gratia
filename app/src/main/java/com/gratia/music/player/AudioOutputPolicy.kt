/*
 * Copyright (C) 2026 Kushagra Singh / Gratia contributors
 * Modified for Gratia by Hussain / Gratia contributors, 2026
 * Licensed under the GNU General Public License v3.0
 *
 * Source: Gratia playback/AudioOutputPolicy.kt.
 * https://github.com/kushagrasinghx/Gratia (GPLv3)
 *
 * Modifications for Gratia: OutputPcmMode lives in Gratia's media-output
 * settings; the policy itself is unchanged.
 */
package com.gratia.music.player

/**
 * Decides whether Media3 may open a PCM-float AudioTrack.
 *
 * Float output is not a quality switch that is safe on every Android route.
 * Some OEM speaker paths accept the AudioTrack and then convert it to PCM16 in
 * AudioFlinger; on affected Samsung FLAC decoders that combination can also
 * produce timestamp discontinuities and severely distorted output. Only an
 * explicitly selected external route that advertises PCM float is allowed.
 */
internal object AudioOutputPolicy {

    /** Which PCM output mode the user's media-output settings select. */
    enum class OutputPcmMode { PCM_16, FLOAT_32 }

    fun shouldUseFloatOutput(
        requestedMode: OutputPcmMode,
        isPreferredUsbRoute: Boolean,
        advertisesPcmFloat: Boolean,
    ): Boolean = requestedMode == OutputPcmMode.FLOAT_32 &&
        isPreferredUsbRoute &&
        advertisesPcmFloat

    /** Samsung's vendor FLAC decoder emits invalid timestamps with PCM float. */
    fun isUnsafeFloatFlacDecoder(name: String): Boolean {
        val normalized = name.lowercase()
        return normalized == "c2.sec.flac.decoder" ||
            (normalized.startsWith("omx.sec.") && normalized.contains("flac"))
    }
}

