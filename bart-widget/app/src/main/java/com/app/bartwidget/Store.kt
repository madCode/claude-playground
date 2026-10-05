package com.app.bartwidget

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore("bart")

object Store {
    private val STARRED = stringPreferencesKey("starred")
    private val SNAPSHOT = stringPreferencesKey("snapshot")
    private val json = Json { ignoreUnknownKeys = true }

    /** Starred stations in the order they were starred; that's the order the widget shows them. */
    fun starred(context: Context): Flow<List<String>> =
        context.dataStore.data.map { p -> p[STARRED]?.split(",")?.filter { it.isNotEmpty() } ?: emptyList() }

    suspend fun toggle(context: Context, abbr: String) {
        context.dataStore.edit { p ->
            val now = p[STARRED]?.split(",")?.filter { it.isNotEmpty() } ?: emptyList()
            p[STARRED] = toggleStar(now, abbr).joinToString(",")
        }
    }

    fun snapshot(context: Context): Flow<Snapshot> = context.dataStore.data.map { p ->
        p[SNAPSHOT]?.let { runCatching { json.decodeFromString<Snapshot>(it) }.getOrNull() } ?: Snapshot()
    }

    suspend fun saveSnapshot(context: Context, snapshot: Snapshot) {
        context.dataStore.edit { it[SNAPSHOT] = json.encodeToString(Snapshot.serializer(), snapshot) }
    }
}
