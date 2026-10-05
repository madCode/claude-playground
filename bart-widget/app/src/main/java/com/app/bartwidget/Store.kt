package com.app.bartwidget

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

class Store(private val data: DataStore<Preferences>) {
    /** Starred stations in the order they were starred; that's the order the widget shows them. */
    val starred: Flow<List<String>> = data.data.map { it.starredList() }

    /** Starred lines, as [lineKey]s ("MONT:ANTC"). */
    val lines: Flow<Set<String>> = data.data.map { it.list(LINES).toSet() }

    val snapshot: Flow<Snapshot> = data.data.map { p ->
        // An unreadable snapshot (an older format, say) is only a cache: start again.
        p[SNAPSHOT]?.let { runCatching { json.decodeFromString<Snapshot>(it) }.getOrNull() } ?: Snapshot()
    }

    suspend fun toggle(abbr: String) {
        data.edit { p -> p[STARRED] = toggleStar(p.starredList(), abbr).joinToString(",") }
    }

    suspend fun toggleLine(key: String) {
        data.edit { p -> p[LINES] = toggleStar(p.list(LINES), key).joinToString(",") }
    }

    suspend fun saveSnapshot(snapshot: Snapshot) {
        data.edit { it[SNAPSHOT] = json.encodeToString(Snapshot.serializer(), snapshot) }
    }

    private fun Preferences.starredList() = list(STARRED)

    private fun Preferences.list(key: Preferences.Key<String>) = this[key]?.split(",")?.filter { it.isNotEmpty() } ?: emptyList()

    private companion object {
        val STARRED = stringPreferencesKey("starred")
        val SNAPSHOT = stringPreferencesKey("snapshot")
        val LINES = stringPreferencesKey("lines")
        val json = Json { ignoreUnknownKeys = true }
    }
}
