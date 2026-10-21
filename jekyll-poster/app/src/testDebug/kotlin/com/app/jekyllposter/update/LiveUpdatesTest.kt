package com.app.jekyllposter.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import java.io.File
import java.io.IOException
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
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
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf

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
    private var publishing = false
    private var screenLeftOpen = false
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

    @After fun tearDown() {
        UpdateInstaller.finish()
        UpdateInstaller.inFront = false
        UpdateInstaller.leftFrontAt = 0
    }

    private fun updates(installedBuild: Int = 130, stage: (File) -> Int = { stageCalls++; stageFails?.let { e -> throw e }; staged += 7; 7 }) = LiveUpdates(
        app, installedBuild, OkHttpClient(), waitFor = { if (publishing) Hold.Post else if (screenLeftOpen) Hold.Screen else null }, prefs = prefs, dir = dir,
        download = { downloads++; it.writeText(serving()) },
        inspect = { apks[it.readText()] },
        installed = { installed },
        stage = stage,
        commit = { id, build, bg -> committed += id to build; if (bg) background += build },
        abandon = { abandoned += it },
        canInstallNow = { canInstall },
        now = { clock },
        work = { work },
    )

    private fun infos(name: String) = work.getWorkInfosForUniqueWork(name).get()

    private fun LiveUpdates.tap(build: Int = 140, hold: () -> Hold? = { null }) = runBlocking { install(build, hold) }

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
            assertTrue(text, problem.why.startsWith("The download isn't a Jekyll Poster this phone can trust"))
            assertEquals("never staged: $text", emptyList<Int>(), staged)
            assertFalse("the notice must be tappable again", UpdateInstaller.installing.value)
            assertEquals("$text: the next tap opens the browser", SelfUpdater.Step.Browser, updates.next(140, publishing = false, canInstall = true))
        }
        assertEquals("a newer build is tried again", SelfUpdater.Step.Install, updates().next(141, publishing = false, canInstall = true))
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
    fun `a post that starts going out while the build is copied holds the install until it's sent`() {
        // Nothing going out at the tap; a post starts by the time the copy is done.
        val updates = updates()
        val problem = updates.tap { if (staged.isNotEmpty()) Hold.Post else null }!!
        assertEquals(UpdateProblem(SelfUpdater.WAIT_FOR_SENT, offerLink = false, resumes = true), problem)
        assertEquals(emptyList<Pair<Int, Int>>(), committed)
        assertEquals("the staged session must not be left behind", listOf(7), abandoned)
        assertFalse(UpdateInstaller.installing.value)
        assertNull("sent: the kept file installs", updates.tap())
        assertEquals(1, downloads)
    }

    @Test
    fun `a post going out comes first, then the permission, then the install`() {
        val updates = updates()
        assertEquals(SelfUpdater.Step.WaitForPost, updates.next(140, publishing = true, canInstall = false))
        assertEquals(SelfUpdater.Step.AllowInstalls, updates.next(140, publishing = false, canInstall = false))
        assertEquals(SelfUpdater.Step.Install, updates.next(140, publishing = false, canInstall = true))
    }

    @Test
    fun `opening a post while the build is copied keeps it for when the post is left`() {
        val updates = updates()
        val problem = updates.tap { if (staged.isNotEmpty()) Hold.Screen else null }!!
        assertEquals(UpdateProblem(SelfUpdater.WAIT_FOR_SCREEN, offerLink = false, resumes = true), problem)
        assertEquals(emptyList<Pair<Int, Int>>(), committed)
        assertEquals("the staged session must not be left behind", listOf(7), abandoned)
        assertFalse(UpdateInstaller.installing.value)
        // Left the post: the checked file installs without downloading again.
        assertNull(updates.tap())
        assertEquals(1, downloads)
        assertEquals(listOf(7 to 140), committed)
    }

    @Test
    fun `the tap's download without the VPN offers no link, and isn't counted`() {
        serving = { throw com.app.jekyllposter.core.github.NoVpnException() }
        val updates = updates()
        repeat(LiveUpdates.MAX_INSTALL_FAILURES + 1) {
            assertEquals(UpdateProblem(SelfUpdater.WAIT_FOR_VPN, offerLink = false), updates.tap())
        }
        assertEquals(SelfUpdater.Step.Install, updates.next(140, false, true))
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

        // In the background too: tried again later, never given up on.
        UpdateInstaller.finish()
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
        assertTrue(updates.tap()!!.why.endsWith("The app won't try this build again."))
        assertEquals(SelfUpdater.Step.Browser, updates.next(140, false, true))
        assertFalse(UpdateInstaller.installing.value)
    }

    private fun answer(build: Int = 140, status: Int, session: Int? = null, background: Boolean = false, confirm: Intent? = null) =
        UpdateInstaller.Result().onReceive(
            app,
            Intent(app, UpdateInstaller.Result::class.java)
                .putExtra(UpdateInstaller.EXTRA_BUILD, build)
                .putExtra(UpdateInstaller.EXTRA_BACKGROUND, background)
                .putExtra(PackageInstaller.EXTRA_STATUS, status)
                .apply { session?.let { putExtra(PackageInstaller.EXTRA_SESSION_ID, it) } }
                .apply { confirm?.let { putExtra(Intent.EXTRA_INTENT, it) } },
        )

    @Test
    fun `Android's own failures count, and one that can't ever install gives up at once`() {
        val updates = updates()
        repeat(3) { answer(status = PackageInstaller.STATUS_FAILURE_ABORTED) }
        assertEquals("the writer saying no isn't a failure", SelfUpdater.Step.Install, updates.next(140, false, true))
        repeat(LiveUpdates.MAX_INSTALL_FAILURES) { answer(status = PackageInstaller.STATUS_FAILURE_STORAGE) }
        assertEquals("no room isn't the build's failure", SelfUpdater.Step.Install, updates.next(140, false, true))
        val retry = infos(LiveInstallWorker.UNIQUE).single()
        assertTrue("tried again once there's room", retry.constraints.requiresStorageNotLow())
        repeat(LiveUpdates.MAX_INSTALL_FAILURES - 1) { answer(status = PackageInstaller.STATUS_FAILURE) }
        assertEquals(SelfUpdater.Step.Install, updates.next(140, false, true))
        answer(status = PackageInstaller.STATUS_FAILURE)
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
        val problem = updates.tap(140)!!
        assertTrue(problem.why.startsWith("The app gave up on build 135"))
        assertEquals("not staged again", LiveUpdates.MAX_INSTALL_FAILURES, stageCalls)
    }

    @Test
    fun `an answer about an older session doesn't end the attempt under way`() {
        assertTrue(UpdateInstaller.start())
        UpdateInstaller.committed = 5
        answer(status = PackageInstaller.STATUS_FAILURE_ABORTED, session = 9)
        assertTrue("still installing", UpdateInstaller.installing.value)
        answer(status = PackageInstaller.STATUS_FAILURE_ABORTED, session = 5)
        assertFalse(UpdateInstaller.installing.value)
    }

    @Test
    fun `Android's question held while the app was behind another is asked once on return`() {
        val confirm = Intent("android.content.pm.action.CONFIRM_INSTALL")
        // A real session, so the check for one Android dropped finds it still waiting.
        val session = app.packageManager.packageInstaller.createSession(
            PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL),
        )
        assertTrue(UpdateInstaller.start())
        UpdateInstaller.committed = session
        UpdateInstaller.committedAt = clock
        UpdateInstaller.inFront = false
        answer(status = PackageInstaller.STATUS_PENDING_USER_ACTION, session = session, confirm = confirm)
        val activity = Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
        val started = { shadowOf(activity).nextStartedActivity?.action }
        val updates = updates()

        updates.resumed(activity)
        assertEquals("android.content.pm.action.CONFIRM_INSTALL", started())
        // Answered with Update and still installing: not asked a second time.
        updates.resumed(activity)
        assertNull(started())
        assertTrue(UpdateInstaller.installing.value)
    }

    @Test
    fun `a question left unanswered with Home doesn't hold the notice for good`() {
        val session = app.packageManager.packageInstaller.createSession(
            PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL),
        )
        assertTrue(UpdateInstaller.start())
        UpdateInstaller.committed = session
        UpdateInstaller.committedAt = clock
        val activity = Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
        val updates = updates()
        clock += LiveUpdates.UNANSWERED_MILLIS
        updates.resumed(activity)
        assertTrue("an approved install may still be finishing", UpdateInstaller.installing.value)
        clock += 1
        updates.resumed(activity)
        assertEquals(listOf(session), abandoned)
        assertFalse("the notice can be tapped again", UpdateInstaller.installing.value)
    }

    @Test
    fun `a failed download offers the link, isn't counted, and lets the writer try again`() {
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
    fun `a downloaded build is installed while the app is closed and no post is going out`() {
        val updates = updates()
        assertEquals(LiveUpdates.Outcome.Done, updates.fetched())
        val install = infos(LiveInstallWorker.UNIQUE).single()
        assertTrue("Android's copy needs room too", install.constraints.requiresStorageNotLow())
        assertEquals(LiveUpdates.Outcome.Started, updates.inBackground())
        assertEquals(listOf(140), background)
    }

    @Test
    fun `the app on screen, only just left, sending a post or a post left open holds the background install back`() {
        val updates = updates()
        updates.fetched()
        for ((why, set) in listOf<Pair<String, () -> Unit>>(
            "on screen" to { UpdateInstaller.inFront = true },
            "only just left: the editor saves a moment later" to { updates.paused() },
            "a post going out" to { publishing = true },
            "a post's editor left open, perhaps behind the camera" to { screenLeftOpen = true },
        )) {
            UpdateInstaller.inFront = false
            UpdateInstaller.leftFrontAt = 0
            publishing = false
            screenLeftOpen = false
            set()
            assertEquals(why, LiveUpdates.Outcome.Busy, updates.inBackground())
            assertEquals("nothing staged: $why", emptyList<Int>(), staged)
            assertFalse(UpdateInstaller.installing.value)
        }
        publishing = false
        screenLeftOpen = false
        updates.paused()
        clock += LiveUpdates.SETTLE_MILLIS - 1
        assertEquals(LiveUpdates.Outcome.Busy, updates.inBackground())
        clock += 1
        assertEquals("left a while ago", LiveUpdates.Outcome.Started, updates.inBackground())
    }

    @Test
    fun `a post that starts while the build is copied stops the background install`() {
        val updates = updates(stage = { publishing = true; 7 })
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
    fun `when Android wants a yes, the background waits for the writer's tap`() {
        val updates = updates()
        updates.fetched()
        answer(status = PackageInstaller.STATUS_PENDING_USER_ACTION, background = true, confirm = Intent("android.content.pm.action.CONFIRM_INSTALL"))
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
        assertTrue(problem.why.startsWith("The app is already installing an update"))
        assertEquals(0, downloads)
    }

    @Test
    fun `a tap's own download doesn't outlive the next start`() {
        stageFails = IOException("No space left on device")
        updates().tap()
        assertEquals(listOf("jekyll-poster-debug.apk"), dir.list()!!.toList())
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
    private val dir = java.nio.file.Files.createTempDirectory("jekyll-poster-update").toFile()
    private val dest = File(dir, "jekyll-poster-debug.apk")
    private val calls = OkHttpClient()

    @After fun stop() { server.close(); dir.deleteRecursively() }

    @Test
    fun `the file is downloaded through GitHub's redirect`() {
        server.enqueue(MockResponse.Builder().code(302).addHeader("Location", server.url("/file").toString()).build())
        server.enqueue(MockResponse.Builder().body("apk bytes").build())
        UpdateInstaller.download(calls, server.url("/release"), dest)
        assertEquals("apk bytes", dest.readText())
    }

    @Test
    fun `a refusal is an error, not a file`() {
        server.enqueue(MockResponse.Builder().code(404).build())
        assertThrows(IOException::class.java) { UpdateInstaller.download(calls, server.url("/x"), dest) }
        assertEquals(emptyList<String>(), dir.list()!!.toList())
    }

    @Test
    fun `a file larger than any build is abandoned, part and all`() {
        server.enqueue(MockResponse.Builder().body("x".repeat(200 * 1024)).build())
        assertThrows(IOException::class.java) { UpdateInstaller.download(calls, server.url("/x"), dest, maxBytes = 100 * 1024) }
        assertEquals(emptyList<String>(), dir.list()!!.toList())
    }
}

/** The download through the app's own update client, behind "Only connect through a VPN". */
@RunWith(AndroidJUnit4::class)
@org.robolectric.annotation.Config(application = com.app.jekyllposter.testutil.TestApp::class)
class UpdateDownloadVpnTest {
    private val app = ApplicationProvider.getApplicationContext<com.app.jekyllposter.testutil.TestApp>()
    private val server = MockWebServer().apply { start() }
    private val dir = java.nio.file.Files.createTempDirectory("jekyll-poster-update").toFile()
    private val dest = File(dir, "jekyll-poster-debug.apk")

    @After fun stop() {
        server.close()
        app.github.close()
        dir.deleteRecursively()
    }

    @Test
    fun `with a blog asking for the VPN and none up, the download sends nothing`() {
        runBlocking { app.signIn(); app.container.setOnlyThroughVpn(com.app.jekyllposter.testutil.SAMPLE_BLOG, true) }
        app.vpn.up.value = false
        server.enqueue(MockResponse.Builder().body("apk bytes").build())
        assertThrows(com.app.jekyllposter.core.github.NoVpnException::class.java) {
            UpdateInstaller.download(app.container.updateCalls, server.url("/release"), dest)
        }
        assertEquals(0, server.requestCount)
        assertEquals(emptyList<String>(), dir.list()!!.toList())
    }

    @Test
    fun `with the VPN up, it downloads through it`() {
        runBlocking { app.signIn(); app.container.setOnlyThroughVpn(com.app.jekyllposter.testutil.SAMPLE_BLOG, true) }
        server.enqueue(MockResponse.Builder().body("apk bytes").build())
        UpdateInstaller.download(app.container.updateCalls, server.url("/release"), dest)
        assertEquals("apk bytes", dest.readText())
        assertTrue(app.vpn.connections > 0)
    }
}

/** The debug build carries the installer; Gradle's `checkReleaseSelfUpdateManifest` checks a release doesn't. */
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

    private val installed = ApkFacts("com.app.jekyllposter.debug", 130, setOf("our-key"))

    @Test
    fun `only a newer build of this app with its key passes`() {
        val newer = installed.copy(versionCode = 140)
        assertEquals(Verdict.Good, verdict(newer, installed))
        assertTrue("another app", verdict(newer.copy(packageName = "com.app.jekyllposter"), installed) is Verdict.Bad)
        assertTrue("another key beside ours", verdict(newer.copy(signers = setOf("our-key", "x")), installed) is Verdict.Bad)
        assertTrue("an older build with another key", verdict(installed.copy(versionCode = 120, signers = setOf("x")), installed) is Verdict.Bad)
        assertTrue("the same build", verdict(installed, installed) is Verdict.Stale)
        assertTrue("no key read on either side", verdict(newer.copy(signers = emptySet()), installed.copy(signers = emptySet())) is Verdict.Bad)
    }

    @Test
    fun `a file that isn't an APK has no facts`() {
        val junk = tmp.newFile("jekyll-poster-debug.apk").apply { writeText("<html>Rate limited</html>") }
        assertNull(ApplicationProvider.getApplicationContext<Context>().packageManager.apkFacts(junk))
    }
}
