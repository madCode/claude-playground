package com.app.jekyllposter.device

import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.services.storage.TestStorage
import androidx.test.uiautomator.UiDevice
import com.app.jekyllposter.MainActivity
import com.app.jekyllposter.data.Account
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The screens as a phone changes them: turned on its side, and with the largest text and the dark
 * theme. Each screen is also put through Android's accessibility checks (labels, touch targets,
 * contrast), and kept as a picture for a person to look at.
 */
@RunWith(AndroidJUnit4::class)
class ScreensOnTheDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val app = ApplicationProvider.getApplicationContext<DeviceTestApp>()
    private val github get() = app.github
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)

    @get:Rule val onFailure = ScreenOnFailure()

    @Before fun signIn() {
        // As well as after: an @After a crash skipped would leave the next tests large and dark.
        resetDevice()
        runBlocking { app.container.accounts.save(Account("sample", github.token, "sample", "sample-blog", "main")) }
    }

    @After fun close() {
        resetDevice()
        github.close()
    }

    private fun resetDevice() {
        device.setOrientationNatural()
        shell("settings put system font_scale 1.0")
        shell("cmd uimode night no")
    }

    private fun shell(command: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use { it.readBytes().decodeToString() }

    private fun waitFor(text: String, timeoutMs: Long = 20_000) =
        compose.waitUntil(timeoutMs) { compose.onAllNodes(hasText(text, substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }

    private fun waitForTag(tag: String) = compose.waitUntil(20_000) { compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }

    private fun keep(name: String) {
        compose.waitForIdle()
        val shot = File(app.cacheDir, "$name.png")
        check(device.takeScreenshot(shot)) { "No screenshot of $name" }
        TestStorage().openOutputFile("screens/$name.png").use { out -> shot.inputStream().use { it.copyTo(out) } }
    }

    private fun openEditor(): ActivityScenario<MainActivity> {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        waitFor("Welcome to the notebook")
        compose.onNodeWithText("New post", useUnmergedTree = true).performClick()
        waitForTag("title")
        return scenario
    }

    private fun ActivityScenario<MainActivity>.activity(): MainActivity {
        var current: MainActivity? = null
        onActivity { current = it }
        return current!!
    }

    @Test fun turningThePhoneKeepsWhatWasTypedAndOpen() {
        val scenario = openEditor()
        compose.onNodeWithTag("title").performTextInput("Turning around")
        compose.onNodeWithTag("body").performTextInput("Typed before turning.")
        compose.onNodeWithContentDescription("Add tag").performClick()
        waitForTag("termQuery")
        compose.onNodeWithTag("termQuery").performTextInput("sideways")

        val before = scenario.activity()
        device.setOrientationLeft()
        // Only the screen made again proves anything was kept.
        compose.waitUntil(20_000) { scenario.activity() !== before }
        waitForTag("termQuery")
        compose.onNodeWithTag("termQuery").assertTextContains("sideways")
        keep("editor-sideways")
        device.setOrientationNatural()
        waitForTag("termQuery")

        waitFor("Turning around")
        waitFor("Typed before turning.")
        assertEquals("One post, however many times the editor was made again", 1, runBlocking { app.container.database.drafts().snapshotCount() })
    }

    @Test fun theLargestTextInTheDarkPassesTheAccessibilityChecks() {
        shell("settings put system font_scale 2.0")
        shell("cmd uimode night yes")
        compose.enableAccessibilityChecks()

        ActivityScenario.launch(MainActivity::class.java)
        waitFor("Welcome to the notebook")
        compose.onRoot().tryPerformAccessibilityChecks()
        keep("home-large-dark")

        compose.onNodeWithText("New post", useUnmergedTree = true).performClick()
        waitForTag("title")
        compose.onNodeWithTag("title").performTextInput("Big and dark")
        compose.onRoot().tryPerformAccessibilityChecks()
        keep("editor-large-dark")

        // The editor's own Back: the system's would only close the keyboard.
        compose.onNodeWithContentDescription("Back").performClick()
        // The new draft heads the list; at this size it pushes the blog's posts out of view.
        waitFor("Big and dark")
        compose.onNodeWithContentDescription("More").performClick()
        compose.onNodeWithText("Settings").performClick()
        waitFor("Blog & privacy")
        compose.onNodeWithText("Blog & privacy").performClick()
        waitFor("Only connect through a VPN")
        compose.onRoot().tryPerformAccessibilityChecks()
        keep("blog-privacy-large-dark")
    }
}
