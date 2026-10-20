package com.app.jekyllposter.device

import android.Manifest
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
import com.app.jekyllposter.data.Draft
import com.app.jekyllposter.data.PostState
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The app as installed, against the real GitHub and the tester's blogs (docs/TESTER_ACCOUNT.md),
 * with the request record on. Only the "Jekyll Poster device live check" workflow runs it, with
 * `liveRepoA`/`liveTokenA` and `liveRepoB`/`liveTokenB` as instrumentation arguments: two blogs
 * under two sign-ins, for the several-blogs audit. Elsewhere it's skipped.
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
        // Kept for the audit; the orchestrator clears the app's own files after the test.
        File(record, "requests.jsonl").takeIf { it.exists() }?.let { file ->
            TestStorage().openOutputFile("requests.jsonl").use { out -> file.inputStream().use { it.copyTo(out) } }
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

    @Test fun twoBlogsUnderTwoSignInsPublishAndGoLive() {
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
}
