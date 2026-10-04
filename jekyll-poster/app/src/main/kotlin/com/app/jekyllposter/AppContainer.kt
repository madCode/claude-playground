package com.app.jekyllposter

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
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
    cipher: SecretCipher = AesGcmCipher.androidKeystore(),
    accountData: DataStore<Preferences> = context.accountDataStore,
    val database: PosterDatabase = Room.databaseBuilder(context, PosterDatabase::class.java, "poster.db").build(),
    /** Starts publishing a queued post; WorkManager in the app, direct calls in tests. */
    val schedulePublish: (Long) -> Unit = { com.app.jekyllposter.publish.PublishWorker.enqueue(context, it) },
    /** When the site has (or hasn't) built a published post; a notification in the app. */
    onBuildFinished: (com.app.jekyllposter.data.Draft) -> Unit = com.app.jekyllposter.publish.Notifier(context)::buildFinished,
) {
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    fun client(token: String) = GitHubClient(http, token, apiBase)

    val accounts = AccountStore(accountData, cipher)
    val drafts = database.drafts()
    val blogs = BlogRepository(accounts, database.posts()) { account: Account -> client(account.token) }
    val publisher = Publisher(drafts, accounts, blogs)
    val images = ImageImporter(context.contentResolver, java.io.File(context.filesDir, "images"))
    val buildWatcher = BuildWatcher(drafts, accounts, { client(it.token) }, onBuildFinished)
}
