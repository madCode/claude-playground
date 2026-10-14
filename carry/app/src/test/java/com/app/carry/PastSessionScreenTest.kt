package com.app.carry

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Reading one past session back.
 *
 * Invented exercises and weights -- the repository is public. What is real
 * is the shape: most rows answered, some left blank, one skipped, and one
 * the owner added because the sheet never listed it.
 *
 * Two properties matter more than the layout. A blank row is shown blank,
 * never as a zero or a skip; and there is nothing here to write with,
 * because the only editable session is today's on the Log tab.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class PastSessionScreenTest {
    // Android rather than plain: the system back button is half of this
    // feature's navigation, and pressing it needs the Activity's own
    // dispatcher rather than a callback a test supplies.
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val out = File(System.getProperty("screenshotDir") ?: "build/screenshots").apply { mkdirs() }

    private val day = SampleSession.past

    /**
     * Five more invented sessions, so the day under test is genuinely off
     * the bottom of the Since screen and the list has to be scrolled to
     * reach it. Their heaviest lift is renamed, so the row the tests scroll
     * to is the only one that matches.
     */
    private fun filler(date: String) =
        SampleSession.pastFile.replace("2026-09-27", date).replace("deadlift", "step-up")

    private val since = Growth.of(
        listOf(SampleSession.pastFile) + listOf(
            "2026-10-11", "2026-10-04", "2026-09-20", "2026-09-13", "2026-09-06",
        ).map(::filler)
    )

    /** The row for the day under test, named by its heaviest lift so it is
     *  the one row in the list that matches — and never by its formatted
     *  date, which is rendered in the reader's own locale. */
    private val thatDay = hasContentDescription("heaviest deadlift, 50 pounds", substring = true)

    /**
     * A LazyColumn does not compose what is off-screen, so there is no node
     * for `performScrollTo` to scroll to. The LIST is scrolled instead,
     * until the node exists.
     */
    private fun reach(matcher: androidx.compose.ui.test.SemanticsMatcher) {
        compose.onNode(hasScrollAction()).performScrollToNode(matcher)
    }

    private fun openThatDay() {
        compose.setContent { SinceScreen(since) }
        reach(thatDay)
        compose.onNode(thatDay).performClick()
    }

    @Test
    fun `a session reads back row by row, with the numbers that were entered`() {
        compose.setContent { PastSession(day, onBack = {}) }
        // Asserted through the TalkBack label, which carries the units the
        // layout leaves to the column a figure sits in -- so one assertion
        // covers both what is on screen and what is read out.
        compose.onNodeWithContentDescription("bench press, 45 pounds, 10 reps, easy")
            .assertIsDisplayed()
        compose.onNodeWithContentDescription(
            "goblet squat · harder, 25 pounds, 8 reps, hard, last two were ugly"
        ).assertIsDisplayed()
        // "10 total" is 5 a side: the file's own wording is shown, never
        // the runner's working number.
        compose.onNodeWithText("10 total x deadlift", substring = true).assertIsDisplayed()
        shoot("22-past-session")
    }

    @Test
    fun `a row nobody filled in is shown blank, and a skipped one is shown skipped`() {
        // The distinction this whole app exists to keep. A blank row is not
        // a zero and not a skip, and the screen that reads a session back
        // is where confusing the three would be invisible.
        compose.setContent { PastSession(day, onBack = {}) }
        compose.onNodeWithContentDescription("torso rotation · seated, not filled in")
            .assertIsDisplayed()
        compose.onNodeWithContentDescription("lateral raise, skipped").assertIsDisplayed()
        // Three of the eight rows on that sheet were left alone, and each
        // of them says so in its own words rather than showing a 0.
        // Those two labels are matched in full, so a row that grew a
        // stand-in zero or a "skipped" on a blank would fail them. Three
        // of the eight rows on that sheet were left alone, and each says
        // so in its own words.
        compose.onAllNodesWithText("blank", substring = true).assertCountEquals(3)
    }

    @Test
    fun `the rows the owner added are part of the session, not left out`() {
        // On a borrowed day these ARE the session: the sheet's rows are
        // another class's block. They live in the file's `added` array,
        // which is not part of the Session model at all -- a detail view
        // built from the model alone would show the owner nothing of what
        // they actually did.
        compose.setContent { PastSession(day, onBack = {}) }
        reach(hasText("farmer carry", substring = true))
        compose.onNodeWithContentDescription(
            "farmer carry, 35 pounds, 20 reps, hurt, you added this row"
        ).assertIsDisplayed()
    }

    @Test
    fun `nothing about a past session can be typed into`() {
        // Not a second editor, deliberately: two ways to write one file is
        // how a wrong number gets in. No text box, no chip to toggle, and
        // the only thing to tap is the way out.
        compose.setContent { PastSession(day, onBack = {}) }
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        compose.onAllNodes(isToggleable()).assertCountEquals(0)
        compose.onAllNodes(hasClickAction()).assertCountEquals(1)
        compose.onNodeWithContentDescription("Back to every session").assertIsDisplayed()
    }

    @Test
    fun `a backfilled session says it is a record`() {
        compose.setContent { PastSession(day.copy(backfilled = true), onBack = {}) }
        compose.onNodeWithText("logged before Carry", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Nothing here can be edited", substring = true).assertIsDisplayed()
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
    }

    @Test
    fun `a session with no numbers on it says so rather than showing a column of dashes`() {
        val blank = day.copy(
            lifts = 0,
            best = null,
            rows = day.rows.map {
                it.copy(weight = null, reps = null, easy = false, hard = false, hurt = false)
            },
        )
        compose.setContent { PastSession(blank, onBack = {}) }
        compose.onNodeWithText("No numbers were entered", substring = true).assertIsDisplayed()
        // Still a record of what the sheet asked, and the skip is still an
        // answer rather than a blank.
        compose.onNodeWithContentDescription("lateral raise, skipped").assertIsDisplayed()
        compose.onNodeWithText("0 lifts", substring = true).assertIsDisplayed()
    }

    @Test
    fun `a file with no exercises in it says that, rather than drawing nothing`() {
        // A blank screen looks exactly like a broken one, and this file is
        // written on another machine.
        compose.setContent { PastSession(day.copy(rows = emptyList(), lifts = 1), onBack = {}) }
        compose.onNodeWithText("lists no exercises", substring = true).assertIsDisplayed()
        compose.onNodeWithText("1 lift", substring = true).assertIsDisplayed()
    }

    @Test
    fun `a row holding a number and no name is not given one`() {
        // Carry never invents an exercise's identity. The save path
        // refuses to write a row like this, so it can only have arrived
        // from elsewhere -- and it is shown as what it is.
        val odd = day.copy(
            rows = listOf(Growth.Row("", null, null, 45.0, 1, added = true)),
        )
        compose.setContent { PastSession(odd, onBack = {}) }
        compose.onNodeWithText("(unnamed)", substring = true).assertIsDisplayed()
        compose.onNodeWithContentDescription(
            "unnamed exercise, 45 pounds, 1 rep, you added this row"
        ).assertIsDisplayed()
        // One rep, not "1 reps".
        compose.onNodeWithText("1 rep", substring = true).assertIsDisplayed()
    }

    @Test
    fun `tapping a session in the list opens that session`() {
        openThatDay()
        compose.onNodeWithContentDescription("bench press, 45 pounds, 10 reps, easy")
            .assertIsDisplayed()
        compose.onNodeWithText("4 lifts", substring = true).assertIsDisplayed()
    }

    @Test
    fun `the way back on screen returns to the list`() {
        openThatDay()
        compose.onNodeWithContentDescription("Back to every session").performClick()
        compose.onNodeWithText("Every session".uppercase()).assertIsDisplayed()
        compose.onNodeWithContentDescription("Back to every session").assertDoesNotExist()
    }

    @Test
    fun `the system back button returns to the list rather than leaving the app`() {
        // There is no nav library here, so without a BackHandler the back
        // button has nothing to pop and closes the app from a screen the
        // owner opened with a tap.
        //
        // Driven through a NavigationEventInput rather than through
        // `onBackPressedDispatcher.onBackPressed()`: a BackHandler now
        // registers with the navigation-event dispatcher, and the legacy
        // call runs only the callbacks added to the legacy list -- so it
        // reports success against a screen that never handled anything.
        val back = DirectNavigationEventInput()
        var dispatcher: NavigationEventDispatcher? = null
        compose.setContent {
            dispatcher = LocalNavigationEventDispatcherOwner.current?.navigationEventDispatcher
            SinceScreen(since)
        }
        compose.runOnUiThread { dispatcher!!.addInput(back) }

        reach(thatDay)
        compose.onNode(thatDay).performClick()
        compose.onNodeWithContentDescription("Back to every session").assertIsDisplayed()

        compose.runOnUiThread { back.backCompleted() }

        compose.onNodeWithContentDescription("Back to every session").assertDoesNotExist()
        // The row that was tapped exists in the list and nowhere else, so
        // finding it again proves which screen this is -- wherever the
        // list is scrolled to, which is where it was left.
        compose.onNode(thatDay).assertExists()
    }

    @Test
    fun `the open session survives the process being killed and restored`() {
        // Rotation and process death have bitten this project before, and
        // being thrown back to the list for turning the phone is the kind
        // of thing that makes an app feel broken.
        val restorer = StateRestorationTester(compose)
        restorer.setContent { SinceScreen(since) }
        reach(thatDay)
        compose.onNode(thatDay).performClick()
        compose.onNodeWithContentDescription("Back to every session").assertIsDisplayed()

        restorer.emulateSavedInstanceStateRestore()

        compose.onNodeWithContentDescription("Back to every session").assertIsDisplayed()
        // The same session, not merely some session: the selection is kept
        // by the session's own id, and a remembered list position would
        // reopen a different day the moment a file arrived.
        compose.onNodeWithContentDescription("bench press, 45 pounds, 10 reps, easy")
            .assertIsDisplayed()
    }

    @Test
    fun `a session that has left the folder closes, and does not reopen later`() {
        // A sync replaces a file rather than editing it, and the folder is
        // re-read on every resume -- so the session behind what is on
        // screen can simply stop being there. Held, the selection would
        // spring the detail view open again over whatever the owner was
        // reading when the file came back.
        val shown = mutableStateOf(since)
        compose.setContent { SinceScreen(shown.value) }
        reach(thatDay)
        compose.onNode(thatDay).performClick()
        compose.onNodeWithContentDescription("Back to every session").assertIsDisplayed()

        shown.value = since.copy(days = since.days.filterNot { it.sheetId == "2026-09-27:3G" })
        compose.onNodeWithContentDescription("Back to every session").assertDoesNotExist()

        shown.value = since
        compose.onNodeWithContentDescription("Back to every session").assertDoesNotExist()
        compose.onNodeWithText("Every session".uppercase()).assertIsDisplayed()
    }

    @Test
    fun `a day row is a touch target, not a line of text`() {
        compose.setContent { SinceScreen(since) }
        reach(thatDay)
        val dp = compose.onNode(thatDay).fetchSemanticsNode().size.height / compose.density.density
        assertTrue("a row tapped at arm's length, and it measured ${dp}dp", dp >= 48f)
    }

    private fun shoot(name: String) {
        compose.waitForIdle()
        val shot = compose.onRoot().captureToImage().asAndroidBitmap()
        check(!shot.isFlat()) { "$name captured a blank image, so it proves nothing" }
        File(out, "$name.png").outputStream().use {
            shot.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
