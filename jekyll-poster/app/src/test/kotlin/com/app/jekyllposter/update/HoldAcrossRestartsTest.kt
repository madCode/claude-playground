package com.app.jekyllposter.update

import android.content.Intent
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.app.jekyllposter.MainActivity
import com.app.jekyllposter.testutil.TestApp
import android.os.Looper
import org.robolectric.Shadows.shadowOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The editor's hold on updates, through the real activity: rotated, and started again. */
@RunWith(AndroidJUnit4::class)
@Config(application = TestApp::class, qualifiers = "w411dp-h891dp")
class HoldAcrossRestartsTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val app = ApplicationProvider.getApplicationContext<TestApp>()
    private val holds get() = app.container.updates.holds
    private val watching = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @After fun close() {
        watching.cancel()
        app.github.close()
    }

    private fun launch(intent: Intent): ActivityScenario<MainActivity> {
        runBlocking { app.signIn() }
        return ActivityScenario.launch(intent.setClass(app, MainActivity::class.java))
    }

    /** Compose's frames as well as the main looper: the editor opens over several of each. */
    private fun waitFor(condition: () -> Boolean) = compose.waitUntil(15_000) {
        shadowOf(Looper.getMainLooper()).idle()
        condition()
    }

    private fun launcher() = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

    @Test fun aRotationKeepsThePostsHoldWithoutAGap() {
        val scenario = launch(launcher())
        waitFor { compose.onAllNodesWithText("New post", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("New post", useUnmergedTree = true).performClick()
        waitFor { holds.held }
        val seen = mutableListOf<Int>()
        watching.launch { holds.open.collect { seen += it.size } }

        scenario.recreate()
        // The editor is back.
        waitFor { compose.onAllNodesWithTag("title").fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()

        assertEquals("never released, never held twice", listOf(1), seen.distinct())
        assertTrue(holds.heldOrLeftOpen)
        scenario.close()
    }

    @Test fun onlyAStartFromTheLauncherClearsAPostLeftOpen() {
        // An editor left open by a process that died: its task waits in recents.
        UpdateHolds(DebugUpdates.prefsOf(app)).hold("editor-of-a-dead-process")

        // A share, or any start but the launcher's, may be in another app's task.
        launch(Intent(Intent.ACTION_VIEW)).close()
        assertTrue(holds.heldOrLeftOpen)

        launch(launcher()).close()
        assertFalse(holds.heldOrLeftOpen)
    }
}
