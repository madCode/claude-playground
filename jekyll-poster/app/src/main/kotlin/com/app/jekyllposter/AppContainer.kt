package com.app.jekyllposter

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import com.app.jekyllposter.core.github.DeviceFlow
import com.app.jekyllposter.core.github.GitHubClient
import com.app.jekyllposter.data.Account
import com.app.jekyllposter.data.AccountStore
import com.app.jekyllposter.data.AesGcmCipher
import com.app.jekyllposter.data.BlogRepository
import com.app.jekyllposter.data.ImageImporter
import com.app.jekyllposter.data.PosterDatabase
import com.app.jekyllposter.data.SecretCipher
import com.app.jekyllposter.data.accountDataStore
import com.app.jekyllposter.data.settingsDataStore
import com.app.jekyllposter.publish.BuildWatcher
import com.app.jekyllposter.publish.Publisher
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

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
    val database: PosterDatabase = Room.databaseBuilder(context, PosterDatabase::class.java, "poster.db")
        .addMigrations(com.app.jekyllposter.data.MIGRATION_1_2, com.app.jekyllposter.data.MIGRATION_2_3, com.app.jekyllposter.data.MIGRATION_3_4, com.app.jekyllposter.data.MIGRATION_4_5).build(),
    /** Starts publishing a queued post; WorkManager in the app, direct calls in tests. */
    val schedulePublish: (id: Long, sendAfter: Long?) -> Unit = { id, after -> com.app.jekyllposter.publish.PublishWorker.enqueue(context, id, after) },
    /** When the site has (or hasn't) built a published post; a notification in the app. */
    onBuildFinished: (com.app.jekyllposter.data.Draft) -> Unit = com.app.jekyllposter.publish.Notifier(context)::buildFinished,
    /** The files in an Obsidian vault folder; tests list a plain folder instead. */
    val vaultFiles: suspend (tree: String) -> com.app.jekyllposter.data.VaultImages = { com.app.jekyllposter.data.listVault(context, it) },
    /** The phone's VPN; tests pretend one is up or down. */
    vpn: com.app.jekyllposter.data.Vpn = com.app.jekyllposter.data.AndroidVpn(context),
    /** Starts a queued post's publish now, past WorkManager's backoff. */
    private val retryPublish: (id: Long, sendAfter: Long?) -> Unit = { id, after -> com.app.jekyllposter.publish.PublishWorker.retryNow(context, id, after) },
) {
    /** Photos shared from another app, waiting for the editor of the post they started. */
    val sharedPhotos = java.util.concurrent.ConcurrentHashMap<Long, List<android.net.Uri>>()

    /** A shared Obsidian note's `![[photo]]` embeds, waiting for the editor to find them in the vault. */
    val sharedEmbeds = java.util.concurrent.ConcurrentHashMap<Long, List<com.app.jekyllposter.core.obsidian.ObsidianNote.Embed>>()

    /** For work that must outlive the screen that starts it, like signing out. */
    val appScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)

    val settings = com.app.jekyllposter.data.Settings(settingsData)

    /** Every client the app reaches the web with goes through this, so the VPN switch covers them all. */
    private val vpnGate = com.app.jekyllposter.data.VpnGate(vpn) { kotlinx.coroutines.runBlocking { settings.onlyThroughVpn() } }

    /** Whether nothing can go out now: the writer asked for a VPN and there isn't one. */
    val waitingForVpn: kotlinx.coroutines.flow.Flow<Boolean> =
        kotlinx.coroutines.flow.combine(settings.onlyThroughVpn, vpn.up) { only, up -> only && !up }

    private val http = com.app.jekyllposter.data.GatedCalls(
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
        val cache = okhttp3.Cache(java.io.File(context.cacheDir, "preview"), 20L * 1024 * 1024)
        com.app.jekyllposter.data.GatedCalls(okhttp3.OkHttpClient.Builder().cache(cache).build(), vpnGate)
    }
    val previewFetcher by lazy { com.app.jekyllposter.ui.editor.PreviewFetcher(previewClient.value) }

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

    /** Sends queued post [id] now, not at the random time it was given. */
    suspend fun sendNow(id: Long) {
        val draft = drafts.get(id)?.takeIf { it.state == com.app.jekyllposter.data.PostState.Queued } ?: return
        drafts.update(draft.copy(sendAfter = null))
        retryPublish(id, null)
    }

    /**
     * Waits up to [timeout] for the VPN, when one is asked for: true once requests can go out.
     * A queued post waits here rather than in WorkManager's backoff, which can grow to hours.
     */
    suspend fun awaitVpn(timeout: kotlin.time.Duration): Boolean =
        kotlinx.coroutines.withTimeoutOrNull(timeout) { waitingForVpn.first { !it } } != null

    val accounts = AccountStore(accountData, cipher, deviceFlow?.let { flow -> { refreshToken: String -> flow.refresh(refreshToken) } })
    val drafts = database.drafts()
    val blogs = BlogRepository(accounts, database.posts()) { account: Account -> client(account.token) }
    val publisher = Publisher(drafts, accounts, blogs, settings)
    val images = ImageImporter(context.contentResolver, java.io.File(context.filesDir, "images"))
    val buildWatcher = BuildWatcher(drafts, accounts, { client(it.token) }, onBuildFinished)

    // Last, so everything it uses is set.
    init {
        // The VPN is back: queued posts go out now, not when WorkManager's backoff comes round.
        appScope.launch {
            var waiting = false
            waitingForVpn.collect { now ->
                if (waiting && !now) drafts.list().filter { it.state == com.app.jekyllposter.data.PostState.Queued }.forEach { retryPublish(it.id, it.sendAfter) }
                waiting = now
            }
        }
    }
}
