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
        .addMigrations(com.app.jekyllposter.data.MIGRATION_1_2, com.app.jekyllposter.data.MIGRATION_2_3, com.app.jekyllposter.data.MIGRATION_3_4).build(),
    /** Starts publishing a queued post; WorkManager in the app, direct calls in tests. */
    val schedulePublish: (Long) -> Unit = { com.app.jekyllposter.publish.PublishWorker.enqueue(context, it) },
    /** When the site has (or hasn't) built a published post; a notification in the app. */
    onBuildFinished: (com.app.jekyllposter.data.Draft) -> Unit = com.app.jekyllposter.publish.Notifier(context)::buildFinished,
    /** The files in an Obsidian vault folder; tests list a plain folder instead. */
    val vaultFiles: suspend (tree: String) -> com.app.jekyllposter.data.VaultImages = { com.app.jekyllposter.data.listVault(context, it) },
    /** The phone's VPN; tests pretend one is up or down. */
    vpn: com.app.jekyllposter.data.Vpn = com.app.jekyllposter.data.AndroidVpn(context),
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

    val http: OkHttpClient = vpnGate.apply(OkHttpClient.Builder())
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    fun client(token: String) = GitHubClient(http, token, apiBase)

    /** "Sign in with GitHub", when the build names a GitHub App. */
    val deviceFlow: DeviceFlow? = githubClientId.takeIf { it.isNotBlank() }?.let { DeviceFlow(http, it, githubWeb) }

    /**
     * For the preview's images: a client of its own, with no GitHub token on it, and a cache, so
     * switching back to the preview doesn't download them all again.
     */
    private val previewClient = lazy {
        val cache = okhttp3.Cache(java.io.File(context.cacheDir, "preview"), 20L * 1024 * 1024)
        vpnGate.apply(okhttp3.OkHttpClient.Builder()).cache(cache).build()
    }
    val previewFetcher by lazy { com.app.jekyllposter.ui.editor.PreviewFetcher(previewClient.value) }

    /**
     * Turns the VPN switch on or off. Turning it on also stops what's under way and drops the
     * open connections: they were made before, maybe not through a VPN, and would otherwise be
     * used again. A publish stopped this way is tried again, as after any lost connection.
     */
    suspend fun setOnlyThroughVpn(only: Boolean) {
        settings.setOnlyThroughVpn(only)
        if (!only) return
        listOfNotNull(http, previewClient.takeIf { it.isInitialized() }?.value).forEach {
            it.dispatcher.cancelAll()
            it.connectionPool.evictAll()
        }
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
}
