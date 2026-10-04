package com.app.jekyllposter.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.app.jekyllposter.core.github.CommitAuthor
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
    private val removeTrackingKey = booleanPreferencesKey("remove_tracking_codes")
    private val noReplyKey = booleanPreferencesKey("commit_as_no_reply")
    private val noReplyLoginKey = stringPreferencesKey("no_reply_login")
    private val noReplyNameKey = stringPreferencesKey("no_reply_name")
    private val noReplyEmailKey = stringPreferencesKey("no_reply_email")

    /** Links shared into the app lose their tracking codes, in the editor where the writer sees it. */
    val removeTrackingCodes: Flow<Boolean> = store.data.map { it[removeTrackingKey] ?: false }

    suspend fun removeTrackingCodes(): Boolean = removeTrackingCodes.first()

    suspend fun setRemoveTrackingCodes(remove: Boolean) {
        store.edit { it[removeTrackingKey] = remove }
    }

    /** Commits name the account's no-reply address, whatever its GitHub email settings. */
    val commitAsNoReply: Flow<Boolean> = store.data.map { it[noReplyKey] ?: false }

    suspend fun commitAsNoReply(): Boolean = commitAsNoReply.first()

    /**
     * Turns the no-reply author on with [author], the address of the account [login], kept so
     * publishing needs no extra call to GitHub; or off, with null. The address stays when off.
     */
    suspend fun setCommitAsNoReply(login: String, author: CommitAuthor?) {
        store.edit {
            it[noReplyKey] = author != null
            if (author != null) {
                // One edit, so the switch and the address it uses never disagree.
                it[noReplyLoginKey] = login
                it[noReplyNameKey] = author.name
                it[noReplyEmailKey] = author.email
            }
        }
    }

    /**
     * Who [login]'s commits are by: null for GitHub's default. When the switch is on but the kept
     * address is another account's (signed in as someone else since), [lookUp] finds this one's,
     * which is kept without touching the switch: the writer may have turned it off meanwhile.
     */
    suspend fun commitAuthor(login: String, lookUp: suspend () -> CommitAuthor?): CommitAuthor? {
        val prefs = store.data.first()
        if (prefs[noReplyKey] != true) return null
        val name = prefs[noReplyNameKey]
        val email = prefs[noReplyEmailKey]
        if (prefs[noReplyLoginKey] == login && name != null && email != null) return CommitAuthor(name, email)
        val found = lookUp() ?: throw NoAddress(login)
        store.edit {
            it[noReplyLoginKey] = login
            it[noReplyNameKey] = found.name
            it[noReplyEmailKey] = found.email
        }
        return found
    }

    /** GitHub gave no account id to build [login]'s no-reply address from. */
    class NoAddress(login: String) : Exception("No no-reply address for $login")
}
