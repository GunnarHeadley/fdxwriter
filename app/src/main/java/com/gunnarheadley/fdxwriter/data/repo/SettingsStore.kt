package com.gunnarheadley.fdxwriter.data.repo

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** How the app chooses between the light and dark colour schemes. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** User-configurable application settings. */
data class AppSettings(
    val autoSaveEnabled: Boolean = false,
    val autoSaveIntervalSeconds: Int = 60,
    val noteAuthor: String = "",
    val characterColorsEnabled: Boolean = true,
    val editorFontSize: Int = 16,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
)

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsStore(context: Context) {

    private val dataStore = context.applicationContext.settingsDataStore

    val settings: Flow<AppSettings> = dataStore.data.map { prefs ->
        AppSettings(
            autoSaveEnabled = prefs[AUTO_SAVE_ENABLED] ?: false,
            autoSaveIntervalSeconds = prefs[AUTO_SAVE_INTERVAL] ?: 60,
            noteAuthor = prefs[NOTE_AUTHOR] ?: "",
            characterColorsEnabled = prefs[CHARACTER_COLORS] ?: true,
            editorFontSize = prefs[EDITOR_FONT_SIZE] ?: 16,
            themeMode = prefs[THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
        )
    }

    suspend fun setAutoSaveEnabled(enabled: Boolean) {
        dataStore.edit { it[AUTO_SAVE_ENABLED] = enabled }
    }

    suspend fun setAutoSaveIntervalSeconds(seconds: Int) {
        dataStore.edit { it[AUTO_SAVE_INTERVAL] = seconds.coerceIn(MIN_INTERVAL, MAX_INTERVAL) }
    }

    suspend fun setNoteAuthor(author: String) {
        dataStore.edit { it[NOTE_AUTHOR] = author }
    }

    suspend fun setCharacterColorsEnabled(enabled: Boolean) {
        dataStore.edit { it[CHARACTER_COLORS] = enabled }
    }

    suspend fun setEditorFontSize(size: Int) {
        dataStore.edit { it[EDITOR_FONT_SIZE] = size.coerceIn(MIN_FONT, MAX_FONT) }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[THEME_MODE] = mode.name }
    }

    companion object {
        const val MIN_INTERVAL = 10
        const val MAX_INTERVAL = 3600
        const val MIN_FONT = 12
        const val MAX_FONT = 28
        private val AUTO_SAVE_ENABLED = booleanPreferencesKey("auto_save_enabled")
        private val AUTO_SAVE_INTERVAL = intPreferencesKey("auto_save_interval_seconds")
        private val NOTE_AUTHOR = stringPreferencesKey("note_author")
        private val CHARACTER_COLORS = booleanPreferencesKey("character_colors_enabled")
        private val EDITOR_FONT_SIZE = intPreferencesKey("editor_font_size")
        private val THEME_MODE = stringPreferencesKey("theme_mode")
    }
}
