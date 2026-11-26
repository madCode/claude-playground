package spotter.app

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
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
import spotter.coach.Journal
import spotter.coach.Load
import spotter.coach.Past
import spotter.coach.Reading
import spotter.coach.TodaysBoard
import spotter.coach.Vocabulary
import java.io.File
import java.time.LocalDate

// With the phone set to dark, every screen is: a card or a page left white would glare at the
// studio door. An invented lifter.
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-night-xxhdpi")
class DarkThemeTest {
    @get:Rule val compose = createComposeRule()
    private val vocabulary = Vocabulary.starting()
    private val identity = Identity(vocabulary)
    private fun known(line: String) = identity.read(BoardLine.read(line).words) as Reading.Known
    private fun day(n: Int) = LocalDate.of(2026, 11, n)

    @Test
    fun everyScreenIsDark() {
        var ids = 0L
        fun entry(day: Int, line: String, lb: Int) =
            Journal.logged(1L, day(day), "2G", "r${ids + 1}", line, known(line), Load(lb, 2), 8..8, null, listOf(8)).copy(id = ++ids)
        val own = listOf(entry(16, "8 x deadlift", 40), entry(18, "8 x deadlift", 40), entry(18, "8 x chest press", 25))
        val board = TodaysBoard.Lines(
            BookedClass("2G", day(21), day(21).atTime(9, 50), "Orange 60 Min 2G"),
            listOf("* 8 x deadlift", "* 8 x chest press", "* 8 each x swing lunge with rotation", "* 8 x tricep push-up"),
        )
        val today = Today.of(board, own, identity, vocabulary)
        compose.setContent {
            HomeScreen("v", waitingBuild = 9, onInstall = {}, onCopyVersion = {}, today = today, past = Past.classes(Journal.read(own, vocabulary), emptyList()))
        }
        shoot("dark-sheet")
        compose.onNodeWithText("History").performClick()
        shoot("dark-history")
        compose.onNodeWithText("Settings").performClick()
        // Settings is all quieter grey here: nothing in the main ink to count.
        shoot("dark-settings", ink = false)
        compose.onNodeWithText("Today").performClick()
        compose.onNodeWithText("Log this class").performClick()
        compose.onNodeWithContentDescription("8 x deadlift: changed").performClick()
        compose.onNodeWithContentDescription("8 x deadlift: 45 pounds").performClick()
        compose.onNodeWithContentDescription("8 x deadlift: twinge").performClick()
        shoot("dark-log")
    }

    // Light text is thin strokes; a white card or page is a block. So: a little near-white, for
    // the words, and no more than a little.
    private fun shoot(name: String, ink: Boolean = true) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val pixels = IntArray(bitmap.width * bitmap.height).also { bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height) }
        val bright = pixels.count { p -> (p shr 16 and 0xFF) > 0xD0 && (p shr 8 and 0xFF) > 0xD0 && (p and 0xFF) > 0xD0 }
        val share = bright * 1000 / pixels.size
        println("$name: $share per mille near white")
        assertTrue("$name: $share per mille of the screen is near white", share <= BRIGHT.last && (!ink || share >= BRIGHT.first))
    }

    private companion object {
        // Per mille of the screen: below, the words are as dark as the page; above, something is a white block.
        val BRIGHT = 1..30
    }
}
