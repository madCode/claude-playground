package com.app.bartwidget

import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.app.bartwidget.testutil.TestApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The rider's journeys through the real app, against a fake BART serving recorded boards. */
@RunWith(AndroidJUnit4::class)
@Config(application = TestApp::class, qualifiers = "w411dp-h891dp")
class FlowTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val app = ApplicationProvider.getApplicationContext<TestApp>()
    private val store = app.container.store

    @After fun close() = app.bart.close()

    private fun launch(station: String? = null): ActivityScenario<MainActivity> =
        ActivityScenario.launch(widgetTap(station))

    private fun widgetTap(station: String?) = Intent(app, MainActivity::class.java).apply {
        // What Glance's actionStartActivity puts in the intent for actionParametersOf(StationParam to …).
        station?.let { putExtra(StationParam.name, it) }
    }

    private fun star(vararg abbrs: String) = runBlocking { abbrs.forEach { store.toggle(it) } }

    private fun scrollTo(tag: String) = compose.onNode(hasScrollAction()).performScrollToNode(hasTestTag(tag))

    @Test
    fun starStationsFromTheListAndSeeTheirDepartures() {
        launch()
        compose.waitFor("All stations")
        scrollTo("star-MONT")
        compose.onNodeWithTag("star-MONT").performClick()
        scrollTo("star-DUBL")
        compose.onNodeWithTag("star-DUBL").performClick()
        compose.waitUntil(5_000) { runBlocking { store.snapshot.first().boards.size == 2 } }
        assertEquals(listOf("MONT", "DUBL"), runBlocking { store.starred.first() })

        scrollTo("board-MONT")
        compose.waitFor("7:22\u00A0(2\u00A0min)")
        // Montgomery's boards: soonest destination first, clock time with minutes to go.
        compose.onNodeWithText("SF Airport").assertExists()
        compose.onNodeWithText("7:20\u00A0(now)   7:27\u00A0(7\u00A0min)   7:38\u00A0(18\u00A0min)").assertExists()
        scrollTo("board-DUBL")
        compose.onNodeWithText("7:20\u00A0(now)   7:39\u00A0(19\u00A0min)   7:59\u00A0(39\u00A0min)").assertExists()
    }

    @Test
    fun aStationTappedOnTheWidgetIsTheOneShownNotAnotherStarredOne() {
        star("MONT", "DUBL")
        launch(station = "DUBL")
        compose.waitFor("Dublin/Pleasanton")
        compose.waitFor("7:39\u00A0(19\u00A0min)")
        // Montgomery is starred too, and first, but only Dublin's board is on screen.
        compose.onNodeWithTag("board-DUBL").assertExists()
        compose.onNodeWithTag("board-MONT").assertDoesNotExist()
        compose.onNodeWithText("SF Airport").assertDoesNotExist()

        compose.onNodeWithContentDescription("All stations").performClick()
        compose.waitForTag("board-MONT")
        scrollTo("board-DUBL")
    }

    @Test
    fun aSecondWidgetTapWhileTheAppIsOpenSwitchesStation() {
        star("MONT", "12TH")
        val controller = Robolectric.buildActivity(MainActivity::class.java, widgetTap("MONT")).setup()
        compose.waitFor("Montgomery St.")
        controller.newIntent(widgetTap("12TH"))
        compose.waitFor("12th St./Oakland City Center")
        compose.onNodeWithTag("board-12TH").assertExists()
        compose.onNodeWithTag("board-MONT").assertDoesNotExist()
        controller.pause().stop().destroy()
    }

    @Test
    fun backFromAStationGoesToTheList() {
        star("MONT")
        val scenario = launch(station = "MONT")
        compose.waitFor("SF Airport")
        scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitForTag("board-MONT")
        compose.onNodeWithContentDescription("All stations").assertDoesNotExist()
    }

    @Test
    fun aStationNotYetStarredCanBeStarredFromItsPage() {
        launch(station = "12TH")
        compose.waitFor("Star this station to keep it on the widget.")
        compose.onNodeWithTag("star-12TH").performClick()
        compose.waitUntil(5_000) { runBlocking { store.starred.first() } == listOf("12TH") }
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasText("Star this station", substring = true)).fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    fun aFailedRefreshSaysHowOldTheTimesAre() {
        star("MONT")
        launch()
        compose.waitFor("Updated 7:20")
        app.now += 5 * 60_000
        app.bart.failing += "MONT"
        compose.onNodeWithContentDescription("Refresh").performClick()
        compose.waitFor("Couldn't refresh. Times are from 7:20.")
        // Trains that left while we couldn't refresh are gone; the rest count down from now.
        compose.onNodeWithText("7:20\u00A0(now)   7:27\u00A0(7\u00A0min)   7:38\u00A0(18\u00A0min)").assertDoesNotExist()
        compose.waitFor("7:27\u00A0(2\u00A0min)")
    }

    @Test
    fun aStationThatHasNeverLoadedSaysItCouldNotRefresh() {
        star("DUBL")
        app.bart.failing += "DUBL"
        launch()
        compose.waitFor("Couldn't refresh")
    }

    @Test
    fun minutesCountDownWhileTheAppIsOpen() {
        star("DUBL")
        launch(station = "DUBL")
        compose.waitFor("7:39\u00A0(19\u00A0min)")
        app.now += 10 * 60_000
        compose.mainClock.advanceTimeBy(16_000)
        compose.waitFor("7:39\u00A0(9\u00A0min)")
        compose.onNodeWithText("7:20\u00A0(", substring = true).assertDoesNotExist()
    }

    @Test
    fun noTrainsIsSaidPlainly() {
        star("WARM")
        launch()
        compose.waitFor("No trains right now")
    }

    @Test
    fun parkingOpensTheOfficialBartApp() {
        val launcher = ComponentName("com.app.bart", "com.app.bart.MainActivity")
        shadowOf(app.packageManager).apply {
            addActivityIfNotPresent(launcher)
            addIntentFilterForActivity(launcher, IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) })
        }
        tapParking()
        assertEquals("com.app.bart", shadowOf(app).nextStartedActivity.component?.packageName)
    }

    @Test
    fun withoutTheBartAppParkingOpensBartsParkingPage() {
        tapParking()
        val intent = shadowOf(app).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("https://www.bart.gov/guide/parking", intent.dataString)
    }

    private fun tapParking() {
        launch()
        compose.waitFor("All stations")
        compose.onNodeWithText("Parking in the BART app").performClick()
    }
}

fun ComposeTestRule.waitFor(text: String, timeoutMs: Long = 5_000) =
    waitUntil(timeoutMs) { onAllNodes(hasText(text, substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }

fun ComposeTestRule.waitForTag(tag: String, timeoutMs: Long = 5_000) =
    waitUntil(timeoutMs) { onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }
