package com.app.jekyllposter.testutil

import android.net.Uri
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import com.app.jekyllposter.AppContainer
import com.app.jekyllposter.PosterApp
import com.app.jekyllposter.core.testing.FakeGitHub
import com.app.jekyllposter.core.testing.FakeVpn
import com.app.jekyllposter.data.Account
import com.app.jekyllposter.data.AesGcmCipher
import com.app.jekyllposter.data.PosterDatabase
import com.app.jekyllposter.data.VaultFile
import com.app.jekyllposter.data.VaultImages
import com.app.jekyllposter.publish.PublishQueue
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.runBlocking

fun testCipher() = AesGcmCipher { SecretKeySpec(ByteArray(32) { it.toByte() }, "AES") }

/**
 * The app against a [FakeGitHub] serving the sample blog, with a database of its own, a software
 * cipher in place of the Android Keystore (Robolectric has none), and publishing run in place of
 * WorkManager: [published] lists the posts the app asked to publish.
 */
class TestApp : PosterApp() {
    val github: FakeGitHub = FakeGitHub.sampleBlog()
    val published = mutableListOf<Long>()
    val vpn = FakeVpn()

    /** Queued posts the app started again, past WorkManager's backoff. */
    val retried = CopyOnWriteArrayList<Long>()

    /** The random time each queued post was given when it was scheduled or started again. */
    val sendAfters = ConcurrentHashMap<Long, Long>()

    /** Posts sent at once with Send now. */
    val sentNow = CopyOnWriteArrayList<Long>()

    /** Signs in to the sample blog, as most tests begin. */
    suspend fun signIn() = container.accounts.save(Account("sample", "good-token", "sample", "sample-blog", "main"))

    /** When set, a publish request runs the publisher straight away, as the worker would. */
    var publishNow = true

    override fun createContainer(): AppContainer {
        lateinit var container: AppContainer
        container = AppContainer(
            this,
            apiBase = github.apiBase,
            githubWeb = github.apiBase,
            githubClientId = "Iv1.test",
            githubAppSlug = "jekyll-poster-test",
            cipher = testCipher(),
            // A file of its own: the app's DataStore is a process-wide singleton that would carry
            // one test's sign-in into the next.
            accountData = PreferenceDataStoreFactory.create {
                File.createTempFile("account", ".preferences_pb").also { it.delete(); it.deleteOnExit() }
            },
            settingsData = PreferenceDataStoreFactory.create {
                File.createTempFile("settings", ".preferences_pb").also { it.delete(); it.deleteOnExit() }
            },
            // A file, as in the app: an in-memory database has a single connection, and a write
            // can then wait behind the screens' live queries in ways the app never sees.
            database = Room.databaseBuilder(this, PosterDatabase::class.java, "test-${System.nanoTime()}.db")
                .allowMainThreadQueries().build(),
            // A plain folder stands in for the vault: Robolectric has no document provider to walk.
            vaultFiles = { tree ->
                val root = File(Uri.parse(tree).path!!)
                VaultImages(
                    root.walkTopDown().filter { it.isFile }.map { VaultFile(it.relativeTo(root).path, Uri.fromFile(it)) }.toList(),
                )
            },
            vpn = vpn,
            publishQueue = object : PublishQueue {
                override fun enqueue(id: Long, sendAfter: Long?) {
                    published += id
                    sendAfter?.let { sendAfters[id] = it }
                    if (publishNow) runBlocking { container.publisher.publish(id) }
                }

                override fun retryNow(id: Long, sendAfter: Long?) {
                    retried += id
                    sendAfter?.let { sendAfters[id] = it }
                }

                override fun sendNow(id: Long) {
                    sentNow += id
                }
            },
        )
        return container
    }
}
