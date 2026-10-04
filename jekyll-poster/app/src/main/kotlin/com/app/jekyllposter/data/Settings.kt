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

/**
 * The writer's privacy choices. Each is off by default, so the app does what GitHub and Jekyll
 * would on their own and never changes what was written; turning one on tightens things.
 */
class Settings(private val store: DataStore<Preferences>) {
    // A new name for the opposite default: a stored "keep" from before mustn't read as "remove".
    private val removeTrackingKey = booleanPreferencesKey("remove_tracking_codes")
    private val noReplyKey = booleanPreferencesKey("commit_as_no_reply")

    /** Links shared into the app lose their tracking codes, in the editor where the writer sees it. */
    val removeTrackingCodes: Flow<Boolean> = store.data.map { it[removeTrackingKey] ?: false }

    suspend fun removeTrackingCodes(): Boolean = removeTrackingCodes.first()

    suspend fun setRemoveTrackingCodes(remove: Boolean) {
        store.edit { it[removeTrackingKey] = remove }
    }

    /** Commits name the account's no-reply address, whatever its GitHub email settings. */
    val commitAsNoReply: Flow<Boolean> = store.data.map { it[noReplyKey] ?: false }

    suspend fun commitAsNoReply(): Boolean = commitAsNoReply.first()

    suspend fun setCommitAsNoReply(noReply: Boolean) {
        store.edit { it[noReplyKey] = noReply }
    }
}
