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

/** User-configurable application settings. */
data class AppSettings(
    val autoSaveEnabled: Boolean = false,
    val autoSaveIntervalSeconds: Int = 60,
    val noteAuthor: String = "",
)

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsStore(context: Context) {

    private val dataStore = context.applicationContext.settingsDataStore

    val settings: Flow<AppSettings> = dataStore.data.map { prefs ->
        AppSettings(
            autoSaveEnabled = prefs[AUTO_SAVE_ENABLED] ?: false,
            autoSaveIntervalSeconds = prefs[AUTO_SAVE_INTERVAL] ?: 60,
            noteAuthor = prefs[NOTE_AUTHOR] ?: "",
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

    companion object {
        const val MIN_INTERVAL = 10
        const val MAX_INTERVAL = 3600
        private val AUTO_SAVE_ENABLED = booleanPreferencesKey("auto_save_enabled")
        private val AUTO_SAVE_INTERVAL = intPreferencesKey("auto_save_interval_seconds")
        private val NOTE_AUTHOR = stringPreferencesKey("note_author")
    }
}
