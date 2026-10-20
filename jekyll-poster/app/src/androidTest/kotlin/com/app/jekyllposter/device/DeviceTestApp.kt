package com.app.jekyllposter.device

import androidx.test.platform.app.InstrumentationRegistry
import com.app.jekyllposter.AppContainer
import com.app.jekyllposter.PosterApp
import com.app.jekyllposter.core.testing.FakeGitHub
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/**
 * The real app on a device, with GitHub swapped for a [FakeGitHub] serving the sample blog on
 * this phone. Everything else is the app's own: WorkManager, the Keystore, notifications.
 */
class DeviceTestApp : PosterApp() {
    lateinit var github: FakeGitHub
        private set

    override fun createContainer(): AppContainer {
        // Off the main thread, where Android refuses network sockets, the server's own included.
        github = runBlocking(Dispatchers.IO) { FakeGitHub(sampleBlogFiles()) }
        return AppContainer(this, apiBase = github.apiBase, githubWeb = github.apiBase, githubClientId = "Iv1.test", githubAppSlug = "jekyll-poster-test")
    }

    /** The sample blog, from the test APK's assets (zipped by the build). */
    private fun sampleBlogFiles(): Map<String, ByteArray> {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        return ZipInputStream(assets.open("sample-blog.zip")).use { zip ->
            generateSequence { zip.nextEntry }.filter { !it.isDirectory }.associate { it.name to zip.readBytes() }
        }
    }
}
