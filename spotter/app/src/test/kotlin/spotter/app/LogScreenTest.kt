package spotter.app

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
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
    private val log = EventLog(db.events())
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
    private fun save(shown: Today.Showing, draft: LogDraft) =
        Logging.events(draft, shown.sheet, Logging.logged(log.entries(), shown.date, shown.className), shown.date, shown.className, 1L)
            .forEach(log::write)

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
        compose.onNodeWithContentDescription("less, 8 x deadlift: 8 reps").performClick()
        compose.onNodeWithText("Twinge").performClick()
        compose.onNodeWithText("Logged: 2×45 · 7 reps · twinge").assertIsDisplayed()
        // A bodyweight row has nothing to log, and says so.
        compose.onNodeWithText("No weight to log").performScrollTo().assertIsDisplayed()
        shoot("log")
        compose.onNodeWithText("Save").performScrollTo().performClick()

        val (shown, draft) = saved!!
        save(shown, draft)
        val stored = Logging.logged(log.entries(), saturday, "2G")
        assertEquals(listOf(30, 45), stored.values.map { it.lb })
        assertTrue(stored.values.last().hurt)
        // Saturday's own sheet keeps Saturday's numbers; the next class reads what was done.
        val again = today(saturday)
        assertEquals("2×30 ▲", again.sheet.glance(again.sheet.rows[0]))
        assertEquals(2, again.logged.size)
        val next = today(saturday.plusDays(3))
        assertEquals("2×35 ▲", next.sheet.glance(next.sheet.rows[0]))
        // Short of the reps on the one set logged is a miss on the first set: down a step.
        assertEquals("2×40 ▼", next.sheet.glance(next.sheet.rows[1]))
    }

    // Reopened, the screen shows what is logged, and the button says so.
    @Test
    fun aLoggedClassReopensWithItsLog() {
        save(today(saturday), LogDraft().with(today(saturday).sheet.rows[1].id, spotter.coach.RowLog(40, 2, 6, stopped = true)))
        show(today(saturday)) { _, _ -> }
        compose.onNodeWithText("Change the log (1 logged)").performClick()
        compose.onNodeWithText("Logged: 2×40 · 6 reps · had to stop").assertIsDisplayed()
    }

    // Half-logged when the phone turns: still logging, with what was entered.
    @Test
    fun aHalfLoggedClassSurvivesRecreation() {
        val restore = StateRestorationTester(compose)
        val shown = today(saturday)
        restore.setContent { HomeScreen("v", waitingBuild = null, onInstall = {}, onCopyVersion = {}, today = shown) }
        compose.onNodeWithText("Log this class").performClick()
        compose.onNodeWithContentDescription("8 x deadlift: changed").performClick()
        compose.onNodeWithContentDescription("more, 8 x deadlift: heaviest two 10 pound dumbbells").performClick()
        restore.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Logged: 2×12 · 8 reps").assertIsDisplayed()
        compose.onNodeWithContentDescription("less, 8 x deadlift: heaviest two 12 pound dumbbells").assertIsDisplayed()
    }

    private fun shoot(name: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val colours = (0 until bitmap.width step 20).flatMap { x -> (0 until bitmap.height step 20).map { y -> bitmap.getPixel(x, y) } }.toSet()
        assertTrue("$name came back flat", colours.size > 2)
        File(out, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
