package com.app.jekyllposter.update

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.app.jekyllposter.core.github.NoVpnException
import com.app.jekyllposter.testutil.SAMPLE_BLOG
import com.app.jekyllposter.testutil.TestApp
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

private fun release(vararg builds: Int) =
    """{"assets":[{"name":"jekyll-poster-debug.apk"}${builds.joinToString("") { ""","name":"jekyll-poster-debug-$it.apk"""" }}]}"""

@RunWith(AndroidJUnit4::class)
class DebugUpdatesTest {
    private val prefs = ApplicationProvider.getApplicationContext<Context>()
        .getSharedPreferences("debug-updates-test", Context.MODE_PRIVATE).apply { edit().clear().commit() }
    private var clock = 1_000_000_000L
    private var asked = 0
    private var answer: () -> String? = { release(131) }

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
    fun `being offline or without the VPN is not counted, so it asks again next time`() = runTest {
        for (failure in listOf(IOException("offline"), NoVpnException())) {
            prefs.edit().clear().commit()
            asked = 0
            answer = { throw failure }
            val u = updates()
            u.checkIfDue()
            answer = { release(131) }
            u.checkIfDue()
            assertEquals(2, asked)
            assertEquals(131, u.newer.value)
        }
    }

    @Test
    fun `a refusal is counted, so a rate limit is not spent again on every return`() = runTest {
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
        // Another app's files on a release are not this app's builds.
        assertNull(DebugUpdates.latestBuild("""{"assets":[{"name":"carry-debug-900.apk"}]}"""))
        // The shape GitHub answers with for the release, as CI publishes it.
        val github = """{"tag_name":"jekyll-poster-debug","body":"Build 167 from main (27eb5a3).","assets":[
            {"name":"jekyll-poster-debug-167.apk","browser_download_url":"https://github.com/madCode/claude-playground/releases/download/jekyll-poster-debug/jekyll-poster-debug-167.apk"},
            {"name":"jekyll-poster-debug.apk","browser_download_url":"https://github.com/madCode/claude-playground/releases/download/jekyll-poster-debug/jekyll-poster-debug.apk"}]}"""
        assertEquals(167, DebugUpdates.latestBuild(github))
        assertEquals(131, DebugUpdates.buildOf("0.1.0-debug.131+012eb32"))
        assertNull(DebugUpdates.buildOf("0.1.0-debug"))
        assertNull(DebugUpdates.buildOf("0.1.0"))
    }
}

/** The real HTTP read, against a local server standing in for GitHub. */
@RunWith(AndroidJUnit4::class)
class FetchReleaseTest {
    private val server = MockWebServer().apply { start() }
    private val calls = OkHttpClient()

    @After fun stop() = server.close()

    private fun fetch(budgetMillis: Long = 30_000) = runBlocking { DebugUpdates.fetchRelease(calls, server.url("/release"), budgetMillis) }

    @Test
    fun `a 200 answer is returned as text`() {
        server.enqueue(MockResponse.Builder().body("""{"name":"jekyll-poster-debug-7.apk"}""").build())
        assertEquals("""{"name":"jekyll-poster-debug-7.apk"}""", fetch())
        assertEquals("application/vnd.github+json", server.takeRequest().headers["Accept"])
    }

    @Test
    fun `a refusal is null, not an error`() {
        server.enqueue(MockResponse.Builder().code(403).body("rate limited").build())
        assertNull(fetch())
    }

    @Test
    fun `a server that drips the answer is given up on`() {
        server.enqueue(MockResponse.Builder().body("x".repeat(4 * 1024)).throttleBody(64, 50, TimeUnit.MILLISECONDS).build())
        assertThrows(IOException::class.java) { fetch(budgetMillis = 300) }
    }

    @Test
    fun `a body far larger than any release is not read`() {
        server.enqueue(MockResponse.Builder().body("x".repeat(300 * 1024)).build())
        assertNull(fetch())
    }
}

/** The app's own update client: no token on it, and behind "Only connect through a VPN". */
@RunWith(AndroidJUnit4::class)
@Config(application = TestApp::class)
class UpdateCheckPrivacyTest {
    private val app = ApplicationProvider.getApplicationContext<TestApp>()
    private val c = app.container
    private val server = MockWebServer().apply { start() }

    @After fun stop() {
        server.close()
        app.github.close()
    }

    private fun fetch() = runBlocking { DebugUpdates.fetchRelease(c.updateCalls, server.url("/release")) }

    private fun answer() = server.enqueue(MockResponse.Builder().body(release(140)).build())

    @Test
    fun `the check carries no token, even signed in`() {
        runBlocking { app.signIn() }
        answer()
        assertEquals(release(140), fetch())
        assertNull(server.takeRequest().headers["Authorization"])
        assertEquals("not through the VPN when no blog asks", 0, app.vpn.connections)
    }

    @Test
    fun `with a blog asking for the VPN, the check goes through it`() {
        runBlocking { app.signIn(); c.setOnlyThroughVpn(SAMPLE_BLOG, true) }
        answer()
        assertEquals(release(140), fetch())
        assertTrue(app.vpn.connections > 0)
    }

    @Test
    fun `with a blog asking for the VPN and none up, nothing is sent`() {
        runBlocking { app.signIn(); c.setOnlyThroughVpn(SAMPLE_BLOG, true) }
        app.vpn.up.value = false
        answer()
        assertThrows(NoVpnException::class.java) { fetch() }
        assertEquals(0, server.requestCount)
    }
}
