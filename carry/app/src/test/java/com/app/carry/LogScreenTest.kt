package com.app.carry

import android.graphics.Bitmap
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

    private fun show(state: SaveState = SaveState.Idle, unreadable: Int = 0) {
        saving.value = state
        compose.setContent {
            LogScreen(
                unreadable = unreadable,
                session = SampleSession.today,
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

    @Test
    fun `typing a weight and reps records exactly what was typed`() {
        show()
        compose.onNodeWithContentDescription("bench press weight in pounds").performTextReplacement("45")
        compose.onNodeWithContentDescription("bench press reps").performTextReplacement("8")
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
        val field = compose.onNodeWithContentDescription("bench press weight in pounds")
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
        val field = compose.onNodeWithContentDescription("bench press weight in pounds")
        "12.5.7".forEach { field.performTextInput(it.toString()) }
        val shown = compose.onNodeWithContentDescription("bench press weight in pounds")
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
        val field = compose.onNodeWithContentDescription("bench press weight in pounds")
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
        compose.onNodeWithContentDescription("bench press weight in pounds").performTextInput("45")
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
        val field = compose.onNodeWithContentDescription("bench press weight in pounds")
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
        val field = compose.onNodeWithContentDescription("bench press reps")
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
        compose.onNodeWithContentDescription("added exercise reps").performTextReplacement("1200")
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
    fun `a row with a number and no name asks for the name in the row`() {
        // The save refuses this row, so the ask belongs in the box that
        // fixes it. Finding out at the save button is how the number got
        // thrown away in the first place.
        show()
        reach("+ Add something the sheet missed"); compose.onNodeWithText("+ Add something the sheet missed").performClick()
        compose.onNodeWithText("what was it?").assertIsDisplayed()
        compose.onNodeWithContentDescription("added exercise weight in pounds")
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
        val boxes = compose.onAllNodesWithContentDescription("added exercise weight in pounds")
        boxes[1].performTextReplacement("30")
        assertEquals(30.0, added.value[1].weight)

        // What the save's re-seed does: the blank row is gone.
        added.value = listOf(added.value[1])
        compose.waitForIdle()
        compose.onAllNodesWithContentDescription("added exercise weight in pounds")[0]
            .assertTextEquals("30")
    }

    @Test
    fun `skipping something is sayable, and is not the same as leaving it blank`() {
        // `skipped` was in the schema, in the patch and in the importer
        // before this screen existed, with no way to set it — so a
        // movement deliberately skipped looked exactly like one not got
        // to, and the coach kept suggesting it for ever.
        show()
        compose.onAllNodesWithText("skip")[0].performClick()
        assertEquals(SessionPatch.Entry(skipped = true), edits.value[0])
        assertEquals("blank is still blank", null, edits.value[1])
        compose.onNodeWithText("1 skipped", substring = true).assertIsDisplayed()
        shoot("19-skipped")
    }

    @Test
    fun `skipping clears what was typed, and un-skipping does not put it back`() {
        // A weight against a set you did not do is the wrong number this
        // app exists to avoid, so it goes. Bringing it back on un-skip
        // would be this app inventing a number nobody re-entered.
        show()
        compose.onNodeWithContentDescription("bench press weight in pounds").performTextReplacement("45")
        assertEquals(45.0, edits.value[0]?.weight)
        compose.onAllNodesWithText("skip")[0].performClick()
        assertEquals(SessionPatch.Entry(skipped = true), edits.value[0])
        compose.onAllNodesWithText("skip")[0].performClick()
        assertEquals(SessionPatch.Entry(), edits.value[0])
    }

    @Test
    fun `a skipped row is not counted as filled`() {
        // It is answered, not filled. Counting it as filled would say the
        // form is further along than it is.
        show()
        compose.onNodeWithContentDescription("bench press weight in pounds").performTextReplacement("45")
        compose.onNodeWithText("1 of 10 filled", substring = true).assertIsDisplayed()
        compose.onAllNodesWithText("skip")[0].performClick()
        compose.onNodeWithText("0 of 10 filled", substring = true).assertIsDisplayed()
    }

    @Test
    fun `easy and hard are one question with two answers`() {
        // A set is not both. The coach reads them in opposite directions —
        // easy earns a bump early, hard blocks the next one — so holding
        // both at once would mean nothing.
        show()
        compose.onAllNodesWithText("easy")[0].performClick()
        assertEquals(SessionPatch.Entry(easy = true), edits.value[0])
        compose.onAllNodesWithText("hard")[0].performClick()
        assertEquals(SessionPatch.Entry(hard = true), edits.value[0])
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
        compose.onAllNodesWithText("hard")[0].performClick()
        assertEquals(SessionPatch.Entry(easier = true, hard = true), edits.value[0])
    }

    @Test
    fun `only a timed row is offered the way to say it just happened`() {
        // One chip, on one row shape. The weighted rows at the top of the
        // sheet have a number to give, so "done" there would be a second
        // way to say what the weight box already says -- and two ways to
        // record one thing is how the two disagree.
        show()
        // The sample's only timed row: 45 seconds of sit-up, no reps, no
        // weight. Both boxes are empty by definition. Scrolled to, because
        // a LazyColumn has not composed it yet -- which is also why this
        // counts "done" against "hurt" rather than asserting it absent at
        // the top: how far ahead the list composes is not this test's
        // claim, and "hurt" is on every row.
        reach("sit-up")
        val anyRow = compose.onAllNodesWithText("hurt").fetchSemanticsNodes().size
        assertTrue("expected several rows composed, saw $anyRow", anyRow > 1)
        compose.onAllNodesWithText("done").assertCountEquals(1)
        compose.onAllNodesWithText("done")[0].performClick()
        assertEquals(SessionPatch.Entry(done = true), edits.value[9])

        // And the tally counts it, because the row is no longer blank --
        // otherwise the form reads as unfinished with nothing left to do.
        compose.onNodeWithText("1 of 10 filled", substring = true).assertIsDisplayed()

        // Six chips do not fit on one 411dp line, and what slid off the
        // right edge was `skip` -- the one chip on the row that says a set
        // did not happen, on the one row shape that has no number to say it
        // with instead. So the chips wrap, and the proof is that this row's
        // `skip` sits on a line BELOW `done` rather than beside it.
        val done = compose.onAllNodesWithText("done")[0].getUnclippedBoundsInRoot()
        val skips = (0 until compose.onAllNodesWithText("skip").fetchSemanticsNodes().size)
            .map { compose.onAllNodesWithText("skip")[it].getUnclippedBoundsInRoot() }
        assertTrue(
            "no skip chip wrapped below done: $skips vs $done",
            skips.any { it.top >= done.bottom },
        )

        reach("+ Add something the sheet missed")
        shoot("22-timed-row")
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
