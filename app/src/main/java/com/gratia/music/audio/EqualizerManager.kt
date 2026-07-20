package com.gratia.music.audio

import android.media.audiofx.Equalizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages the Android hardware equalizer attached to the player's audio session.
 *
 * Lifecycle:
 * - Created as a singleton by [com.gratia.music.GratiaApp].
 * - Call [attachToSession] whenever the ExoPlayer audio session changes.
 * - Call [release] only during Application.onTerminate.
 *
 * Architecture: Manager → StateFlow → Compose UI (via ViewModel).
 * Persistence is handled by [EqualizerRepository].
 */
class EqualizerManager(private val repository: EqualizerRepository) {

    companion object {
        private const val TAG = "GratiaEQ"
    }

    // ── Hardware state ──────────────────────────────────────────────────
    private val equalizers = mutableMapOf<Int, Equalizer>()

    // ── Observable state ────────────────────────────────────────────────
    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _bandLevels = MutableStateFlow<List<Short>>(emptyList())
    val bandLevels: StateFlow<List<Short>> = _bandLevels.asStateFlow()

    private val _bandFrequencies = MutableStateFlow<List<Int>>(emptyList())
    val bandFrequencies: StateFlow<List<Int>> = _bandFrequencies.asStateFlow()

    private val _numberOfBands = MutableStateFlow(0)
    val numberOfBands: StateFlow<Int> = _numberOfBands.asStateFlow()

    private val _levelRange = MutableStateFlow(Pair<Short, Short>(0, 0))
    val levelRange: StateFlow<Pair<Short, Short>> = _levelRange.asStateFlow()

    private val _activePresetName = MutableStateFlow<String?>(null)
    val activePresetName: StateFlow<String?> = _activePresetName.asStateFlow()

    private val _isAvailable = MutableStateFlow(false)
    val isAvailable: StateFlow<Boolean> = _isAvailable.asStateFlow()

    // ── Preset catalogue ────────────────────────────────────────────────
    val presets: List<EqPreset> get() = EqPreset.builtIn

    // ── Public API ──────────────────────────────────────────────────────

    /**
     * Attach (or re-attach) the equalizer to an audio session.
     * Called from PlaybackService when ExoPlayer provides a session ID.
     */
    fun attachToSession(audioSessionId: Int) {
        if (audioSessionId <= 0) {
            Log.w(TAG, "attachToSession: invalid session ID $audioSessionId")
            return
        }
        if (equalizers.containsKey(audioSessionId)) {
            Log.d(TAG, "attachToSession: already attached to session $audioSessionId")
            return
        }

        try {
            val newEq = Equalizer(0, audioSessionId).apply {
                enabled = true
            }
            equalizers[audioSessionId] = newEq

            val eq = newEq
            val bands = eq.numberOfBands.toInt()
            val range = Pair(eq.bandLevelRange[0], eq.bandLevelRange[1])

            // Read hardware band center frequencies
            val freqs = (0 until bands).map { band ->
                eq.getCenterFreq(band.toShort()) / 1000 // milliHz → Hz
            }

            _numberOfBands.value = bands
            _levelRange.value = range
            _bandFrequencies.value = freqs
            _isAvailable.value = true

            Log.d(TAG, "attachToSession: $bands bands, range $range, freqs $freqs")

            // Restore saved state
            restoreSavedState()

        } catch (e: Exception) {
            Log.e(TAG, "attachToSession: failed to create Equalizer for $audioSessionId", e)
            if (equalizers.isEmpty()) _isAvailable.value = false
        }
    }

    /**
     * Detach the equalizer from an audio session.
     * Called when an ExoPlayer instance is released during crossfade.
     */
    fun detachFromSession(audioSessionId: Int) {
        val eq = equalizers.remove(audioSessionId)
        if (eq != null) {
            try {
                eq.release()
                Log.d(TAG, "detachFromSession: released EQ for $audioSessionId")
            } catch (e: Exception) {
                Log.w(TAG, "detachFromSession: failed to release EQ - ${e.message}")
            }
        }
        if (equalizers.isEmpty()) {
            _isAvailable.value = false
        }
    }

    /** Toggle the equalizer on/off. */
    fun setEnabled(enabled: Boolean) {
        _enabled.value = enabled
        if (enabled) {
            val savedLevels = repository.loadBandLevels()
            if (savedLevels.isNotEmpty()) {
                applyBandLevels(savedLevels)
            }
            withEqSafe("enable") { it.enabled = true }
        } else {
            // Zero all bands to avoid audio pop, keep hardware EQ "enabled"
            flattenBands()
            withEqSafe("disable") { it.enabled = true } // keep HW EQ on; gains are zeroed
        }
        repository.saveEnabled(enabled)
        if (!enabled) _activePresetName.value = null
    }

    /** Set a single band level. */
    fun setBandLevel(band: Int, level: Short) {
        if (band < 0 || band >= _numberOfBands.value) return

        withEqSafe("setBandLevel($band, $level)") {
            it.setBandLevel(band.toShort(), level)
        }

        val updated = _bandLevels.value.toMutableList()
        if (band < updated.size) {
            updated[band] = level
            _bandLevels.value = updated
        }
        _activePresetName.value = "Custom"
        repository.saveBandLevels(updated)
        repository.savePresetName("Custom")
    }

    /** Apply a named preset. */
    fun applyPreset(preset: EqPreset) {
        val bands = _numberOfBands.value
        if (bands <= 0) return

        val interpolated = interpolateGains(preset.gains, bands)
        applyBandLevels(interpolated)

        _activePresetName.value = preset.name
        _enabled.value = true
        withEqSafe("enable for preset") { it.enabled = true }

        repository.saveEnabled(true)
        repository.saveBandLevels(interpolated)
        repository.savePresetName(preset.name)

        Log.d(TAG, "applyPreset: '${preset.name}' → $interpolated")
    }

    /** Reset to flat response. */
    fun reset() {
        applyPreset(EqPreset.FLAT)
        setEnabled(false)
    }

    /** Release hardware resources. Only called by GratiaApp. */
    fun release() {
        releaseHardware()
        _isAvailable.value = false
    }

    // ── Internal ────────────────────────────────────────────────────────

    private fun restoreSavedState() {
        val savedEnabled = repository.loadEnabled()
        val savedLevels = repository.loadBandLevels()
        val savedPresetName = repository.loadPresetName()

        if (savedLevels.isNotEmpty()) {
            applyBandLevels(savedLevels)
        } else {
            // Read hardware defaults
            val bands = _numberOfBands.value
            val defaults = (0 until bands).map { band ->
                withEqSafe("readBand($band)", 0.toShort()) { eq ->
                    eq.getBandLevel(band.toShort())
                }
            }
            _bandLevels.value = defaults
        }

        if (savedEnabled) {
            withEqSafe("enable on restore") { it.enabled = true }
        } else {
            flattenBands()
            withEqSafe("keep enabled flat") { it.enabled = true }
        }

        _enabled.value = savedEnabled
        _activePresetName.value = savedPresetName

        Log.d(TAG, "restoreSavedState: enabled=$savedEnabled, preset=$savedPresetName, levels=${_bandLevels.value}")
    }

    private fun applyBandLevels(levels: List<Short>) {
        if (equalizers.isEmpty()) return
        val bands = _numberOfBands.value
        val range = _levelRange.value

        val clamped = levels.map { it.coerceIn(range.first, range.second) }
        val applied = if (clamped.size >= bands) clamped.take(bands) else {
            // Pad with zeros if we have fewer saved bands than hardware
            clamped + List(bands - clamped.size) { 0.toShort() }
        }

        applied.forEachIndexed { index, level ->
            withEqSafe("setBand($index, $level)") {
                it.setBandLevel(index.toShort(), level)
            }
        }

        _bandLevels.value = applied
    }

    private fun flattenBands() {
        val bands = _numberOfBands.value
        val zeroed = List(bands) { 0.toShort() }
        applyBandLevels(zeroed)
    }

    private fun releaseHardware() {
        equalizers.values.forEach { eq ->
            try {
                eq.release()
            } catch (e: Exception) {
                Log.w(TAG, "releaseHardware: ${e.message}")
            }
        }
        equalizers.clear()
    }

    /**
     * Interpolate preset gains from [sourceGains] (any number of bands)
     * to [targetBands] (hardware band count).
     */
    private fun interpolateGains(sourceGains: List<Short>, targetBands: Int): List<Short> {
        if (sourceGains.size == targetBands) return sourceGains
        if (sourceGains.isEmpty()) return List(targetBands) { 0.toShort() }

        return List(targetBands) { targetIndex ->
            val sourcePosition = targetIndex.toFloat() * (sourceGains.size - 1) / (targetBands - 1).coerceAtLeast(1)
            val lowerIndex = sourcePosition.toInt().coerceIn(0, sourceGains.size - 1)
            val upperIndex = (lowerIndex + 1).coerceAtMost(sourceGains.size - 1)
            val fraction = sourcePosition - lowerIndex

            val interpolated = sourceGains[lowerIndex] * (1f - fraction) + sourceGains[upperIndex] * fraction
            interpolated.toInt().toShort()
        }
    }

    /** Safely execute an operation on all active hardware equalizers, catching ISE. */
    private inline fun <T> withEqSafe(operation: String, defaultValue: T, block: (Equalizer) -> T): T {
        if (equalizers.isEmpty()) return defaultValue
        var lastResult: T = defaultValue
        equalizers.values.forEach { eq ->
            try {
                lastResult = block(eq)
            } catch (e: IllegalStateException) {
                Log.w(TAG, "EQ $operation: effect not initialised — ${e.message}")
            } catch (e: Exception) {
                Log.w(TAG, "EQ $operation failed: ${e.message}")
            }
        }
        return lastResult
    }

    private inline fun withEqSafe(operation: String, block: (Equalizer) -> Unit) {
        withEqSafe(operation, Unit) { block(it) }
    }
}
