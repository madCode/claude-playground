package com.app.jekyllposter.device

import android.Manifest
import android.os.ParcelFileDescriptor
import android.util.Base64
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.app.jekyllposter.MainActivity
import com.app.jekyllposter.data.Account
import com.app.jekyllposter.data.Draft
import com.app.jekyllposter.data.PostState
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The writer's smaller journeys, walked through the screens on the emulator against the fake
 * GitHub: what's thrown away on the phone, a note's own date, the notification prompt and a tap
 * on a notification, removing a sign-in with posts still to go, and finding posts on Home.
 */
@RunWith(AndroidJUnit4::class)
class JourneysOnTheDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    @get:Rule val onFailure = ScreenOnFailure()

    private val app = ApplicationProvider.getApplicationContext<DeviceTestApp>()
    private val github get() = app.github
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)

    @After fun close() = github.close()

    private fun waitFor(text: String, timeoutMs: Long = 20_000) =
        compose.waitUntil(timeoutMs) { compose.onAllNodes(hasText(text, substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }

    private fun waitForTag(tag: String) = compose.waitUntil(20_000) { compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }

    private fun gone(text: String) = compose.waitUntil(20_000) { compose.onAllNodes(hasText(text), useUnmergedTree = true).fetchSemanticsNodes().isEmpty() }

    /** Waits for [condition] off the screen: on the fake GitHub or the database. */
    private fun waitUntil(what: String, timeoutMs: Long = 60_000, condition: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            check(System.currentTimeMillis() < end) { "Timed out waiting for $what" }
            Thread.sleep(200)
        }
    }

    private fun shell(command: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use { it.readBytes().decodeToString() }

    private fun signIn() = runBlocking { app.container.accounts.save(Account("sample", github.token, "sample", "sample-blog", "main")) }

    private fun allowNotifications() = instrumentation.uiAutomation.grantRuntimePermission(app.packageName, Manifest.permission.POST_NOTIFICATIONS)

    private fun drafts() = runBlocking { app.container.drafts.list() }

    /** A node whose click action is labelled [label], as TalkBack announces it. */
    private fun clickLabel(label: String) = SemanticsMatcher("click label $label") {
        it.config.getOrNull(SemanticsActions.OnClick)?.label == label
    }

    private fun fromTheMenu(item: String) {
        compose.onNodeWithContentDescription("More").performClick()
        compose.onNodeWithText(item).performClick()
    }

    private fun confirm(button: String) = compose.onNode(hasText(button) and hasAnyAncestor(isDialog())).performClick()

    /**
     * Delete draft, Discard changes and Remove from this list, from the editor's menu: each clears
     * the phone's copy and leaves the blog as it was.
     */
    @Test fun aDraftIsDeletedAnEditDiscardedAndAPublishedPostRemovedFromTheList() {
        allowNotifications()
        signIn()
        ActivityScenario.launch(MainActivity::class.java)
        waitFor("Welcome to the notebook")
        val head = github.head

        compose.onNodeWithText("New post", useUnmergedTree = true).performClick()
        waitForTag("title")
        compose.onNodeWithTag("title").performTextInput("Scratch")
        fromTheMenu("Delete draft")
        confirm("Delete")
        waitFor("Welcome to the notebook")
        gone("Scratch")
        assertTrue(drafts().none { it.title == "Scratch" })

        compose.onNodeWithText("What I read in April").performClick()
        waitFor("A few books, a few essays.")
        compose.onNodeWithTag("body").performTextInput("Not this after all. ")
        fromTheMenu("Discard changes")
        confirm("Discard")
        waitFor("Welcome to the notebook")
        assertTrue(drafts().none { it.editingPath != null })
        assertEquals("The blog changed", head, github.head)

        compose.onNodeWithText("New post", useUnmergedTree = true).performClick()
        waitForTag("title")
        compose.onNodeWithTag("title").performTextInput("Short and done")
        compose.onNodeWithText("Publish").performClick()
        waitUntil("the post on GitHub") { github.files().keys.any { it.endsWith("-short-and-done.md") } }
        waitUntil("the post marked published") { drafts().any { it.title == "Short and done" && it.state == PostState.Published } }
        // The phone's row, listed above the blog's copy of the same post.
        compose.waitUntil(20_000) { compose.onAllNodes(hasText("Short and done"), useUnmergedTree = true).fetchSemanticsNodes().size == 2 }
        compose.onAllNodes(hasText("Short and done"), useUnmergedTree = true)[0].performClick()
        waitFor("Short and done")
        fromTheMenu("Remove from this list")
        confirm("Remove")
        waitUntil("the phone's row gone") { drafts().none { it.title == "Short and done" } }
        assertTrue("The post left the blog", github.files().keys.any { it.endsWith("-short-and-done.md") })
    }

    /** A note's own date, dropped with Use the publish day: the post is dated when it goes out. */
    @Test fun aSharedNotesDateIsDroppedForThePublishDay() {
        allowNotifications()
        signIn()
        val note = Base64.encodeToString("---\ndate: 2024-03-09\n---\nWritten long ago.\n".toByteArray(), Base64.NO_WRAP)
        val started = shell("am start -W -n com.app.jekyllposter.sender/.ShareActivity --es to ${app.packageName} --es what note --es text64 $note")
        assertTrue(started, started.contains("Status: ok"))
        waitForTag("title")
        waitFor("from the note")
        compose.onNodeWithText("Use the publish day").performClick()
        gone("Use the publish day")
        compose.onNodeWithText("Publish").performClick()

        waitUntil("the note's post on GitHub") { github.files().keys.any { it.endsWith("-lunch-at-the-market.md") } }
        val path = github.files().keys.first { it.endsWith("-lunch-at-the-market.md") }
        assertFalse(path, path.contains("2024-03-09"))
        assertTrue(path, path.startsWith("_posts/${LocalDate.now(ZoneOffset.UTC).year}-"))
        assertFalse(github.text(path)!!.contains("2024-03-09"))
    }

    /**
     * The first Publish asks to send notifications, and publishing goes ahead; a failed build's
     * notification, tapped, opens the app on the post.
     */
    @Test fun theFirstPublishAsksForNotificationsAndAFailedBuildsNotificationOpensTheApp() {
        shell("pm revoke ${app.packageName} ${Manifest.permission.POST_NOTIFICATIONS}")
        signIn()
        ActivityScenario.launch(MainActivity::class.java)
        waitFor("Welcome to the notebook")
        compose.onNodeWithText("New post", useUnmergedTree = true).performClick()
        waitForTag("title")
        compose.onNodeWithTag("title").performTextInput("Asked first")
        compose.onNodeWithText("Publish").performClick()

        // Android's own prompt, drawn by the system.
        val allow = device.wait(Until.findObject(By.text("Allow")), 20_000)
        assertTrue("Not asked for notifications", allow != null)
        allow.click()
        waitUntil("the post on GitHub") { github.files().keys.any { it.endsWith("-asked-first.md") } }

        device.pressHome()
        synchronized(github) { github.runs[github.head] = "completed" to "failure" }
        device.openNotification()
        val note = device.wait(Until.findObject(By.textContains("didn't rebuild")), 90_000)
        assertTrue("No notification for the failed build", note != null)
        note.click()
        waitFor("the site build failed", 30_000)
    }

    /** Removing a sign-in with a post still to go: refused, Send them now sends it, then it can go. */
    @Test fun removingASignInWithAPostWaitingSendsItThenRemoves() {
        allowNotifications()
        signIn()
        val later = System.currentTimeMillis() + 3_600_000
        runBlocking {
            app.container.drafts.insert(Draft(blog = "sample/sample-blog@main", title = "Still to go", body = "x", state = PostState.Queued, sendAfter = later))
        }
        ActivityScenario.launch(MainActivity::class.java)
        waitFor("Welcome to the notebook")
        fromTheMenu("Settings")
        waitFor("Your blogs")
        compose.onNodeWithText("Your blogs").performClick()
        compose.onNodeWithContentDescription("Remove @sample").performClick()
        confirm("Remove")
        waitFor("Posts are still to go")
        waitFor("“Still to go”")
        compose.onNodeWithText("Send them now").performClick()
        waitUntil("the waiting post on GitHub") { github.files().keys.any { it.endsWith("-still-to-go.md") } }

        compose.onNodeWithContentDescription("Remove @sample").performClick()
        confirm("Remove")
        // The last sign-in gone: back to signing in.
        waitFor("Connect with a token", 30_000)
    }

    /** Home's category pills, search, and Read the blog again picking up a post pushed meanwhile. */
    @Test fun homeFindsPostsByCategoryAndSearchAndReadsTheBlogAgain() {
        signIn()
        ActivityScenario.launch(MainActivity::class.java)
        waitFor("Welcome to the notebook")

        // The pill, first; the posts below carry their categories too.
        compose.onAllNodes(hasText("travel"), useUnmergedTree = true)[0].performClick()
        waitFor("A coastal walk")
        gone("Welcome to the notebook")
        compose.onAllNodes(hasText("travel"), useUnmergedTree = true)[0].performClick()
        waitFor("Welcome to the notebook")

        compose.onNodeWithContentDescription("Search your posts").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("sourdough")
        waitFor("Sourdough, again")
        gone("A coastal walk")
        compose.onNodeWithContentDescription("Stop searching").performClick()
        waitFor("A coastal walk")

        github.push("Laptop", mapOf("_posts/2025-08-01-from-the-laptop.md" to "---\ntitle: From the laptop\n---\n\nPushed meanwhile.\n"))
        compose.onNodeWithContentDescription("Read the blog again").performClick()
        waitFor("From the laptop")
    }
}
