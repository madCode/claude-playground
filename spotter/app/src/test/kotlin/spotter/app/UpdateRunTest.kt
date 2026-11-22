package spotter.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import spotter.app.update.BadDownload
import spotter.app.update.UpdateFeed
import spotter.app.update.UpdatePolicy
import spotter.app.update.UpdateRun
import spotter.app.update.UpdateRun.Outcome
import java.io.File
import java.io.IOException

class UpdateRunTest {
    private val sha = "b".repeat(64)
    private fun feedText(build: Int) =
        """{"build":$build,"commit":"abc1234","apk":"https://github.com/madCode/claude-playground/releases/download/spotter-debug/spotter-debug-$build.apk","size":10,"sha256":"$sha"}"""

    private class Store : UpdateRun.Store {
        override var waiting = 0
        override var needsTap = 0
        val failed = mutableMapOf<Int, Int>()
        override fun failures(build: Int) = failed[build] ?: 0
        override fun failed(build: Int) { failed[build] = failures(build) + 1 }
    }

    private val store = Store()
    private val installs = mutableListOf<Pair<Int, Boolean>>()
    private val dir = kotlin.io.path.createTempDirectory("spotter").toFile()

    private fun run(
        installed: Int = 41,
        feed: () -> String? = { feedText(42) },
        download: (UpdateFeed, File) -> Unit = { _, apk -> apk.writeText("apk") },
        install: (File, Int, Boolean) -> Unit = { apk, build, background ->
            assertTrue("the checked file is what's installed", apk.exists())
            installs += build to background
        },
        onScreen: Boolean = false,
        canInstall: Boolean = true,
        tapped: Boolean = false,
    ) = UpdateRun(installed, store, feed, download, install, { File(dir, "spotter-$it.apk") }, { onScreen }, { canInstall }).run(tapped)

    @Test
    fun aNewerBuildInstallsInTheBackgroundWhileSpotterIsClosed() {
        assertEquals(Outcome.HANDED_TO_INSTALLER, run())
        assertEquals(listOf(42 to true), installs)
    }

    // Installing replaces the running app: not while the sheet is being read.
    @Test
    fun whileSpotterIsOnScreenTheBuildWaitsForATap() {
        assertEquals(Outcome.LEFT_WAITING, run(onScreen = true))
        assertEquals(42, store.waiting)
        assertTrue(installs.isEmpty())
    }

    @Test
    fun aTapInstallsEvenOnScreenAndMayAskToConfirm() {
        assertEquals(Outcome.HANDED_TO_INSTALLER, run(onScreen = true, tapped = true))
        assertEquals(listOf(42 to false), installs)
    }

    @Test
    fun withoutLeaveToInstallAppsTheBuildWaits() {
        assertEquals(Outcome.LEFT_WAITING, run(canInstall = false))
        assertEquals(42, store.waiting)
    }

    @Test
    fun offlineIsRetriedNotCounted() {
        assertEquals(Outcome.OFFLINE, run(feed = { throw IOException("no network") }))
        assertTrue(store.failed.isEmpty())
    }

    @Test
    fun onceUpToDateNothingWaits() {
        store.waiting = 42
        assertEquals(Outcome.UP_TO_DATE, run(installed = 42))
        assertEquals(0, store.waiting)
    }

    @Test
    fun aRefusedOrUnreadableFeedChangesNothing() {
        assertEquals(Outcome.UP_TO_DATE, run(feed = { null }))
        assertEquals(Outcome.UP_TO_DATE, run(feed = { "<html>" }))
        assertTrue(installs.isEmpty())
    }

    // Flaky Wi-Fi isn't the build's fault: three drops mustn't block it until the next merge.
    @Test
    fun aDroppedDownloadIsRetriedNotCounted() {
        val outcome = run(download = { _, apk -> apk.writeText("half"); throw IOException("cut off") })
        assertEquals(Outcome.OFFLINE, outcome)
        assertEquals(0, store.failures(42))
        assertFalse(File(dir, "spotter-42.apk").exists())
    }

    @Test
    fun bytesThatArentTheBuildCountAgainstItAndLeaveNoFile() {
        val outcome = run(download = { _, apk -> apk.writeText("other"); throw BadDownload("hash") })
        assertEquals(Outcome.FAILED, outcome)
        assertEquals(1, store.failures(42))
        assertFalse(File(dir, "spotter-42.apk").exists())
    }

    @Test
    fun theDownloadIsDeletedOnceTheInstallerHasIt() {
        run()
        assertFalse(File(dir, "spotter-42.apk").exists())
    }

    // Android 8 to 11 always ask: downloading the whole build every three hours, on mobile data
    // too, until someone taps would be a waste. It waits for the tap instead.
    @Test
    fun aBuildAndroidAskedToConfirmIsNotDownloadedAgainInTheBackground() {
        store.needsTap = 42
        var downloads = 0
        assertEquals(Outcome.LEFT_WAITING, run(download = { _, apk -> downloads++; apk.writeText("apk") }))
        assertEquals(0, downloads)
        assertEquals(Outcome.HANDED_TO_INSTALLER, run(download = { _, apk -> downloads++; apk.writeText("apk") }, tapped = true))
        assertEquals(1, downloads)
    }

    // Opened during a long download: installing now would close the sheet being read.
    @Test
    fun spotterOpenedDuringTheDownloadIsNotReplaced() {
        var open = false
        val outcome = UpdateRun(41, store, { feedText(42) }, { _, apk -> apk.writeText("apk"); open = true },
            { _, b, bg -> installs += b to bg }, { File(dir, "spotter-$it.apk") }, { open }, { true }).run(tapped = false)
        assertEquals(Outcome.LEFT_WAITING, outcome)
        assertTrue(installs.isEmpty())
        assertEquals(42, store.waiting)
    }

    @Test
    fun aBuildGivenUpOnIsNoLongerOffered() {
        store.waiting = 42
        repeat(UpdatePolicy.MAX_ATTEMPTS) { store.failed(42) }
        assertEquals(Outcome.UP_TO_DATE, run())
        assertEquals(0, store.waiting)
    }

    @Test
    fun anInstallerErrorCountsAgainstTheBuild() {
        assertEquals(Outcome.FAILED, run(install = { _, _, _ -> throw SecurityException("not allowed") }))
        assertEquals(1, store.failures(42))
    }
}
