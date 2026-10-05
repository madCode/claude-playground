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

    val snapshot: Flow<Snapshot> = data.data.map { p ->
        // An unreadable snapshot (an older format, say) is only a cache: start again.
        p[SNAPSHOT]?.let { runCatching { json.decodeFromString<Snapshot>(it) }.getOrNull() } ?: Snapshot()
    }

    suspend fun toggle(abbr: String) {
        data.edit { p -> p[STARRED] = toggleStar(p.starredList(), abbr).joinToString(",") }
    }

    suspend fun saveSnapshot(snapshot: Snapshot) {
        data.edit { it[SNAPSHOT] = json.encodeToString(Snapshot.serializer(), snapshot) }
    }

    private fun Preferences.starredList() = this[STARRED]?.split(",")?.filter { it.isNotEmpty() } ?: emptyList()

    private companion object {
        val STARRED = stringPreferencesKey("starred")
        val SNAPSHOT = stringPreferencesKey("snapshot")
        val json = Json { ignoreUnknownKeys = true }
    }
}
