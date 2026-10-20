package com.app.jekyllposter.device

import android.Manifest
import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
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
                github.files(blog.owner, blog.name, blog.branch).filter { "device-check-$stamp" in it.path }.forEach { entry ->
                    val sent = github.file(blog.owner, blog.name, blog.branch, entry.path) ?: return@forEach
                    github.commit(blog.owner, blog.name, blog.branch, "Delete post: device check", listOf(FileChange.delete(entry.path)), expect = mapOf(entry.path to sent.sha))
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

        // A second sign-in, with its own token, and a post for its blog sent in the background.
        runBlocking {
            val login = GitHubClient(http, b.token).user().login
            app.container.accounts.save(Account(login, b.token, b.owner, b.name, b.branch))
            val id = app.container.drafts.insert(
                Draft(blog = "${b.fullName}@${b.branch}", title = titleB, body = "The second blog's post, from the same run.", state = PostState.Queued),
            )
            app.container.publishQueue.enqueue(id, null)
        }
        waitUntil("the post on ${b.fullName}", TimeUnit.MINUTES.toMillis(3)) { hasPost(b, "device-check-$stamp-second-blog") }

        hearItIsLive(titleA)
        hearItIsLive(titleB)
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
        runBlocking {
            val github = GitHubClient(http, a.token)
            val user = github.user()
            app.container.accounts.save(Account(user.login, a.token, a.owner, a.name, a.branch))
            app.container.privacy.update(key) {
                it.copy(plainCommitMessages = true, sendAtRandomTime = true, datesByDayOnly = true, removeTrackingCodes = true)
            }
            app.container.privacy.setCommitAsNoReply(key, user.login, user.noReplyAuthor)
        }

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
                app.container.privacy.update(key) { it.copy(onlyThroughVpn = true) }
                app.container.drafts.insert(Draft(blog = key, title = titleVpn, body = "Sent through a VPN by the nightly device check, then deleted.", state = PostState.Queued))
                    .also { app.container.publishQueue.enqueue(it, null) }
            }
            ActivityScenario.launch(MainActivity::class.java)
            waitFor("Waiting for your VPN…")
            // Long enough for a worker that ignored the switch to have sent it.
            Thread.sleep(30_000)
            assertFalse("Sent without the VPN", runBlocking { hasPost(a, "device-check-$stamp-through-the-vpn") })
            assertEquals(PostState.Queued, runBlocking { app.container.drafts.get(id) }!!.state)

            setTunnel(tunnel, up = true)
            val vpnAddress = publicAddress()
            assertTrue("The tunnel didn't change the address ($vpnAddress)", vpnAddress != ownAddress)
            waitUntil("the post on ${a.fullName} through the VPN", TimeUnit.MINUTES.toMillis(3)) { hasPost(a, "device-check-$stamp-through-the-vpn") }
        } finally {
            setTunnel(tunnel, up = false)
        }
    }

    /** The address the internet sees this phone at, through whatever network is up. */
    private fun publicAddress(): String =
        http.newCall(Request.Builder().url("https://api.ipify.org").build()).execute().use { it.body.string().trim() }

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
