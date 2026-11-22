package spotter.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import spotter.app.update.UpdateFeed
import spotter.app.update.UpdatePolicy
import java.io.File

class UpdateFeedTest {
    private val sha = "a".repeat(64)
    private val apk = "https://github.com/madCode/claude-playground/releases/download/spotter-debug/spotter-debug-7.apk"
    private fun feed(build: Int = 7, size: Long = 100, sha256: String = sha, url: String = apk) =
        """{"build":$build,"commit":"abc1234","apk":"$url","size":$size,"sha256":"$sha256"}"""

    // The feed CI publishes is written by tools/debug-feed.sh; reading that script's own output
    // means a change to either side that breaks the other fails here, not on the phone.
    @Test
    fun readsWhatCiWrites() {
        val apkFile = File.createTempFile("spotter", ".apk").apply { writeText("not really an apk") }
        val script = File("../tools/debug-feed.sh").absoluteFile
        val process = ProcessBuilder("bash", script.path, apkFile.path, "42", "abc1234").redirectErrorStream(true).start()
        val out = process.inputStream.bufferedReader().readText()
        assertEquals(out, 0, process.waitFor())
        val parsed = assertNotNullAnd(UpdateFeed.parse(out))
        assertEquals(42, parsed.build)
        assertEquals(apkFile.length(), parsed.size)
        assertEquals("https://github.com/madCode/claude-playground/releases/download/spotter-debug/spotter-debug-42.apk", parsed.apk)
    }

    @Test
    fun refusesAFeedItCantTrust() {
        assertNull(UpdateFeed.parse("not json"))
        assertNull(UpdateFeed.parse("""{"build":7}"""))
        assertNull(UpdateFeed.parse(feed(url = "https://example.com/spotter.apk")))
        assertNull(UpdateFeed.parse(feed(sha256 = "short")))
        assertNull(UpdateFeed.parse(feed(size = 0)))
        assertNull(UpdateFeed.parse(feed(size = UpdateFeed.MAX_APK_BYTES + 1)))
        assertNull(UpdateFeed.parse(feed(build = 0)))
        assertNotNull(UpdateFeed.parse(feed()))
    }

    @Test
    fun aNewerFieldInTheFeedIsIgnored() {
        assertNotNull(UpdateFeed.parse(feed().replace("}", ""","notes":"later"}""")))
    }

    @Test
    fun onlyANewerBuildIsInstalled() {
        val seven = UpdateFeed.parse(feed())
        assertEquals(seven, UpdatePolicy.toInstall(installed = 6, feed = seven, failures = { 0 }))
        assertNull(UpdatePolicy.toInstall(installed = 7, feed = seven, failures = { 0 }))
        assertNull(UpdatePolicy.toInstall(installed = 8, feed = seven, failures = { 0 }))
        assertNull(UpdatePolicy.toInstall(installed = 6, feed = null, failures = { 0 }))
    }

    @Test
    fun aBuildCiDidntMakeNeverReplacesItself() {
        // A local build is what's being tried; main's build would throw it away.
        assertNull(UpdatePolicy.toInstall(installed = 0, feed = UpdateFeed.parse(feed()), failures = { 0 }))
    }

    @Test
    fun aBuildThatKeepsFailingIsLeftAlone() {
        val seven = UpdateFeed.parse(feed())
        assertNotNull(UpdatePolicy.toInstall(6, seven) { UpdatePolicy.MAX_ATTEMPTS - 1 })
        assertNull(UpdatePolicy.toInstall(6, seven) { UpdatePolicy.MAX_ATTEMPTS })
    }

    private fun <T> assertNotNullAnd(value: T?): T { assertNotNull(value); return value!! }
}
