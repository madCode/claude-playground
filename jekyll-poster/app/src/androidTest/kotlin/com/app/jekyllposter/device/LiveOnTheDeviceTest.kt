package com.app.jekyllposter.device

import android.Manifest
import android.os.ParcelFileDescriptor
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.exifinterface.media.ExifInterface
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.services.storage.TestStorage
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.app.jekyllposter.MainActivity
import com.app.jekyllposter.core.github.FileChange
import com.app.jekyllposter.core.github.GitHubClient
import com.app.jekyllposter.data.Account
import com.app.jekyllposter.data.AndroidVpn
import com.app.jekyllposter.data.Draft
import com.app.jekyllposter.data.PostState
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The app as installed, against the real GitHub and the tester's blogs (docs/TESTER_ACCOUNT.md),
 * with the request record on. Only the "Jekyll Poster device live check" workflow runs it, with
 * `liveRepoA`/`liveTokenA` and `liveRepoB`/`liveTokenB` as instrumentation arguments: two blogs
 * under two sign-ins, for the several-blogs audit, and `vpnTunnel` when it has a VPN to try.
 * Elsewhere it's skipped.
 *
 * Blog A is signed in to through the screens and published to from the editor; blog B is added
 * as a second sign-in and its post goes out through WorkManager. Both wait for the "is live"
 * notification, so Pages has built them. The posts are deleted through the API afterwards, and
 * the record is kept as a test output (`requests.jsonl`).
 */
@RunWith(AndroidJUnit4::class)
class LiveOnTheDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    @get:Rule val onFailure = ScreenOnFailure()

    private val app = ApplicationProvider.getApplicationContext<DeviceTestApp>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)
    private val args = InstrumentationRegistry.getArguments()
    private val http = OkHttpClient.Builder().readTimeout(60, TimeUnit.SECONDS).build()

    private class Blog(val owner: String, val name: String, val token: String) {
        val fullName get() = "$owner/$name"
        lateinit var branch: String
    }

    private fun blog(letter: String): Blog? {
        val repo = args.getString("liveRepo$letter").orEmpty()
        val token = args.getString("liveToken$letter").orEmpty()
        if (!repo.contains('/') || token.isBlank()) return null
        val (owner, name) = repo.split('/', limit = 2)
        return Blog(owner, name, token)
    }

    private val a = blog("A")
    private val b = blog("B")

    // One stamp for the run's posts, so cleanup finds them even after a failure.
    private val stamp = System.currentTimeMillis() / 1000
    private val titleA = "Device check $stamp"
    private val titleB = "Device check $stamp, second blog"
    private val titlePrivate = "Device check $stamp, private"
    private val titleVpn = "Device check $stamp, through the VPN"
    private val titlePhoto = "Device check $stamp, photo"
    private val titleConflict = "Device check $stamp, conflict"
    private val titleScheduled = "Device check $stamp, scheduled"
    private val titleHidden = "Device check $stamp, hidden"
    private val titleDraft = "Device check $stamp, draft"
    private val record = File(app.filesDir, "requests")

    @Before fun setUp() {
        assumeTrue("A live run only: pass liveRepoA, liveTokenA, liveRepoB and liveTokenB", a != null && b != null)
        instrumentation.uiAutomation.grantRuntimePermission(app.packageName, Manifest.permission.POST_NOTIFICATIONS)
        record.mkdirs()
        File(record, "on").createNewFile()
        runBlocking { listOf(a!!, b!!).forEach { it.branch = GitHubClient(http, it.token).repo(it.owner, it.name).defaultBranch } }
    }

    @After fun cleanUp() {
        if (a == null || b == null) return
        device.pressHome()
        runBlocking {
            for (blog in listOf(a, b)) {
                val github = GitHubClient(http, blog.token)
                // With an earlier run's post from the kill check, which the workflow only looks for.
                github.files(blog.owner, blog.name, blog.branch).filter { "device-check-$stamp" in it.path || "device-check-run-" in it.path }.forEach { entry ->
                    // One file refused doesn't leave the rest on the public blog.
                    runCatching {
                        val sent = github.file(blog.owner, blog.name, blog.branch, entry.path) ?: return@runCatching
                        github.commit(blog.owner, blog.name, blog.branch, "Delete post: device check", listOf(FileChange.delete(entry.path)), expect = mapOf(entry.path to sent.sha))
                    }
                }
            }
        }
        // Kept for the audit; the orchestrator clears the app's own files after the test. Each
        // test's record under its own name, as both run in one workflow.
        File(record, "requests.jsonl").takeIf { it.exists() }?.let { file ->
            TestStorage().openOutputFile("requests-$testName.jsonl").use { out -> file.inputStream().use { it.copyTo(out) } }
        }
    }

    private fun waitFor(text: String, timeoutMs: Long = 30_000) =
        compose.waitUntil(timeoutMs) { compose.onAllNodes(hasText(text, substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }

    /** Waits for [condition] on GitHub, polling every few seconds. */
    private fun waitUntil(what: String, timeoutMs: Long, condition: suspend () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (!runBlocking { condition() }) {
            check(System.currentTimeMillis() < end) { "Timed out waiting for $what" }
            Thread.sleep(5_000)
        }
    }

    private suspend fun hasPost(blog: Blog, slug: String) =
        GitHubClient(http, blog.token).files(blog.owner, blog.name, blog.branch).any { it.path.endsWith("-$slug.md") }

    /** The notification the writer would see, read from the shade; a Pages build takes a minute or two. */
    private fun hearItIsLive(title: String) {
        device.openNotification()
        check(device.wait(Until.hasObject(By.textContains("“$title” is live")), TimeUnit.MINUTES.toMillis(12))) { "No “is live” notification for $title" }
        device.pressBack()
    }

    private var testName = "test"

    @Test fun twoBlogsUnderTwoSignInsPublishAndGoLive() {
        testName = "two-blogs"
        val a = a!!
        val b = b!!
        ActivityScenario.launch(MainActivity::class.java)
        waitFor("Connect with a token")
        compose.onNodeWithText("Token").performTextInput(a.token)
        compose.onNodeWithText("Continue").performClick()
        waitFor(a.fullName)
        compose.onNodeWithText(a.fullName).performClick()
        waitFor("New post")

        compose.onNodeWithText("New post", useUnmergedTree = true).performClick()
        compose.waitUntil(20_000) { compose.onAllNodes(hasTestTag("title")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("title").performTextInput(titleA)
        compose.onNodeWithTag("body").performTextInput("Posted from an emulator by the nightly device check, then deleted.")
        compose.onNodeWithText("Publish").performClick()
        waitUntil("the post on ${a.fullName}", TimeUnit.MINUTES.toMillis(3)) { hasPost(a, "device-check-$stamp") }

        // A second sign-in, with its own token, added from Settings as a writer would. A blog
        // added beside another opens its Blog & privacy first: its switches start off.
        compose.onNodeWithContentDescription("More").performClick()
        compose.onNodeWithText("Settings").performClick()
        waitFor("+ Add a sign-in")
        compose.onNodeWithText("+ Add a sign-in").performClick()
        waitFor("Token")
        compose.onNodeWithText("Token").performTextInput(b.token)
        compose.onNodeWithText("Continue").performClick()
        waitFor(b.fullName)
        compose.onNodeWithText(b.fullName).performClick()
        waitFor("Blog & privacy")
        device.pressBack()
        waitFor("${b.name} · @")

        compose.onNodeWithText("New post", useUnmergedTree = true).performClick()
        compose.waitUntil(20_000) { compose.onAllNodes(hasTestTag("title")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("title").performTextInput(titleB)
        compose.onNodeWithTag("body").performTextInput("The second blog's post, from the same run.")
        compose.onNodeWithText("Publish").performClick()
        waitUntil("the post on ${b.fullName}", TimeUnit.MINUTES.toMillis(3)) { hasPost(b, "device-check-$stamp-second-blog") }

        // Back to the first blog, from Home's title.
        waitFor("${b.name} · @")
        compose.onNode(clickLabel("Switch blog")).performClick()
        waitFor("Your blogs")
        compose.onNode(clickLabel("Switch to this blog")).performClick()
        waitFor("${a.name} · @")

        hearItIsLive(titleA)
        hearItIsLive(titleB)
    }

    /** A node whose click action is labelled [label], as TalkBack announces it. */
    private fun clickLabel(label: String) = SemanticsMatcher("click label $label") {
        it.config.getOrNull(SemanticsActions.OnClick)?.label == label
    }

    private fun shell(command: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use { it.readBytes().decodeToString() }

    private fun getJson(blog: Blog, path: String): JsonObject {
        val request = Request.Builder().url("https://api.github.com/$path").header("Authorization", "Bearer ${blog.token}").header("Accept", "application/vnd.github+json").build()
        return http.newCall(request).execute().use { Json.parseToJsonElement(it.body.string()).jsonObject }
    }

    /**
     * Every switch "Set up for writing anonymously" turns on except the VPN, which has a test of its
     * own. Then a link with tracking codes is shared in,
     * published, and what GitHub kept of it is read back and written to `privacy-report.json`.
     */
    @Test fun atMostPrivacyGitHubKeepsOnlyTheAccountAndTheTimeItWentOut() {
        testName = "most-private"
        val a = a!!
        val key = "${a.fullName}@${a.branch}"
        signInToA()
        // Set up for writing anonymously, tapped in Blog & privacy: the no-reply address is
        // looked up from GitHub there, as for a writer.
        ActivityScenario.launch(MainActivity::class.java)
        waitFor("New post")
        compose.onNodeWithContentDescription("More").performClick()
        compose.onNodeWithText("Settings").performClick()
        waitFor("Blog & privacy")
        compose.onNodeWithText("Blog & privacy").performClick()
        waitFor("Set up for writing anonymously")
        compose.onNodeWithText("Set up for writing anonymously").performClick()
        compose.onNodeWithText("Turn them on").performClick()
        waitUntil("every switch on", 60_000) {
            app.container.privacy.get(key).let { p ->
                p.plainCommitMessages && p.sendAtRandomTime && p.datesByDayOnly && p.removeTrackingCodes && p.commitAsNoReply && p.onlyThroughVpn
            }
        }
        // The VPN has a test of its own, with a real tunnel; here the post goes without one.
        runBlocking { app.container.setOnlyThroughVpn(key, false) }
        device.pressBack()
        device.pressBack()

        val link = "https://example.com/read?utm_source=feed&utm_medium=social&fbclid=abc123&id=7"
        val started = shell("am start -W -a android.intent.action.SEND -t text/plain -p ${app.packageName} --es android.intent.extra.TEXT $link --es android.intent.extra.SUBJECT Good")
        assertTrue(started, started.contains("Status: ok"))
        compose.waitUntil(30_000) { compose.onAllNodes(hasTestTag("title")).fetchSemanticsNodes().isNotEmpty() }
        waitFor("example.com/read")
        compose.onNodeWithTag("title").performTextInput(titlePrivate)
        compose.onNodeWithText("Publish").performClick()

        // Sent at a random time: queued with a time ahead, then sent now, as Send now does.
        val queued = run {
            var draft: Draft? = null
            waitUntil("the post queued", 30_000) { draft = app.container.drafts.list().firstOrNull { it.title == titlePrivate && it.state == PostState.Queued }; draft != null }
            draft!!
        }
        val randomDelay = (queued.sendAfter ?: 0) - System.currentTimeMillis()
        assertTrue("Not given a random time: ${queued.sendAfter}", randomDelay > 0)
        runBlocking { app.container.sendNow(queued.id) }
        waitUntil("the private post on ${a.fullName}", TimeUnit.MINUTES.toMillis(3)) { hasPost(a, "device-check-$stamp-private") }

        val sha = run {
            var published: Draft? = null
            waitUntil("the post marked published", 60_000) { published = app.container.drafts.get(queued.id)?.takeIf { it.commitSha != null }; published != null }
            published!!.commitSha!!
        }
        val commit = getJson(a, "repos/${a.fullName}/commits/$sha")
        val details = commit["commit"]!!.jsonObject
        val author = details["author"]!!.jsonObject
        val committer = details["committer"]!!.jsonObject
        val path = runBlocking { GitHubClient(http, a.token).files(a.owner, a.name, a.branch).first { it.path.endsWith("-device-check-$stamp-private.md") }.path }
        val text = runBlocking { GitHubClient(http, a.token).text(a.owner, a.name, a.branch, path) }.orEmpty()
        // The commit as Git keeps it, with its time zone offset, from the public patch.
        val patchDate = http.newCall(Request.Builder().url("https://github.com/${a.fullName}/commit/$sha.patch").build()).execute()
            .use { r -> r.body.string().lineSequence().firstOrNull { it.startsWith("Date: ") }?.removePrefix("Date: ") }

        val report = buildJsonObject {
            put("message", details["message"]!!.jsonPrimitive.content)
            put("authorName", author["name"]!!.jsonPrimitive.content)
            put("authorEmail", author["email"]!!.jsonPrimitive.content)
            put("authorDate", author["date"]!!.jsonPrimitive.content)
            put("committerName", committer["name"]!!.jsonPrimitive.content)
            put("committerEmail", committer["email"]!!.jsonPrimitive.content)
            put("committerDate", committer["date"]!!.jsonPrimitive.content)
            put("patchDate", patchDate)
            put("attributedTo", commit["author"]?.let { (it as? JsonObject)?.get("login")?.jsonPrimitive?.content })
            put("verified", details["verification"]?.jsonObject?.get("verified")?.jsonPrimitive?.content)
            put("path", path)
            put("file", text)
            put("randomDelayMinutes", randomDelay / 60_000)
        }
        TestStorage().openOutputFile("privacy-report.json").use { it.write(report.toString().toByteArray()) }

        // What the switches promise; the report says what's left.
        assertEquals("Update blog", details["message"]!!.jsonPrimitive.content)
        assertTrue(author["email"]!!.jsonPrimitive.content, author["email"]!!.jsonPrimitive.content.endsWith("@users.noreply.github.com"))
        assertTrue(text, Regex("""(?m)^date: \d{4}-\d{2}-\d{2}$""").containsMatchIn(text))
        assertFalse(text, text.contains("utm_") || text.contains("fbclid"))
        assertTrue(text, text.contains("example.com/read?id=7"))
        // By the day alone with the no-reply address, the commit is dated by the post's day too:
        // midnight UTC, which GitHub gives the committer as well. The API shows dates in UTC
        // whatever Git keeps, so the stored offset is read from the patch.
        val day = Regex("""(?m)^date: (\d{4}-\d{2}-\d{2})$""").find(text)!!.groupValues[1]
        assertEquals("${day}T00:00:00Z", author["date"]!!.jsonPrimitive.content)
        assertEquals(author["date"]!!.jsonPrimitive.content, committer["date"]!!.jsonPrimitive.content)
        assertTrue(patchDate.orEmpty(), patchDate.orEmpty().endsWith("00:00:00 +0000"))
    }

    /**
     * Only connect through a VPN, with a real one: the WireGuard app and a Proton config, which
     * the workflow installs when it has the `PROTON_WG_CONF` secret and names as `vpnTunnel`.
     * Without the tunnel the post waits and nothing reaches GitHub; with it up, the post goes out,
     * and from another address than the phone's own.
     */
    @Test fun onlyThroughAVpnAPostWaitsThenGoesOutThroughIt() {
        testName = "vpn"
        val tunnel = args.getString("vpnTunnel").orEmpty()
        assumeTrue("Needs the WireGuard tunnel the workflow sets up from PROTON_WG_CONF", tunnel.isNotBlank())
        val a = a!!
        val key = "${a.fullName}@${a.branch}"
        setTunnel(tunnel, up = false)
        val ownAddress = publicAddress()
        try {
            val id = runBlocking {
                val login = GitHubClient(http, a.token).user().login
                app.container.accounts.save(Account(login, a.token, a.owner, a.name, a.branch))
                // Only the VPN switch: a random send time would hold the post back for its own reason.
                app.container.privacy.update(key) { it.copy(onlyThroughVpn = true, sendAtRandomTime = false) }
                app.container.drafts.insert(Draft(blog = key, title = titleVpn, body = "Sent through a VPN by the nightly device check, then deleted.", state = PostState.Queued))
                    .also { app.container.publishQueue.enqueue(it, null) }
            }
            ActivityScenario.launch(MainActivity::class.java)
            waitFor("Waiting for your VPN…")
            // Long enough for a worker that ignored the switch to have sent it.
            Thread.sleep(30_000)
            assertFalse("Sent without the VPN", runBlocking { hasPost(a, "device-check-$stamp-through-the-vpn") })
            val waiting = runBlocking { app.container.drafts.get(id) }!!
            assertEquals(PostState.Queued, waiting.state)
            assertEquals("Held for a random time, not the VPN", null, waiting.sendAfter)
            // Home read the blog meanwhile, and the worker may have tried: none of it may have got
            // a connection. The record sees only requests that did, each with GitHub's answer.
            val reached = File(record, "requests.jsonl").takeIf { it.exists() }?.readLines().orEmpty()
                .map { Json.parseToJsonElement(it).jsonObject }
                .filter { it["outcome"]!!.jsonPrimitive.content.matches(Regex("""\d{3}""")) }
                .map { "${it["method"]!!.jsonPrimitive.content} ${it["url"]!!.jsonPrimitive.content}" }
            assertEquals("Reached GitHub without the VPN", emptyList<String>(), reached)

            setTunnel(tunnel, up = true)
            val vpnAddress = publicAddress()
            assertTrue("The tunnel didn't change the address ($vpnAddress)", vpnAddress != ownAddress)
            waitUntil("the post on ${a.fullName} through the VPN", TimeUnit.MINUTES.toMillis(3)) { hasPost(a, "device-check-$stamp-through-the-vpn") }
        } catch (e: Throwable) {
            // The test's own failure is the one to report, not the tunnel's on the way out.
            runCatching { setTunnel(tunnel, up = false) }.exceptionOrNull()?.let(e::addSuppressed)
            throw e
        }
        setTunnel(tunnel, up = false)
    }

    /**
     * Sign in with GitHub as far as a test can take it: the build has the GitHub App's client ID,
     * so the button shows, and GitHub answers it with a device code, which proves the ID is one it
     * knows with device flow on. Entering the code needs a browser signed in as the tester, which
     * stays a person's check (docs/DEVICE-CHECKS.md). The code expires unused.
     */
    @Test fun signInWithGitHubGetsACodeFromGitHub() {
        testName = "sign-in-with-github"
        ActivityScenario.launch(MainActivity::class.java)
        waitFor("Connect with a token")
        compose.onNodeWithText("Sign in with GitHub").performClick()
        waitFor("Enter this code on GitHub")
        val deviceCode = SemanticsMatcher("a device code") { node ->
            node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { Regex("[A-Z0-9]{4}-[A-Z0-9]{4}").matches(it.text) }
        }
        assertTrue("No device code from GitHub on screen", compose.onAllNodes(deviceCode, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
    }

    private fun signInToA(): Blog {
        val a = a!!
        runBlocking { app.container.accounts.save(Account(GitHubClient(http, a.token).user().login, a.token, a.owner, a.name, a.branch)) }
        return a
    }

    private fun postPath(blog: Blog, slug: String) =
        runBlocking { GitHubClient(http, blog.token).files(blog.owner, blog.name, blog.branch).firstOrNull { it.path.endsWith("-$slug.md") }?.path }

    private fun text(blog: Blog, path: String) = runBlocking { GitHubClient(http, blog.token).text(blog.owner, blog.name, blog.branch, path) }

    /** A file's bytes as GitHub serves them, for a photo. */
    private fun bytes(blog: Blog, path: String): ByteArray {
        val request = Request.Builder().url("https://api.github.com/repos/${blog.fullName}/contents/$path?ref=${blog.branch}")
            .header("Authorization", "Bearer ${blog.token}").header("Accept", "application/vnd.github.raw").build()
        return http.newCall(request).execute().use { check(it.isSuccessful) { "No $path: ${it.code}" }; it.body.bytes() }
    }

    /**
     * Opens the blog's copy of [title] from Home, the row after the phone's own rows for it, which
     * come first and may show before the blog's list has loaded.
     */
    private fun openFromTheBlog(title: String) {
        val rows = runBlocking { app.container.database.drafts().list() }.count { it.title == title } + 1
        ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntil(TimeUnit.MINUTES.toMillis(2)) { compose.onAllNodes(hasText(title), useUnmergedTree = true).fetchSemanticsNodes().size >= rows }
        compose.onAllNodes(hasText(title), useUnmergedTree = true)[rows - 1].performClick()
        compose.waitUntil(30_000) { compose.onAllNodes(hasTestTag("body")).fetchSemanticsNodes().isNotEmpty() }
    }

    /**
     * A photo shared in from `:sender` with a location in it, published, then edited and deleted
     * from the screens: the copy on GitHub has no location, the edit lands, and the delete removes
     * the post.
     */
    @Test fun aPhotoPostIsEditedThenDeletedFromTheScreens() {
        testName = "photo-edit-delete"
        val a = signInToA()
        val started = shell("am start -W -n com.app.jekyllposter.sender/.ShareActivity --es to ${app.packageName} --es what photo")
        assertTrue(started, started.contains("Status: ok"))
        waitFor("Describe the photo")
        compose.onNodeWithTag("altText").performTextInput("A teal square")
        compose.onNodeWithText("Done").performClick()
        compose.waitUntil(30_000) { compose.onAllNodes(hasTestTag("title")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("title").performTextInput(titlePhoto)
        compose.onNodeWithText("Publish").performClick()
        val slug = "device-check-$stamp-photo"
        waitUntil("the photo post on ${a.fullName}", TimeUnit.MINUTES.toMillis(3)) { hasPost(a, slug) }
        val path = postPath(a, slug)!!
        val photo = Regex("""\{\{ '/([^']+)' \| relative_url \}\}""").find(text(a, path)!!)!!.groupValues[1]
        val exif = bytes(a, photo).inputStream().use { ExifInterface(it) }
        assertEquals("The photo on GitHub has a location", null, exif.latLong)

        openFromTheBlog(titlePhoto)
        compose.onNodeWithTag("body").performTextInput("Edited from the screens. ")
        compose.onNodeWithText("Update").performClick()
        waitUntil("the edit on ${a.fullName}", TimeUnit.MINUTES.toMillis(3)) { text(a, path)?.contains("Edited from the screens.") == true }

        openFromTheBlog(titlePhoto)
        compose.onNodeWithContentDescription("More").performClick()
        compose.onNodeWithText("Delete from the blog").performClick()
        compose.onNode(hasText("Delete") and hasAnyAncestor(isDialog())).performClick()
        waitUntil("the post gone from ${a.fullName}", TimeUnit.MINUTES.toMillis(3)) { !hasPost(a, slug) }
    }

    /**
     * A post scheduled from the editor's menu a few minutes ahead: nothing of it reaches GitHub
     * before its time, then it goes out by itself, through WorkManager.
     */
    @Test fun aScheduledPostWaitsForItsTimeThenGoesOut() {
        testName = "scheduled"
        val a = signInToA()
        ActivityScenario.launch(MainActivity::class.java)
        waitFor("New post")
        compose.onNodeWithText("New post", useUnmergedTree = true).performClick()
        compose.waitUntil(30_000) { compose.onAllNodes(hasTestTag("title")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("title").performTextInput(titleScheduled)
        compose.onNodeWithTag("body").performTextInput("Scheduled from the screens.")
        compose.onNodeWithContentDescription("More").performClick()
        compose.onNodeWithText("Schedule…").performClick()
        // Three whole minutes ahead, so the time typed is still ahead when Schedule is tapped.
        val at = LocalDateTime.now().plusMinutes(3).truncatedTo(ChronoUnit.MINUTES)
        waitFor("Tomorrow")
        compose.onNodeWithText(if (at.toLocalDate() == LocalDate.now()) "Today" else "Tomorrow").performClick()
        setTime(at.toLocalTime())
        compose.onNodeWithText("Schedule").performClick()
        waitFor("Scheduled for")

        val due = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val slug = "device-check-$stamp-scheduled"
        while (System.currentTimeMillis() < due - 20_000) {
            assertFalse("The scheduled post reached GitHub before its time", runBlocking { hasPost(a, slug) })
            Thread.sleep(15_000)
        }
        waitUntil("the scheduled post on ${a.fullName}", TimeUnit.MINUTES.toMillis(8)) { hasPost(a, slug) }
        // The app's own record: its first write for the post came at or after the time chosen.
        val firstWrite = File(record, "requests.jsonl").readLines().map { Json.parseToJsonElement(it).jsonObject }
            .first { it["method"]?.jsonPrimitive?.content == "POST" && it["url"]?.jsonPrimitive?.content.orEmpty().endsWith("/git/blobs") }
        val wrote = Instant.parse(firstWrite["at"]!!.jsonPrimitive.content).toEpochMilli()
        assertTrue("Sent at $wrote, before its time $due", wrote >= due - 1_000)
    }

    /** Opens a new post from Home and writes [title] and [body] in it. */
    private fun newPost(title: String, body: String) {
        ActivityScenario.launch(MainActivity::class.java)
        waitFor("New post")
        compose.onNodeWithText("New post", useUnmergedTree = true).performClick()
        compose.waitUntil(30_000) { compose.onAllNodes(hasTestTag("title")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("title").performTextInput(title)
        compose.onNodeWithTag("body").performTextInput(body)
    }

    /** Taps [item] in the editor's menu. */
    private fun fromTheMenu(item: String) {
        compose.onNodeWithContentDescription("More").performClick()
        compose.onNodeWithText(item).performClick()
    }

    /**
     * Hide from the site and Show on the site again, from the editor's menu, on a post published
     * from the screens: the file stays, marked `published: false`, the site rebuilds without it,
     * and showing takes the line out again.
     */
    @Test fun aPostIsHiddenFromTheSiteThenShownAgain() {
        testName = "hide-show"
        val a = signInToA()
        newPost(titleHidden, "Hidden, then shown.")
        compose.onNodeWithText("Publish").performClick()
        val slug = "device-check-$stamp-hidden"
        waitUntil("the post on ${a.fullName}", TimeUnit.MINUTES.toMillis(3)) { hasPost(a, slug) }
        val path = postPath(a, slug)!!

        openFromTheBlog(titleHidden)
        fromTheMenu("Hide from the site")
        waitUntil("published: false on ${a.fullName}", TimeUnit.MINUTES.toMillis(3)) { text(a, path)?.contains("published: false") == true }
        // The phone heard the site rebuild after the hide.
        waitFor("Hidden from the site; the file stays on GitHub", TimeUnit.MINUTES.toMillis(12))

        openFromTheBlog(titleHidden)
        fromTheMenu("Show on the site again")
        waitUntil("the post shown again on ${a.fullName}", TimeUnit.MINUTES.toMillis(3)) { text(a, path)?.contains("published:") == false }
    }

    /**
     * A Jekyll draft from the screens: saved to the blog's `_drafts`, updated there, then
     * published to the site, which moves it into `_posts` in one commit.
     */
    @Test fun aJekyllDraftIsSavedUpdatedThenPublishedToTheSite() {
        testName = "jekyll-draft"
        val a = signInToA()
        newPost(titleDraft, "First version.")
        fromTheMenu("Save to the blog's _drafts")
        val slug = "device-check-$stamp-draft"
        val draft = "_drafts/$slug.md"
        waitUntil("$draft on ${a.fullName}", TimeUnit.MINUTES.toMillis(3)) { text(a, draft) != null }

        openFromTheBlog(titleDraft)
        compose.onNodeWithTag("body").performTextInput("Second version. ")
        compose.onNodeWithText("Update draft").performClick()
        waitUntil("the draft updated on ${a.fullName}", TimeUnit.MINUTES.toMillis(3)) { text(a, draft)?.contains("Second version.") == true }

        openFromTheBlog(titleDraft)
        fromTheMenu("Publish to the site")
        waitUntil("the draft published on ${a.fullName}", TimeUnit.MINUTES.toMillis(3)) { hasPost(a, slug) && text(a, draft) == null }
        assertTrue(text(a, postPath(a, slug)!!)!!.contains("Second version."))
    }

    /** Types [time] into the schedule dialog's fields: hour, minute and, on a 12-hour clock, AM or PM. */
    private fun setTime(time: LocalTime) {
        val fields = compose.onAllNodes(hasSetTextAction() and hasAnyAncestor(isDialog()))
        val twelveHour = compose.onAllNodes(hasText("AM")).fetchSemanticsNodes().isNotEmpty()
        val hour = if (twelveHour) (time.hour % 12).let { if (it == 0) 12 else it } else time.hour
        fields[0].performTextReplacement(hour.toString())
        fields[1].performTextReplacement("%02d".format(time.minute))
        if (twelveHour) compose.onNode(hasText(if (time.hour < 12) "AM" else "PM")).performClick()
    }

    /**
     * The post changes on GitHub while it's open on the phone, as from a laptop: Update refuses,
     * and the laptop's version stays.
     */
    @Test fun anEditOfAPostThatChangedOnGitHubMeanwhileIsRefused() {
        testName = "edit-conflict"
        val a = signInToA()
        val date = java.time.LocalDate.now(java.time.ZoneOffset.UTC)
        val path = "_posts/$date-device-check-$stamp-conflict.md"
        val github = GitHubClient(http, a.token)
        runBlocking { github.commit(a.owner, a.name, a.branch, "Add post: device check", listOf(FileChange.text(path, "---\ntitle: \"$titleConflict\"\n---\n\nFrom a laptop.\n")), expect = mapOf(path to null)) }

        openFromTheBlog(titleConflict)
        waitFor("From a laptop.")
        compose.onNodeWithTag("body").performTextInput("From the phone. ")
        val laptop = "---\ntitle: \"$titleConflict\"\n---\n\nFrom a laptop, changed again.\n"
        runBlocking {
            val sent = github.file(a.owner, a.name, a.branch, path)!!
            github.commit(a.owner, a.name, a.branch, "Update post: device check", listOf(FileChange.text(path, laptop)), expect = mapOf(path to sent.sha))
        }
        compose.onNodeWithText("Update").performClick()

        waitFor("changed on GitHub since you opened it", TimeUnit.MINUTES.toMillis(2))
        assertEquals(laptop, text(a, path))
    }

    // The first half of a check the workflow finishes: a post queued, the app's process ended,
    // and the workflow looks on GitHub for it before anything starts the app again.
    private val killRun = args.getString("killRun").orEmpty()
    private val titleAfterKill = "Device check run $killRun, after the app was killed"

    /** Queues a post for a minute from now, then ends, and the process with it. */
    @Test fun aPostWaitsWhileTheAppIsKilled() {
        assumeTrue("The workflow's first step only", args.getString("killStep") == "queue" && killRun.isNotBlank())
        testName = "killed-queue"
        val a = a!!
        runBlocking {
            val login = GitHubClient(http, a.token).user().login
            app.container.accounts.save(Account(login, a.token, a.owner, a.name, a.branch))
            val sendAfter = System.currentTimeMillis() + 60_000
            val id = app.container.drafts.insert(
                Draft(blog = "${a.fullName}@${a.branch}", title = titleAfterKill, body = "Queued, then the app was killed.", state = PostState.Queued, sendAfter = sendAfter),
            )
            app.container.publishQueue.enqueue(id, sendAfter)
        }
        assertFalse("Sent before its time", runBlocking { hasPost(a, "device-check-run-$killRun-after-the-app-was-killed") })
    }

    /**
     * The address the internet sees this phone at, through whatever network is up. Tried a few
     * times: just after Android says the tunnel is up, its first handshake may not be done.
     */
    private fun publicAddress(): String {
        repeat(3) {
            runCatching { http.newCall(Request.Builder().url("https://api.ipify.org").build()).execute().use { r -> r.body.string().trim().takeIf { r.isSuccessful } } }
                .getOrNull()?.let { return it }
            Thread.sleep(5_000)
        }
        error("Couldn't read this phone's public address")
    }

    /** Turns [tunnel] on or off in the WireGuard app's list, then waits for Android to agree. */
    private fun setTunnel(tunnel: String, up: Boolean) {
        shell("am start -W -n com.wireguard.android/.activity.MainActivity")
        check(device.wait(Until.hasObject(By.text(tunnel)), 20_000)) { "WireGuard doesn't list $tunnel" }
        val toggle = device.findObject(By.res("com.wireguard.android", "tunnel_switch"))
            ?: device.findObject(By.clazz("android.widget.Switch"))
            ?: error("No switch for $tunnel")
        if (toggle.isChecked != up) toggle.click()
        val end = System.currentTimeMillis() + 30_000
        while ((AndroidVpn(app).current() != null) != up) {
            check(System.currentTimeMillis() < end) { "The tunnel didn't come ${if (up) "up" else "down"}" }
            Thread.sleep(500)
        }
        device.pressHome()
    }
}
