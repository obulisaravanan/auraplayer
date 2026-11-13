package com.mymusic.player.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mymusic.player.model.PlayedThreshold
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_settings")

enum class ThemeMode {
    SYSTEM,
    DARK,
    LIGHT
}

enum class SongSortOrder {
    TITLE,
    ARTIST,
    ALBUM,
    DATE_ADDED,
    DURATION,
    PLAY_COUNT
}

class UserPreferencesRepository(private val context: Context) {

    private object PreferencesKeys {
        val PLAYED_THRESHOLD = stringPreferencesKey("played_threshold")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val SORT_ORDER = stringPreferencesKey("sort_order")
        val SORT_ASCENDING = booleanPreferencesKey("sort_ascending")
        val SLEEP_TIMER_DEFAULT = intPreferencesKey("sleep_timer_default")
        val EQUALIZER_ENABLED = booleanPreferencesKey("equalizer_enabled")
        val EQUALIZER_PRESET = stringPreferencesKey("equalizer_preset")
        val BASS_BOOST = intPreferencesKey("bass_boost")
        val LAST_PLAYED_SONG_ID = longPreferencesKey("last_played_song_id")
        val LAST_PLAYED_POSITION = longPreferencesKey("last_played_position")
        val LAST_PLAYED_QUEUE_IDS = stringPreferencesKey("last_played_queue_ids")
        val AUTO_FETCH_ONLINE_ARTWORK = booleanPreferencesKey("auto_fetch_online_artwork")
    }

    val autoFetchOnlineArtworkFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.AUTO_FETCH_ONLINE_ARTWORK] ?: true
    }

    val playedThresholdFlow: Flow<PlayedThreshold> = context.dataStore.data.map { preferences ->
        val raw = preferences[PreferencesKeys.PLAYED_THRESHOLD] ?: PlayedThreshold.SECONDS_30.name
        try {
            PlayedThreshold.valueOf(raw)
        } catch (e: Exception) {
            PlayedThreshold.SECONDS_30
        }
    }

    val themeModeFlow: Flow<ThemeMode> = context.dataStore.data.map { preferences ->
        val raw = preferences[PreferencesKeys.THEME_MODE] ?: ThemeMode.DARK.name
        try {
            ThemeMode.valueOf(raw)
        } catch (e: Exception) {
            ThemeMode.DARK
        }
    }

    val sortOrderFlow: Flow<SongSortOrder> = context.dataStore.data.map { preferences ->
        val raw = preferences[PreferencesKeys.SORT_ORDER] ?: SongSortOrder.TITLE.name
        try {
            SongSortOrder.valueOf(raw)
        } catch (e: Exception) {
            SongSortOrder.TITLE
        }
    }

    val sortAscendingFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.SORT_ASCENDING] ?: true
    }

    val equalizerEnabledFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.EQUALIZER_ENABLED] ?: false
    }

    val equalizerPresetFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.EQUALIZER_PRESET] ?: "Normal"
    }

    val bassBoostFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.BASS_BOOST] ?: 0
    }

    val lastPlayedSongIdFlow: Flow<Long> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.LAST_PLAYED_SONG_ID] ?: -1L
    }

    val lastPlayedPositionFlow: Flow<Long> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.LAST_PLAYED_POSITION] ?: 0L
    }

    val lastPlayedQueueIdsFlow: Flow<List<Long>> = context.dataStore.data.map { preferences ->
        val raw = preferences[PreferencesKeys.LAST_PLAYED_QUEUE_IDS] ?: ""
        if (raw.isBlank()) emptyList()
        else raw.split(",").mapNotNull { it.trim().toLongOrNull() }
    }

    suspend fun setPlayedThreshold(threshold: PlayedThreshold) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.PLAYED_THRESHOLD] = threshold.name
        }
    }

    suspend fun setThemeMode(theme: ThemeMode) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.THEME_MODE] = theme.name
        }
    }

    suspend fun setSortOrder(order: SongSortOrder, ascending: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SORT_ORDER] = order.name
            preferences[PreferencesKeys.SORT_ASCENDING] = ascending
        }
    }

    suspend fun setEqualizerEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.EQUALIZER_ENABLED] = enabled
        }
    }

    suspend fun setEqualizerPreset(preset: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.EQUALIZER_PRESET] = preset
        }
    }

    suspend fun setBassBoost(level: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.BASS_BOOST] = level
        }
    }

    suspend fun saveLastPlayback(songId: Long, positionMs: Long, queueIds: List<Long>? = null) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_PLAYED_SONG_ID] = songId
            preferences[PreferencesKeys.LAST_PLAYED_POSITION] = positionMs
            if (queueIds != null) {
                preferences[PreferencesKeys.LAST_PLAYED_QUEUE_IDS] = queueIds.joinToString(",")
            }
        }
    }

    suspend fun setAutoFetchOnlineArtwork(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.AUTO_FETCH_ONLINE_ARTWORK] = enabled
        }
    }
}
