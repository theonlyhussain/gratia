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

    /**
     * Preferences left behind by the retired Smart Update feature.
     *
     * The feature is gone — its screen, its route, its switch and the periodic
     * check it drove. These two keys are the only trace it leaves on an existing
     * install, and they are deleted rather than merely unread: a stored
     * `smart_update_enabled = true` that nothing consults is dead state, and a
     * stored value that something *did* consult would be worse.
     */
    private val SMART_UPDATE_ENABLED_KEY = androidx.datastore.preferences.core.booleanPreferencesKey("smart_update_enabled")
    private val SMART_UPDATE_ONBOARDING_KEY = androidx.datastore.preferences.core.booleanPreferencesKey("smart_update_onboarding_shown")

    suspend fun clearObsoleteUpdatePreferences() {
        context.dataStore.edit { preferences ->
            preferences.remove(SMART_UPDATE_ENABLED_KEY)
            preferences.remove(SMART_UPDATE_ONBOARDING_KEY)
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

    /**
     * The AutoPlay default, persisted (spec 16/93).
     *
     * ON by default for remote playback: the Infinity button is not something
     * the listener should have to re-enable every session. The queue's pill
     * controls the current session; this is what a new session starts from.
     * Local-only playback never applies it — a local collection is not to be
     * silently replaced by remote radio.
     */
    private val AUTOPLAY_DEFAULT_KEY = androidx.datastore.preferences.core.booleanPreferencesKey("autoplay_default_enabled")

    val autoplayDefaultFlow: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[AUTOPLAY_DEFAULT_KEY] ?: true
        }

    suspend fun setAutoplayDefault(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[AUTOPLAY_DEFAULT_KEY] = enabled
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
    //
    // The full snapshot (current song, queue, position, origin, autoplay
    // tier) is one serialized document. The older id-only keys are kept
    // readable for exactly one thing: migrating an install that last saved
    // under the old scheme. New writes never touch them, and they are
    // removed as soon as a new-format snapshot exists.
    private val SAVED_SNAPSHOT_KEY = stringPreferencesKey("playback_snapshot_v1")
    private val SAVED_QUEUE_IDS_KEY = stringPreferencesKey("saved_queue_ids") // legacy, comma separated
    private val SAVED_CURRENT_SONG_ID_KEY = stringPreferencesKey("saved_current_song_id") // legacy
    private val SAVED_CURRENT_TIME_MS_KEY = androidx.datastore.preferences.core.longPreferencesKey("saved_current_time_ms") // legacy

    /** The raw serialized [com.gratia.music.player.PlaybackSnapshot], or null when nothing is persisted. */
    val savedPlaybackSnapshotJsonFlow: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[SAVED_SNAPSHOT_KEY]
    }

    /** Legacy id-based state, for migrating installs that saved under the old scheme. */
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

    /**
     * Persists the complete playback snapshot.
     *
     * DataStore writes only when the value actually changed, so the periodic
     * position tick costs nothing when the listener is paused and a plain
     * single-key rewrite when they are playing.
     */
    suspend fun savePlaybackSnapshot(snapshotJson: String) {
        context.dataStore.edit { preferences ->
            preferences[SAVED_SNAPSHOT_KEY] = snapshotJson
            // The new document replaces the id-only state outright.
            preferences.remove(SAVED_QUEUE_IDS_KEY)
            preferences.remove(SAVED_CURRENT_SONG_ID_KEY)
            preferences.remove(SAVED_CURRENT_TIME_MS_KEY)
        }
    }

    /**
     * Drops the persisted playback snapshot. Used when the saved source is
     * validated at startup and found gone: a snapshot pointing at a file
     * that no longer exists must not be restored, and must not linger to
     * point at nothing again next time.
     */
    suspend fun clearPlaybackSnapshot() {
        context.dataStore.edit { preferences ->
            preferences.remove(SAVED_SNAPSHOT_KEY)
            preferences.remove(SAVED_QUEUE_IDS_KEY)
            preferences.remove(SAVED_CURRENT_SONG_ID_KEY)
            preferences.remove(SAVED_CURRENT_TIME_MS_KEY)
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

    // --- Blur unfocused lyrics ---
    // Visual only, and separate from every other lyrics setting: it decides
    // whether lyrics away from the line being sung soften as they fall away.
    // Switching it off keeps the brightness hierarchy — the active line is
    // still the brightest — and stops only the blur, so sync, word animation
    // and auto-scroll are untouched either way.
    private val BLUR_UNFOCUSED_LYRICS_KEY =
        androidx.datastore.preferences.core.booleanPreferencesKey("blur_unfocused_lyrics")

    val blurUnfocusedLyricsFlow: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[BLUR_UNFOCUSED_LYRICS_KEY] ?: true // ON by default
        }

    suspend fun setBlurUnfocusedLyrics(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[BLUR_UNFOCUSED_LYRICS_KEY] = enabled
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

    // --- Developer mode ---
    // Gates the playback diagnostics surface. Nothing here changes playback; it
    // only decides whether the developer-facing screens are reachable, so a
    // normal install never sees them and a developer can turn them on in one
    // long-press instead of a rebuild.
    private val DEVELOPER_MODE_KEY =
        androidx.datastore.preferences.core.booleanPreferencesKey("developer_mode")

    val developerModeFlow: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[DEVELOPER_MODE_KEY] ?: false // OFF by default
        }

    suspend fun setDeveloperMode(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[DEVELOPER_MODE_KEY] = enabled
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

    // --- Download constraints (spec 37) -----------------------------------
    // Whether downloads wait for an unmetered network. Honoured at enqueue
    // time as a WorkManager constraint, so a queued download on mobile data
    // simply waits rather than silently burning the user's plan.
    private val WIFI_ONLY_DOWNLOADS_KEY = androidx.datastore.preferences.core.booleanPreferencesKey("downloads_wifi_only")

    val wifiOnlyDownloadsFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[WIFI_ONLY_DOWNLOADS_KEY] ?: false
    }

    suspend fun setWifiOnlyDownloads(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[WIFI_ONLY_DOWNLOADS_KEY] = enabled
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

    // --- Explore category artwork -------------------------------------------
    //
    // YouTube's mood/genre buttons carry no image of their own, so a category's
    // tile is drawn from the first cover the category's own page happens to
    // hold. That lookup is a full browse request, and paying it again on every
    // cold start is what made Explore arrive as a wall of gradients. The result
    // is small (an id, a URL and when it was found) and changes slowly, so it is
    // kept on disk; the image bytes are Coil's business, not ours.

    private val CATEGORY_ARTWORK_KEY = stringSetPreferencesKey("explore_category_artwork")

    /** One entry per category: `id\turl\tfetchedAtMs`. */
    data class CachedCategoryArtwork(val url: String, val fetchedAtMs: Long)

    val categoryArtworkFlow: Flow<Map<String, CachedCategoryArtwork>> = context.dataStore.data
        .map { preferences ->
            (preferences[CATEGORY_ARTWORK_KEY] ?: emptySet()).mapNotNull { entry ->
                val parts = entry.split('\t')
                if (parts.size < 3) return@mapNotNull null
                val at = parts[2].toLongOrNull() ?: return@mapNotNull null
                if (parts[0].isBlank() || parts[1].isBlank()) return@mapNotNull null
                parts[0] to CachedCategoryArtwork(parts[1], at)
            }.toMap()
        }

    /**
     * Writes the given resolutions, and drops entries for categories that are no
     * longer in the taxonomy — a removed mood should not keep a row on disk
     * forever.
     */
    suspend fun putCategoryArtwork(
        resolved: Map<String, CachedCategoryArtwork>,
        liveCategoryIds: Set<String>? = null,
    ) {
        if (resolved.isEmpty()) return
        context.dataStore.edit { preferences ->
            val existing = (preferences[CATEGORY_ARTWORK_KEY] ?: emptySet()).mapNotNull { entry ->
                val parts = entry.split('\t')
                if (parts.size < 3) return@mapNotNull null
                parts[0] to entry
            }.toMap().toMutableMap()

            resolved.forEach { (id, value) ->
                existing[id] = "$id\t${value.url}\t${value.fetchedAtMs}"
            }
            if (liveCategoryIds != null) {
                existing.keys.retainAll(liveCategoryIds)
            }
            preferences[CATEGORY_ARTWORK_KEY] = existing.values.toSet()
        }
    }
}

