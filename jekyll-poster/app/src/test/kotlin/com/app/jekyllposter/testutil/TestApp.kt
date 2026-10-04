package com.app.jekyllposter.testutil

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import com.app.jekyllposter.AppContainer
import com.app.jekyllposter.PosterApp
import com.app.jekyllposter.core.testing.FakeGitHub
import com.app.jekyllposter.data.AesGcmCipher
import com.app.jekyllposter.data.PosterDatabase
import javax.crypto.spec.SecretKeySpec

fun testCipher() = AesGcmCipher { SecretKeySpec(ByteArray(32) { it.toByte() }, "AES") }

/**
 * The app against a [FakeGitHub] serving the sample blog, with an in-memory database, a software
 * cipher in place of the Android Keystore (Robolectric has none), and publishing run in place of
 * WorkManager: [published] lists the posts the app asked to publish.
 */
class TestApp : PosterApp() {
    val github: FakeGitHub = FakeGitHub.sampleBlog()
    val published = mutableListOf<Long>()

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
                java.io.File.createTempFile("account", ".preferences_pb").also { it.delete(); it.deleteOnExit() }
            },
            settingsData = PreferenceDataStoreFactory.create {
                java.io.File.createTempFile("settings", ".preferences_pb").also { it.delete(); it.deleteOnExit() }
            },
            // A file, as in the app: an in-memory database has a single connection, and a write
            // can then wait behind the screens' live queries in ways the app never sees.
            database = Room.databaseBuilder(this, PosterDatabase::class.java, "test-${System.nanoTime()}.db")
                .allowMainThreadQueries().build(),
            schedulePublish = { id ->
                published += id
                if (publishNow) kotlinx.coroutines.runBlocking { container.publisher.publish(id) }
            },
        )
        return container
    }
}
