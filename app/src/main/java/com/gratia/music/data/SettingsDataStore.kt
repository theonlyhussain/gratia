package com.gratia.music.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Enum for Theme Options
enum class ThemeOption(val value: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark"),
    AMOLED("amoled");

    companion object {
        fun fromValue(value: String): ThemeOption {
            return values().find { it.value == value } ?: SYSTEM
        }
    }
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "gratia_settings")

class SettingsDataStore(private val context: Context) {

    private val THEME_KEY = stringPreferencesKey("app_theme")

    val themeOptionFlow: Flow<ThemeOption> = context.dataStore.data
        .map { preferences ->
            val themeValue = preferences[THEME_KEY] ?: ThemeOption.SYSTEM.value
            ThemeOption.fromValue(themeValue)
        }

    suspend fun setThemeOption(themeOption: ThemeOption) {
        context.dataStore.edit { preferences ->
            preferences[THEME_KEY] = themeOption.value
        }
    }

    private val ONBOARDING_KEY = androidx.datastore.preferences.core.booleanPreferencesKey("onboarding_completed")

    val onboardingCompletedFlow: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[ONBOARDING_KEY] ?: false
        }

    private val INITIAL_SCAN_KEY = androidx.datastore.preferences.core.booleanPreferencesKey("initial_scan_completed")

    val initialScanCompletedFlow: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[INITIAL_SCAN_KEY] ?: false
        }

    suspend fun setOnboardingCompleted(completed: Boolean = true) {
        context.dataStore.edit { preferences ->
            preferences[ONBOARDING_KEY] = completed
        }
    }

    suspend fun setInitialScanCompleted(completed: Boolean = true) {
        context.dataStore.edit { preferences ->
            preferences[INITIAL_SCAN_KEY] = completed
        }
    }

    private val SEARCH_HISTORY_KEY = stringSetPreferencesKey("search_history")

    val searchHistoryFlow: Flow<Set<String>> = context.dataStore.data
        .map { preferences ->
            preferences[SEARCH_HISTORY_KEY] ?: emptySet()
        }

    suspend fun addSearchHistory(query: String) {
        context.dataStore.edit { preferences ->
            val current = preferences[SEARCH_HISTORY_KEY] ?: emptySet()
            // Keep up to 10 recent searches
            val updated = (setOf(query) + current).take(10).toSet()
            preferences[SEARCH_HISTORY_KEY] = updated
        }
    }

    suspend fun removeSearchHistory(query: String) {
        context.dataStore.edit { preferences ->
            val current = preferences[SEARCH_HISTORY_KEY] ?: emptySet()
            preferences[SEARCH_HISTORY_KEY] = current - query
        }
    }

    suspend fun clearSearchHistory() {
        context.dataStore.edit { preferences ->
            preferences[SEARCH_HISTORY_KEY] = emptySet()
        }
    }

    // Playback State Persistence
    private val SAVED_QUEUE_IDS_KEY = stringPreferencesKey("saved_queue_ids") // comma separated
    private val SAVED_CURRENT_SONG_ID_KEY = stringPreferencesKey("saved_current_song_id")
    private val SAVED_CURRENT_TIME_MS_KEY = androidx.datastore.preferences.core.longPreferencesKey("saved_current_time_ms")

    val savedQueueIdsFlow: Flow<List<String>> = context.dataStore.data.map { preferences ->
        val str = preferences[SAVED_QUEUE_IDS_KEY] ?: ""
        if (str.isEmpty()) emptyList() else str.split(",")
    }

    val savedCurrentSongIdFlow: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[SAVED_CURRENT_SONG_ID_KEY]
    }

    val savedCurrentTimeMsFlow: Flow<Long> = context.dataStore.data.map { preferences ->
        preferences[SAVED_CURRENT_TIME_MS_KEY] ?: 0L
    }

    suspend fun savePlaybackState(queueIds: List<String>, currentSongId: String?, currentTimeMs: Long) {
        context.dataStore.edit { preferences ->
            preferences[SAVED_QUEUE_IDS_KEY] = queueIds.joinToString(",")
            if (currentSongId != null) {
                preferences[SAVED_CURRENT_SONG_ID_KEY] = currentSongId
            } else {
                preferences.remove(SAVED_CURRENT_SONG_ID_KEY)
            }
            preferences[SAVED_CURRENT_TIME_MS_KEY] = currentTimeMs
        }
    }
}
