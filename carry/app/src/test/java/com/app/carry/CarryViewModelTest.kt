package com.app.carry

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
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
