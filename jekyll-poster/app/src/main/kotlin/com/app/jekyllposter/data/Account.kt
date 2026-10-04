package com.app.jekyllposter.data

import android.content.Context
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Who is signed in and which blog they post to. */
data class Account(
    val login: String,
    val token: String,
    val owner: String,
    val repo: String,
    val branch: String,
    /** The blog's address, when GitHub Pages reports one; for "View post" links. */
    val siteUrl: String? = null,
) {
    val repoName: String get() = "$owner/$repo"

    /** Which blog a draft belongs to: `owner/repo@branch`. */
    val blogKey: String get() = "$owner/$repo@$branch"
}

val Context.accountDataStore: DataStore<Preferences> by preferencesDataStore(name = "account")

/**
 * The account, with its token sealed by [cipher]. A token that can't be unsealed (the Keystore
 * key went away, e.g. after a restore onto a new phone) reads as signed out, not as a crash.
 */
class AccountStore(private val store: DataStore<Preferences>, private val cipher: SecretCipher) {
    val account: Flow<Account?> = store.data.map { read(it) }

    suspend fun current(): Account? = account.first()

    suspend fun save(account: Account) {
        val sealed = Base64.encodeToString(cipher.encrypt(account.token.toByteArray()), Base64.NO_WRAP)
        store.edit {
            it[LOGIN] = account.login
            it[TOKEN] = sealed
            it[OWNER] = account.owner
            it[REPO] = account.repo
            it[BRANCH] = account.branch
            if (account.siteUrl != null) it[SITE_URL] = account.siteUrl else it.remove(SITE_URL)
        }
    }

    suspend fun signOut() {
        store.edit { it.clear() }
    }

    private fun read(prefs: Preferences): Account? {
        val token = prefs[TOKEN]?.let { sealed ->
            runCatching { String(cipher.decrypt(Base64.decode(sealed, Base64.NO_WRAP))) }.getOrNull()
        } ?: return null
        return Account(
            login = prefs[LOGIN] ?: return null,
            token = token,
            owner = prefs[OWNER] ?: return null,
            repo = prefs[REPO] ?: return null,
            branch = prefs[BRANCH] ?: return null,
            siteUrl = prefs[SITE_URL],
        )
    }

    private companion object {
        val LOGIN = stringPreferencesKey("login")
        val TOKEN = stringPreferencesKey("token")
        val OWNER = stringPreferencesKey("owner")
        val REPO = stringPreferencesKey("repo")
        val BRANCH = stringPreferencesKey("branch")
        val SITE_URL = stringPreferencesKey("site_url")
    }
}
