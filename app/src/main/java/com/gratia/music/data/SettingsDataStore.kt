package com.gratia.music.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
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
}
