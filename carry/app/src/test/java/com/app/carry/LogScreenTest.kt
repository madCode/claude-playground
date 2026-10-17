package com.app.carry

import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class LogScreenTest {
    @get:Rule val compose = createComposeRule()
    private val out = File(System.getProperty("screenshotDir") ?: "build/screenshots").apply { mkdirs() }

    private val edits = mutableStateOf<Map<Int, SessionPatch.Entry>>(emptyMap())
    private val added = mutableStateOf<List<AddedSet>>(emptyList())
    private var saves = 0

    /** Rows are two lines tall, so most of this screen starts off-screen —
     *  and a LazyColumn does not compose what is off-screen, so there is
     *  no node to scroll TO. The list has to be scrolled to it. */
    private fun reach(text: String) {
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(text, substring = true))
    }

    private var shared: String? = null

    /** Held as state, not passed by value, so a test can drive one
     *  composition from Idle to Saved. The intel post is the last item of
     *  the list and only exists once saved, so proving it ABSENT before a
     *  save means also proving it present after one — in the same
     *  composition, with the same scroll. */
    private val saving = mutableStateOf<SaveState>(SaveState.Idle)

    private fun show(
        state: SaveState = SaveState.Idle, unreadable: Int = 0, fontScale: Float = 1f,
        session: Session = SampleSession.today,
    ) {
        saving.value = state
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
            LogScreen(
                unreadable = unreadable,
                session = session,
                entries = edits.value,
                added = added.value,
                onEdit = { ref, e -> edits.value = edits.value + (ref to e) },
                onAdd = { added.value = added.value + AddedSet(id = AddedSet.newId()) },
                onEditAdded = { i, row -> added.value = added.value.toMutableList().also { it[i] = row } },
                onSave = { saves++ },
                saving = saving.value,
                onShare = { shared = it },
            )
            }
        }
    }

    @Test
    fun `typing a weight and reps records exactly what was typed`() {
        show()
        compose.onNodeWithContentDescription("bench press, lb").performTextReplacement("45")
        compose.onNodeWithContentDescription("bench press, reps").performTextReplacement("8")
        assertEquals(SessionPatch.Entry(weight = 45.0, reps = 8), edits.value[0])
    }

    @Test
    fun `a half-pound dumbbell survives being typed one key at a time`() {
        // The previous version of this test used performTextReplacement,
        // which commits the whole string in one go -- the one path that
        // worked. Typed key by key, the field rendered the PARSED value
        // back, so "12." became "12" and the next key made 125: a weight
        // ten times too large, silently, in a training history.
        show()
        val field = compose.onNodeWithContentDescription("bench press, lb")
        "12.5".forEach { field.performTextInput(it.toString()) }
        assertEquals(12.5, edits.value[0]!!.weight!!, 0.001)
    }

    @Test
    fun `the box and the stored value never disagree`() {
        // The real rule. A second decimal point is dropped rather than
        // accepted, and the digit after it still lands -- so "12.5.7" is
        // 12.57, which is also what the box reads. Whatever the owner can
        // see is what gets written; the failure that matters is the two
        // drifting apart silently.
        show()
        val field = compose.onNodeWithContentDescription("bench press, lb")
        "12.5.7".forEach { field.performTextInput(it.toString()) }
        val shown = compose.onNodeWithContentDescription("bench press, lb")
            .fetchSemanticsNode().config
            .getOrNull(androidx.compose.ui.semantics.SemanticsProperties.EditableText)?.text
        assertEquals("12.57", shown)
        assertEquals(shown!!.toDouble(), edits.value[0]!!.weight!!, 0.001)
    }

    @Test
    fun `clearing a field clears the value rather than leaving the old one`() {
        // A mistyped weight has to be unmakeable; the write path supports
        // it, and the screen must actually produce the blank entry.
        show()
        val field = compose.onNodeWithContentDescription("bench press, lb")
        field.performTextReplacement("95")
        field.performTextReplacement("")
        assertTrue("expected a blank entry, got ${edits.value[0]}", edits.value[0]!!.blank)
    }

    @Test
    fun `the two chips are independent, and turning one off leaves the other`() {
        show()
        assertEquals(null, edits.value[0])
        compose.onAllNodesWithText("hurt")[0].performClick()
        assertEquals(SessionPatch.Entry(hurt = true), edits.value[0])
        compose.onAllNodesWithText("easy")[0].performClick()
        assertEquals(SessionPatch.Entry(easy = true, hurt = true), edits.value[0])
        // The half a bad copy() would break.
        compose.onAllNodesWithText("hurt")[0].performClick()
        assertEquals(SessionPatch.Entry(easy = true, hurt = false), edits.value[0])
    }

    @Test
    fun `adding a row gives somewhere to name it`() {
        // On a day with no intel for the owner's class this is the session,
        // not an afterthought.
        show()
        reach("+ Add something the sheet missed"); compose.onNodeWithText("+ Add something the sheet missed").performClick()
        reach("what was it?"); compose.onNodeWithContentDescription("added exercise name").performTextReplacement("ybell deadlift")
        assertEquals("ybell deadlift", added.value.single().name)
        shoot("11-log")
    }

    @Test
    fun `a failed save says whether the file is in trouble`() {
        // "Try again" and "your file needs rescuing" are different things
        // to tell someone.
        show(SaveState.Failed("Couldn't save, and the file on disk may be damaged.", damaged = true))
        reach("Try again")
        compose.onNodeWithText("⚠ Couldn't save, and the file on disk may be damaged.").assertIsDisplayed()
        compose.onNodeWithText("Try again").assertIsDisplayed()
        shoot("12-save-failed")
    }

    @Test
    fun `nothing saves by itself`() {
        // Previously this asserted 0 saves without interacting at all,
        // so it could only fail if the button were entirely unwired.
        show()
        compose.onNodeWithContentDescription("bench press, lb").performTextInput("45")
        compose.onAllNodesWithText("easy")[0].performClick()
        reach("+ Add something the sheet missed"); compose.onNodeWithText("+ Add something the sheet missed").performClick()
        assertEquals("typing must not write to the file", 0, saves)
        reach("Save"); compose.onNodeWithText("Save").performClick()
        assertEquals(1, saves)
    }

    @Test
    fun `a rewritten session says so and offers a way out`() {
        // Without the escape the owner is stuck: the save refuses, every
        // refresh re-raises the warning, and the only other exits destroy
        // the numbers they typed.
        var discarded = 0
        compose.setContent {
            LogScreen(
                session = SampleSession.today,
                entries = emptyMap(), added = emptyList(),
                onEdit = { _, _ -> }, onAdd = {}, onEditAdded = { _, _ -> },
                onSave = {}, saving = SaveState.Idle,
                fileMoved = true, onDiscard = { discarded++ },
            )
        }
        compose.onNodeWithText("Your numbers are still here", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Use the new sheet instead").performClick()
        assertEquals(1, discarded)
        shoot("13-file-moved")
    }

    @Test
    fun `a weight over 1000 is kept as typed and the row says what is wrong`() {
        // The importer refuses the whole day over this one box. Dropping
        // the last key would make 2500 into 250, a wrong number; clamping
        // makes it 1000. So the box keeps what was typed and says so, in
        // words under the row and on the box itself for TalkBack.
        show()
        val field = compose.onNodeWithContentDescription("bench press, lb")
        "2500".forEach { field.performTextInput(it.toString()) }
        assertEquals(2500.0, edits.value[0]!!.weight!!, 0.001)
        field.assertTextEquals("2500")
        compose.onNodeWithText("Weight 2500 is over 1000. Fix it to save.").assertIsDisplayed()
        field.assert(SemanticsMatcher.expectValue(SemanticsProperties.Error, "weight 2500 is over 1000"))
        shoot("29-out-of-range")

        field.performTextReplacement("250")
        compose.onAllNodesWithText("over 1000", substring = true).assertCountEquals(0)
        field.assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Error))
    }

    @Test
    fun `reps with a fraction are held, not stored as blank`() {
        // The box takes one '.', so 8.5 can be typed. Stored as null it
        // would save as "no reps" under a box reading 8.5; the importer
        // wants a whole number, so the row is held and flagged instead.
        show()
        val field = compose.onNodeWithContentDescription("bench press, reps")
        "8.".forEach { field.performTextInput(it.toString()) }
        assertEquals("mid-typing is not a mistake", SessionPatch.Entry(reps = 8), edits.value[0])
        compose.onAllNodesWithText("whole number", substring = true).assertCountEquals(0)
        field.performTextInput("5")
        field.assertTextEquals("8.5")
        assertEquals(SessionPatch.Entry(fractionalReps = 8.5), edits.value[0])
        compose.onNodeWithText("Reps 8.5 isn't a whole number. Fix it to save.").assertIsDisplayed()
    }

    @Test
    fun `an added row says what is wrong with its numbers too`() {
        show()
        reach("+ Add something the sheet missed"); compose.onNodeWithText("+ Add something the sheet missed").performClick()
        compose.onNodeWithContentDescription("added exercise name").performTextReplacement("pop squat")
        compose.onNodeWithContentDescription("added exercise, reps").performTextReplacement("1200")
        assertEquals(1200, added.value.single().reps)
        compose.onNodeWithText("Reps 1200 is over 1000. Fix it to save.").assertIsDisplayed()
    }

    @Test
    fun `a refused save is announced, not only shown`() {
        // The tap gives a listener no other sign that nothing was written.
        val why = "1 row needs fixing: bench press weight 2500 is over 1000. Nothing was saved."
        show(SaveState.Failed(why, damaged = false))
        reach("Nothing was saved")
        compose.onNodeWithText(why).assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, androidx.compose.ui.semantics.LiveRegionMode.Polite))
        shoot("30-save-refused")
    }

    @Test
    fun `a save refused because the file changed is announced and offers the new sheet`() {
        var discarded = 0
        val why = "This session changed since you opened it, so nothing was written. Your numbers are still here."
        compose.setContent {
            LogScreen(
                session = SampleSession.today,
                entries = mapOf(0 to SessionPatch.Entry(weight = 45.0, reps = 10)), added = emptyList(),
                onEdit = { _, _ -> }, onAdd = {}, onEditAdded = { _, _ -> },
                onSave = {}, saving = SaveState.Failed(why, damaged = false, newSheet = true),
                onDiscard = { discarded++ },
            )
        }
        reach("Try again")
        compose.onNodeWithText(why).assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        shoot("40-save-file-changed")
        compose.onNodeWithText("Use the new sheet instead").performClick()
        assertEquals(1, discarded)
    }

    @Test
    fun `a row with a number and no name asks for the name in the row`() {
        // The save refuses this row, so the ask belongs in the box that
        // fixes it. Finding out at the save button is how the number got
        // thrown away in the first place.
        show()
        reach("+ Add something the sheet missed"); compose.onNodeWithText("+ Add something the sheet missed").performClick()
        compose.onNodeWithText("what was it?").assertIsDisplayed()
        compose.onNodeWithContentDescription("added exercise, lb")
            .performTextReplacement("20")
        assertEquals(20.0, added.value.single().weight)
        compose.onNodeWithText("name this").assertIsDisplayed()
        shoot("15-name-this")
    }

    @Test
    fun `a box keeps its own number when the list shrinks under it`() {
        // Save a session whose first added row was left blank: that row
        // is dropped from the file, the second slides into slot 0, and a
        // box keyed by index renders the blank row's empty string while
        // the file holds 30. The screen says "Saved" and shows a number
        // that is not there -- re-entering from memory is how a wrong
        // number gets into the history.
        show()
        reach("+ Add something the sheet missed"); compose.onNodeWithText("+ Add something the sheet missed").performClick()
        reach("+ Add something the sheet missed"); compose.onNodeWithText("+ Add something the sheet missed").performClick()
        val boxes = compose.onAllNodesWithContentDescription("added exercise, lb")
        boxes[1].performTextReplacement("30")
        assertEquals(30.0, added.value[1].weight)

        // What the save's re-seed does: the blank row is gone.
        added.value = listOf(added.value[1])
        compose.waitForIdle()
        compose.onAllNodesWithContentDescription("added exercise, lb")[0]
            .assertTextEquals("30")
    }

    @Test
    fun `there is no hard chip, and a row the file marked hard reads as blank`() {
        // madCode, 2026-10-16: the coach treats a completed set as completed
        // however it felt, so `hard` is no longer offered. A row an older
        // file marked only `hard` looks blank, so it isn't counted filled.
        // Tapping `easy` clears it: the owner's fresh answer wins.
        edits.value = mapOf(0 to SessionPatch.Entry(hard = true))
        show()
        compose.onAllNodesWithText("easy")[0].assertIsDisplayed()
        compose.onNodeWithText("hard").assertDoesNotExist()
        compose.onNodeWithText("0 of 10 noted", substring = true).assertIsDisplayed()
        compose.onAllNodesWithText("easy")[0].performClick()
        assertEquals(SessionPatch.Entry(easy = true), edits.value[0])
    }

    @Test
    fun `saying you did the easier version is its own answer`() {
        // Knee push-ups. For much of this owner's training there is no
        // weight to progress, so this is the only axis they have — and
        // it is independent of how it felt.
        show()
        compose.onAllNodesWithText("mod")[0].performClick()
        compose.onAllNodesWithText("easy")[0].performClick()
        assertEquals(SessionPatch.Entry(easier = true, easy = true), edits.value[0])
    }

    @Test
    fun `a timed row says it happened with done, in the reps box's place`() {
        // #127 v4: a hold prescribes no count, so the reps slot holds a
        // `done` tag under a "time" label. It writes the existing `done`.
        show()
        reach("sit-up")
        compose.onAllNodesWithText("done").assertCountEquals(1)
        compose.onNodeWithContentDescription("sit-up, reps").assertDoesNotExist()
        compose.onNodeWithContentDescription("sit-up, time").performClick()
        assertEquals(SessionPatch.Entry(done = true), edits.value[9])
        compose.onNodeWithText("1 of 10 noted", substring = true).assertIsDisplayed()
        val tag = compose.onNodeWithContentDescription("sit-up, time").getUnclippedBoundsInRoot()
        val label = compose.onNodeWithText("time", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue("the time label sits above its tag: $label vs $tag", label.bottom <= tag.top)
        reach("+ Add something the sheet missed")
        shoot("22-timed-row")
    }

    @Test
    fun `a bodyweight row is done in one tap on its faint reps`() {
        // The empty reps box shows the runner's count faintly; focusing it
        // fills it in, so "did them all" is one tap. Typing still works.
        show()
        reach("10 x squat")
        val box = compose.onNodeWithContentDescription("squat, reps")
        box.performClick()
        assertEquals(SessionPatch.Entry(reps = 10), edits.value[8])
        box.performTextReplacement("7")
        assertEquals(SessionPatch.Entry(reps = 7), edits.value[8])
        compose.onNodeWithText("1 of 10 noted", substring = true).assertIsDisplayed()
    }

    @Test
    fun `a day of only bodyweight rows gets a post once a row is tapped`() {
        // Before v4 a bodyweight row had no way to say it happened short of
        // typing a number, so such a day saved with nothing to post.
        show()
        reach("10 x squat")
        compose.onNodeWithContentDescription("squat, reps").performClick()
        saving.value = SaveState.Saved
        reach("Post what 2G did")
        compose.onNodeWithText("Post what 2G did").performClick()
        reach("Send it")
        compose.onNode(hasText("- 10 x squat", substring = true)).assertIsDisplayed()
        shoot("38-post-bodyweight")
    }

    private fun bodyweight(ref: Int, name: String, asked: String, reps: Int?, weight: Double? = null) = ExerciseSet(
        ref = ref, line = "$asked x $name", canonical = name, prescribedReps = reps, prescribedText = asked,
        suggestedWeight = weight,
    )

    private val mixed = Session(
        sheetId = "2026-10-17:3G", date = "2026-10-17", classTag = "3G",
        sets = listOf(
            bodyweight(0, "jump lunge", "10 total", 5),
            bodyweight(1, "push-up", "6-12", 6),
            bodyweight(2, "bench press", "10", 10, weight = 45.0),
        ),
    )

    @Test
    fun `the faint fill is the coach's count, not the board's words`() {
        // "10 total x jump lunge" is 5 a side, and the importer compares reps
        // against 5. Filling 10 would put a wrong number in history.
        show(session = mixed)
        compose.onNodeWithContentDescription("jump lunge, reps").performClick()
        assertEquals(5, edits.value[0]?.reps)
    }

    @Test
    fun `a tap-fill keeps what was already entered on the row`() {
        // The tap watcher outlives recompositions; a stale copy of the row
        // would write reps over it as it was before these edits.
        show(session = mixed)
        compose.onNodeWithContentDescription("jump lunge, lb").performTextReplacement("20")
        compose.onAllNodesWithText("easy")[0].performClick()
        compose.onNodeWithContentDescription("jump lunge, reps").performClick()
        assertEquals(SessionPatch.Entry(weight = 20.0, reps = 5, easy = true), edits.value[0])
    }

    @Test
    @OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
    fun `a range, a weighted row, and focus without a tap fill nothing`() {
        show(session = mixed)
        // A range's floor would go into history as the count done.
        compose.onNodeWithContentDescription("push-up, reps").performClick()
        assertEquals(null, edits.value[1])
        // A weighted row: blank reps already mean all of them, and a stray
        // tap would record a set with no weight.
        compose.onNodeWithContentDescription("bench press, reps").performClick()
        assertEquals(null, edits.value[2])
        // Focus by keyboard or D-pad is not a tap.
        compose.onNodeWithContentDescription("jump lunge, reps").requestFocus()
        assertEquals(null, edits.value[0])
        // TalkBack: the count is said, and the fill is an action.
        compose.onNodeWithContentDescription("jump lunge, reps")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "empty, prescribed 5"))
            .performCustomAccessibilityActionWithLabel("Fill in 5")
        assertEquals(5, edits.value[0]?.reps)
    }

    @Test
    fun `untouched means not done, and the tally says so`() {
        show()
        compose.onNodeWithText(
            "0 of 10 noted · anything untouched counts as not done", substring = true,
        ).assertIsDisplayed()
        compose.onNodeWithText("skip").assertDoesNotExist()
        // hurt alone does not say a set happened.
        compose.onAllNodesWithText("hurt")[0].performClick()
        compose.onNodeWithText("0 of 10 noted", substring = true).assertIsDisplayed()
        compose.onAllNodesWithText("easy")[0].performClick()
        compose.onNodeWithText("1 of 10 noted", substring = true).assertIsDisplayed()
    }

    @Test
    fun `an older file's skipped row reads as not done, and an edit clears it`() {
        // There is no skip control any more. A file that says `skipped`
        // still reads; the row looks untouched, and typing on it clears
        // the flag rather than saving two answers at once.
        edits.value = mapOf(0 to SessionPatch.Entry(skipped = true))
        show()
        compose.onNodeWithText("skip").assertDoesNotExist()
        compose.onNodeWithText("0 of 10 noted", substring = true).assertIsDisplayed()
        shoot("19-skipped")
        compose.onNodeWithContentDescription("bench press, lb").performTextReplacement("45")
        assertEquals(SessionPatch.Entry(weight = 45.0), edits.value[0])
    }

    @Test
    fun `mod shows only while the weight box is empty`() {
        show()
        val before = compose.onAllNodesWithText("mod").fetchSemanticsNodes().size
        compose.onNodeWithContentDescription("bench press, lb").performTextReplacement("45")
        assertEquals(before - 1, compose.onAllNodesWithText("mod").fetchSemanticsNodes().size)
    }

    @Test
    fun `hurt sits on the name line, and labels sit above the boxes`() {
        show()
        val name = compose.onNodeWithText("10 x bench press").getUnclippedBoundsInRoot()
        val hurt = compose.onAllNodesWithText("hurt")[0].getUnclippedBoundsInRoot()
        val box = compose.onNodeWithContentDescription("bench press, lb").getUnclippedBoundsInRoot()
        val easy = compose.onAllNodesWithText("easy")[0].getUnclippedBoundsInRoot()
        val lb = compose.onAllNodesWithText("lb", useUnmergedTree = true)[0].getUnclippedBoundsInRoot()
        assertTrue("hurt is beside the name: $hurt vs $name", hurt.top < name.bottom && hurt.bottom > name.top)
        assertTrue("the lb label is above its box: $lb vs $box", lb.bottom <= box.top)
        // Chips level with the boxes, not with the labels.
        val off = (easy.top + easy.bottom - box.top - box.bottom).value / 2
        assertTrue("easy is level with the box: $easy vs $box", off in -6f..6f)
    }

    @Test
    fun `at twice the font size every row is still reachable`() {
        show(fontScale = 2f)
        compose.onNodeWithText("How did it go?").assertIsDisplayed()
        reach("sit-up")
        compose.onNodeWithContentDescription("sit-up, time").assertIsDisplayed()
        shoot("37-log-large-text")
    }

    @Test
    fun `on a borrowed day the add-row leads, because it is the session`() {
        // Intel for this class is posted on a small minority of days, so
        // the sheet is usually another class's block and whatever the
        // owner adds by hand IS the workout. That button used to sit
        // under eleven rows of the wrong exercises.
        val borrowed = SampleSession.today.copy(borrowedFrom = "3G")
        compose.setContent {
            LogScreen(
                session = borrowed, entries = emptyMap(), added = emptyList(),
                onEdit = { _, _ -> }, onAdd = { added.value = added.value + AddedSet(id = "x") },
                onEditAdded = { _, _ -> }, onSave = {}, saving = SaveState.Idle,
            )
        }
        compose.onNodeWithText("+ Add what you did").assertIsDisplayed()
        compose.onNodeWithText("No 2G intel", substring = true).assertIsDisplayed()
        shoot("20-borrowed-log")
    }

    @Test
    fun `on a normal day it stays where it was, and only appears once`() {
        show()
        compose.onNodeWithText("+ Add what you did").assertDoesNotExist()
        reach("+ Add something the sheet missed")
        compose.onNodeWithText("+ Add something the sheet missed").assertIsDisplayed()
    }

    @Test
    fun `the intel post is offered only once the log is written down`() {
        // Before the save it would be offering to publish numbers that
        // are not yet anywhere.
        //
        // Both halves are one test on purpose. The button is the last item
        // of a LazyColumn, which does not compose what is off-screen, so
        // `assertDoesNotExist` passes just as well when the list simply
        // never scrolled that far — which is exactly what rebasing onto the
        // two-line rows did to this test. Asserting it IS reachable after
        // the save, from the same composition, is what gives the absence
        // its meaning.
        edits.value = mapOf(0 to SessionPatch.Entry(weight = 45.0))
        show()
        reach("Save")
        compose.onNodeWithText("Post what 2G did").assertDoesNotExist()

        saving.value = SaveState.Saved
        reach("Post what 2G did")
        compose.onNodeWithText("Post what 2G did").assertIsDisplayed()
    }

    @Test
    fun `nothing is sent until the owner has seen the text`() {
        // The claim under the button is one this screen cannot verify --
        // the text is built from a file written on another machine -- so
        // the only assurance worth anything is reading it first.
        edits.value = mapOf(0 to SessionPatch.Entry(weight = 47.5))
        show(SaveState.Saved)
        reach("Post what 2G did")
        compose.onNodeWithText("Post what 2G did").performClick()
        assertEquals("opening the preview must not send", null, shared)
        reach("Send it")
        // "2G floor block", not "bench press": the row label contains that
        // too, so matching on it would pass with no preview on screen.
        compose.onNodeWithText("2G floor block", substring = true).assertIsDisplayed()
        shoot("16-post-intel")

        compose.onNodeWithText("Send it").performClick()
        val post = shared!!
        assertTrue(post, post.contains("bench press"))
        assertTrue("the owner's load is theirs", !post.contains("47.5"))
    }

    @Test
    fun `an empty log is offered no post to make`() {
        // Scrolled to the bottom first, and the positive half proved in the
        // same composition. The share card is the LAST item of a
        // LazyColumn, which composes nothing off-screen, so an
        // `assertDoesNotExist` with no scroll passes whether or not the
        // card is there — it never got that far.
        show(SaveState.Saved)
        reach("Save")
        compose.onNodeWithText("Post what 2G did").assertDoesNotExist()

        edits.value = mapOf(0 to SessionPatch.Entry(weight = 45.0))
        reach("Post what 2G did")
        compose.onNodeWithText("Post what 2G did").assertIsDisplayed()
    }

    @Test
    fun `after a save, each typed name is shown back with the question`() {
        // madCode's call, 2026-10-16: "show the typed name back, no digit
        // check". A digit test missed these two and flagged real names, so
        // the screen asks instead, about every name the owner typed.
        added.value = listOf(
            AddedSet(id = "a", name = "deadlift twenty six", reps = 8),
            AddedSet(id = "b", name = "\u00bd rack row", weight = 20.0),
        )
        show(SaveState.Saved)
        reach("You typed")
        val note = compose.onNodeWithText(
            "You typed: \"\u2068deadlift twenty six\u2069\", \"\u2068\u00bd rack row\u2069\". " +
                "Is each weight in its weight box?"
        )
        note.assertIsDisplayed()
        // Announced when it appears, without taking focus.
        note.assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        // Above the button just tapped, not below it where the list may end.
        assertTrue(
            "the line must sit above the Saved button",
            note.getUnclippedBoundsInRoot().bottom <= compose.onNodeWithText("Saved").getUnclippedBoundsInRoot().top,
        )
        shoot("35-typed-back")
        // And it goes with "Saved": the next edit sets the form back to Idle.
        saving.value = SaveState.Idle
        compose.onNodeWithText("You typed", substring = true).assertDoesNotExist()
    }

    @Test
    fun `a typed name is shown back on one line, whole, and cut visibly only past 200`() {
        // A line separator in a name read from the file would break the
        // sentence; a weight at the end of a long name must still show.
        val long = "a".repeat(180) + " deadlift 26 lb"
        added.value = listOf(
            AddedSet(id = "a", name = "row\u2028machine", weight = 30.0),
            AddedSet(id = "b", name = long, weight = 26.0),
            AddedSet(id = "c", name = "x".repeat(300), weight = 10.0),
        )
        show(SaveState.Saved)
        // The long names make their own boxes scrollable; the list is the
        // first scrollable node, being their ancestor.
        compose.onAllNodes(hasScrollAction())[0].performScrollToNode(hasText("You typed", substring = true))
        val text = compose.onNode(hasText("You typed", substring = true))
            .fetchSemanticsNode().config[SemanticsProperties.Text].joinToString("")
        assertTrue(text, "\u2068row machine\u2069" in text)
        assertTrue(text, "deadlift 26 lb\u2069" in text)
        assertTrue(text, "x".repeat(200) + "\u2026\u2069" in text)
    }

    @Test
    fun `nothing is shown back before the save`() {
        added.value = listOf(AddedSet(id = "a", name = "deadlift twenty six", weight = 26.0))
        show(SaveState.Idle)
        reach("Save")
        compose.onNodeWithText("You typed", substring = true).assertDoesNotExist()
        // The control: the same row, saved, is shown back.
        saving.value = SaveState.Saved
        reach("You typed")
        compose.onNodeWithText("You typed", substring = true).assertIsDisplayed()
    }

    @Test
    fun `a save with no typed name shows nothing back`() {
        edits.value = mapOf(0 to SessionPatch.Entry(weight = 45.0))
        added.value = listOf(AddedSet(id = "a", name = "  ", weight = null))
        show(SaveState.Saved)
        reach("Post what 2G did")
        compose.onNodeWithText("You typed", substring = true).assertDoesNotExist()
    }

    @Test
    fun `a typed name with digits in it is posted plainly, with no mark`() {
        // "bicep curl 21s" is a movement. The digit test flagged it; the
        // question after the save replaces that test, so the post carries
        // no mark at all and the caption makes no promise about weights.
        edits.value = mapOf(0 to SessionPatch.Entry(weight = 45.0))
        added.value = listOf(AddedSet(id = "a", name = "bicep curl 21s", weight = 15.0))
        show(SaveState.Saved)
        reach("Post what 2G did")
        compose.onNodeWithText("Post what 2G did").performClick()
        reach("Send it")
        val preview = compose.onNode(hasText("- bicep curl 21s", substring = true))
        preview.assertIsDisplayed()
        compose.onNodeWithText("\u26a0", substring = true).assertDoesNotExist()
        compose.onNodeWithText("The movements only. A name you typed goes out as you typed it.").assertIsDisplayed()
        // No description on the preview, so it stays navigable line by line.
        preview.assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.ContentDescription))
        shoot("36-post-typed-digits")
    }

    @Test
    fun `a line the sheet already has, typed again by hand, is in the post once`() {
        // The owner's answer, 2026-10-16: "show it once". The sheet lists
        // "10 x bench press" and the owner also added a row with that whole
        // line as its name. The preview shows it as one bullet.
        edits.value = mapOf(0 to SessionPatch.Entry(weight = 45.0))
        added.value = listOf(AddedSet(id = "a", name = "10 x bench press", weight = 45.0))
        show(SaveState.Saved)
        reach("Post what 2G did")
        compose.onNodeWithText("Post what 2G did").performClick()
        reach("Send it")
        val preview = compose.onNode(hasText("2G floor block", substring = true))
        preview.assertIsDisplayed()
        val text = preview.fetchSemanticsNode().config[SemanticsProperties.Text].joinToString("")
        assertEquals(text, 1, text.lines().count { it.endsWith("- 10 x bench press") })
        compose.onNodeWithText("⚠ marks the lines", substring = true).assertDoesNotExist()
        shoot("31-post-repeated-line")
    }

    @Test
    fun `the number boxes say which is weight and which is reps`() {
        // madCode, 2026-10-16: visible labels, not only placeholders. The
        // question after a save names "the weight box", so it must be
        // findable without TalkBack. Hidden from TalkBack, whose label on
        // the box already says it.
        show(SaveState.Idle)
        val lb = compose.onAllNodesWithText("lb", useUnmergedTree = true)
        val reps = compose.onAllNodesWithText("reps", useUnmergedTree = true)
        assertTrue(lb.fetchSemanticsNodes().isNotEmpty())
        assertTrue(reps.fetchSemanticsNodes().isNotEmpty())
        lb[0].assertIsDisplayed()
        lb[0].assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.HideFromAccessibility))
        reps[0].assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.HideFromAccessibility))
    }

    @Test
    fun `a row with no box says why there is no box`() {
        // Here the consequence of a dropped row is a box that is not on
        // screen. Without the line the owner looks for the exercise they
        // did, does not find it, and concludes they already filled it in
        // -- so the set reaches history as nothing.
        // Above the list, not in it: this is a reason the sheet is short,
        // so it must be on screen before any scrolling.
        show(unreadable = 2)
        compose.onNodeWithText(
            "\u26a0 2 rows of this sheet didn't read. They stay in the file untouched; Carry can't show them."
        ).assertIsDisplayed()
    }

    @Test
    fun `the mark is on the preview only, never on what is sent`() {
        // The one thing this must not do. `post.text` goes to the share
        // sheet untouched; the warning lives in the preview that is built
        // from it separately.
        edits.value = mapOf(0 to SessionPatch.Entry(weight = 45.0))
        added.value = listOf(AddedSet(id = "a", name = "ybell deadlift 26", weight = 26.0))
        show(SaveState.Saved)
        reach("Post what 2G did")
        compose.onNodeWithText("Post what 2G did").performClick()
        reach("Send it")
        compose.onNodeWithText("Send it").performClick()
        assertTrue("nothing was handed over", shared != null)
        assertFalse("the warning mark must not reach the subreddit", shared!!.contains("⚠"))
        assertTrue(shared!!, shared!!.contains("ybell deadlift 26"))
    }

    private fun shoot(name: String) {
        compose.waitForIdle()
        val shot = compose.onRoot().captureToImage().asAndroidBitmap()
        // A capture that came back as one flat colour is not evidence. It
        // happens silently -- the test stays green and CI uploads the blank
        // as an artifact -- and it fooled me twice on this project, once
        // into thinking the fonts had broken rendering entirely.
        check(!shot.isFlat()) { "$name captured a blank image, so it proves nothing" }
        File(out, "$name.png").outputStream().use {
            shot.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
