package com.app.jekyllposter.device

import android.os.ParcelFileDescriptor
import android.util.Base64
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.app.jekyllposter.MainActivity
import com.app.jekyllposter.data.Account
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What other apps hand the app on a real device: a photo and a note shared from `:sender`, a
 * second app with its own URI grants, and a photo from the emulator's camera app.
 */
@RunWith(AndroidJUnit4::class)
class FromOtherAppsTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val app = ApplicationProvider.getApplicationContext<DeviceTestApp>()
    private val github get() = app.github
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)

    @get:Rule val onFailure = ScreenOnFailure()

    @Before fun signIn() {
        runBlocking { app.container.accounts.save(Account("sample", github.token, "sample", "sample-blog", "main")) }
    }

    // No pressHome here: @After runs before the failure rule, whose picture should show the failure.
    @After fun close() = github.close()

    private fun waitFor(text: String, timeoutMs: Long = 20_000) =
        compose.waitUntil(timeoutMs) { compose.onAllNodes(hasText(text, substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }

    private fun waitForTag(tag: String) = compose.waitUntil(20_000) { compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }

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

    /**
     * Has `:sender` share [what] into the app, through the system, as a share sheet would. A note's
     * [text] goes as Base64: the shell command is split at spaces, with no quoting.
     */
    private fun share(what: String, text: String? = null) {
        val more = text?.let { "--es text64 " + Base64.encodeToString(it.toByteArray(), Base64.NO_WRAP) }.orEmpty()
        val started = shell("am start -W -n com.app.jekyllposter.sender/.ShareActivity --es to ${app.packageName} --es what $what $more")
        assertTrue(started, started.contains("Status: ok"))
    }

    private fun postFile(slug: String) = github.files().keys.firstOrNull { it.endsWith("-$slug.md") }

    private fun publish(title: String?) {
        title?.let { compose.onNodeWithTag("title").performTextInput(it) }
        compose.onNodeWithText("Publish").performClick()
    }

    /** The one photo the post put on GitHub, checked for anything that came from the camera. */
    private fun assertPhotoHasNoLocation(post: String) {
        val path = Regex("""\{\{ '(/assets/images/[^']+)' \| relative_url \}\}""").find(github.text(post)!!)!!.groupValues[1]
        val exif = ExifInterface(github.files().getValue(path.removePrefix("/")).inputStream())
        assertNull("The photo on GitHub has a location", exif.latLong)
        assertNull("The photo on GitHub says what made it", exif.getAttribute(ExifInterface.TAG_MAKE))
    }

    @Test fun aPhotoSharedFromAnotherAppGoesOutWithoutItsLocation() {
        share("photo")
        waitFor("Describe the photo")
        compose.onNodeWithTag("altText").performTextInput("A teal square")
        compose.onNodeWithText("Done").performClick()
        waitForTag("title")
        publish("From the gallery")

        waitUntil("the post on GitHub") { postFile("from-the-gallery") != null }
        val post = postFile("from-the-gallery")!!
        assertTrue(github.text(post)!!.contains("![A teal square]("))
        assertPhotoHasNoLocation(post)
    }

    @Test fun aNoteSharedFromObsidianArrivesReadyForTheBlog() {
        // As a writer who has opened their blog before: `[[links]]` are found among its posts.
        ActivityScenario.launch(MainActivity::class.java)
        waitFor("Welcome to the notebook")
        share("note", "---\ntags: [lunch]\n---\nSoup, then [[What I read in April]]. %%ask about the bread%%\n")
        waitForTag("title")
        waitFor("post_url 2025-04-20-reading-list")
        // The title is the note's file name, as Obsidian shows it.
        compose.onNodeWithText("Lunch at the market").assertExists()
        publish(null)

        waitUntil("the note's post on GitHub") { postFile("lunch-at-the-market") != null }
        val text = github.text(postFile("lunch-at-the-market")!!)!!
        assertTrue(text, text.contains("Soup, then [What I read in April]({{ site.baseurl }}{% post_url 2025-04-20-reading-list %})."))
        assertTrue(text, text.contains("lunch"))
        assertFalse("The Obsidian comment went out", text.contains("bread") || text.contains("%%"))
    }

    @Test fun twoPhotosSharedAtOnceBothGoOutWithoutTheirLocation() {
        share("photos")
        // One description at a time, a dialog for each photo.
        listOf("A teal square", "A pink square").forEach { alt ->
            waitFor("Describe the photo")
            compose.onNodeWithTag("altText").performTextInput(alt)
            compose.onNodeWithText("Done").performClick()
            compose.waitUntil(20_000) { compose.onAllNodes(hasText(alt, substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        }
        waitForTag("title")
        publish("Two from the gallery")

        waitUntil("the post on GitHub") { postFile("two-from-the-gallery") != null }
        val post = postFile("two-from-the-gallery")!!
        val text = github.text(post)!!
        assertTrue(text, text.contains("![A teal square](") && text.contains("![A pink square]("))
        Regex("""\{\{ '(/assets/images/[^']+)' \| relative_url \}\}""").findAll(text).map { it.groupValues[1] }.toList().also { assertEquals(2, it.distinct().size) }.forEach { path ->
            val exif = ExifInterface(github.files().getValue(path.removePrefix("/")).inputStream())
            assertNull("A photo on GitHub has a location", exif.latLong)
        }
    }

    @Test fun aNoteSharedAsAPlainTextFileIsReadAsANote() {
        share("notetext", "---\ntags: [lunch]\n---\nSoup. %%ask about the bread%%\n")
        waitForTag("title")
        waitFor("Soup.")
        compose.onNodeWithText("Lunch at the market").assertExists()
        publish(null)

        waitUntil("the note's post on GitHub") { postFile("lunch-at-the-market") != null }
        val text = github.text(postFile("lunch-at-the-market")!!)!!
        assertTrue(text, text.contains("lunch"))
        assertFalse("The Obsidian comment went out", text.contains("bread") || text.contains("%%"))
    }

    @Test fun aNoteOpenedWithTheAppStartsAPostFromIt() {
        share("open", "Opened from a file manager.\n")
        waitForTag("title")
        waitFor("Opened from a file manager.")
        compose.onNodeWithText("Lunch at the market").assertExists()
        publish(null)
        waitUntil("the opened note's post on GitHub") { postFile("lunch-at-the-market") != null }
        assertTrue(github.text(postFile("lunch-at-the-market")!!)!!.contains("Opened from a file manager."))
    }

    // The camera has no location to give on CI's emulator: the shared photo above covers that.
    @Test fun aPhotoFromTheCameraAppIsAddedAndItsOriginalDeleted() {
        // Since Android 11 only a camera app that came with the phone answers the app's request.
        val camera = shell("cmd package query-activities --brief -a android.media.action.IMAGE_CAPTURE")
            .lines().map { it.trim() }.firstOrNull { "/" in it }?.substringBefore("/")
        check(camera != null) { "No camera app on this emulator" }
        listOf("CAMERA", "RECORD_AUDIO").forEach { shell("pm grant $camera android.permission.$it") }

        share("note", "Taken just now.\n")
        waitForTag("title")
        compose.onNodeWithContentDescription("Add a photo").performClick()
        compose.onNodeWithText("Take a photo").performClick()

        // The camera app's own screens: first-run prompts, then the shutter, then its confirm.
        tapInCamera(camera, "shutter_button")
        tapInCamera(camera, "done_button")

        waitFor("Describe the photo", 30_000)
        compose.onNodeWithText("Skip").performClick()
        publish(null)
        waitUntil("the camera's post on GitHub") { postFile("lunch-at-the-market")?.let { github.text(it)!!.contains("/assets/images/") } == true }
        assertEquals("The camera's original is still in the cache", emptyList<String>(), File(app.cacheDir, "camera").list().orEmpty().toList())
    }

    /** Taps the camera app's [id], getting past any first-run prompt in its way. */
    private fun tapInCamera(camera: String, id: String) {
        val target: BySelector = By.res(camera, id)
        val prompts = listOf("NEXT", "Next", "OK", "Got it", "GOT IT", "Allow", "While using the app", "Only this time", "No thanks", "Don't allow")
        val end = System.currentTimeMillis() + 30_000
        while (System.currentTimeMillis() < end) {
            device.findObject(target)?.takeIf { it.isEnabled }?.let { it.click(); return }
            prompts.firstNotNullOfOrNull { device.findObject(By.text(it)) }?.click()
            device.wait(Until.hasObject(target), 1_000)
        }
        throw AssertionError("The camera app's $id never showed")
    }
}
