package com.example.fdxwriter.data.repo

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** A recently opened FDX file, identified by its persisted content URI. */
data class RecentFile(val uri: String, val name: String)

private val Context.recentFilesDataStore: DataStore<Preferences> by preferencesDataStore(name = "recent_files")

class RecentFilesStore(context: Context) {

    private val dataStore = context.applicationContext.recentFilesDataStore

    val recentFiles: Flow<List<RecentFile>> = dataStore.data.map { prefs -> decode(prefs[RECENT] ?: "") }

    suspend fun add(uri: String, name: String) {
        dataStore.edit { prefs ->
            val current = decode(prefs[RECENT] ?: "")
            val updated = (listOf(RecentFile(uri, name)) + current.filterNot { it.uri == uri }).take(MAX)
            prefs[RECENT] = encode(updated)
        }
    }

    suspend fun remove(uri: String) {
        dataStore.edit { prefs ->
            prefs[RECENT] = encode(decode(prefs[RECENT] ?: "").filterNot { it.uri == uri })
        }
    }

    private fun encode(list: List<RecentFile>): String =
        list.joinToString("\n") { "${it.uri}\t${it.name}" }

    private fun decode(raw: String): List<RecentFile> =
        raw.split("\n").filter { it.isNotBlank() }.mapNotNull { line ->
            val parts = line.split("\t", limit = 2)
            if (parts.size == 2) RecentFile(parts[0], parts[1]) else null
        }

    companion object {
        private const val MAX = 10
        private val RECENT = stringPreferencesKey("recent")
    }
}
