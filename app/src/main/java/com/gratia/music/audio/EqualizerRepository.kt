package com.gratia.music.audio

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

/**
 * Persists equalizer settings to SharedPreferences.
 *
 * Uses SharedPreferences instead of DataStore because EQ settings
 * must be available synchronously during audio session initialization
 * (before any coroutine scope is ready).
 */
class EqualizerRepository(context: Context) {

    companion object {
        private const val TAG = "GratiaEQ"
        private const val PREFS_NAME = "gratia_equalizer"
        private const val KEY_ENABLED = "eq_enabled"
        private const val KEY_BAND_LEVELS = "eq_band_levels"
        private const val KEY_PRESET_NAME = "eq_preset_name"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ── Read ────────────────────────────────────────────────────────────

    fun loadEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, false)

    fun loadBandLevels(): List<Short> {
        val raw = prefs.getString(KEY_BAND_LEVELS, null) ?: return emptyList()
        return try {
            raw.split(",").mapNotNull { it.trim().toShortOrNull() }
        } catch (e: Exception) {
            Log.w(TAG, "loadBandLevels: parse error — ${e.message}")
            emptyList()
        }
    }

    fun loadPresetName(): String? = prefs.getString(KEY_PRESET_NAME, null)

    // ── Write ───────────────────────────────────────────────────────────

    fun saveEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun saveBandLevels(levels: List<Short>) {
        val raw = levels.joinToString(",")
        prefs.edit().putString(KEY_BAND_LEVELS, raw).apply()
    }

    fun savePresetName(name: String?) {
        if (name != null) {
            prefs.edit().putString(KEY_PRESET_NAME, name).apply()
        } else {
            prefs.edit().remove(KEY_PRESET_NAME).apply()
        }
    }

    /** Clear all saved EQ settings. */
    fun clear() {
        prefs.edit().clear().apply()
    }
}
