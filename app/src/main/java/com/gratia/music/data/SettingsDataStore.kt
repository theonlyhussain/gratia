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
    DARK("dark");

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

    suspend fun setOnboardingCompleted(completed: Boolean = true) {
        context.dataStore.edit { preferences ->
            preferences[ONBOARDING_KEY] = completed
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
}
