package spotter.app

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class HomeScreenTest {
    @get:Rule val compose = createComposeRule()
    private val out = File(System.getProperty("screenshotDir") ?: "build/screenshots").apply { mkdirs() }

    @Test
    fun aWaitingBuildIsOfferedAndInstallsOnATap() {
        var installs = 0
        compose.setContent { HomeScreen("0.1.0-debug.41+abc1234 (41)", waitingBuild = 42, onInstall = { installs++ }, onCopyVersion = {}) }
        compose.onNodeWithText("A newer build, 42, is ready.").assertIsDisplayed()
        compose.onNodeWithText("Install").performClick()
        assertEquals(1, installs)
        shoot("home-update-waiting")
    }

    // Where the log is kept is said in plain words, with the one thing to do about it.
    @Test
    fun theBackupSaysWhereTheLogIs() {
        var picks = 0
        var backup by mutableStateOf(BackupState())
        compose.setContent { HomeScreen("v", waitingBuild = null, onInstall = {}, onCopyVersion = {}, backup = backup, onChooseFolder = { picks++ }) }
        compose.onNodeWithText("Your log is only on this phone.").assertIsDisplayed()
        compose.onNodeWithText("Choose a backup folder").performClick()
        assertEquals(1, picks)
        backup = BackupState("Download/Spotter")
        compose.onNodeWithText("Your log will be copied to Download/Spotter.").assertIsDisplayed()
        backup = BackupState("Download/Spotter", "22 Nov, 14:30")
        compose.onNodeWithText("Log copied to Download/Spotter · 22 Nov, 14:30").assertIsDisplayed()
        shoot("home-backup")
        backup = BackupState("Download/Spotter", "22 Nov, 14:30", ok = false)
        compose.onNodeWithText("Couldn't copy your log to Download/Spotter. Choose the folder again.").assertIsDisplayed()
        compose.onNodeWithText("Change folder").performClick()
        assertEquals(2, picks)
    }

    // Progress is under the sheet once there is some: what went up, and what is about to, each
    // heard as one sentence. With nothing to say, there is no heading.
    @Test
    fun progressShowsWhatMovedAndWhatIsNext() {
        var progress by mutableStateOf(spotter.coach.Progress())
        compose.setContent { HomeScreen("v", waitingBuild = null, onInstall = {}, onCopyVersion = {}, progress = progress) }
        compose.onNodeWithText("Progress").assertDoesNotExist()
        val load = { lb: Int -> spotter.coach.Load(lb, 2) }
        progress = spotter.coach.Progress(
            wentUp = listOf(spotter.coach.Progress.WentUp("chest press", load(25), load(30), 8, java.time.LocalDate.of(2026, 11, 20))),
            next = listOf(
                spotter.coach.Progress.Next("deadlift", load(45), 8, ready = true),
                spotter.coach.Progress.Next("bicep curl [alternating=yes]", load(20), 12, ready = false),
            ),
        )
        compose.onNodeWithText("Progress").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("2×25 → 2×30 for 8 · 20 Nov", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("chest press: up from two 25 pound dumbbells to two 30 pound dumbbells for 8, on 20 Nov").assertIsDisplayed()
        compose.onNodeWithContentDescription("deadlift: up to two 45 pound dumbbells for 8 next time").assertIsDisplayed()
        compose.onNodeWithText("one more clean session at 2×20 for 12", useUnmergedTree = true).assertIsDisplayed()
        // And what became of the sheet's numbers, in counts; a kind with none isn't listed.
        compose.onNodeWithText("The sheet's numbers, last 4 weeks").assertDoesNotExist()
        progress = progress.copy(advice = spotter.coach.Progress.Advice(asSuggested = 14, heavier = 3, lighterOrShort = 0))
        compose.onNodeWithText("14 of 17 done as suggested").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("3 lifted heavier").assertIsDisplayed()
        progress = progress.copy(advice = spotter.coach.Progress.Advice(asSuggested = 14, heavier = 3, lighterOrShort = 2))
        compose.onNodeWithText("3 lifted heavier · 2 lighter or short").assertIsDisplayed()
        shoot("home-progress")
        // With only that to say, the card is still there.
        progress = spotter.coach.Progress(advice = spotter.coach.Progress.Advice(asSuggested = 4))
        compose.onNodeWithText("4 of 4 done as suggested").assertIsDisplayed()
        compose.onNodeWithText("Next").assertDoesNotExist()
        compose.onNodeWithText("lifted heavier", substring = true).assertDoesNotExist()
    }

    // The words Spotter doesn't know are listed with where they were seen, each heard as one
    // sentence, the rest counted; with none, there is no card.
    @Test
    fun unknownWordsAreListedAndCounted() {
        var words by mutableStateOf(emptyList<spotter.coach.UnknownWord>())
        compose.setContent { HomeScreen("v", waitingBuild = null, onInstall = {}, onCopyVersion = {}, unknownWords = words) }
        compose.onNodeWithText("Words Spotter doesn't know yet").assertDoesNotExist()
        val day = java.time.LocalDate.of(2026, 11, 20)
        words = listOf(spotter.coach.UnknownWord("zorbing", "6 x zorbing", day, 3), spotter.coach.UnknownWord("frobnicated", "10 x frobnicated squat", day, 1)) +
            (1..8).map { spotter.coach.UnknownWord("word$it", "8 x word$it press", day, 1) }
        compose.onNodeWithText("Words Spotter doesn't know yet").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("zorbing · 3 days", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("frobnicated: on 1 day, as in 10 x frobnicated squat").assertIsDisplayed()
        compose.onNodeWithText("8 x word6 press", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("8 x word7 press", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("and 2 more").performScrollTo().assertIsDisplayed()
        shoot("home-unknown-words")
        // A stranger's very long line takes two lines of the card, not the screen.
        val normal = compose.onNodeWithText("Words Spotter doesn't know yet").fetchSemanticsNode().layoutInfo.parentInfo!!.height
        words = words.map { it.copy(line = "8 x " + "verylongword ".repeat(22)) }
        val long = compose.onNodeWithText("Words Spotter doesn't know yet").fetchSemanticsNode().layoutInfo.parentInfo!!.height
        assertTrue("$long against $normal", long < normal * 2)
    }

    // A word is sorted by tapping it: passed over, or another name for an exercise found by
    // searching; nothing is answered until the yes, and an answer can be undone.
    @Test
    fun anUnknownWordIsSortedInTwoTaps() {
        val day = java.time.LocalDate.of(2026, 11, 20)
        val words = listOf(
            spotter.coach.UnknownWord("zorbed", "10 x zorbed shoulder press", day, 2), spotter.coach.UnknownWord("head smasher", "12 x head smasher", day, 1),
            spotter.coach.UnknownWord("press", "8 x alt deadbug with press", day, 1), spotter.coach.UnknownWord("zorbed frobnicated", "8 x zorbed squat frobnicated", day, 1),
        )
        val lists = spotter.coach.Vocabulary.starting()
        var answers by mutableStateOf(emptyList<spotter.coach.WordAnswer>())
        val undone = mutableListOf<String>()
        compose.setContent {
            HomeScreen(
                "v", waitingBuild = null, onInstall = {}, onCopyVersion = {}, unknownWords = words, wordAnswers = answers,
                movements = lists.movements,
                lineReads = { line, a -> spotter.coach.WordAnswers.sayBack(line, a, answers, lists) },
                onAnswerWord = { answers = answers + it }, onUndoWord = { undone += it },
            )
        }
        // Passing a word over: said back first, and nothing kept until the yes.
        compose.onNodeWithContentDescription("zorbed: on 2 days, as in 10 x zorbed shoulder press").performScrollTo().performClick()
        compose.onNodeWithContentDescription("zorbed: ignore this word").performScrollTo().performClick()
        compose.onNodeWithText("\"zorbed\" will be passed over on every board. This line will read as shoulder press.").performScrollTo().assertIsDisplayed()
        assertTrue(answers.isEmpty())
        compose.onNodeWithContentDescription("zorbed: back").performScrollTo().performClick()
        compose.onNodeWithContentDescription("zorbed: ignore this word").performScrollTo().performClick()
        compose.onNodeWithContentDescription("zorbed: yes, read it that way").performScrollTo().performClick()
        assertEquals(listOf(spotter.coach.WordAnswer("zorbed")), answers)
        compose.onNodeWithText("zorbed: ignored").performScrollTo().assertIsDisplayed()
        // Another name: found by part of the exercise's name, the nearest first.
        compose.onNodeWithContentDescription("head smasher: on 1 day, as in 12 x head smasher").performScrollTo().performClick()
        compose.onNodeWithContentDescription("head smasher: it's another name for an exercise").performScrollTo().performClick()
        compose.onNodeWithText("Which exercise? Type part of its name").performScrollTo().performTextInput("zzz")
        compose.onNodeWithText("No exercise by that name.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("zzz").performTextReplacement("tricep")
        compose.onNodeWithContentDescription("head smasher is another name for tricep kickback").performScrollTo().assertIsDisplayed()
        // By another of its names too.
        compose.onNodeWithText("tricep").performTextReplacement("rdl")
        compose.onNodeWithContentDescription("head smasher is another name for romanian deadlift").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("rdl").performTextReplacement("tricep")
        shoot("home-word-search")
        compose.onNodeWithContentDescription("head smasher is another name for tricep extension").performScrollTo().performClick()
        compose.onNodeWithText("\"head smasher\" will be read as tricep extension on every board, with its history and weight. This line will read as tricep extension.").performScrollTo().assertIsDisplayed()
        shoot("home-word-confirm")
        compose.onNodeWithContentDescription("head smasher: yes, read it that way").performScrollTo().performClick()
        assertEquals(spotter.coach.WordAnswer("head smasher", "tricep extension"), answers.last())
        compose.onNodeWithText("head smasher: another name for tricep extension").performScrollTo().assertIsDisplayed()
        // A word the reader settles by its neighbours can't be answered, and an answer that
        // would change nothing isn't offered a yes.
        compose.onNodeWithContentDescription("press: on 1 day, as in 8 x alt deadbug with press").performScrollTo().performClick()
        compose.onNodeWithText("This one can't be sorted here: it is a count, or a word that means different things on different lines.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("press: ignore this word").assertDoesNotExist()
        compose.onNodeWithContentDescription("press: close").performScrollTo().performClick()
        compose.onNodeWithContentDescription("zorbed frobnicated: on 1 day, as in 8 x zorbed squat frobnicated").performScrollTo().performClick()
        compose.onNodeWithContentDescription("zorbed frobnicated: ignore this word").performScrollTo().performClick()
        compose.onNodeWithText("That wouldn't change this line: its words aren't side by side here, or mean something else. Nothing to keep.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("zorbed frobnicated: yes, read it that way").assertDoesNotExist()
        compose.onNodeWithContentDescription("zorbed frobnicated: back").performScrollTo().performClick()
        compose.onNodeWithContentDescription("zorbed frobnicated: neither, leave it").performScrollTo().performClick()
        // Leaving a word alone closes it and answers nothing.
        compose.onNodeWithContentDescription("zorbed: on 2 days, as in 10 x zorbed shoulder press").performScrollTo().performClick()
        compose.onNodeWithContentDescription("zorbed: neither, leave it").performScrollTo().performClick()
        compose.onNodeWithContentDescription("zorbed: ignore this word").assertDoesNotExist()
        assertEquals(2, answers.size)
        compose.onNodeWithContentDescription("Undo the answer for zorbed").performScrollTo().performClick()
        assertEquals(listOf("zorbed"), undone)
        shoot("home-word-answers")
    }

    // An answer undone while another waits for its yes: what is said back is said again.
    @Test
    fun theSayBackFollowsTheAnswers() {
        val lists = spotter.coach.Vocabulary.starting()
        var answers by mutableStateOf(listOf(spotter.coach.WordAnswer("zorbed")))
        val words = listOf(spotter.coach.UnknownWord("frobnicated", "8 x zorbed frobnicated squat", java.time.LocalDate.of(2026, 11, 20), 1))
        compose.setContent {
            HomeScreen(
                "v", waitingBuild = null, onInstall = {}, onCopyVersion = {}, unknownWords = words, wordAnswers = answers, movements = lists.movements,
                lineReads = { line, a -> spotter.coach.WordAnswers.sayBack(line, a, answers, lists) }, onUndoWord = { answers = emptyList() },
            )
        }
        compose.onNodeWithContentDescription("frobnicated: on 1 day, as in 8 x zorbed frobnicated squat").performScrollTo().performClick()
        compose.onNodeWithContentDescription("frobnicated: ignore this word").performScrollTo().performClick()
        compose.onNodeWithText("\"frobnicated\" will be passed over on every board. This line will read as squat.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Undo the answer for zorbed").performScrollTo().performClick()
        compose.onNodeWithText("\"frobnicated\" will be passed over on every board. This line stays \"not sure\" until zorbed is known too.").performScrollTo().assertIsDisplayed()
    }

    // Turning the phone mid-answer keeps the word open, what was typed, and the pick.
    @Test
    fun aHalfGivenAnswerSurvivesRecreation() {
        val restore = androidx.compose.ui.test.junit4.StateRestorationTester(compose)
        val all = listOf(spotter.coach.UnknownWord("head smasher", "12 x head smasher", java.time.LocalDate.of(2026, 11, 20), 1))
        // As the app is after a rotation: the answers are there at once, the words a moment later.
        var words by mutableStateOf(all)
        restore.setContent {
            HomeScreen(
                "v", waitingBuild = null, onInstall = {}, onCopyVersion = {}, unknownWords = words,
                wordAnswers = listOf(spotter.coach.WordAnswer("zorbed")), movements = spotter.coach.Vocabulary.starting().movements,
            )
        }
        compose.onNodeWithContentDescription("head smasher: on 1 day, as in 12 x head smasher").performScrollTo().performClick()
        compose.onNodeWithContentDescription("head smasher: it's another name for an exercise").performScrollTo().performClick()
        compose.onNodeWithText("Which exercise? Type part of its name").performScrollTo().performTextInput("tri")
        restore.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("tri").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("head smasher is another name for tricep extension").performScrollTo().performClick()
        words = emptyList()
        restore.emulateSavedInstanceStateRestore()
        compose.waitForIdle()
        words = all
        compose.onNodeWithContentDescription("head smasher: yes, read it that way").performScrollTo().assertIsDisplayed()
    }

    // With every word sorted the card stays for its answers, so one can still be undone.
    @Test
    fun answersStayWhenNoWordIsLeft() {
        compose.setContent {
            HomeScreen("v", waitingBuild = null, onInstall = {}, onCopyVersion = {}, wordAnswers = listOf(spotter.coach.WordAnswer("zorbed")))
        }
        compose.onNodeWithText("Words Spotter doesn't know yet").assertDoesNotExist()
        compose.onNodeWithText("Your answers").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Undo the answer for zorbed").assertIsDisplayed()
    }

    // What the numbers are made from is said, with what the import left out, and removing it
    // asks first.
    @Test
    fun theHistorySaysWhatWasReadAndLeftOut() {
        var imports = 0
        var removals = 0
        var stops = 0
        var counts = 0
        var history by mutableStateOf(spotter.app.history.HistoryState())
        compose.setContent {
            HomeScreen(
                "v", waitingBuild = null, onInstall = {}, onCopyVersion = {}, history = history,
                onImportHistory = { imports++ }, onRemoveHistory = { removals++ }, onStopHistory = { stops++ },
                onPickCounts = { counts++ },
            )
        }
        compose.onNodeWithText("No history imported. Until you log an exercise, its row says \"new: go light\".").assertIsDisplayed()
        // The counts go with a history: nothing to add them to yet.
        compose.onNodeWithText("Add your dumbbell counts").assertDoesNotExist()
        compose.onNodeWithText("Import a history file").performClick()
        assertEquals(1, imports)
        history = spotter.app.history.HistoryState(698, listOf("409 no weight", "91 form broke down"), "Nothing in that file could be read as a history.")
        compose.onNodeWithText("History imported: 698 rows read.").assertIsDisplayed()
        compose.onNodeWithText("Left out: 409 no weight, 91 form broke down.").assertIsDisplayed()
        compose.onNodeWithText("Nothing in that file could be read as a history.").assertIsDisplayed()
        shoot("home-history")
        compose.onNodeWithText("Replace the history").performClick()
        assertEquals(2, imports)
        compose.onNodeWithText("Add your dumbbell counts").performClick()
        assertEquals(1, counts)
        history = history.copy(refused = null, counts = 14, ruled = 212, countAssumed = 37)
        compose.onNodeWithText("37 rows didn't say how many dumbbells: read as the lift's usual number.").assertIsDisplayed()
        compose.onNodeWithText("Your dumbbell counts: 14 lifts; 212 rows took the count from them.").assertIsDisplayed()
        compose.onNodeWithText("Replace the dumbbell counts").assertIsDisplayed()
        shoot("home-history-counts")
        compose.onNodeWithText("Remove the history").performClick()
        compose.onNodeWithText("Remove the imported history? Your own log stays.").assertIsDisplayed()
        assertEquals(0, removals)
        compose.onNodeWithContentDescription("Keep the imported history").performClick()
        compose.onNodeWithText("Remove the history").performClick()
        compose.onNodeWithContentDescription("Remove the imported history").performClick()
        assertEquals(1, removals)
        // While a file is being read it says so, and the one thing to tap is Stop.
        history = history.copy(reading = true)
        compose.onNodeWithText("Reading the file…").assertIsDisplayed()
        compose.onNodeWithText("Replace the history").assertDoesNotExist()
        compose.onNodeWithContentDescription("Stop reading the file").performClick()
        assertEquals(1, stops)
    }

    // At the largest text every history button is whole: each wraps under the one before, it
    // isn't squeezed to nothing beside it.
    @Test
    fun theHistoryButtonsStayWholeAtLargeText() {
        compose.setContent {
            val density = androidx.compose.ui.platform.LocalDensity.current
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density, fontScale = 2f),
            ) {
                HomeScreen("v", waitingBuild = null, onInstall = {}, onCopyVersion = {}, history = spotter.app.history.HistoryState(698))
            }
        }
        val replace = compose.onNodeWithText("Replace the history").fetchSemanticsNode().boundsInRoot
        val counts = compose.onNodeWithText("Add your dumbbell counts").fetchSemanticsNode().boundsInRoot
        val remove = compose.onNodeWithText("Remove the history").fetchSemanticsNode().boundsInRoot
        assertTrue("$counts under $replace", counts.top >= replace.bottom && counts.width >= replace.width * 0.8f)
        assertTrue("$remove under $counts", remove.top >= counts.bottom && remove.width >= replace.width * 0.8f)
        shoot("home-history-large-text")
    }

    @Test
    fun theVersionCopiesInOneTap() {
        var copies = 0
        compose.setContent { HomeScreen("0.1.0-debug.42+abc1234 (42)", waitingBuild = null, onInstall = {}, onCopyVersion = { copies++ }) }
        compose.onNodeWithText("Install").assertDoesNotExist()
        compose.onNodeWithText("Version 0.1.0-debug.42+abc1234 (42) · tap to copy").performClick()
        assertEquals(1, copies)
        shoot("home")
    }

    private fun shoot(name: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        // A blank capture would pass every assertion above while showing nothing.
        val colours = (0 until bitmap.width step 20).flatMap { x -> (0 until bitmap.height step 20).map { y -> bitmap.getPixel(x, y) } }.toSet()
        assertTrue("$name came back flat", colours.size > 2)
        File(out, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
