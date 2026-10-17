package com.app.jekyllposter.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** What the app keeps for every blog: the Obsidian vault folder. Each blog's switches are in [Privacy]. */
class Settings(private val store: DataStore<Preferences>) {
    private val vaultKey = stringPreferencesKey("obsidian_vault")

    /** The Obsidian vault folder (a document tree URI) photos in shared notes are found in. */
    val obsidianVault: Flow<String?> = store.data.map { it[vaultKey] }

    suspend fun obsidianVault(): String? = obsidianVault.first()

    suspend fun setObsidianVault(tree: String?) {
        store.edit { if (tree == null) it.remove(vaultKey) else it[vaultKey] = tree }
    }
}
