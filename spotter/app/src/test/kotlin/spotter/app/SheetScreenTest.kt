package spotter.app

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import spotter.app.today.Today
import spotter.coach.BoardLine
import spotter.coach.BookedClass
import spotter.coach.Identity
import spotter.coach.Load
import spotter.coach.Logged
import spotter.coach.Reading
import spotter.coach.TodaysBoard
import spotter.coach.Vocabulary
import java.io.File
import java.time.LocalDate

// The real 2026-11-21 board from the public feed, its unread line swapped for one still unread
// (a corpus line), and an invented lifter's log.
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class SheetScreenTest {
    @get:Rule val compose = createComposeRule()
    private val out = File(System.getProperty("screenshotDir") ?: "build/screenshots").apply { mkdirs() }

    private val identity = Identity(Vocabulary.starting())
    private val day = LocalDate.of(2026, 11, 21)
    private val booked = BookedClass("2G", day, day.atTime(9, 50), "Orange 60 Min 2G")
    private val lines = listOf(
        "**Floor Block 1 - 12.5 minutes circuit**",
        "* 8 x close grip chest press (explosive)",
        "* 8 x deadlift",
        "* 8 each x swing lunge with rotation",
        "* 8 x dumbbell sit-up with torso rotation",
        "**Floor Block 2 - 9.5 minutes circuit**",
        "* 8 x tricep push-up",
        "* 8 each x goblet lateral shift",
    )
    private fun exercise(line: String) = identity.read(BoardLine.read(line).words) as Reading.Known
    private val log = listOf(
        Logged(day.minusDays(3), exercise("8 x close grip chest press (explosive)"), Load(25, 2), 8..8, listOf(8, 8, 8), easy = true),
        Logged(day.minusDays(3), exercise("8 x deadlift"), Load(40, 2), 8..8, listOf(8, 7, 8)),
    )

    private fun show(today: Today, onAllow: () -> Unit = {}) = compose.setContent {
        HomeScreen("0.1.0-debug.60+abc1234 (60)", waitingBuild = null, onInstall = {}, onCopyVersion = {}, today = today, onAllowCalendar = onAllow)
    }

    @Test
    fun theSheetShowsTheBoardsWordsAndTheNumber() {
        show(Today.from(TodaysBoard.Lines(booked, lines), log, identity))
        compose.onNodeWithText("2G · Sat 21 Nov · 9:50").assertIsDisplayed()
        compose.onNodeWithText("8 x close grip chest press (explosive)").assertIsDisplayed()
        // The number leads its row, with where it comes from beneath it.
        compose.onNodeWithText("2×30", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("▲ up", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("2×40", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("same again", useUnmergedTree = true).assertIsDisplayed()
        val number = compose.onNodeWithText("2×30", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val words = compose.onNodeWithText("8 x close grip chest press (explosive)", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("$number before $words", number.right <= words.left)
        // The reader's own key isn't on the glance; a line it couldn't read says why in its row,
        // once, and not again above the sheet.
        compose.onNodeWithText("chest press [grip=close, tempo=explosive]").assertDoesNotExist()
        compose.onNodeWithText("not sure", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("\"swing\" isn't known yet", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("8 each x swing lunge with rotation: not sure what this is (unknown words)").assertDoesNotExist()
        compose.onNodeWithText("no weight", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        // Logging is for after class: under the rows, not above them.
        val log = compose.onNodeWithText("Log this class").performScrollTo().fetchSemanticsNode().boundsInRoot
        val last = compose.onNodeWithText("no weight", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("$log under $last", log.top >= last.bottom)
        shoot("sheet")
    }

    @Test
    fun aTapShowsWhy() {
        show(Today.from(TodaysBoard.Lines(booked, lines), log, identity))
        compose.onNodeWithText("· clean at 2×25: up a dumbbell").assertDoesNotExist()
        compose.onNodeWithText("8 x close grip chest press (explosive)").performClick()
        compose.onNodeWithText("· clean at 2×25: up a dumbbell").assertIsDisplayed()
        // In plain words, so a misreading of the line can be seen.
        compose.onNodeWithText("Read as: close grip explosive chest press").assertIsDisplayed()
        shoot("sheet-why")
    }

    // An outage and a borrowed board both say so, above the rows.
    @Test
    fun aStaleOrBorrowedBoardSaysSo() {
        show(Today.from(TodaysBoard.Lines(booked, lines, borrowedFrom = "3G", staleFrom = day.minusDays(1)), log, identity))
        compose.onNodeWithText("This is the board from Fri 20 Nov: the one for Sat 21 Nov couldn't be fetched.").assertIsDisplayed()
        compose.onNodeWithText("No 2G board yet, so this is the 3G board. Leave rows that aren't in your class.").assertIsDisplayed()
        shoot("sheet-stale-borrowed")
    }

    @Test
    fun theCalendarIsAskedFor() {
        var asks = 0
        show(Today.NeedsCalendar) { asks++ }
        compose.onNodeWithText("Allow the calendar").performClick()
        assertEquals(1, asks)
        shoot("sheet-needs-calendar")
    }

    @Test
    fun noClassSaysWhy() {
        show(Today.from(TodaysBoard.Nothing("no class booked today or tomorrow"), emptyList(), identity))
        compose.onNodeWithText("No class booked today or tomorrow.").assertIsDisplayed()
    }

    // TalkBack hears words for the number and the arrow.
    @Test
    fun theNumberIsSpokenInWords() {
        show(Today.from(TodaysBoard.Lines(booked, lines), log, identity))
        compose.onNodeWithContentDescription("two 30 pound dumbbells, up from last time").assertIsDisplayed()
    }

    // An open "why" stays open when the activity is recreated (rotation).
    @Test
    fun anOpenWhySurvivesRecreation() {
        val restore = StateRestorationTester(compose)
        val today = Today.from(TodaysBoard.Lines(booked, lines), log, identity)
        restore.setContent { HomeScreen("v", waitingBuild = null, onInstall = {}, onCopyVersion = {}, today = today) }
        compose.onNodeWithText("8 x close grip chest press (explosive)").performClick()
        restore.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("· clean at 2×25: up a dumbbell").assertIsDisplayed()
    }

    private fun shoot(name: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val colours = (0 until bitmap.width step 20).flatMap { x -> (0 until bitmap.height step 20).map { y -> bitmap.getPixel(x, y) } }.toSet()
        assertTrue("$name came back flat", colours.size > 2)
        File(out, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}

// At the largest font scale a long reason must not crush the board's words.
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi", fontScale = 2.0f)
class SheetLargeTextTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun aLongReasonLeavesTheBoardsWordsReadable() {
        val identity = Identity(Vocabulary.starting())
        val day = LocalDate.of(2026, 11, 21)
        val booked = BookedClass("2G", day, day.atTime(9, 50), "Orange 60 Min 2G")
        val press = identity.read(BoardLine.read("20 x chest press").words) as Reading.Known
        // 8 reps last time and 20 now: too far apart to convert, so a long reason.
        val log = listOf(Logged(day.minusDays(2), press, Load(25, 2), 8..8, listOf(8, 8, 8), easy = true))
        compose.setContent {
            HomeScreen("v", waitingBuild = null, onInstall = {}, onCopyVersion = {}, today = Today.from(TodaysBoard.Lines(booked, listOf("* 20 x chest press")), log, identity))
        }
        // The row merges its texts for TalkBack; the words' own bounds are in the unmerged tree.
        val words = compose.onNodeWithText("20 x chest press", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        // At least a third of the screen's width for the board's words.
        assertTrue("words squeezed to ${words.width} of ${root.width}", words.width > root.width / 3)
    }
}
