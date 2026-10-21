package com.app.carry

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import androidx.work.Configuration
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class LiveUpdatesTest {
    @get:Rule val tmp = TemporaryFolder()

    private val app = ApplicationProvider.getApplicationContext<Context>()
    private val prefs = LiveUpdates.prefsOf(app).apply { edit().clear().commit() }

    private val ourKey = setOf("our-key")
    private val installed = ApkFacts(app.packageName, 130, ourKey)

    /** What each downloaded file, by its text, says about itself; anything else isn't an APK. */
    private val apks = mutableMapOf<String, ApkFacts>()
    private var serving: () -> String = { "apk-140" }
    private var stageFails: Exception? = null
    private val staged = mutableListOf<Int>()
    private val committed = mutableListOf<Pair<Int, Int>>()
    private val abandoned = mutableListOf<Int>()
    private var canInstall = true
    private var clock = 1_000_000_000L
    private var stageCalls = 0
    private var busy = false
    private var downloads = 0
    private val background = mutableListOf<Int>()
    private val dir by lazy { tmp.newFolder("updates") }
    private val work get() = WorkManager.getInstance(app)

    @Before fun setUp() {
        // Workers never run here: each test drives LiveUpdates itself and reads what was enqueued.
        WorkManagerTestInitHelper.initializeTestWorkManager(app, Configuration.Builder().setExecutor { }.build())
        UpdateInstaller.finish()
        apks["apk-140"] = ApkFacts(app.packageName, 140, ourKey)
    }

    @After fun tearDown() = UpdateInstaller.finish()

    private fun updates(installedBuild: Int = 130) = LiveUpdates(
        app, installedBuild = installedBuild, prefs = prefs, dir = dir,
        download = { downloads++; it.writeText(serving()) },
        inspect = { apks[it.readText()] },
        installed = { installed },
        stage = { stageCalls++; stageFails?.let { e -> throw e }; staged += 7; 7 },
        commit = { id, build, bg -> committed += id to build; if (bg) background += build },
        abandon = { abandoned += it },
        canInstallNow = { canInstall },
        now = { clock },
        busy = { busy },
        work = { work },
    )

    private fun infos(name: String) = work.getWorkInfosForUniqueWork(name).get()

    private fun LiveUpdates.tap(build: Int = 140, unsaved: () -> Boolean = { false }) = runBlocking { install(build, unsaved) }

    /** What Android's installer throws for a session it won't take, whatever the room. */
    private val refused = IllegalStateException("Session is sealed")

    @Test
    fun `a newer build of this app with its key is handed to Android`() {
        assertNull(updates().tap())
        assertEquals(listOf(7 to 140), committed)
        assertTrue("installing until Android answers", UpdateInstaller.installing.value)
    }

    @Test
    fun `a file of another app, another key or no app at all is refused and given up on`() {
        for ((text, facts) in listOf(
            "another app" to ApkFacts("com.example.other", 140, ourKey),
            "another key" to ApkFacts(app.packageName, 140, setOf("someone-else")),
            "no key read" to ApkFacts(app.packageName, 140, emptySet()),
            "<html>rate limited</html>" to null,
        )) {
            prefs.edit().clear().commit()
            facts?.let { apks[text] = it }
            serving = { text }
            val updates = updates()
            val problem = updates.tap()!!
            assertTrue(text, problem.offerLink)
            assertTrue(text, problem.why.startsWith("The download isn't a Carry this phone can trust"))
            assertEquals("never staged: $text", emptyList<Int>(), staged)
            assertFalse("the notice must be tappable again", UpdateInstaller.installing.value)
            assertEquals("$text: the next tap opens the browser", SelfUpdater.Step.Browser, updates.next(140, unsaved = false, canInstall = true))
        }
        assertEquals("a newer build is tried again", SelfUpdater.Step.Install, updates().next(141, unsaved = false, canInstall = true))
    }

    @Test
    fun `a file that isn't newer yet is not installed, and not given up on`() {
        apks["apk-130"] = installed
        serving = { "apk-130" }
        val updates = updates()
        val problem = updates.tap()!!
        assertFalse(problem.offerLink)
        assertEquals(emptyList<Int>(), staged)
        assertEquals("CI's fixed-name file lags: tapping again may find it", SelfUpdater.Step.Install, updates.next(140, false, true))
    }

    @Test
    fun `typing while the build is copied to the installer abandons it rather than installing`() {
        // Nothing typed at the tap; something typed by the time the copy is done.
        val problem = updates().tap { staged.isNotEmpty() }!!
        assertEquals(emptyList<Pair<Int, Int>>(), committed)
        assertEquals("the staged session must not be left behind", listOf(7), abandoned)
        assertFalse(problem.offerLink)
        assertFalse(UpdateInstaller.installing.value)
    }

    @Test
    fun `no room to stage the build isn't held against it`() {
        stageFails = IOException("No space left on device")
        val updates = updates()
        repeat(LiveUpdates.MAX_INSTALL_FAILURES + 1) {
            val problem = updates.tap()!!
            assertEquals("Android couldn't take the update: No space left on device. Free some space, then tap again.", problem.why)
            assertFalse(problem.offerLink)
        }
        assertEquals(SelfUpdater.Step.Install, updates.next(140, false, true))
        assertFalse(UpdateInstaller.installing.value)

        // In the background too: tried again later, never given up on.
        updates.fetched()
        repeat(LiveUpdates.MAX_INSTALL_FAILURES + 1) { assertEquals(LiveUpdates.Outcome.Retry, updates.inBackground()) }
        assertEquals(SelfUpdater.Step.Install, updates.next(140, false, true))
    }

    @Test
    fun `three failed hand-offs give up on the build`() {
        stageFails = refused
        val updates = updates()
        repeat(LiveUpdates.MAX_INSTALL_FAILURES - 1) {
            assertEquals("Android wouldn't take the update: Session is sealed.", updates.tap()!!.why)
            assertEquals(SelfUpdater.Step.Install, updates.next(140, false, true))
        }
        assertTrue(updates.tap()!!.why.endsWith("Carry won't try this build again."))
        assertEquals(SelfUpdater.Step.Browser, updates.next(140, false, true))
        assertFalse(UpdateInstaller.installing.value)
    }

    @Test
    fun `Android's own failures count, and one that can't ever install gives up at once`() {
        fun answer(build: Int, status: Int) = UpdateInstaller.Result().onReceive(
            app,
            Intent(app, UpdateInstaller.Result::class.java)
                .putExtra(UpdateInstaller.EXTRA_BUILD, build)
                .putExtra(PackageInstaller.EXTRA_STATUS, status),
        )
        val updates = updates()
        answer(140, PackageInstaller.STATUS_FAILURE_ABORTED)
        answer(140, PackageInstaller.STATUS_FAILURE_ABORTED)
        answer(140, PackageInstaller.STATUS_FAILURE_ABORTED)
        assertEquals("the owner saying no isn't a failure", SelfUpdater.Step.Install, updates.next(140, false, true))
        repeat(LiveUpdates.MAX_INSTALL_FAILURES) { answer(140, PackageInstaller.STATUS_FAILURE_STORAGE) }
        assertEquals("a full phone isn't the build's fault", SelfUpdater.Step.Install, updates.next(140, false, true))
        answer(140, PackageInstaller.STATUS_FAILURE)
        answer(140, PackageInstaller.STATUS_FAILURE)
        assertEquals(SelfUpdater.Step.Install, updates.next(140, false, true))
        answer(140, PackageInstaller.STATUS_FAILURE)
        assertEquals(SelfUpdater.Step.Browser, updates.next(140, false, true))

        answer(141, PackageInstaller.STATUS_FAILURE_CONFLICT)
        assertEquals(SelfUpdater.Step.Browser, updates.next(141, false, true))
    }

    @Test
    fun `failures count against the build in the file, so a newer one isn't given up on untried`() {
        // The fixed-name file still holds 135 while GitHub already lists 140.
        apks["apk-135"] = ApkFacts(app.packageName, 135, ourKey)
        serving = { "apk-135" }
        stageFails = refused
        val updates = updates()
        repeat(LiveUpdates.MAX_INSTALL_FAILURES) { updates.tap(140) }
        assertEquals(SelfUpdater.Step.Browser, updates.next(135, false, true))
        assertEquals("140 was never tried", SelfUpdater.Step.Install, updates.next(140, false, true))
    }

    private fun answer(session: Int, status: Int, confirm: Intent? = null) = UpdateInstaller.Result().onReceive(
        app,
        Intent(app, UpdateInstaller.Result::class.java)
            .putExtra(UpdateInstaller.EXTRA_BUILD, 140)
            .putExtra(PackageInstaller.EXTRA_SESSION_ID, session)
            .putExtra(PackageInstaller.EXTRA_STATUS, status)
            .apply { confirm?.let { putExtra(Intent.EXTRA_INTENT, it) } },
    )

    @Test
    fun `an answer about an older session doesn't end the attempt under way`() {
        assertTrue(UpdateInstaller.start())
        UpdateInstaller.committed = 5
        answer(9, PackageInstaller.STATUS_FAILURE_ABORTED)
        assertTrue("still installing", UpdateInstaller.installing.value)
        answer(5, PackageInstaller.STATUS_FAILURE_ABORTED)
        assertFalse(UpdateInstaller.installing.value)
    }

    @Test
    fun `Android's question held for a return is asked once, and dropped over new typing`() {
        val confirm = Intent("android.content.pm.action.CONFIRM_INSTALL")
        // A real session, so the check for one Android dropped finds it still waiting.
        val session = app.packageManager.packageInstaller.createSession(
            PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL),
        )
        fun held() {
            assertTrue(UpdateInstaller.start())
            UpdateInstaller.committed = session
            UpdateInstaller.committedAt = clock
            UpdateInstaller.inFront = false
            answer(session, PackageInstaller.STATUS_PENDING_USER_ACTION, confirm)
        }
        val activity = org.robolectric.Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
        val started = { org.robolectric.Shadows.shadowOf(activity).nextStartedActivity?.action }
        val updates = updates()

        held()
        updates.resumed(activity, unsaved = false)
        assertEquals("android.content.pm.action.CONFIRM_INSTALL", started())
        // Answered with Update and still installing: not asked a second time.
        updates.resumed(activity, unsaved = false)
        assertNull(started())
        assertTrue(UpdateInstaller.installing.value)

        UpdateInstaller.finish()
        held()
        updates.resumed(activity, unsaved = true)
        assertNull("not asked over typing", started())
        assertEquals("the waiting session is dropped", listOf(session), abandoned)
        assertFalse(UpdateInstaller.installing.value)
        UpdateInstaller.inFront = false
    }

    @Test
    fun `a question left unanswered with Home doesn't hold the notice for good`() {
        val session = app.packageManager.packageInstaller.createSession(
            PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL),
        )
        assertTrue(UpdateInstaller.start())
        UpdateInstaller.committed = session
        UpdateInstaller.committedAt = clock
        val activity = org.robolectric.Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
        val updates = updates()
        clock += LiveUpdates.UNANSWERED_MILLIS
        updates.resumed(activity, unsaved = false)
        assertTrue("an approved install may still be finishing", UpdateInstaller.installing.value)
        clock += 1
        updates.resumed(activity, unsaved = false)
        assertEquals(listOf(session), abandoned)
        assertFalse("the notice can be tapped again", UpdateInstaller.installing.value)
        UpdateInstaller.inFront = false
    }

    @Test
    fun `while the newest file lags, a build given up on isn't downloaded and tried again`() {
        apks["apk-135"] = ApkFacts(app.packageName, 135, ourKey)
        serving = { "apk-135" }
        stageFails = refused
        val updates = updates()
        repeat(LiveUpdates.MAX_INSTALL_FAILURES) { updates.tap(140) }
        assertEquals(LiveUpdates.MAX_INSTALL_FAILURES, stageCalls)
        val problem = updates.tap(140)!!
        assertTrue(problem.offerLink)
        assertTrue(problem.why.startsWith("Carry gave up on build 135"))
        assertEquals("not staged again", LiveUpdates.MAX_INSTALL_FAILURES, stageCalls)
    }

    @Test
    fun `typing comes first, then the permission, then the install`() {
        val updates = updates()
        assertEquals(SelfUpdater.Step.SaveFirst, updates.next(140, unsaved = true, canInstall = false))
        assertEquals(SelfUpdater.Step.AllowInstalls, updates.next(140, unsaved = false, canInstall = false))
        assertEquals(SelfUpdater.Step.Install, updates.next(140, unsaved = false, canInstall = true))
    }

    @Test
    fun `a failed download offers the link, isn't counted, and lets the owner try again`() {
        serving = { throw IOException("the download answered 404") }
        val updates = updates()
        assertEquals(UpdateProblem("Couldn't download the update: the download answered 404.", offerLink = true), updates.tap())
        assertFalse(UpdateInstaller.installing.value)
        repeat(3) { updates.tap() }
        assertEquals(SelfUpdater.Step.Install, updates.next(140, false, true))
    }

    private fun LiveUpdates.fetched(target: Int = 140) = runBlocking { fetch(target) }
    private fun LiveUpdates.inBackground() = runBlocking { installInBackground() }

    @Test
    fun `a newer build waits for Wi-Fi to download, and the check runs every six hours on any network`() {
        val updates = updates()
        updates.start()
        val check = infos(LiveCheckWorker.UNIQUE).single()
        assertEquals(NetworkType.CONNECTED, check.constraints.requiredNetworkType)
        assertEquals(6 * 60 * 60 * 1000L, check.periodicityInfo!!.repeatIntervalMillis)

        updates.newerBuild(140)
        val download = infos(LiveDownloadWorker.UNIQUE).single()
        assertEquals(NetworkType.UNMETERED, download.constraints.requiredNetworkType)
        assertTrue(download.constraints.requiresStorageNotLow())
        assertEquals("nothing to install yet", emptyList<WorkInfo>(), infos(LiveInstallWorker.UNIQUE))

        updates.newerBuild(130)
        updates.newerBuild(125)
        assertEquals("not newer: nothing more enqueued", 1, infos(LiveDownloadWorker.UNIQUE).size)
    }

    @Test
    fun `a downloaded build is installed while Carry is closed with nothing unsaved`() {
        val updates = updates()
        assertEquals(LiveUpdates.Outcome.Done, updates.fetched())
        val install = infos(LiveInstallWorker.UNIQUE).single()
        assertTrue("Android's copy needs room too", install.constraints.requiresStorageNotLow())
        assertEquals(LiveUpdates.Outcome.Started, updates.inBackground())
        assertEquals(listOf(140), background)
    }

    @Test
    fun `Carry on screen or typing not saved holds the background install back`() {
        val updates = updates()
        updates.fetched()
        busy = true
        assertEquals(LiveUpdates.Outcome.Busy, updates.inBackground())
        assertEquals("nothing staged while busy", emptyList<Int>(), staged)
        assertFalse(UpdateInstaller.installing.value)
    }

    @Test
    fun `typing that starts while the build is copied stops the background install`() {
        val updates = LiveUpdates(
            app, 130, prefs = prefs, dir = dir, download = { it.writeText("apk-140") }, inspect = { apks[it.readText()] },
            installed = { installed }, stage = { busy = true; 7 }, commit = { id, b, _ -> committed += id to b },
            abandon = { abandoned += it }, canInstallNow = { true }, busy = { busy }, work = { work },
        )
        updates.fetched()
        assertEquals(LiveUpdates.Outcome.Busy, updates.inBackground())
        assertEquals(emptyList<Pair<Int, Int>>(), committed)
        assertEquals(listOf(7), abandoned)
        assertFalse(UpdateInstaller.installing.value)
    }

    @Test
    fun `a tap after the background download installs that file without downloading again`() {
        val updates = updates()
        updates.fetched()
        assertEquals(1, downloads)
        assertNull(updates.tap())
        assertEquals(1, downloads)
        assertEquals(listOf(7 to 140), committed)
        assertEquals("a tap is not a background attempt", emptyList<Int>(), background)
    }

    @Test
    fun `when Android wants a yes, the background waits for the owner's tap`() {
        val updates = updates()
        updates.fetched()
        UpdateInstaller.Result().onReceive(
            app,
            Intent(app, UpdateInstaller.Result::class.java)
                .putExtra(UpdateInstaller.EXTRA_BUILD, 140)
                .putExtra(UpdateInstaller.EXTRA_BACKGROUND, true)
                .putExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_PENDING_USER_ACTION)
                .putExtra(Intent.EXTRA_INTENT, Intent("android.content.pm.action.CONFIRM_INSTALL")),
        )
        assertNull("no dialog out of nowhere", UpdateInstaller.awaitingConfirm)
        assertFalse(UpdateInstaller.installing.value)
        assertEquals(LiveUpdates.Outcome.Done, updates.inBackground())
        assertEquals("not asked again in the background", emptyList<Int>(), staged)
        assertNull(updates.tap())
        assertEquals(listOf(7 to 140), committed)
    }

    @Test
    fun `without permission to install apps the background does nothing`() {
        canInstall = false
        val updates = updates()
        updates.fetched()
        assertEquals(LiveUpdates.Outcome.Done, updates.inBackground())
        assertEquals(emptyList<Int>(), staged)
    }

    @Test
    fun `a download that keeps failing is given up on after five tries`() {
        serving = { throw IOException("connection reset") }
        val updates = updates()
        repeat(LiveUpdates.DOWNLOAD_TRIES - 1) { assertEquals(LiveUpdates.Outcome.Retry, updates.fetched()) }
        assertEquals(LiveUpdates.Outcome.Failed, updates.fetched())
        assertEquals(SelfUpdater.Step.Browser, updates.next(140, false, true))
        updates.newerBuild(140)
        assertEquals("a given-up build isn't fetched again", emptyList<WorkInfo>(), infos(LiveDownloadWorker.UNIQUE))
    }

    @Test
    fun `a downloaded file of another key is refused and nothing is kept`() {
        apks["apk-140"] = ApkFacts(app.packageName, 140, setOf("someone-else"))
        val updates = updates()
        assertEquals(LiveUpdates.Outcome.Failed, updates.fetched())
        assertEquals(emptyList<String>(), dir.list()!!.toList())
        assertEquals(LiveUpdates.Outcome.Done, updates.inBackground())
        assertEquals(emptyList<Int>(), staged)
    }

    @Test
    fun `a tap while the background is installing says so`() {
        assertTrue("the background's attempt", UpdateInstaller.start())
        val problem = updates().tap()!!
        assertTrue(problem.why.startsWith("Carry is already installing an update"))
        assertEquals(0, downloads)
    }

    @Test
    fun `a tap's own download doesn't outlive the next start`() {
        stageFails = IOException("No space left on device")
        updates().tap()
        assertEquals(listOf("carry-debug.apk"), dir.list()!!.toList())
        updates().start()
        assertEquals(emptyList<String>(), dir.list()!!.toList())
    }

    @Test
    fun `while the newest file lags, a build given up on isn't kept as the one to install`() {
        apks["apk-135"] = ApkFacts(app.packageName, 135, ourKey)
        serving = { "apk-135" }
        stageFails = refused
        val updates = updates()
        repeat(LiveUpdates.MAX_INSTALL_FAILURES) { updates.tap(135) }
        repeat(LiveUpdates.DOWNLOAD_TRIES + 1) { assertEquals(LiveUpdates.Outcome.Retry, updates.fetched(140)) }
        assertEquals("140 is never given up on untried", SelfUpdater.Step.Install, updates.next(140, false, true))
        assertEquals(emptyList<String>(), dir.list()!!.filter { it.startsWith("build-") })
        assertEquals(emptyList<WorkInfo>(), infos(LiveInstallWorker.UNIQUE))
    }

    @Test
    fun `the build that installed clears its file away on the next start`() {
        updates().fetched()
        assertEquals(listOf("build-140.apk"), dir.list()!!.toList())
        updates(installedBuild = 140).start()
        assertEquals(emptyList<String>(), dir.list()!!.toList())
    }
}

/** The APK download, against a local server standing in for GitHub and its file host. */
@RunWith(AndroidJUnit4::class)
class UpdateDownloadTest {
    private val server = MockWebServer().apply { start() }
    private val dir = java.nio.file.Files.createTempDirectory("carry-update").toFile()
    private val dest = File(dir, "carry-debug.apk")

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
        assertThrows(IOException::class.java) { UpdateInstaller.download(server.url("/x").toString(), dest) }
        assertEquals(emptyList<String>(), dir.list()!!.toList())
    }

    @Test
    fun `a file larger than any build is abandoned, part and all`() {
        server.enqueue(MockResponse.Builder().body("x".repeat(200 * 1024)).build())
        assertThrows(IOException::class.java) {
            UpdateInstaller.download(server.url("/x").toString(), dest, maxBytes = 100 * 1024)
        }
        assertEquals(emptyList<String>(), dir.list()!!.toList())
    }
}

/** The debug build carries the installer; the release build's merged manifest is checked by `check`. */
@RunWith(AndroidJUnit4::class)
class LiveBuildManifestTest {
    @Test
    fun `the debug build can install its updates, and its answer can only come from Android`() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        @Suppress("DEPRECATION")
        val info = app.packageManager.getPackageInfo(app.packageName, PackageManager.GET_PERMISSIONS or PackageManager.GET_RECEIVERS)
        assertTrue(info.requestedPermissions.orEmpty().contains("android.permission.REQUEST_INSTALL_PACKAGES"))
        assertTrue(info.receivers.orEmpty().any { it.name == UpdateInstaller.Result::class.java.name && !it.exported })
    }
}

/** What the archive check reads from a file, as far as Robolectric can parse one. */
@RunWith(AndroidJUnit4::class)
class ApkCheckTest {
    @get:Rule val tmp = TemporaryFolder()

    private val installed = ApkFacts("com.app.carry.debug", 130, setOf("our-key"))

    @Test
    fun `only a newer build of this app with its key passes`() {
        val newer = installed.copy(versionCode = 140)
        assertEquals(Verdict.Good, verdict(newer, installed))
        assertTrue("another app", verdict(newer.copy(packageName = "com.app.carry"), installed) is Verdict.Bad)
        assertTrue("another key beside ours", verdict(newer.copy(signers = setOf("our-key", "x")), installed) is Verdict.Bad)
        assertTrue("an older build with another key", verdict(installed.copy(versionCode = 120, signers = setOf("x")), installed) is Verdict.Bad)
        assertTrue("the same build", verdict(installed, installed) is Verdict.Stale)
        assertTrue("no key read on either side", verdict(newer.copy(signers = emptySet()), installed.copy(signers = emptySet())) is Verdict.Bad)
    }

    @Test
    fun `a file that isn't an APK has no facts`() {
        val junk = tmp.newFile("carry-debug.apk").apply { writeText("<html>Rate limited</html>") }
        assertNull(ApplicationProvider.getApplicationContext<Context>().packageManager.apkFacts(junk))
    }
}
