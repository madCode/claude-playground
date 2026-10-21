package com.app.jekyllposter.device

import android.Manifest
import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.app.jekyllposter.MainActivity
import com.app.jekyllposter.data.Account
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The app as installed, on an emulator: its own WorkManager, Keystore and notifications, and
 * intents from outside its process. Only GitHub is fake, on the device itself.
 */
@RunWith(AndroidJUnit4::class)
class OnTheDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    @get:Rule val onFailure = ScreenOnFailure()

    private val app = ApplicationProvider.getApplicationContext<DeviceTestApp>()
    private val github get() = app.github
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)

    @Before fun allowNotifications() {
        instrumentation.uiAutomation.grantRuntimePermission(app.packageName, Manifest.permission.POST_NOTIFICATIONS)
    }

    // No pressHome here: @After runs before the failure rule, whose picture should show the failure.
    @After fun close() = github.close()

    private fun waitFor(text: String, timeoutMs: Long = 20_000, substring: Boolean = true) =
        compose.waitUntil(timeoutMs) { compose.onAllNodes(hasText(text, substring = substring), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }

    private fun waitForTag(tag: String) =
        compose.waitUntil(20_000) { compose.onAllNodes(androidx.compose.ui.test.hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }

    /** Waits for [condition] off the screen: on the fake GitHub or the database. */
    private fun waitUntil(what: String, timeoutMs: Long = 60_000, condition: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            check(System.currentTimeMillis() < end) { "Timed out waiting for $what" }
            Thread.sleep(200)
        }
    }

    /** Runs [command] as the shell does, from outside the app's process. */
    private fun shell(command: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use { it.readBytes().decodeToString() }

    private fun postFile(slug: String) = github.files().keys.firstOrNull { it.endsWith("-$slug.md") }

    @Test fun signInWritePublishAndHearItIsLive() {
        ActivityScenario.launch(MainActivity::class.java)
        waitFor("Connect with a token")
        compose.onNodeWithText("Token").performTextInput(github.token)
        compose.onNodeWithText("Continue").performClick()
        waitFor("sample/sample-blog")
        compose.onNodeWithText("sample/sample-blog").performClick()
        waitFor("Welcome to the notebook")

        compose.onNodeWithText("New post", useUnmergedTree = true).performClick()
        waitForTag("title")
        compose.onNodeWithTag("title").performTextInput("On the device")
        compose.onNodeWithTag("body").performTextInput("Written on an emulator.")
        compose.onNodeWithText("Publish").performClick()

        // WorkManager's own worker sends it, once the network is there.
        waitUntil("the post on GitHub") { postFile("on-the-device") != null }
        assertTrue(github.text(postFile("on-the-device")!!)!!.endsWith("Written on an emulator.\n"))
        synchronized(github) { github.runs[github.head] = "completed" to "success" }

        // The build watch starts 20 s after the commit; the notification is what the writer sees.
        device.openNotification()
        assertTrue("No “is live” notification", device.wait(Until.hasObject(By.textContains("“On the device” is live")), 90_000))
    }

    @Test fun aLinkSharedFromAnotherAppStartsAPost() {
        runBlocking { app.container.accounts.save(Account("sample", github.token, "sample", "sample-blog", "main")) }
        // Through the system, as another app's share sheet sends it: an implicit intent, not a call.
        val started = shell(
            "am start -W -a android.intent.action.SEND -t text/plain -p ${app.packageName} " +
                "--es android.intent.extra.TEXT https://example.com/read --es android.intent.extra.SUBJECT Good",
        )
        assertTrue(started, started.contains("Status: ok"))
        waitForTag("title")
        waitFor("[Good](https://example.com/read)")
        compose.onNodeWithTag("title").performTextInput("A good read")
        compose.onNodeWithText("Publish").performClick()

        waitUntil("the shared post on GitHub") { postFile("a-good-read") != null }
        assertTrue(github.text(postFile("a-good-read")!!)!!.contains("[Good](https://example.com/read)"))
        assertEquals(1, runBlocking { app.container.database.drafts().snapshotCount() })
    }

    @Test fun aPostWithFrontMatterTheAppCantFollowIsLeftAlone() {
        // Pushed from a laptop: a `?` key, which an edit from the phone would have rewritten.
        val path = "_posts/2025-05-05-odd-keys.md"
        val odd = "---\ntitle: Odd keys\n? complex key\n: kept\n---\n\nText.\n"
        github.push("Laptop", mapOf(path to odd))
        val head = github.head
        runBlocking { app.container.accounts.save(Account("sample", github.token, "sample", "sample-blog", "main")) }
        ActivityScenario.launch(MainActivity::class.java)
        waitFor("Odd keys")
        compose.onNodeWithText("Odd keys").performClick()

        waitFor("The app can't change this post without changing front matter")
        assertTrue("The editor opened", compose.onAllNodes(androidx.compose.ui.test.hasTestTag("title")).fetchSemanticsNodes().isEmpty())
        assertEquals(head, github.head)
        assertEquals(odd, github.text(path))
        assertEquals("A draft was made of it", 0, runBlocking { app.container.database.drafts().snapshotCount() })
    }

    @Test fun signingInWithGitHubTwiceKeepsOneSignIn() {
        github.addRepository("garden", mapOf("_config.yml" to "title: The Garden\n".toByteArray()))
        // The pretend GitHub answers as if the code were entered: the browser's part is a person's.
        // Each sign-in gets a new token, as on GitHub, so only the app's own rule can merge them.
        github.deviceApproved = true
        github.newTokenPerSignIn = true
        ActivityScenario.launch(MainActivity::class.java)
        signInWithGitHub("Post to your Jekyll blog", "sample/sample-blog")
        waitFor("New post")
        openSettings()
        compose.onNodeWithText("+ Add a sign-in").performScrollTo().performClick()
        signInWithGitHub("Add a sign-in", "sample/garden")
        // A blog beside others opens its Blog & privacy first.
        waitFor("Blog & privacy", substring = false)
        // Its title comes from its own repository, which the new token has to reach.
        waitFor("The Garden · sample/garden")
        device.pressBack()
        waitFor("New post")

        openSettings()
        waitFor("2 blogs, 1 sign-in")
    }

    /** Signs in from the Connect screen headed [heading], exactly: Settings, being left, mentions it too. */
    private fun signInWithGitHub(heading: String, repo: String) {
        waitFor(heading, substring = false)
        compose.onNodeWithText("Sign in with GitHub").performClick()
        waitFor(repo, substring = false)
        compose.onNodeWithText(repo).performClick()
    }

    private fun openSettings() {
        compose.onNodeWithContentDescription("More").performClick()
        compose.onNodeWithText("Settings").performClick()
        waitFor("Your blogs")
    }
}

