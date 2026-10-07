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
import com.app.jekyllposter.ui.editor.PreviewFetcher
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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
    vpn: Vpn = AndroidVpn(context),
) {
    /** What a share brought, waiting for the editor of the post it started. */
    val pendingShares = ConcurrentHashMap<Long, PendingShare>()

    /** For work that must outlive the screen that starts it, like signing out. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settings = Settings(settingsData)

    /** Every client the app reaches the web with goes through this, so the VPN switch covers them all. */
    private val vpnGate = VpnGate(vpn) { runBlocking { settings.onlyThroughVpn() } }

    /** Whether nothing can go out now: the writer asked for a VPN and there isn't one. */
    val waitingForVpn: Flow<Boolean> =
        combine(settings.onlyThroughVpn, vpn.up) { only, up -> only && !up }

    private val http = GatedCalls(
        OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build(),
        vpnGate,
    )

    fun client(token: String) = GitHubClient(http, token, apiBase)

    /** "Sign in with GitHub", when the build names a GitHub App. */
    val deviceFlow: DeviceFlow? = githubClientId.takeIf { it.isNotBlank() }?.let { DeviceFlow(http, it, githubWeb) }

    /**
     * For the preview's images: a client of its own, with no GitHub token on it, and a cache, so
     * switching back to the preview doesn't download them all again.
     */
    private val previewClient = lazy {
        val cache = Cache(File(context.cacheDir, "preview"), 20L * 1024 * 1024)
        GatedCalls(OkHttpClient.Builder().cache(cache).build(), vpnGate)
    }
    val previewFetcher by lazy { PreviewFetcher(previewClient.value) }

    /**
     * Turns the VPN switch on or off. Requests from then on go on new connections: those made
     * before were maybe not through a VPN. Requests already under way finish as they started.
     */
    suspend fun setOnlyThroughVpn(only: Boolean) {
        // Before and after: a connection made while the setting is being written goes on a
        // client the second renew retires.
        renewClients()
        settings.setOnlyThroughVpn(only)
        renewClients()
    }

    private fun renewClients() {
        http.renew()
        if (previewClient.isInitialized()) previewClient.value.renew()
    }

    /**
     * Takes queued post [id] back to a draft to edit, unless it's already gone: true if it was
     * taken back. Between publishes, so it can't be taken back mid-commit and land anyway.
     */
    suspend fun takeBack(id: Long): Boolean = publisher.betweenPublishes {
        // Cancelled under the lock too: a Publish tapped right after queues work that must stay.
        // A worker already started finds a draft and sends nothing.
        (drafts.takeBack(id) > 0).also { if (it) publishQueue.cancel(id) }
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
    suspend fun awaitVpn(timeout: Duration): Boolean =
        withTimeoutOrNull(timeout) { waitingForVpn.first { !it } } != null

    val accounts = AccountStore(accountData, cipher, deviceFlow?.let { flow -> { refreshToken: String -> flow.refresh(refreshToken) } })
    val drafts = database.drafts()
    val blogs = BlogRepository(accounts, database.posts()) { account: Account -> client(account.token) }
    val publisher = Publisher(drafts, accounts, blogs, settings)
    val images = ImageImporter(context.contentResolver, File(context.filesDir, "images"))
    val buildWatcher = BuildWatcher(drafts, accounts, { client(it.token) }, onBuildFinished)

    /** Signs out: the account and the blog it read go; drafts stay for when it's signed in again. */
    suspend fun signOut() {
        accounts.signOut()
        blogs.clear()
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
            var waiting = false
            waitingForVpn.collect { now ->
                if (waiting && !now) drafts.list().filter { it.state == PostState.Queued }.forEach { publishQueue.retryNow(it.id, it.sendAfter) }
                waiting = now
            }
        }
    }
}

/** A share's photos, and a shared note's `![[photo]]` embeds to find in the Obsidian vault. */
data class PendingShare(val photos: List<Uri> = emptyList(), val embeds: List<ObsidianNote.Embed> = emptyList())
