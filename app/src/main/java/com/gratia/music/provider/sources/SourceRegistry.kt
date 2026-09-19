/*
 * Copyright (C) 2026 Kushagra Singh / Gratia contributors
 * Modified for Gratia by Hussain / Gratia contributors, 2026
 * Licensed under the GNU General Public License v3.0
 *
 * Source: Gratia data/sources/SourceKind.kt.
 * https://github.com/kushagrasinghx/Gratia (GPLv3)
 *
 * Modifications for Gratia: the MODULE kind is not carried — Gratia has no
 * addon-module system; an AddonSource-style lossless module can be registered
 * later without changing anything above [SourceKind]. YouTube remains the base
 * playback source and is not itself a remote [MusicSource]: its streams come
 * from Gratia's existing StreamResolver.
 */
package com.gratia.music.provider.sources

import com.gratia.music.provider.RemoteTrack

/**
 * Which service a configured source is, as distinct from which instance.
 *
 * The behaviour attached to a kind — whether it can hold FLAC, whether it is
 * worth prefetching against the clock — belongs here, on the kind rather than
 * the instance, so that every JioSaavn the user could ever enable behaves the
 * same without any of them having to remember to say so.
 */
enum class SourceKind(val label: String) {
    /** YouTube Music. Not a remote source — Gratia's own [StreamResolver] serves it. */
    YOUTUBE("YouTube Music"),

    /** JioSaavn's public catalogue. Lossy only, but frequently better lossy than YouTube. */
    JIOSAAVN("JioSaavn"),

    /** A user-configured lossless module (future: addon sources). */
    MODULE("Module"),

    /** A catalogue of a kind this build does not model. */
    OTHER("Source"),
    ;

    /** Whether this kind can hold bit-exact copies of a master. */
    val canServeLossless: Boolean
        get() = this == MODULE

    /**
     * Whether this kind answers fast enough to be asked before a track starts.
     *
     * A module that has to be contacted and walked is not; JioSaavn, which
     * answers in a round trip, is.
     */
    val worthPrefetching: Boolean
        get() = this == JIOSAAVN

    companion object {
        /**
         * The lowest rendition of a kind that is worth playing at all.
         *
         * YouTube's Opus lands around 160kbps; a JioSaavn 96kbps row would be
         * a downgrade dressed as an upgrade, so the source layer refuses it
         * rather than handing it up for [SourceResolver.worthSwapping] to
         * reject after the round trip has already been spent.
         */
        fun minUsableKbpsFor(kind: SourceKind): Int = when (kind) {
            JIOSAAVN -> 160
            else -> 96
        }

        /** Best rendition a kind is *known* to hold, for honest labelling. */
        fun bestKnownSummary(kind: SourceKind): String = when (kind) {
            JIOSAAVN -> "AAC · up to 320 kbps"
            MODULE -> "Lossless capable"
            else -> "Opus · ~160 kbps"
        }
    }
}

/** Builds the track key that ties a candidate row back to its source. */
object SourceTrackKey {
    fun of(configId: String, trackId: String): String = "$configId::$trackId"

    /** @return the (configId, trackId) pair a key carries, or null if it isn't one. */
    fun parse(key: String): Pair<String, String>? {
        val split = key.indexOf("::")
        if (split <= 0 || split >= key.length - 2) return null
        return key.substring(0, split) to key.substring(split + 2)
    }

    fun belongsTo(key: String, configId: String): Boolean =
        key.startsWith("$configId::")
}

/** The sources configured in this build, as instances Gratia knows up front. */
object SourceRegistry {

    @Volatile
    private var jioSaavn: JioSaavnSource? = null

    /**
     * Whether the better-rendition source is enabled at all. Off by default
     * and flipped by Settings; when off, nothing in the app contacts
     * JioSaavn, and the resolver's answer to "can anything outrank YouTube?"
     * is a memory read.
     */
    @Volatile
    var jioSaavnEnabled: Boolean = false
        private set

    fun enableJioSaavn(enabled: Boolean) {
        jioSaavnEnabled = enabled
    }

    /** The JioSaavn source, built lazily and safe to hold (no connections until asked). */
    fun jioSaavnSource(): JioSaavnSource? =
        if (jioSaavnEnabled) {
            jioSaavn ?: JioSaavnSource().also { jioSaavn = it }
        } else {
            null
        }

    /**
     * Every source enabled for playback right now, in rank order — anything
     * listed outranks YouTube, which is not in the list at all because it is
     * not a remote source here.
     */
    fun activeForPlayback(): List<MusicSource> = listOfNotNull(jioSaavnSource())
}

