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

    private val CROSSFADE_KEY = androidx.datastore.preferences.core.intPreferencesKey("crossfade_duration_ms")

    val crossfadeDurationFlow: Flow<Int> = context.dataStore.data
        .map { preferences ->
            preferences[CROSSFADE_KEY] ?: 4000
        }

    suspend fun setCrossfadeDuration(durationMs: Int) {
        context.dataStore.edit { preferences ->
            preferences[CROSSFADE_KEY] = durationMs
        }
    }

    private val OLED_THEME_KEY = androidx.datastore.preferences.core.booleanPreferencesKey("oled_theme_enabled")

    val oledThemeEnabledFlow: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[OLED_THEME_KEY] ?: false
        }

    suspend fun setOledThemeEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[OLED_THEME_KEY] = enabled
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

    private val SMART_UPDATE_ENABLED_KEY = androidx.datastore.preferences.core.booleanPreferencesKey("smart_update_enabled")
    
    val smartUpdateEnabledFlow: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[SMART_UPDATE_ENABLED_KEY] ?: false
        }

    suspend fun setSmartUpdateEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SMART_UPDATE_ENABLED_KEY] = enabled
        }
    }

    private val UPDATE_CHANNELS_KEY = stringSetPreferencesKey("update_channels")

    val updateChannelsFlow: Flow<Set<String>> = context.dataStore.data
        .map { preferences ->
            preferences[UPDATE_CHANNELS_KEY] ?: setOf("stable")
        }

    suspend fun setUpdateChannels(channels: Set<String>) {
        context.dataStore.edit { preferences ->
            preferences[UPDATE_CHANNELS_KEY] = channels
        }
    }

    private val CACHE_LIMIT_MB_KEY = androidx.datastore.preferences.core.intPreferencesKey("cache_limit_mb")

    val cacheLimitMbFlow: Flow<Int> = context.dataStore.data
        .map { preferences ->
            preferences[CACHE_LIMIT_MB_KEY] ?: 500
        }

    suspend fun setCacheLimitMb(limit: Int) {
        context.dataStore.edit { preferences ->
            preferences[CACHE_LIMIT_MB_KEY] = limit
        }
    }

    private val SMART_UPDATE_ONBOARDING_KEY = androidx.datastore.preferences.core.booleanPreferencesKey("smart_update_onboarding_shown")
    
    val smartUpdateOnboardingShownFlow: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[SMART_UPDATE_ONBOARDING_KEY] ?: false
        }

    suspend fun setSmartUpdateOnboardingShown(shown: Boolean = true) {
        context.dataStore.edit { preferences ->
            preferences[SMART_UPDATE_ONBOARDING_KEY] = shown
        }
    }

    private val ONLINE_DATA_ENABLED_KEY = androidx.datastore.preferences.core.booleanPreferencesKey("online_data_enabled")
    val onlineDataEnabledFlow: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[ONLINE_DATA_ENABLED_KEY] ?: true // default to true
        }

    suspend fun setOnlineDataEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[ONLINE_DATA_ENABLED_KEY] = enabled
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

    // YouTube Music Backend URL
    private val YTMUSIC_BACKEND_URL_KEY = stringPreferencesKey("ytmusic_backend_url")

    val ytmusicBackendUrlFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[YTMUSIC_BACKEND_URL_KEY] ?: "http://10.0.2.2:8000"
    }

    suspend fun setYtmusicBackendUrl(url: String) {
        context.dataStore.edit { preferences ->
            preferences[YTMUSIC_BACKEND_URL_KEY] = url
        }
    }

    // --- Lyrics Animation ---
    private val ANIMATED_WORD_LYRICS_KEY = androidx.datastore.preferences.core.booleanPreferencesKey("animated_word_lyrics")

    val animatedWordLyricsFlow: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[ANIMATED_WORD_LYRICS_KEY] ?: true // ON by default
        }

    suspend fun setAnimatedWordLyrics(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[ANIMATED_WORD_LYRICS_KEY] = enabled
        }
    }

    // --- Reduce Animation ---
    // Freezes the lyrics animation for older or slower devices: the sweep and
    // the bloom stop, lines hand over instantly rather than easing, and the
    // list jumps to the line being sung instead of scrolling to it.
    private val REDUCE_ANIMATION_KEY =
        androidx.datastore.preferences.core.booleanPreferencesKey("reduce_animation")

    val reduceAnimationFlow: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[REDUCE_ANIMATION_KEY] ?: false // OFF by default
        }

    suspend fun setReduceAnimation(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[REDUCE_ANIMATION_KEY] = enabled
        }
    }

    // --- Lyrics Scroll Debug ---
    // Draws the auto-scroll's own view of the page over the lyrics: the band the
    // followed group is kept inside, the rows counted as being sung, and the move
    // the follow would make right now. A development aid for the scroll policy —
    // it changes nothing about playback or how the list is laid out.
    private val LYRICS_SCROLL_DEBUG_KEY =
        androidx.datastore.preferences.core.booleanPreferencesKey("lyrics_scroll_debug")

    val lyricsScrollDebugFlow: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[LYRICS_SCROLL_DEBUG_KEY] ?: false // OFF by default
        }

    suspend fun setLyricsScrollDebug(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[LYRICS_SCROLL_DEBUG_KEY] = enabled
        }
    }

    private val AUDIO_QUALITY_KEY = stringPreferencesKey("audio_quality")

    val audioQualityFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[AUDIO_QUALITY_KEY] ?: "HIGH" // LOW, NORMAL, HIGH
    }

    suspend fun setAudioQuality(quality: String) {
        context.dataStore.edit { preferences ->
            preferences[AUDIO_QUALITY_KEY] = quality
        }
    }

    // --- Per-network streaming quality -----------------------------------
    // the original model: the ceiling depends on the connection in hand, not on
    // a single global switch. The Wi-Fi setting is "whatever the sources can
    // do"; mobile data defaults one step down to protect a data plan. The
    // effective answer is derived per request — see PlayerManager.
    private val WIFI_QUALITY_KEY = stringPreferencesKey("streaming_quality_wifi")
    private val MOBILE_QUALITY_KEY = stringPreferencesKey("streaming_quality_mobile")
    private val JIOSAAVN_ENABLED_KEY =
        androidx.datastore.preferences.core.booleanPreferencesKey("source_jiosaavn_enabled")

    /** LOW, NORMAL, HIGH, BEST, LOSSLESS. LOSSLESS is only honoured when a lossless source is enabled. */
    val wifiQualityFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[WIFI_QUALITY_KEY] ?: "BEST"
    }

    val mobileQualityFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[MOBILE_QUALITY_KEY] ?: "NORMAL"
    }

    suspend fun setWifiQuality(quality: String) {
        context.dataStore.edit { preferences -> preferences[WIFI_QUALITY_KEY] = quality }
    }

    suspend fun setMobileQuality(quality: String) {
        context.dataStore.edit { preferences -> preferences[MOBILE_QUALITY_KEY] = quality }
    }

    /** Whether the better-rendition source (JioSaavn) may be contacted at all. */
    val jioSaavnEnabledFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[JIOSAAVN_ENABLED_KEY] ?: false
    }

    suspend fun setJioSaavnEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences -> preferences[JIOSAAVN_ENABLED_KEY] = enabled }
    }
}

