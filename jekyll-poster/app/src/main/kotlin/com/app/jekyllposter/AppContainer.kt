package com.app.jekyllposter

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.app.jekyllposter.core.github.DeviceFlow
import com.app.jekyllposter.core.github.GitHubClient
import com.app.jekyllposter.core.net.GatedCalls
import com.app.jekyllposter.core.net.Vpn
import com.app.jekyllposter.core.net.VpnGate
import com.app.jekyllposter.core.obsidian.ObsidianNote
import com.app.jekyllposter.data.Account
import com.app.jekyllposter.data.AccountStore
import com.app.jekyllposter.data.AesGcmCipher
import com.app.jekyllposter.data.AndroidVpn
import com.app.jekyllposter.data.BlogRepository
import com.app.jekyllposter.data.Draft
import com.app.jekyllposter.data.ImageImporter
import com.app.jekyllposter.data.PostState
import com.app.jekyllposter.data.PosterDatabase
import com.app.jekyllposter.data.Privacy
import com.app.jekyllposter.data.Removal
import com.app.jekyllposter.data.SecretCipher
import com.app.jekyllposter.data.Settings
import com.app.jekyllposter.data.VaultImages
import com.app.jekyllposter.data.accountDataStore
import com.app.jekyllposter.data.listVault
import com.app.jekyllposter.data.settingsDataStore
import com.app.jekyllposter.publish.BuildWatcher
import com.app.jekyllposter.publish.Notifier
import com.app.jekyllposter.publish.PublishQueue
import com.app.jekyllposter.publish.Publisher
import com.app.jekyllposter.publish.WorkManagerQueue
import com.app.jekyllposter.ui.catching
import com.app.jekyllposter.ui.editor.PreviewFetcher
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Cache
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

/** The app's objects, built once. Tests pass their own GitHub address, cipher and database. */
class AppContainer(
    val context: Context,
    val apiBase: HttpUrl = "https://api.github.com/".toHttpUrl(),
    /** github.com itself, where the device flow's sign-in pages live. */
    githubWeb: HttpUrl = "https://github.com/".toHttpUrl(),
    githubClientId: String = BuildConfig.GITHUB_CLIENT_ID,
    /** For the link that installs the GitHub App on the blog's repository. */
    val githubAppSlug: String = BuildConfig.GITHUB_APP_SLUG,
    cipher: SecretCipher = AesGcmCipher.androidKeystore(),
    accountData: DataStore<Preferences> = context.accountDataStore,
    settingsData: DataStore<Preferences> = context.settingsDataStore,
    val database: PosterDatabase = PosterDatabase.create(context),
    /** Where queued posts go to be sent; WorkManager in the app, direct calls in tests. */
    val publishQueue: PublishQueue = WorkManagerQueue(context),
    /** When the site has (or hasn't) built a published post; a notification in the app. */
    onBuildFinished: (Draft) -> Unit = Notifier(context)::buildFinished,
    /** The files in an Obsidian vault folder; tests list a plain folder instead. */
    val vaultFiles: suspend (tree: String) -> VaultImages = { listVault(context, it) },
    /** The phone's VPN; tests pretend one is up or down. */
    private val vpn: Vpn = AndroidVpn(context),
) {
    /** What a share brought, waiting for the editor of the post it started. */
    val pendingShares = ConcurrentHashMap<Long, PendingShare>()

    /** For work that must outlive the screen that starts it, like signing out. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settings = Settings(settingsData)

    /** Each blog's privacy switches; the ones kept for the whole app before move over on first use. */
    val privacy = Privacy(database.privacy(), settingsData) { accounts.blogs.first() }

    private val base = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()

    /**
     * Each blog's requests go through a gate of its own, so its VPN switch covers them whichever
     * blog is open: a queued pseudonymous post goes through the VPN while a personal blog is open.
     */
    private val blogCalls = ConcurrentHashMap<String, GatedCalls>()

    private fun callsFor(key: String) = blogCalls.getOrPut(key) { GatedCalls(base, VpnGate(vpn) { runBlocking { privacy.get(key).onlyThroughVpn } }) }

    /**
     * Signing in, listing a sign-in's repositories and renewing its token aren't for one blog: they
     * go through the VPN if any blog asks for it, since the app can't know which blog they're for.
     */
    private val signInCalls = GatedCalls(base, VpnGate(vpn) { runBlocking { privacy.anyOnlyThroughVpn() } })

    /** For a sign-in that isn't one blog's yet: see [signInCalls]. */
    fun client(token: String) = GitHubClient(signInCalls, token, apiBase)

    /** For [account]'s blog, behind that blog's VPN switch. */
    fun client(account: Account) = GitHubClient(callsFor(account.blogKey), account.token, apiBase)

    /** Whether nothing can go out for blog [key] now: it asks for a VPN and there isn't one. */
    fun waitingForVpn(key: String?): Flow<Boolean> = combine(privacy.of(key), vpn.up) { p, up -> p.onlyThroughVpn && !up }

    /** [waitingForVpn] for the open blog. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val waitingForVpn: Flow<Boolean> get() = accounts.currentKey.flatMapLatest { waitingForVpn(it) }

    /** "Sign in with GitHub", when the build names a GitHub App. */
    val deviceFlow: DeviceFlow? = githubClientId.takeIf { it.isNotBlank() }?.let { DeviceFlow(signInCalls, it, githubWeb) }

    /**
     * Renewing a sign-in's token goes the way its own blogs ask: through the VPN if one of them
     * does, but not held up by another sign-in's, which would fail a blog that asks for none.
     */
    private val renewalCalls = ConcurrentHashMap<String, GatedCalls>()

    private val renewal: (suspend (String, String) -> DeviceFlow.Tokens)? = deviceFlow?.let { _ ->
        { key, refreshToken ->
            val calls = renewalCalls.getOrPut(key) { GatedCalls(base, VpnGate(vpn) { runBlocking { privacy.signInAsksForVpn(key) } }) }
            DeviceFlow(calls, githubClientId, githubWeb).refresh(refreshToken)
        }
    }

    /**
     * For the preview's images: a client of its own, with no GitHub token on it, and a cache, so
     * switching back to the preview doesn't download them all again. The site is the open blog's.
     */
    private val previewClient = lazy {
        val cache = Cache(File(context.cacheDir, "preview"), 20L * 1024 * 1024)
        GatedCalls(OkHttpClient.Builder().cache(cache).build(), VpnGate(vpn) { runBlocking { privacy.get(accounts.currentKey()).onlyThroughVpn } })
    }
    val previewFetcher by lazy { PreviewFetcher(previewClient.value) }

    /**
     * Turns blog [key]'s VPN switch on or off. Its requests from then on go on new connections:
     * those made before were maybe not through a VPN. Requests already under way finish as they
     * started. Signing in and the preview follow it too.
     */
    suspend fun setOnlyThroughVpn(key: String, only: Boolean) {
        // Before and after: a connection made while the setting is being written goes on a
        // client the second renew retires.
        renewClients(key)
        privacy.update(key) { it.copy(onlyThroughVpn = only) }
        renewClients(key)
    }

    /**
     * Blog [key] was just added, or opened by adding it: its requests, its sign-in's renewals and
     * the preview start on new connections, made under the switches it has now.
     */
    fun added(key: String) = renewClients(key)

    private fun renewClients(key: String) {
        callsFor(key).renew()
        signInCalls.renew()
        renewalCalls.values.forEach { it.renew() }
        renewPreview()
    }

    /** The preview follows the open blog's switch: a connection made for the blog left mustn't serve the next. */
    private fun renewPreview() {
        if (previewClient.isInitialized()) previewClient.value.renew()
    }

    /**
     * Takes queued post [id] back to a draft to edit, if no commit was tried for it: the row
     * taken back, or null. Between publishes, so it can't be taken back mid-commit and land anyway.
     */
    suspend fun takeBack(id: Long): Draft? = publisher.betweenPublishes {
        if (drafts.takeBack(id) == 0) return@betweenPublishes null
        // Cancelled under the lock too: a Publish tapped right after queues work that must stay.
        // A worker already started finds a draft and sends nothing.
        publishQueue.cancel(id)
        drafts.get(id)
    }

    /** Sends queued post [id] now, not at the random time it was given. */
    suspend fun sendNow(id: Long) {
        // One column, and only while queued: a whole-row write could undo a publish landing now.
        if (drafts.sendNow(id) > 0) publishQueue.sendNow(id)
    }

    /**
     * Waits up to [timeout] for the VPN, when one is asked for: true once requests can go out.
     * A queued post waits here rather than in WorkManager's backoff, which can grow to hours.
     */
    suspend fun awaitVpn(key: String?, timeout: Duration): Boolean =
        withTimeoutOrNull(timeout) { waitingForVpn(key).first { !it } } != null

    val accounts = AccountStore(accountData, database.accounts(), cipher, renewal)
    val drafts = database.drafts()
    val blogs = BlogRepository(accounts, database.posts()) { account: Account -> client(account) }
    val publisher = Publisher(drafts, accounts, blogs, privacy)
    val images = ImageImporter(context.contentResolver, File(context.filesDir, "images"))
    val buildWatcher = BuildWatcher(drafts, accounts, { client(it) }, onBuildFinished)

    /**
     * Opens blog [key], an added one, and reads it. Nothing is published or changed on any blog.
     * False when it has gone meanwhile.
     */
    suspend fun open(key: String): Boolean {
        if (!accounts.open(key)) return false
        renewPreview()
        // After the switch: a read of the blog left, finishing now, can't put its title back.
        blogs.clear()
        catching { blogs.refresh() }
        return true
    }

    /**
     * Removes a sign-in and the blogs it posts to, unless a post is still to go out to one of
     * them; drafts stay, for when it's added again. What's kept of its blogs goes with it.
     */
    suspend fun remove(signInId: Long): Removal {
        val open = accounts.currentKey()
        val removal = accounts.remove(signInId)
        if (removal is Removal.Removed) {
            blogs.forget(removal.blogs)
            if (accounts.currentKey() != open) {
                renewPreview()
                blogs.clear()
                catching { blogs.refresh() }
            }
        }
        return removal
    }

    /**
     * When the VPN is back, queued posts go out now, not when WorkManager's backoff comes round.
     * Started by the app once the container is built, not from its constructor: a launch there
     * can run before the properties it uses are set.
     */
    private var vpnRetryStarted = false

    fun startVpnRetry() {
        // Once: a second collector would start each queued post twice.
        if (vpnRetryStarted) return
        vpnRetryStarted = true
        appScope.launch {
            // The blogs whose posts wait for the VPN; when one leaves (the VPN is back, or its
            // switch went off), its queued posts go.
            var waiting = emptySet<String>()
            combine(privacy.all, vpn.up) { all, up -> if (up) emptySet() else all.filter { it.onlyThroughVpn }.map { it.blogKey }.toSet() }
                .collect { now ->
                    val freed = waiting - now
                    if (freed.isNotEmpty()) drafts.list().filter { it.state == PostState.Queued && it.blog in freed }.forEach { publishQueue.retryNow(it.id, it.sendAfter) }
                    waiting = now
                }
        }
    }
}

/** A share's photos, and a shared note's `![[photo]]` embeds to find in the Obsidian vault. */
data class PendingShare(val photos: List<Uri> = emptyList(), val embeds: List<ObsidianNote.Embed> = emptyList())
