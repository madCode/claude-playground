package com.app.jekyllposter.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** The writer's choices that aren't about the blog. */
class Settings(private val store: DataStore<Preferences>) {
    private val keepTrackingKey = booleanPreferencesKey("keep_tracking_codes")

    /** Off by default: links shared in lose their tracking codes, unless the writer needs them. */
    val keepTrackingCodes: Flow<Boolean> = store.data.map { it[keepTrackingKey] ?: false }

    suspend fun keepTrackingCodes(): Boolean = keepTrackingCodes.first()

    suspend fun setKeepTrackingCodes(keep: Boolean) {
        store.edit { it[keepTrackingKey] = keep }
    }
}
