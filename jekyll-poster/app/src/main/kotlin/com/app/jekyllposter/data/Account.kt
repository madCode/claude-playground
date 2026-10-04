package com.app.jekyllposter.data

import android.content.Context
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.app.jekyllposter.core.github.DeviceFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    /** From "Sign in with GitHub": renews [token] before it expires at [expiresAt] (epoch ms). */
    val refreshToken: String? = null,
    val expiresAt: Long? = null,
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
class AccountStore(
    private val store: DataStore<Preferences>,
    private val cipher: SecretCipher,
    /** Renews a "Sign in with GitHub" token; absent when the app has no GitHub App configured. */
    private val refresh: (suspend (String) -> DeviceFlow.Tokens)? = null,
    private val now: () -> Long = System::currentTimeMillis,
) {
    val account: Flow<Account?> = store.data.map { read(it) }
    /**
     * Held while renewing, saving and signing out, so a renewal that finishes after the writer
     * signed out or picked another blog can't write the old account back.
     */
    private val lock = Mutex()

    /**
     * The account, its token renewed first if it expires within five minutes. If renewing fails
     * the old token is returned, and GitHub's answer to it says to sign in again.
     */
    suspend fun current(): Account? = lock.withLock {
        val account = account.first() ?: return null
        val expiresAt = account.expiresAt ?: return account
        val refreshToken = account.refreshToken ?: return account
        if (expiresAt - now() > 5 * 60_000) return account
        val tokens = runCatching { refresh?.invoke(refreshToken) }.getOrNull() ?: return account
        val renewed = account.withTokens(tokens, now())
        write(renewed)
        renewed
    }

    suspend fun save(account: Account) = lock.withLock { write(account) }

    private suspend fun write(account: Account) {
        val sealed = Base64.encodeToString(cipher.encrypt(account.token.toByteArray()), Base64.NO_WRAP)
        val sealedRefresh = account.refreshToken?.let { Base64.encodeToString(cipher.encrypt(it.toByteArray()), Base64.NO_WRAP) }
        store.edit {
            it[LOGIN] = account.login
            it[TOKEN] = sealed
            if (sealedRefresh != null) it[REFRESH] = sealedRefresh else it.remove(REFRESH)
            if (account.expiresAt != null) it[EXPIRES] = account.expiresAt else it.remove(EXPIRES)
            it[OWNER] = account.owner
            it[REPO] = account.repo
            it[BRANCH] = account.branch
            if (account.siteUrl != null) it[SITE_URL] = account.siteUrl else it.remove(SITE_URL)
        }
    }

    suspend fun signOut() = lock.withLock {
        store.edit { it.clear() }
        Unit
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
            refreshToken = prefs[REFRESH]?.let { runCatching { String(cipher.decrypt(Base64.decode(it, Base64.NO_WRAP))) }.getOrNull() },
            expiresAt = prefs[EXPIRES],
        )
    }

    private companion object {
        val LOGIN = stringPreferencesKey("login")
        val TOKEN = stringPreferencesKey("token")
        val OWNER = stringPreferencesKey("owner")
        val REPO = stringPreferencesKey("repo")
        val BRANCH = stringPreferencesKey("branch")
        val SITE_URL = stringPreferencesKey("site_url")
        val REFRESH = stringPreferencesKey("refresh_token")
        val EXPIRES = longPreferencesKey("expires_at")
    }
}

/** This account with new tokens from the device flow, their expiry counted from [now]. */
fun Account.withTokens(tokens: DeviceFlow.Tokens, now: Long) = copy(
    token = tokens.accessToken,
    refreshToken = tokens.refreshToken ?: refreshToken,
    expiresAt = tokens.expiresIn?.let { now + it * 1000 },
)
