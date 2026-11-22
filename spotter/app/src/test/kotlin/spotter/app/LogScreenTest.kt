package spotter.app

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import spotter.app.log.EventLog
import spotter.app.log.LogDatabase
import spotter.app.today.Today
import spotter.coach.BookedClass
import spotter.coach.Identity
import spotter.coach.LogDraft
import spotter.coach.Logging
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import spotter.coach.TodaysBoard
import spotter.coach.Vocabulary
import java.io.File
import java.time.LocalDate

// Logging a class made of public board lines, by an invented lifter, through the real screen and
// the real Room store.
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class LogScreenTest {
    @get:Rule val compose = createComposeRule()
    private val out = File(System.getProperty("screenshotDir") ?: "build/screenshots").apply { mkdirs() }
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), LogDatabase::class.java)
        .allowMainThreadQueries().build()
    private val log = EventLog(db)
    private val vocabulary = Vocabulary.starting()
    private val identity = Identity(vocabulary)
    private val monday = LocalDate.of(2026, 11, 16)
    private val saturday = LocalDate.of(2026, 11, 21)
    private val lines = listOf(
        "**Floor Block 1 - 12.5 minutes circuit**",
        "* 8 x close grip chest press (explosive)",
        "* 8 x deadlift",
        "* 8 x tricep push-up",
    )

    @After fun close() = db.close()

    private fun board(day: LocalDate) = TodaysBoard.Lines(BookedClass("2G", day, day.atTime(9, 50), "Orange 60 Min 2G"), lines)
    private fun today(day: LocalDate) = Today.of(board(day), log.entries(), identity, vocabulary) as Today.Showing

    // What MainActivity.saveLog does, against the in-memory store.
    private fun save(shown: Today.Showing, draft: LogDraft) = log.save(draft, shown.sheet, shown.date, shown.className, 1L)

    private fun show(today: Today.Showing, onSave: (Today.Showing, LogDraft) -> Unit) = compose.setContent {
        HomeScreen("v", waitingBuild = null, onInstall = {}, onCopyVersion = {}, today = today, onSaveLog = onSave)
    }

    // The car after class: one row changed, one ticked, saved, and the next class's sheet moves.
    @Test
    fun aClassIsLoggedAndTheNextSheetUsesIt() {
        save(today(monday), LogDraft().with(today(monday).sheet.rows[0].id, spotter.coach.RowLog(25, 2, 8)).with(today(monday).sheet.rows[1].id, spotter.coach.RowLog(40, 2, 8)))
        var saved: Pair<Today.Showing, LogDraft>? = null
        show(today(saturday)) { t, d -> saved = t to d }
        compose.onNodeWithText("Log this class").performClick()
        compose.onNodeWithContentDescription("8 x close grip chest press (explosive): done as listed").performClick()
        compose.onNodeWithContentDescription("8 x deadlift: changed").performClick()
        // Opening the rack logs nothing: no made-up set until a weight is picked. Only the row
        // ticked above says "Logged".
        compose.onAllNodesWithText("Logged:", substring = true).assertCountEquals(1)
        compose.onNodeWithText("Heaviest dumbbell, lb. Last time: 2×40.").assertIsDisplayed()
        compose.onNodeWithContentDescription("8 x deadlift: 45 pounds").performClick()
        compose.onNodeWithContentDescription("less, 8 x deadlift: 8 reps").performClick()
        compose.onNodeWithText("Twinge").performClick()
        compose.onNodeWithText("Logged: 2×45 · 7 reps · twinge").performScrollTo().assertIsDisplayed()
        // A bodyweight row has nothing to log, and says so.
        compose.onNodeWithText("No weight to log").performScrollTo().assertIsDisplayed()
        shoot("log")
        compose.onNodeWithText("Save").performScrollTo().performClick()

        val (shown, draft) = saved!!
        save(shown, draft)
        val stored = Logging.logged(shown.sheet, log.entries(), saturday, "2G").byRow
        assertEquals(listOf(30, 45), stored.values.map { it.lb })
        assertTrue(stored.values.last().hurt)
        // Saturday's own sheet keeps Saturday's numbers; the next class reads what was done.
        val again = today(saturday)
        assertEquals("2×30 ▲", again.sheet.glance(again.sheet.rows[0]))
        assertEquals(2, again.logged.size)
        val next = today(saturday.plusDays(3))
        assertEquals("2×35 ▲", next.sheet.glance(next.sheet.rows[0]))
        // One short on the one set logged, with a twinge: the weight holds.
        assertEquals("2×45 =", next.sheet.glance(next.sheet.rows[1]))
    }

    // Logged from the other format's board before the class's own was posted: the press carries
    // over to the real board's press, and the row that isn't on it is shown, to keep or clear.
    @Test
    fun aLogFromABorrowedBoardFollowsTheRealOne() {
        val borrowed = TodaysBoard.Lines(
            BookedClass("2G", saturday, saturday.atTime(9, 50), "Orange 60 Min 2G"),
            listOf("**Floor Block 1 - 10 minutes**", "* 10 x close grip chest press (explosive)", "* 12 x goblet squat"),
            borrowedFrom = "3G",
        )
        val before = Today.of(borrowed, log.entries(), identity, vocabulary) as Today.Showing
        save(before, LogDraft().with(before.sheet.rows[0].id, spotter.coach.RowLog(25, 2, 10)).with(before.sheet.rows[1].id, spotter.coach.RowLog(35, 1, 12)))

        var saved: Pair<Today.Showing, LogDraft>? = null
        show(today(saturday)) { t, d -> saved = t to d }
        // Both count as logged: the one carried over and the one with no row here.
        compose.onNodeWithText("Change the log (2 logged)").performClick()
        compose.onNodeWithText("Logged: 2×25 · 10 reps").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Logged, but not on this board").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Logged: 1×35 · 12 reps").performScrollTo().assertIsDisplayed()
        shoot("log-strays")
        compose.onNodeWithContentDescription("12 x goblet squat, not on this board: clear").performScrollTo().performClick()
        compose.onNodeWithText("Removed when you save.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Save").performScrollTo().performClick()

        val (shown, draft) = saved!!
        // The press wasn't touched: it settles on its row, and the squat is removed.
        assertEquals(listOf(spotter.coach.JournalEntry.Kind.CORRECTED, spotter.coach.JournalEntry.Kind.REMOVED), save(shown, draft).map { it.kind })
        val again = today(saturday)
        assertEquals(listOf(25), again.logged.values.map { it.lb })
        assertTrue(again.strays.isEmpty())
    }

    // A board with nothing to log (all bodyweight) still opens the Log screen when rows logged
    // from an earlier board are waiting there.
    @Test
    fun straysAreReachableOnABoardWithNothingToLog() {
        val borrowed = TodaysBoard.Lines(BookedClass("2G", saturday, saturday.atTime(9, 50), "Orange 60 Min 2G"), listOf("* 12 x goblet squat"), borrowedFrom = "3G")
        val before = Today.of(borrowed, log.entries(), identity, vocabulary) as Today.Showing
        save(before, LogDraft().with(before.sheet.rows[0].id, spotter.coach.RowLog(35, 1, 12)))
        val own = Today.of(
            TodaysBoard.Lines(BookedClass("2G", saturday, saturday.atTime(9, 50), "Orange 60 Min 2G"), listOf("* 8 x tricep push-up")), log.entries(), identity, vocabulary,
        ) as Today.Showing
        show(own) { _, _ -> }
        compose.onNodeWithText("Change the log (1 logged)").performClick()
        compose.onNodeWithText("Logged: 1×35 · 12 reps").performScrollTo().assertIsDisplayed()
    }

    // Reopened, the screen shows what is logged, and the button says so.
    @Test
    fun aLoggedClassReopensWithItsLog() {
        save(today(saturday), LogDraft().with(today(saturday).sheet.rows[1].id, spotter.coach.RowLog(40, 2, 6, stopped = true)))
        show(today(saturday)) { _, _ -> }
        compose.onNodeWithText("Change the log (1 logged)").performClick()
        compose.onNodeWithText("Logged: 2×40 · 6 reps · had to stop").performScrollTo().assertIsDisplayed()
    }

    // Half-logged when the phone turns: still logging, with what was entered, and the row being
    // changed still open although it starts at its listed number.
    @Test
    fun aHalfLoggedClassSurvivesRecreation() {
        save(today(monday), LogDraft().with(today(monday).sheet.rows[1].id, spotter.coach.RowLog(40, 2, 8)))
        val restore = StateRestorationTester(compose)
        val shown = today(saturday)
        restore.setContent { HomeScreen("v", waitingBuild = null, onInstall = {}, onCopyVersion = {}, today = shown) }
        compose.onNodeWithText("Log this class").performClick()
        compose.onNodeWithContentDescription("8 x deadlift: changed").performClick()
        compose.onNodeWithContentDescription("8 x deadlift: 45 pounds").performClick()
        restore.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Logged: 2×45 · 8 reps").performScrollTo().assertIsDisplayed()
        // The rack is still open, one tap from another weight.
        compose.onNodeWithContentDescription("8 x deadlift: 50 pounds").performClick()
        compose.onNodeWithText("Logged: 2×50 · 8 reps").performScrollTo().assertIsDisplayed()
    }

    // The next class replacing this one mid-log starts that class's screen afresh, rather than
    // saving these rows under its date.
    @Test
    fun anotherClassDoesNotInheritTheDraft() {
        var shown by androidx.compose.runtime.mutableStateOf(today(saturday))
        var saved: LogDraft? = null
        compose.setContent { HomeScreen("v", waitingBuild = null, onInstall = {}, onCopyVersion = {}, today = shown, onSaveLog = { _, d -> saved = d }) }
        compose.onNodeWithText("Log this class").performClick()
        compose.onNodeWithContentDescription("8 x deadlift: changed").performClick()
        compose.onNodeWithContentDescription("8 x deadlift: 40 pounds").performClick()
        // A new row's count isn't guessed: it is asked for, and shows without one until said.
        compose.onNodeWithText("Logged: 40 · 8 reps").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("One dumbbell or two?").assertIsDisplayed()
        compose.onNodeWithContentDescription("8 x deadlift: two dumbbells").performClick()
        compose.onNodeWithText("Logged: 2×40 · 8 reps").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("One dumbbell or two?").assertDoesNotExist()
        shown = today(saturday.plusDays(1))
        compose.onNodeWithText("Log this class").performClick()
        compose.onNodeWithText("Save").performScrollTo().performClick()
        assertTrue(saved!!.rows.isEmpty())
    }

    // "Changed" tapped by mistake closes again, and a rack left open with nothing picked saves
    // nothing for its row.
    @Test
    fun anOpenRackWithNothingPickedLogsNothing() {
        save(today(monday), LogDraft().with(today(monday).sheet.rows[1].id, spotter.coach.RowLog(40, 2, 8)))
        var saved: LogDraft? = null
        show(today(saturday)) { _, d -> saved = d }
        compose.onNodeWithText("Log this class").performClick()
        compose.onNodeWithContentDescription("8 x deadlift: changed").performClick()
        compose.onNodeWithContentDescription("8 x deadlift: changed").performClick()
        compose.onNodeWithContentDescription("8 x deadlift: 45 pounds").assertDoesNotExist()
        compose.onNodeWithContentDescription("8 x deadlift: changed").performClick()
        compose.onNodeWithContentDescription("8 x deadlift: 45 pounds").assertIsDisplayed()
        compose.onNodeWithText("Save").performScrollTo().performClick()
        assertTrue(saved!!.rows.isEmpty())
    }

    // Ticked as listed, then the rack opened to look: still as listed, and only that says so.
    @Test
    fun asListedStaysSelectedWithTheRackOpen() {
        save(today(monday), LogDraft().with(today(monday).sheet.rows[1].id, spotter.coach.RowLog(40, 2, 8)))
        show(today(saturday)) { _, _ -> }
        compose.onNodeWithText("Log this class").performClick()
        compose.onNodeWithContentDescription("8 x deadlift: done as listed").performClick()
        compose.onNodeWithContentDescription("8 x deadlift: changed").performClick()
        compose.onNodeWithContentDescription("8 x deadlift: done as listed").assertIsSelected()
        compose.onNodeWithContentDescription("8 x deadlift: changed").assertIsNotSelected()
        compose.onNodeWithContentDescription("8 x deadlift: 45 pounds").assertIsSelected()
        // Another weight, and it is a change.
        compose.onNodeWithContentDescription("8 x deadlift: 40 pounds").performClick()
        compose.onNodeWithContentDescription("8 x deadlift: changed").assertIsSelected()
        compose.onNodeWithContentDescription("8 x deadlift: done as listed").assertIsNotSelected()
    }

    // The sheet has moved on to the next class: the one just done is first on the screen, and
    // logging it saves under its own day.
    @Test
    fun anEarlierClassIsLoggedFromTheNextClassesSheet() {
        var saved: Pair<Today.Showing, LogDraft>? = null
        var earlier by androidx.compose.runtime.mutableStateOf<Today.Showing?>(today(saturday))
        compose.setContent {
            HomeScreen("v", waitingBuild = null, onInstall = {}, onCopyVersion = {}, today = today(saturday.plusDays(1)), onSaveLog = { t, d -> saved = t to d }, earlier = earlier)
        }
        compose.onNodeWithText("2G · Sat 21 Nov · 9:50 isn't logged yet.").assertIsDisplayed()
        shoot("sheet-earlier")
        compose.onNodeWithContentDescription("Log 2G · Sat 21 Nov · 9:50").performClick()
        compose.onNodeWithText("Log · 2G · Sat 21 Nov · 9:50").assertIsDisplayed()
        compose.onNodeWithContentDescription("8 x deadlift: changed").performClick()
        compose.onNodeWithContentDescription("8 x deadlift: 40 pounds").performClick()
        compose.onNodeWithText("Save").performScrollTo().performClick()
        assertEquals(saturday, saved!!.first.date)
        save(saved!!.first, saved!!.second)
        // Logged, it is offered quietly, for corrections.
        earlier = today(saturday)
        compose.onNodeWithText("Change the log for 2G · Sat 21 Nov · 9:50").assertIsDisplayed()
        compose.onNodeWithText("2G · Sat 21 Nov · 9:50 isn't logged yet.").assertDoesNotExist()
    }

    // Just saved, and the next class's board isn't on the phone yet: the logged class's sheet
    // doesn't sit there without its log while the fetch runs.
    @Test
    fun aLoggedClassDoesNotLingerWhileTheNextIsFetched() {
        val shown = today(saturday)
        val logged = setOf(saturday to "2G")
        assertEquals(Today.Loading, Today.afterKept(shown, null, logged))
        // Not logged, or nothing changed: what is showing stays until the fetch says otherwise.
        assertEquals(shown, Today.afterKept(shown, null, emptySet()))
        val next = today(saturday.plusDays(1))
        assertEquals(next, Today.afterKept(shown, next, logged))
    }

    // A class with nothing to log isn't put at the top of the screen.
    @Test
    fun anEarlierClassWithNothingToLogIsNotOffered() {
        val bodyweight = Today.of(
            TodaysBoard.Lines(BookedClass("2G", saturday, saturday.atTime(9, 50), "Orange 60 Min 2G"), listOf("* 8 x tricep push-up")),
            emptyList(), identity, vocabulary,
        ) as Today.Showing
        compose.setContent { HomeScreen("v", waitingBuild = null, onInstall = {}, onCopyVersion = {}, today = today(saturday.plusDays(1)), earlier = bodyweight) }
        compose.onNodeWithText("Log this class").assertIsDisplayed()
        compose.onNodeWithText("isn't logged yet", substring = true).assertDoesNotExist()
    }

    private fun shoot(name: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val colours = (0 until bitmap.width step 20).flatMap { x -> (0 until bitmap.height step 20).map { y -> bitmap.getPixel(x, y) } }.toSet()
        assertTrue("$name came back flat", colours.size > 2)
        File(out, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}

// At the largest text, the rack's last weight and the stepper's + stay on screen and big enough to tap.
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi", fontScale = 2.0f)
class LogLargeTextTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun theRackAndStepperFitAtTheLargestText() {
        val identity = Identity(Vocabulary.starting())
        val day = LocalDate.of(2026, 11, 21)
        val board = TodaysBoard.Lines(BookedClass("2G", day, day.atTime(9, 50), "Orange 60 Min 2G"), listOf("* 8 x close grip chest press (explosive)"))
        val shown = Today.of(board, emptyList(), identity, Vocabulary.starting()) as Today.Showing
        compose.setContent { HomeScreen("v", waitingBuild = null, onInstall = {}, onCopyVersion = {}, today = shown) }
        compose.onNodeWithText("Log this class").performClick()
        compose.onNodeWithContentDescription("8 x close grip chest press (explosive): changed").performClick()
        compose.onNodeWithContentDescription("8 x close grip chest press (explosive): 10 pounds").performClick()
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        for (label in listOf("8 x close grip chest press (explosive): 50 pounds", "more, 8 x close grip chest press (explosive): 8 reps")) {
            val plus = compose.onNodeWithContentDescription(label).performScrollTo().fetchSemanticsNode().boundsInRoot
            assertTrue("$label at ${plus.right} of ${root.right}", plus.right <= root.right && plus.width >= 48f * 3)
        }
    }
}
