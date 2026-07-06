package com.gunnarheadley.fdxwriter.data.repo

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Where the editor was last scrolled for a file: [index] is the first visible paragraph. */
data class EditorPosition(val index: Int, val offset: Int)

private val Context.editorPositionsDataStore: DataStore<Preferences> by preferencesDataStore(name = "editor_positions")

/** Remembers, per file URI, the editor scroll position so reopening a script resumes in place. */
class EditorPositionStore(context: Context) {

    private val dataStore = context.applicationContext.editorPositionsDataStore

    /** The saved position for [uri], or null if none was recorded. */
    suspend fun get(uri: String): EditorPosition? {
        val raw = dataStore.data.map { it[stringPreferencesKey(uri)] }.first() ?: return null
        val parts = raw.split(":")
        val index = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val offset = parts.getOrNull(1)?.toIntOrNull() ?: 0
        return EditorPosition(index, offset)
    }

    /** Record [position] as the latest scroll location for [uri]. */
    suspend fun set(uri: String, position: EditorPosition) {
        dataStore.edit { it[stringPreferencesKey(uri)] = "${position.index}:${position.offset}" }
    }
}
