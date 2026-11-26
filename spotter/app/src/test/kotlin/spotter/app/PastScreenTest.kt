package spotter.app

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import spotter.app.today.Today
import spotter.coach.BoardLine
import spotter.coach.BookedClass
import spotter.coach.Identity
import spotter.coach.Imported
import spotter.coach.Journal
import spotter.coach.Load
import spotter.coach.Logged
import spotter.coach.Past
import spotter.coach.Reading
import spotter.coach.TodaysBoard
import spotter.coach.Vocabulary
import java.io.File
import java.time.LocalDate

// An invented lifter's classes: looking back at them, read only.
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class PastScreenTest {
    @get:Rule val compose = createComposeRule()
    private val vocabulary = Vocabulary.starting()
    private val identity = Identity(vocabulary)
    private fun known(line: String) = identity.read(BoardLine.read(line).words) as Reading.Known
    private fun day(n: Int) = LocalDate.of(2026, 11, n)

    private var ids = 0L
    private fun entry(day: Int, line: String, lb: Int, reps: List<Int>, hurt: Boolean = false, easy: Boolean = false) =
        Journal.logged(1L, day(day), "2G", "r${ids + 1}", line, known(line), Load(lb, 2), 8..8, null, reps, hurt = hurt, easy = easy).copy(id = ++ids)

    private val own = listOf(
        entry(16, "8 x deadlift", 40, listOf(8, 8)),
        entry(21, "8 x close grip chest press (explosive)", 30, listOf(8), easy = true),
        entry(21, "8 x deadlift", 45, listOf(7), hurt = true),
    )
    private val imported = listOf(Imported(Logged(day(10), known("8 x deadlift"), Load(40, 2), 8..8, listOf(8)), "3G", "8 x deadlift"))
    private val past = Past.classes(Journal.read(own, vocabulary), imported)

    // The list, then a class's rows, then one lift over time, and back out by the screen's
    // own buttons or the phone's.
    @Test
    fun aClassAndALiftCanBeLookedBackAt() {
        compose.setContent { HomeScreen("v", waitingBuild = null, onInstall = {}, onCopyVersion = {}, past = past, startTab = Tab.HISTORY) }
        compose.onNodeWithText("Past classes").assertIsDisplayed()
        compose.onNodeWithText("Sat 21 Nov · 2G").assertIsDisplayed()
        compose.onNodeWithText("2 rows logged").assertIsDisplayed()
        compose.onNodeWithText("1 row · imported").assertIsDisplayed()
        shoot("history")
        compose.onNodeWithText("Sat 21 Nov · 2G").performClick()
        compose.onNodeWithText("2G · Sat 21 Nov").assertIsDisplayed()
        compose.onNodeWithText("Past classes").assertDoesNotExist()
        // Heard as one thing: the lift, what was held in words, and what was done with it.
        compose.onNodeWithContentDescription("deadlift, two 45 pound dumbbells, 7 reps · twinge").assertIsDisplayed()
        compose.onNodeWithContentDescription("close grip explosive chest press, two 30 pound dumbbells, 8 reps · felt easy").assertIsDisplayed()
        shoot("history-class")
        compose.onNodeWithContentDescription("deadlift, two 45 pound dumbbells, 7 reps · twinge").performClick()
        compose.onNodeWithText("deadlift").assertIsDisplayed()
        compose.onNodeWithContentDescription("21 Nov 2026, two 45 pound dumbbells, 7 reps · twinge").assertIsDisplayed()
        compose.onNodeWithContentDescription("16 Nov 2026, two 40 pound dumbbells, 8 reps").assertIsDisplayed()
        compose.onNodeWithContentDescription("10 Nov 2026, two 40 pound dumbbells, 8 reps, imported").assertIsDisplayed()
        shoot("history-lift")
        // Back to the class it was opened from, then to the list.
        compose.onNodeWithContentDescription("Back to 2G · Sat 21 Nov").performClick()
        compose.onNodeWithText("2G · Sat 21 Nov").assertIsDisplayed()
        Espresso.pressBack()
        compose.onNodeWithText("Past classes").assertIsDisplayed()
    }

    // A class or a lift that is open stays open through a rotation.
    @Test
    fun whatIsOpenSurvivesRecreation() {
        val restore = StateRestorationTester(compose)
        restore.setContent { HomeScreen("v", waitingBuild = null, onInstall = {}, onCopyVersion = {}, past = past, startTab = Tab.HISTORY) }
        compose.onNodeWithText("Mon 16 Nov · 2G").performClick()
        restore.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("2G · Mon 16 Nov").assertIsDisplayed()
        compose.onNodeWithContentDescription("deadlift, two 40 pound dumbbells, 8 reps").performClick()
        restore.emulateSavedInstanceStateRestore()
        compose.onNodeWithContentDescription("21 Nov 2026, two 45 pound dumbbells, 7 reps · twinge").assertIsDisplayed()
    }

    // From the sheet: a row's "why" leads to the sessions its number rests on. A lift never
    // done has none to show, and no way offered to them.
    @Test
    fun aSheetRowOpensItsPastSessions() {
        val board = TodaysBoard.Lines(BookedClass("2G", day(23), day(23).atTime(9, 50), "Orange 60 Min 2G"), listOf("* 8 x deadlift", "* 8 x squat"))
        val today = Today.of(board, own, identity, vocabulary)
        compose.setContent { HomeScreen("v", waitingBuild = null, onInstall = {}, onCopyVersion = {}, today = today, past = past) }
        compose.onNodeWithText("8 x squat", useUnmergedTree = true).performClick()
        compose.onNodeWithContentDescription("8 x squat: past sessions").assertDoesNotExist()
        compose.onNodeWithText("8 x deadlift", useUnmergedTree = true).performClick()
        compose.onNodeWithContentDescription("8 x deadlift: past sessions").performScrollTo().performClick()
        compose.onNodeWithText("History").assertIsSelected()
        compose.onNodeWithContentDescription("16 Nov 2026, two 40 pound dumbbells, 8 reps").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back to History").performClick()
        compose.onNodeWithText("Past classes").assertIsDisplayed()
    }

    // A number read from another wording leads to that wording's sessions; and a class left
    // open under History earlier isn't where "back" goes from them.
    @Test
    fun aRowReadFromAnotherWordingOpensThoseSessions() {
        val board = TodaysBoard.Lines(BookedClass("2G", day(23), day(23).atTime(9, 50), "Orange 60 Min 2G"), listOf("* 8 total x alt dumbbell deadlift"))
        val today = Today.of(board, own, identity, vocabulary)
        compose.setContent { HomeScreen("v", waitingBuild = null, onInstall = {}, onCopyVersion = {}, today = today, past = past) }
        compose.onNodeWithText("History").performClick()
        compose.onNodeWithText("Sat 21 Nov · 2G").performClick()
        compose.onNodeWithText("Today").performClick()
        compose.onNodeWithText("8 total x alt dumbbell deadlift", useUnmergedTree = true).performClick()
        compose.onNodeWithContentDescription("8 total x alt dumbbell deadlift: past sessions").performScrollTo().performClick()
        compose.onNodeWithText("deadlift").assertIsDisplayed()
        compose.onNodeWithContentDescription("16 Nov 2026, two 40 pound dumbbells, 8 reps").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back to History").performClick()
        compose.onNodeWithText("Past classes").assertIsDisplayed()
    }

    // The history a lift was opened from is removed: the tab says so, not nothing.
    @Test
    fun anOpenLiftThatIsGoneLeavesTheTabReadable() {
        var classes by mutableStateOf(past)
        compose.setContent { HomeScreen("v", waitingBuild = null, onInstall = {}, onCopyVersion = {}, past = classes, startTab = Tab.HISTORY) }
        compose.onNodeWithText("Tue 10 Nov · 3G").performClick()
        compose.onNodeWithContentDescription("deadlift, two 40 pound dumbbells, 8 reps").performClick()
        classes = emptyList()
        compose.onNodeWithText("Nothing logged yet. What you lift, and where it is heading, shows here.").assertIsDisplayed()
    }

    // Far down a long list, another tab and back, or into a class and back: still there.
    @Test
    fun theListKeepsItsPlace() {
        val many = (1..CLASSES_SHOWN).map { n ->
            Imported(Logged(LocalDate.of(2026, 1, 1).plusDays(n.toLong()), known("8 x deadlift"), Load(40, 2), 8..8, listOf(8)), "2G", "8 x deadlift")
        }
        compose.setContent { HomeScreen("v", waitingBuild = null, onInstall = {}, onCopyVersion = {}, past = Past.classes(emptyList(), many), startTab = Tab.HISTORY) }
        compose.onNodeWithText("Fri 2 Jan · 2G").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("History").performClick()
        compose.onNodeWithText("Fri 2 Jan · 2G").assertIsDisplayed()
        compose.onNodeWithText("Fri 2 Jan · 2G").performClick()
        compose.onNodeWithText("2G · Fri 2 Jan").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back to History").performClick()
        compose.onNodeWithText("Fri 2 Jan · 2G").assertIsDisplayed()
    }

    // A long history is shown a lot at a time.
    @Test
    fun earlierClassesAreAskedFor() {
        val many = (1..(CLASSES_SHOWN + 5)).map { n ->
            Imported(Logged(LocalDate.of(2026, 1, 1).plusDays(n.toLong()), known("8 x deadlift"), Load(40, 2), 8..8, listOf(8)), "2G", "8 x deadlift")
        }
        compose.setContent { HomeScreen("v", waitingBuild = null, onInstall = {}, onCopyVersion = {}, past = Past.classes(emptyList(), many), startTab = Tab.HISTORY) }
        compose.onNodeWithText("Fri 2 Jan · 2G").assertDoesNotExist()
        compose.onNodeWithText("Show earlier classes (5 more)").performScrollTo().performClick()
        compose.onNodeWithText("Fri 2 Jan · 2G").performScrollTo().assertIsDisplayed()
    }

    private fun shoot(name: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
