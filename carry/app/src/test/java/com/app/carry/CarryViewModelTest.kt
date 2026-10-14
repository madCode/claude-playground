package com.app.carry

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CarryViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val app: Application get() = ApplicationProvider.getApplicationContext()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `a row from a file with no ids gets a real one on the way in`() = runTest(dispatcher) {
        // The stand-in `addedIn` hands back is the old index identity
        // under a new name: drop a blank row and the next row inherits
        // its id, its screen slot and the text box keyed to it. Nothing
        // in the patch promotes it, so if this does not, the whole fix
        // applies only to rows added on this phone -- which is not where
        // the bug was.
        val raw = Session.json.encodeToString(SampleSession.today)
            .replace("\"sets\"", "\"added\": [{\"name\": \"plank\", \"reps\": 60}], \"sets\"")
        val model = CarryViewModel(app, SavedStateHandle())
        model.read = { CarryState.Ready(SampleSession.today, raw, "doc") }
        model.refresh()
        advanceUntilIdle()

        // The seed runs through Dispatchers.Default, which the test
        // dispatcher does not drive, so wait for the condition rather
        // than for a number of ticks.
        withContext(Dispatchers.Default) {
            withTimeout(5_000) { while (model.added.value.isEmpty()) delay(5) }
        }
        val row = model.added.value.single()
        assertEquals("plank", row.name)
        assertFalse("still a stand-in: ${row.id}", SessionPatch.isStandIn(row.id))
    }

    @Test
    fun `a folder that never answers ends in a state with a way out`() = runTest(dispatcher) {
        // openInputStream has no timeout and runCatching never fires on a
        // wedged provider, so without this the owner sits on "Reading" with
        // no button at all and force-quitting is the only exit.
        val model = CarryViewModel(app, SavedStateHandle())
        model.read = { awaitCancellation() }
        model.refresh()
        advanceUntilIdle()
        assertEquals(CarryState.Stalled, model.state.value)
    }

    @Test
    fun `a refresh that comes back empty leaves the sheet alone`() = runTest(dispatcher) {
        val model = CarryViewModel(app, SavedStateHandle())
        model.read = { readyFor() }
        model.refresh()
        advanceUntilIdle()
        assertEquals(readyFor(), model.state.value)

        // Syncthing replaces the file; the next read lands mid-write.
        model.read = { CarryState.Unreadable("session-2026-10-17-2G.json", wrongSchema = false) }
        model.refresh()
        advanceUntilIdle()
        assertEquals(
            "a sheet must not vanish while it is being looked at",
            readyFor(), model.state.value,
        )
    }

    @Test
    fun `a save re-reads the folder, once the button already says Saved`() = runTest(dispatcher) {
        // The Since tab is built from the folder, and only a resume used to
        // recompute it. So the owner could save the day's log, open that
        // day from the list and read back the rows they had just filled in
        // as blank -- under the words "what you saved that day".
        //
        // Through the save that writes NOTHING, which is the one path here
        // that reaches "Saved" without a real folder behind it. The file
        // already holding these numbers means something else wrote it, so
        // the history is just as stale.
        // Both halves in one test, and so one wait rather than two: the
        // save finishes on `Dispatchers.Default`, which the test dispatcher
        // does not drive, and waiting on that is the shape of this class's
        // one known intermittent failure.
        val model = CarryViewModel(app, SavedStateHandle())
        model.read = { readyFor() }
        var reads = 0
        var sayingWhenRead: SaveState? = null
        model.readSince = { reads++; sayingWhenRead = model.saving.value; Growth.of(emptyList()) }
        model.refresh()
        advanceUntilIdle()
        val onOpening = reads

        model.save()
        awaitSaved(model, "the save never finished")
        advanceUntilIdle()

        assertTrue(
            "the Since tab would still be showing the folder as it was before the save",
            reads > onOpening,
        )
        // That read is one provider call per file over a year of sessions,
        // against a provider that can block for as long as it likes. Done
        // before the state flips, the button goes on saying "Saving..."
        // over a file that is already written, with the two-taps-in-one-
        // frame guard refusing another try.
        assertEquals(
            "the folder read has to come after the button stops saying \"Saving…\"",
            SaveState.Saved, sayingWhenRead,
        )
    }


    /** The patch runs on `Dispatchers.Default`, which the test dispatcher
     *  does not drive — so this waits for the condition rather than for a
     *  number of ticks. */
    private suspend fun awaitSaved(model: CarryViewModel, why: String) {
        val reached = runCatching {
            withContext(Dispatchers.Default) {
                withTimeout(20_000) { while (model.saving.value != SaveState.Saved) delay(5) }
            }
        }.isSuccess
        assertTrue(why, reached)
    }

    @Test
    fun `a stale answer cannot overwrite a newer one`() = runTest(dispatcher) {
        // Two resumes in quick succession (unlock then rotate). Cancelling
        // does not stop the blocking read underneath -- it only discards the
        // result -- and discarding it is the point: an older answer landing
        // after a newer one would put an older sheet on screen.
        val model = CarryViewModel(app, SavedStateHandle())
        model.read = { awaitCancellation() }
        model.refresh()
        model.read = { readyFor() }
        model.refresh()
        advanceUntilIdle()
        assertEquals(readyFor(), model.state.value)
    }

    private val sheet = """
        {"schema":"gym-session/1","sheet_id":"2026-10-17:2G","date":"2026-10-17","class":"2G",
         "sets":[{"ref":0,"line":"10 x deadlift","canonical":"deadlift","weight":null,"reps":null},
                 {"ref":1,"line":"10 x bench press","canonical":"bench press","weight":null,"reps":null}],
         "added":[]}
    """.trimIndent()

    private fun ready(raw: String = sheet, id: String = "doc") =
        CarryState.Ready(Session.json.decodeFromString(raw), raw, id)

    @Test
    fun `a half-filled form survives the process dying`() = runTest(dispatcher) {
        // The likeliest real loss in the app: ten rows filled in the car,
        // switch to Spotify, Android reclaims the process.
        val handle = SavedStateHandle()
        val first = CarryViewModel(app, handle)
        first.read = { ready() }
        first.refresh()
        advanceUntilIdle()
        first.edit(0, SessionPatch.Entry(weight = 95.0, reps = 8))

        // Same SavedStateHandle, brand new ViewModel: that is what process
        // death looks like from in here.
        val reborn = CarryViewModel(app, handle)
        reborn.read = { ready() }
        reborn.refresh()
        advanceUntilIdle()
        assertEquals(SessionPatch.Entry(weight = 95.0, reps = 8), reborn.edits.value[0])
    }

    @Test
    fun `the same session rewritten keeps what was typed`() = runTest(dispatcher) {
        // The coach re-runs and the file is replaced underneath. The form
        // used to go blank with no prompt. (A file for a DIFFERENT session
        // is a separate case entirely -- see the test above.)
        val model = CarryViewModel(app, SavedStateHandle())
        model.read = { ready() }
        model.refresh(); advanceUntilIdle()
        model.edit(0, SessionPatch.Entry(weight = 95.0, reps = 8))

        model.read = { ready(sheet.replace("10 x deadlift", "12 x deadlift")) }
        model.refresh(); advanceUntilIdle()
        assertEquals(SessionPatch.Entry(weight = 95.0, reps = 8), model.edits.value[0])
        assertTrue("the owner has to be told", model.fileMovedUnderEdits.value)
    }

    @Test
    fun `another day's numbers are never shown against this session`() = runTest(dispatcher) {
        // The worst thing the first version did. Saturday's unsaved weights
        // would load onto Tuesday's sheet, and if the template repeated an
        // exercise at the same index they would be WRITTEN into it.
        val handle = SavedStateHandle()
        val saturday = CarryViewModel(app, handle)
        saturday.read = { ready() }
        saturday.refresh(); advanceUntilIdle()
        saturday.edit(0, SessionPatch.Entry(weight = 95.0, reps = 8))

        val tuesday = CarryViewModel(app, handle)
        tuesday.read = { ready(sheet.replace("2026-10-17:2G", "2026-10-20:2G"), id = "tue") }
        tuesday.refresh(); advanceUntilIdle()
        assertEquals("a draft belongs to one session", emptyMap<Int, SessionPatch.Entry>(), tuesday.edits.value)
        assertFalse(tuesday.fileMovedUnderEdits.value)
    }

    @Test
    fun `there is a way out of a session that was rewritten`() = runTest(dispatcher) {
        // Otherwise the warning is permanent: the save refuses, every
        // refresh re-raises it, and the only exits destroy the numbers.
        val model = CarryViewModel(app, SavedStateHandle())
        model.read = { ready() }
        model.refresh(); advanceUntilIdle()
        model.edit(0, SessionPatch.Entry(weight = 95.0, reps = 8))

        val rewritten = sheet.replace("10 x deadlift", "12 x deadlift")
        model.read = { ready(rewritten) }
        model.refresh(); advanceUntilIdle()
        assertTrue(model.fileMovedUnderEdits.value)

        model.discardEdits()
        assertFalse(model.fileMovedUnderEdits.value)
        assertEquals(emptyMap<Int, SessionPatch.Entry>(), model.edits.value)
    }

    @Test
    fun `a renumbered file refuses the save instead of writing to the wrong exercise`() = runTest(dispatcher) {
        // ref is only an index. A re-run that renumbers rows turns an edit
        // meant for a deadlift into one against a bench press, and the
        // patcher cannot see it -- it only refuses refs that have GONE.
        val model = CarryViewModel(app, SavedStateHandle())
        model.read = { ready() }
        model.refresh(); advanceUntilIdle()
        model.edit(0, SessionPatch.Entry(weight = 95.0, reps = 8))

        val swapped = sheet.replace("\"ref\":0", "\"ref\":9").replace("\"ref\":1", "\"ref\":0")
        model.read = { ready(swapped) }
        model.refresh(); advanceUntilIdle()
        model.save(); advanceUntilIdle()

        val state = model.saving.value
        assertTrue("expected a refusal, got $state", state is SaveState.Failed)
        assertTrue((state as SaveState.Failed).why.contains("no longer line up"))
    }

    @Test
    fun `the draft does not carry the file around with it`() = runTest(dispatcher) {
        // It used to hold the entire document, JSON-escaped, rewritten on
        // every keystroke and carried through saved instance state -- which
        // past about half a megabyte is a TransactionTooLargeException that
        // destroys exactly the typing the draft exists to protect.
        val handle = SavedStateHandle()
        val model = CarryViewModel(app, handle)
        model.read = { ready() }
        model.refresh(); advanceUntilIdle()
        model.edit(0, SessionPatch.Entry(weight = 95.0, reps = 8))

        val stored = handle.get<String>("draft")!!
        // It keeps the row identities -- that is what a renumbering is
        // detected against -- but not the document: no schema, no
        // suggestions, no fields it never compares.
        assertFalse("the document must not be in here", stored.contains("\"schema\""))
        assertFalse(stored.contains("suggested_weight"))
        assertTrue("the identities must be", stored.contains("deadlift"))
        // Not asserting "smaller than the file": on a two-row fixture the
        // JSON escaping dominates and the claim is false while the design
        // is right. What is saved is the FIELDS not kept, which is what
        // the two assertions above pin — on a real sheet those are most of
        // the document.
    }

    @Test
    fun `the folder can be handed back, and that clears everything with it`() {
        // A recursive grant that survives reboots needs a way out that is
        // not "uninstall the app".
        val model = CarryViewModel(app, SavedStateHandle())
        model.folder.remember(android.net.Uri.parse("content://x/tree/primary%3ASync%2Fgym"))
        model.forgetFolder()
        assertEquals(null, model.folder.uri)
        assertEquals(emptyMap<Int, SessionPatch.Entry>(), model.edits.value)
        assertEquals(CarryState.NeedsFolder(), model.state.value)
    }

    @Test
    fun `picking the whole phone is reported rather than held`() {
        val model = CarryViewModel(app, SavedStateHandle())
        model.rejectedTooWide()
        val state = model.state.value
        assertTrue(state is CarryState.NeedsFolder && state.tooWide)
    }

    @Test
    fun `the folder outlives the activity that asked for it`() {
        // Held against the Application, so an abandoned read cannot keep a
        // destroyed Activity alive while a blocked provider finishes.
        val model = CarryViewModel(app, SavedStateHandle())
        assertEquals(false, model.folder.chosen)
    }
}
