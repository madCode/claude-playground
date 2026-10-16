package com.app.carry

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class DebugUpdatesTest {
    private val prefs = ApplicationProvider.getApplicationContext<Context>()
        .getSharedPreferences("debug-updates-test", Context.MODE_PRIVATE).apply { edit().clear().commit() }
    private var clock = 1_000_000_000L
    private var asked = 0
    private var answer: () -> String? = { release(131) }

    private fun release(vararg builds: Int) =
        """{"assets":[{"name":"carry-debug.apk"}${builds.joinToString("") { ""","name":"carry-debug-$it.apk"""" }}]}"""

    private fun updates(installed: Int? = 130) =
        DebugUpdates(prefs, installed, fetch = { asked++; answer() }, now = { clock })

    @Test
    fun `a newer build on the release is reported, and this one or an older one is not`() = runTest {
        val u = updates(installed = 130)
        u.checkIfDue()
        assertEquals(131, u.newer.value)

        clock += DebugUpdates.INTERVAL_MILLIS
        answer = { release(130) }
        u.checkIfDue()
        assertNull(u.newer.value)
    }

    @Test
    fun `it asks at most once in the interval`() = runTest {
        val u = updates()
        u.checkIfDue()
        clock += DebugUpdates.INTERVAL_MILLIS - 1
        u.checkIfDue()
        assertEquals(1, asked)
        clock += 1
        u.checkIfDue()
        assertEquals(2, asked)
    }

    @Test
    fun `being offline is not counted, so it asks again next time`() = runTest {
        answer = { throw IOException("offline") }
        val u = updates()
        u.checkIfDue()
        answer = { release(131) }
        u.checkIfDue()
        assertEquals(2, asked)
        assertEquals(131, u.newer.value)
    }

    @Test
    fun `a refusal is counted, so a rate limit is not spent again on every resume`() = runTest {
        answer = { null }
        val u = updates()
        u.checkIfDue()
        u.checkIfDue()
        assertEquals(1, asked)
        assertNull(u.newer.value)
    }

    @Test
    fun `a local or release build never asks`() = runTest {
        val u = updates(installed = null)
        u.checkIfDue()
        assertEquals(0, asked)
        assertNull(u.newer.value)
    }

    @Test
    fun `a clock set back counts as due`() = runTest {
        val u = updates()
        u.checkIfDue()
        clock -= 1
        u.checkIfDue()
        assertEquals(2, asked)
    }

    @Test
    fun `what it last heard survives the app being killed`() = runTest {
        updates().checkIfDue()
        assertEquals(131, updates().newer.value)
    }

    @Test
    fun `the newest numbered file wins, and the version name gives the installed build`() {
        assertEquals(140, DebugUpdates.latestBuild(release(9, 140, 131)))
        assertNull(DebugUpdates.latestBuild(release()))
        assertEquals(131, DebugUpdates.buildOf("0.1.0-debug.131+012eb32"))
        assertNull(DebugUpdates.buildOf("0.1.0-debug"))
        assertNull(DebugUpdates.buildOf("0.1.0"))
    }
}

/** The real HTTP read, against a local server standing in for GitHub. */
@RunWith(AndroidJUnit4::class)
class FetchReleaseTest {
    private val server = MockWebServer().apply { start() }

    @After fun stop() = server.close()

    private fun url() = server.url("/release").toString()

    @Test
    fun `a 200 answer is returned as text`() {
        server.enqueue(MockResponse.Builder().body("""{"name":"carry-debug-7.apk"}""").build())
        assertEquals("""{"name":"carry-debug-7.apk"}""", DebugUpdates.fetchRelease(url()))
        assertEquals("application/vnd.github+json", server.takeRequest().headers["Accept"])
    }

    @Test
    fun `a refusal is null, not an error`() {
        server.enqueue(MockResponse.Builder().code(403).body("rate limited").build())
        assertNull(DebugUpdates.fetchRelease(url()))
    }

    @Test
    fun `a server that drips the answer is given up on`() {
        server.enqueue(MockResponse.Builder().body("x".repeat(4 * 1024))
            .throttleBody(64, 50, java.util.concurrent.TimeUnit.MILLISECONDS).build())
        org.junit.Assert.assertThrows(IOException::class.java) { DebugUpdates.fetchRelease(url(), budgetMillis = 300) }
    }

    @Test
    fun `a body far larger than any release is not read`() {
        server.enqueue(MockResponse.Builder().body("x".repeat(300 * 1024)).build())
        assertNull(DebugUpdates.fetchRelease(url()))
    }
}

/** The APK download, against a local server standing in for GitHub and its file host. */
@RunWith(AndroidJUnit4::class)
class UpdateDownloadTest {
    private val server = MockWebServer().apply { start() }
    private val dir = java.nio.file.Files.createTempDirectory("carry-update").toFile()
    private val dest = java.io.File(dir, "carry-debug.apk")

    @After fun stop() { server.close(); dir.deleteRecursively() }

    @Test
    fun `the file is downloaded through GitHub's redirect`() {
        server.enqueue(MockResponse.Builder().code(302).addHeader("Location", server.url("/file").toString()).build())
        server.enqueue(MockResponse.Builder().body("apk bytes").build())
        UpdateInstaller.download(server.url("/release").toString(), dest)
        assertEquals("apk bytes", dest.readText())
    }

    @Test
    fun `a refusal is an error, not a file`() {
        server.enqueue(MockResponse.Builder().code(404).build())
        org.junit.Assert.assertThrows(IOException::class.java) { UpdateInstaller.download(server.url("/x").toString(), dest) }
        assertEquals(emptyList<String>(), dir.list()!!.toList())
    }

    @Test
    fun `a file larger than any build is abandoned, part and all`() {
        server.enqueue(MockResponse.Builder().body("x".repeat(200 * 1024)).build())
        org.junit.Assert.assertThrows(IOException::class.java) {
            UpdateInstaller.download(server.url("/x").toString(), dest, maxBytes = 100 * 1024)
        }
        assertEquals(emptyList<String>(), dir.list()!!.toList())
    }

    @Test
    fun `typing comes first, then the permission, then the install`() {
        assertEquals(UpdateInstaller.Step.SaveFirst, UpdateInstaller.next(unsaved = true, canInstall = false))
        assertEquals(UpdateInstaller.Step.AllowInstalls, UpdateInstaller.next(unsaved = false, canInstall = false))
        assertEquals(UpdateInstaller.Step.Install, UpdateInstaller.next(unsaved = false, canInstall = true))
    }
}
