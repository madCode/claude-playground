package com.app.jekyllposter.data

import android.content.Context
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.app.jekyllposter.core.github.DeviceFlow
import java.io.IOException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** A blog and the sign-in that posts to it. */
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
    /**
     * Made with "Sign in with GitHub", not a pasted token. Its tokens may not expire, and then come
     * with no [refreshToken].
     */
    val withGitHub: Boolean = refreshToken != null,
) {
    val repoName: String get() = "$owner/$repo"

    /** Which blog a draft belongs to: `owner/repo@branch`. */
    val blogKey: String get() = blogKey(owner, repo, branch)
}

/** A blog the app can post to, with the sign-in it posts with, as the blog switcher lists it. */
data class BlogEntry(
    val key: String,
    val owner: String,
    val repo: String,
    val branch: String,
    /** The site's own title, once the blog has been read. */
    val title: String?,
    val signInId: Long,
    val login: String,
    val withGitHub: Boolean,
) {
    val repoName: String get() = "$owner/$repo"
}

/** What removing a sign-in did. */
sealed interface Removal {
    /** Removed, with the keys of the blogs that went with it. */
    data class Removed(val blogs: List<String>) : Removal

    /** Not removed: these posts are still to go out to its blogs, and would fail without it. */
    data class Refused(val waiting: List<Draft>) : Removal
}

/** The key a blog is known by, in drafts, the post cache and [SavedBlog]: `owner/repo@branch`. */
fun blogKey(owner: String, repo: String, branch: String) = "$owner/$repo@$branch"

/** Holds which blog is open, and, until it's moved into the database, the account from before. */
val Context.accountDataStore: DataStore<Preferences> by preferencesDataStore(name = "account")

/**
 * The writer's sign-ins and blogs, from the database, and which blog is open, from [store]. Each
 * is read as an [Account]: a blog with its sign-in. A token that can't be unsealed by [cipher]
 * (the Keystore key went away, e.g. after a move to a new phone) reads as signed out, not as a crash.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AccountStore(
    private val store: DataStore<Preferences>,
    private val dao: AccountDao,
    private val cipher: SecretCipher,
    /**
     * Renews a "Sign in with GitHub" token, for blog [String]'s sign-in, so the request goes the
     * way that sign-in's blogs ask; absent when the app has no GitHub App configured.
     */
    private val refresh: (suspend (key: String, refreshToken: String) -> DeviceFlow.Tokens)? = null,
    private val now: () -> Long = System::currentTimeMillis,
) {
    /** The open blog's key ([Account.blogKey]), or null when signed out. */
    val currentKey: Flow<String?> = flow {
        imported()
        emitAll(store.data.map { it[CURRENT] }.distinctUntilChanged())
    }

    /** The open blog and its sign-in. */
    val account: Flow<Account?> = currentKey.flatMapLatest { key ->
        if (key == null) {
            flowOf(null)
        } else {
            dao.watch(key).map { it?.let(::read) }
                // Removing the open blog moves the key first, but Room can report the row gone
                // before the new key reaches this flow. That null isn't "signed out": the next blog follows.
                .filter { it != null || stillOpen(key) }
        }
    }

    /** Every blog, grouped by the sign-in it posts with. */
    val blogs: Flow<List<BlogEntry>> = flow {
        imported()
        emitAll(
            dao.all().map { rows ->
                rows.mapNotNull { row ->
                    val b = row.blog
                    row.signIn?.let { BlogEntry(b.blogKey, b.owner, b.repo, b.branch, b.title, b.signInId, it.login, it.kind == SignInKind.GitHubApp) }
                }
            },
        )
    }

    /**
     * Held while renewing, saving and signing out, so a renewal that finishes after the writer
     * signed out can't write the old sign-in back.
     */
    private val lock = Mutex()

    suspend fun currentKey(): String? = currentKey.first()

    /** Whether [key] is still the open blog; on a store that can't be read, taken as yes. */
    private suspend fun stillOpen(key: String): Boolean = try {
        store.data.first()[CURRENT] == key
    } catch (e: IOException) {
        true
    }

    /**
     * The open blog's account, its token renewed first if it expires within five minutes. If
     * renewing fails the old token is returned, and GitHub's answer to it says to sign in again.
     */
    suspend fun current(): Account? = lock.withLock { currentKey()?.let { renewed(it) } }

    /**
     * Blog [key]'s account, open or not, renewed as [current]'s is; null when it has no sign-in
     * any more (signed out).
     */
    suspend fun forBlog(key: String): Account? = lock.withLock {
        imported()
        renewed(key)
    }

    private suspend fun renewed(key: String): Account? {
        val row = dao.get(key) ?: return null
        val account = read(row) ?: return null
        val expiresAt = account.expiresAt ?: return account
        val refreshToken = account.refreshToken ?: return account
        if (expiresAt - now() > 5 * 60_000) return account
        // Not cancellable: GitHub replaces the refresh token as it answers, so an answer dropped
        // midway, or not kept, would leave a dead one and the writer signing in again.
        return withContext(NonCancellable) {
            val tokens = runCatching { refresh?.invoke(key, refreshToken) }.getOrNull() ?: return@withContext account
            val renewed = account.withTokens(tokens, now())
            dao.update(sealed(renewed).copy(id = row.blog.signInId))
            renewed
        }
    }

    /**
     * A fresh sign-in to [account]'s blog, which becomes the open one. An account already signed
     * in the same way keeps its one sign-in, with the new tokens: any "Sign in with GitHub" token
     * reaches what the app was given, but a pasted token reaches only the repositories it was made
     * for, so only the same token is the same sign-in.
     */
    suspend fun save(account: Account) = lock.withLock {
        imported()
        val signIn = sealed(account)
        val same = dao.signInsFor(account.login, signIn.kind)
            .lastOrNull { signIn.kind == SignInKind.GitHubApp || unseal(it.sealedToken) == account.token }
        dao.signInTo(signIn, account.savedBlog(), reuse = same?.id)
        store.edit { it[CURRENT] = account.blogKey }
        Unit
    }

    /** Opens blog [key], one already added. False when it has gone meanwhile. */
    suspend fun open(key: String): Boolean = lock.withLock {
        if (dao.blog(key) == null) return@withLock false
        store.edit { it[CURRENT] = key }
        true
    }

    /**
     * A sign-in's account, through any of its blogs, its token renewed as [current]'s is: for
     * listing the repositories it can add. Null when it was removed meanwhile.
     */
    suspend fun forSignIn(signInId: Long): Account? = lock.withLock {
        dao.blogsOf(signInId).firstOrNull()?.let { renewed(it) }
    }

    /**
     * Adds a blog under sign-in [signInId], and opens it. Every other blog stays, so posts still
     * waiting for them go out to them. False when the sign-in was removed meanwhile.
     */
    suspend fun addBlog(signInId: Long, owner: String, repo: String, branch: String, siteUrl: String?): Boolean = lock.withLock {
        if (dao.signIn(signInId) == null) return@withLock false
        val key = blogKey(owner, repo, branch)
        dao.upsert(SavedBlog(key, owner, repo, branch, siteUrl, signInId, title = dao.blog(key)?.title))
        // The blog may have had another sign-in, now unused: one left from before a move to this
        // phone, or one the writer is moving it away from.
        dao.deleteUnused()
        store.edit { it[CURRENT] = key }
        true
    }

    /**
     * Removes sign-in [signInId] with every blog it posts to, unless a post is still to go out to
     * one of them. Drafts stay. If the open blog went, another opens, or none is left.
     */
    suspend fun remove(signInId: Long): Removal = withContext(NonCancellable) {
        // Not cancellable: stopped between its steps, the open blog could be left as one removed.
        lock.withLock {
            dao.waitingFor(signInId).takeIf { it.isNotEmpty() }?.let { return@withLock Removal.Refused(it) }
            val open = currentKey()
            val leaving = open != null && open in dao.blogsOf(signInId)
            // The open blog moves first: with its row gone and the key not yet moved, the app
            // would read as signed out.
            if (leaving) {
                val next = dao.firstBlogNotOf(signInId)
                store.edit { if (next == null) it.remove(CURRENT) else it[CURRENT] = next }
            }
            val removed = dao.removeUnlessWaiting(signInId)
            if (removed == null) {
                // A post was queued since the check above: the sign-in stays, and so does its blog.
                if (leaving) store.edit { it[CURRENT] = open!! }
                return@withLock Removal.Refused(dao.waitingFor(signInId))
            }
            Removal.Removed(removed)
        }
    }

    /** Keeps blog [key]'s title, as its `_config.yml` gave it. */
    suspend fun setTitle(key: String, title: String?) = dao.setTitle(key, title)

    /** Whether blog [key] still has a sign-in. */
    suspend fun has(key: String): Boolean = dao.blog(key) != null

    private fun sealed(account: Account) = SignIn(
        login = account.login,
        kind = if (account.withGitHub) SignInKind.GitHubApp else SignInKind.Token,
        sealedToken = seal(account.token),
        sealedRefreshToken = account.refreshToken?.let(::seal),
        expiresAt = account.expiresAt,
    )

    private fun Account.savedBlog() = SavedBlog(blogKey, owner, repo, branch, siteUrl, signInId = 0)

    private fun read(row: BlogWithSignIn): Account? {
        val signIn = row.signIn ?: return null
        val blog = row.blog
        return Account(
            login = signIn.login,
            token = unseal(signIn.sealedToken) ?: return null,
            owner = blog.owner,
            repo = blog.repo,
            branch = blog.branch,
            siteUrl = blog.siteUrl,
            refreshToken = signIn.sealedRefreshToken?.let(::unseal),
            expiresAt = signIn.expiresAt,
            withGitHub = signIn.kind == SignInKind.GitHubApp,
        )
    }

    private fun seal(secret: String) = Base64.encodeToString(cipher.encrypt(secret.toByteArray()), Base64.NO_WRAP)

    private fun unseal(sealed: String) = runCatching { String(cipher.decrypt(Base64.decode(sealed, Base64.NO_WRAP))) }.getOrNull()

    private val importLock = Mutex()

    @Volatile private var importDone = false

    /**
     * Moves the account kept in [store] before sign-ins had a table into the database, once, before
     * anything reads the account. Its sealed tokens are copied as they are. The database is written
     * first and the old record cleared after: stopped in between, the next start writes the same
     * rows again ([AccountDao.importAccount] doesn't duplicate them) and then clears it.
     */
    private suspend fun imported() {
        if (importDone) return
        importLock.withLock {
            if (importDone) return
            val prefs = store.data.first()
            val token = prefs[TOKEN]
            val login = prefs[LOGIN]
            val owner = prefs[OWNER]
            val repo = prefs[REPO]
            val branch = prefs[BRANCH]
            if (token != null && login != null && owner != null && repo != null && branch != null) {
                val refreshToken = prefs[REFRESH]
                val signIn = SignIn(
                    login = login,
                    kind = if (refreshToken != null) SignInKind.GitHubApp else SignInKind.Token,
                    sealedToken = token, sealedRefreshToken = refreshToken, expiresAt = prefs[EXPIRES],
                )
                val blog = SavedBlog(blogKey(owner, repo, branch), owner, repo, branch, prefs[SITE_URL], signInId = 0)
                dao.importAccount(signIn, blog)
                store.edit { p ->
                    p[CURRENT] = blog.blogKey
                    OLD_KEYS.forEach { p.remove(it) }
                }
            }
            importDone = true
        }
    }

    private companion object {
        val CURRENT = stringPreferencesKey("current_blog")

        // The account as kept before sign-ins had a table; read only to move it there.
        val LOGIN = stringPreferencesKey("login")
        val TOKEN = stringPreferencesKey("token")
        val OWNER = stringPreferencesKey("owner")
        val REPO = stringPreferencesKey("repo")
        val BRANCH = stringPreferencesKey("branch")
        val SITE_URL = stringPreferencesKey("site_url")
        val REFRESH = stringPreferencesKey("refresh_token")
        val EXPIRES = longPreferencesKey("expires_at")
        val OLD_KEYS = listOf(LOGIN, TOKEN, OWNER, REPO, BRANCH, SITE_URL, REFRESH, EXPIRES)
    }
}

/** This account with new tokens from the device flow, their expiry counted from [now]. */
fun Account.withTokens(tokens: DeviceFlow.Tokens, now: Long) = copy(
    token = tokens.accessToken,
    refreshToken = tokens.refreshToken ?: refreshToken,
    expiresAt = tokens.expiresIn?.let { now + it * 1000 },
    withGitHub = true,
)
