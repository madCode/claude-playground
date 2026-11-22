package spotter.app

import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import spotter.app.update.BadDownload
import spotter.app.update.Download
import spotter.app.update.UpdateFeed
import java.io.File
import java.io.IOException
import java.security.MessageDigest

class DownloadTest {
    private val server = MockWebServer()
    private val dir = kotlin.io.path.createTempDirectory("spotter").toFile()
    private val dest = File(dir, "spotter-7.apk")
    private val bytes = ByteArray(200_000) { (it % 251).toByte() }
    private val sha = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private val feed = UpdateFeed(7, "abc1234", "unused", bytes.size.toLong(), sha)

    @Before fun start() = server.start()
    @After fun stop() { server.close(); dir.deleteRecursively() }

    private fun serve(body: ByteArray, code: Int = 200) =
        server.enqueue(MockResponse.Builder().code(code).body(Buffer().write(body)).build())

    private fun url() = server.url("/spotter-debug-7.apk").toString()

    @Test
    fun keepsTheBuildTheFeedNames() {
        serve(bytes)
        Download.apk(feed, dest, url())
        assertArrayEquals(bytes, dest.readBytes())
        assertFalse(File(dir, "spotter-7.apk.part").exists())
    }

    @Test
    fun followsGitHubsRedirectToItsFileHost() {
        server.enqueue(MockResponse.Builder().code(302).addHeader("Location", server.url("/file").toString()).build())
        serve(bytes)
        Download.apk(feed, dest, url())
        assertArrayEquals(bytes, dest.readBytes())
    }

    @Test
    fun aSwappedBuildIsNeverKept() {
        serve(bytes.copyOf().also { it[0] = 99 })
        assertTrue(runCatching { Download.apk(feed, dest, url()) }.exceptionOrNull() is BadDownload)
        assertFalse(dest.exists())
    }

    @Test
    fun aCutOffDownloadIsNeverKept() {
        serve(bytes.copyOf(bytes.size - 1))
        assertFails { Download.apk(feed, dest, url()) }
        assertFalse(dest.exists())
    }

    @Test
    fun aLargerDownloadIsStoppedAndNotKept() {
        serve(bytes + ByteArray(10))
        assertFails { Download.apk(feed, dest, url()) }
        assertFalse(dest.exists())
    }

    @Test
    fun aRefusalIsAnError() {
        serve(ByteArray(0), code = 404)
        assertFails { Download.apk(feed, dest, url()) }
    }

    @Test
    fun theFeedIsReadOrNullWhenRefused() {
        server.enqueue(MockResponse.Builder().body("""{"build":7}""").build())
        assertEquals("""{"build":7}""", Download.text(server.url("/feed").toString()))
        server.enqueue(MockResponse.Builder().code(404).build())
        assertNull(Download.text(server.url("/feed").toString()))
    }

    @Test
    fun anOversizedFeedIsAnError() {
        server.enqueue(MockResponse.Builder().body("x".repeat(100)).build())
        assertFails { Download.text(server.url("/feed").toString(), maxBytes = 10) }
    }

    private fun assertFails(block: () -> Unit) {
        assertTrue(runCatching(block).exceptionOrNull() is IOException)
    }
}
