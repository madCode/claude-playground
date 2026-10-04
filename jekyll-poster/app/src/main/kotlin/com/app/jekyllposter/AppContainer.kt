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
import com.app.jekyllposter.publish.BuildWatcher
import com.app.jekyllposter.publish.Publisher
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** The app's objects, built once. Tests pass their own GitHub address, cipher and database. */
class AppContainer(
    context: Context,
    val apiBase: HttpUrl = "https://api.github.com/".toHttpUrl(),
    /** github.com itself, where the device flow's sign-in pages live. */
    githubWeb: HttpUrl = "https://github.com/".toHttpUrl(),
    githubClientId: String = BuildConfig.GITHUB_CLIENT_ID,
    /** For the link that installs the GitHub App on the blog's repository. */
    val githubAppSlug: String = BuildConfig.GITHUB_APP_SLUG,
    cipher: SecretCipher = AesGcmCipher.androidKeystore(),
    accountData: DataStore<Preferences> = context.accountDataStore,
    val database: PosterDatabase = Room.databaseBuilder(context, PosterDatabase::class.java, "poster.db")
        .addMigrations(com.app.jekyllposter.data.MIGRATION_1_2).build(),
    /** Starts publishing a queued post; WorkManager in the app, direct calls in tests. */
    val schedulePublish: (Long) -> Unit = { com.app.jekyllposter.publish.PublishWorker.enqueue(context, it) },
    /** When the site has (or hasn't) built a published post; a notification in the app. */
    onBuildFinished: (com.app.jekyllposter.data.Draft) -> Unit = com.app.jekyllposter.publish.Notifier(context)::buildFinished,
) {
    /** For work that must outlive the screen that starts it, like signing out. */
    val appScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)

    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    fun client(token: String) = GitHubClient(http, token, apiBase)

    /** "Sign in with GitHub", when the build names a GitHub App. */
    val deviceFlow: DeviceFlow? = githubClientId.takeIf { it.isNotBlank() }?.let { DeviceFlow(http, it, githubWeb) }

    val accounts = AccountStore(accountData, cipher, deviceFlow?.let { flow -> { refreshToken: String -> flow.refresh(refreshToken) } })
    val drafts = database.drafts()
    val blogs = BlogRepository(accounts, database.posts()) { account: Account -> client(account.token) }
    val publisher = Publisher(drafts, accounts, blogs)
    val images = ImageImporter(context.contentResolver, java.io.File(context.filesDir, "images"))
    val buildWatcher = BuildWatcher(drafts, accounts, { client(it.token) }, onBuildFinished)
}
