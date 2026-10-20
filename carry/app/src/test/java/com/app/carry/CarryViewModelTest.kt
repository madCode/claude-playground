package com.app.carry

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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

    // Every ViewModel a test builds, so teardown can reach all of them. Two
    // of these tests build two, and a single field would hold only the
    // second -- leaving the first's scope running into the next test, which
    // is the race this registry exists to close.
    private val models = mutableListOf<CarryViewModel>()

    private fun viewModel(handle: SavedStateHandle = SavedStateHandle()) =
        CarryViewModel(app, handle).also { models += it }

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    /**
     * Nothing from one test may still be touching Main when the next one
     * sets it. A `viewModelScope` job outlives the test that launched it --
     * two tests park one on `awaitCancellation` for ever -- and nothing
     * else clears these: there is no ViewModelStore in a unit test, so
     * `onCleared` never runs.
     *
     * Cancelled is not finished, which is why the cancel alone was not
     * enough: `refresh` ends by resuming from `Dispatchers.IO`, and that
     * resumption READS `Dispatchers.Main` from an IO thread. The blocking
     * read it is parked in cannot be interrupted, so cancelling only stops
     * the resumption's effect, not the dispatch -- and a dispatch landing
     * while `resetMain` or the next `setMain` writes is
     * "Dispatchers.Main is used concurrently with setting it", measured at
     * one run in thirty with the cancel but without the wait below.
     * Waiting for the job to finish puts that last dispatch before
     * `resetMain` rather than racing it.
     */
    @After fun tearDown() {
        val jobs = models.map { it.viewModelScope.coroutineContext.job }
        jobs.forEach { it.cancel() }
        val deadline = System.currentTimeMillis() + 10_000
        while (jobs.any { !it.isCompleted }) {
            // A cancelled continuation still has to run, and `runTest` has
            // stopped driving the dispatcher by now, so drive it here.
            dispatcher.scheduler.advanceUntilIdle()
            // Loud rather than silent: a job that cannot be drained is the
            // bug this teardown exists for, and leaving it to corrupt a
            // later test is how it stayed unexplained for so long.
            check(System.currentTimeMillis() < deadline) {
                "a viewModelScope job outlived its test"
            }
            Thread.sleep(1)
        }
        Dispatchers.resetMain()
    }

    @Test
    fun `every view model in this file is registered for teardown`() {
        // Not a behaviour test: a guard on the one mistake this file's
        // teardown cannot survive. A model built with the constructor
        // directly is never cancelled, so its coroutines run on into the
        // next test -- which is this class's documented intermittent
        // failure, and exactly what one test did until 2026-10-16.
        //
        // Read off the source because there is nothing at runtime to ask:
        // an unregistered model is invisible by definition.
        val src = java.io.File("src/test/java/com/app/carry/CarryViewModelTest.kt")
            .takeIf { it.exists() }
            ?: java.io.File("app/src/test/java/com/app/carry/CarryViewModelTest.kt")
        val built = src.readLines().withIndex().filter { (_, l) ->
            l.contains("CarryViewModel(app") && !l.contains("models +=")
        }
        assertTrue(
            "build it with viewModel(), not the constructor: ${built.map { it.index + 1 }}",
            built.isEmpty(),
        )
    }

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
        val model = viewModel()
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
    fun `a row with no box on screen is not seeded as one that has been filled`() = runTest(dispatcher) {
        // `entriesIn` reads the RAW json, so it finds every ref in the
        // file -- including a row this build could not decode and so does
        // not show. Seeding those is wrong twice over: the header counts
        // them as filled, so an untouched form reads "1 of 1 filled" over
        // two empty boxes and the owner leaves thinking they logged it;
        // and the save's identity check tests them, so a rerun that
        // changed only an INVISIBLE row refuses a save they can do
        // nothing about.
        val raw = """{"schema":"gym-session/1","sheet_id":"s","date":"2026-10-17","sets":[
            {"ref":0,"line":"10 x squat","canonical":"squat","weight":45,"reps":10},
            {"ref":1,"canonical":"row","key":"row","weight":60,"reps":8}
        ]}"""
        val session = SessionFiles.parse(raw)!!
        assertEquals("the premise: row 1 has no `line` and does not decode", 1, session.sets.size)

        val model = viewModel()
        model.read = { CarryState.Ready(session, raw, "doc", unreadable = 1) }
        model.refresh()
        advanceUntilIdle()
        // The visible row carries a number too, so the seed has a NON-EMPTY
        // answer to wait for. Waiting a fixed time for an empty one would
        // pass just as happily before the seed had run at all.
        // The seed runs through Dispatchers.Default, which the test
        // dispatcher does not drive, so wait for the condition.
        withContext(Dispatchers.Default) {
            withTimeout(5_000) { while (model.edits.value.isEmpty()) delay(5) }
        }
        assertEquals(mapOf(0 to SessionPatch.Entry(weight = 45.0, reps = 10)), model.edits.value)

        // The same three seeds, and the first fix caught only the first:
        // discarding showed the bug verbatim and the post-save re-seed
        // showed it worse, counting a row nobody can see as filled.
        model.discardEdits()
        assertEquals("discard re-seeded the invisible row", 1, model.edits.value.size)
        assertEquals(setOf(0), model.edits.value.keys)
    }

    @Test
    fun `a folder that never answers ends in a state with a way out`() = runTest(dispatcher) {
        // openInputStream has no timeout and runCatching never fires on a
        // wedged provider, so without this the owner sits on "Reading" with
        // no button at all and force-quitting is the only exit.
        val model = viewModel()
        model.read = { awaitCancellation() }
        model.refresh()
        advanceUntilIdle()
        assertEquals(CarryState.Stalled, model.state.value)
    }

    @Test
    fun `a refresh that comes back empty leaves the sheet alone`() = runTest(dispatcher) {
        val model = viewModel()
        model.read = { readyFor() }
        model.refresh()
        advanceUntilIdle()
        assertEquals(readyFor(), model.state.value)

        // Syncthing replaces the file; the next read lands mid-write.
        model.read = { CarryState.Unreadable("session-2026-10-17-2G.json", CarryState.Unreadable.Why.DAMAGED) }
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
        // Through `viewModel()`, which is what registers it for teardown.
        // This was the one model in the file built directly, so its
        // viewModelScope was never cancelled and its coroutines ran on
        // into whatever test came next -- the exact race the registry
        // above exists to close, opted out of by the test whose own
        // comment calls this class's intermittent failure by name.
        val model = viewModel()
        model.read = { readyFor() }
        var reads = 0
        var sayingWhenRead: SaveState? = null
        model.readSince = { reads++; sayingWhenRead = model.saving.value; Growth.of(emptyList()) }
        model.refresh()
        advanceUntilIdle()
        // Asserted, because `save()` returns at its first line when the
        // state is not Ready and the only evidence downstream is a wait
        // that times out twenty seconds later saying nothing. Reproduced
        // 2026-10-16: "the save never finished (saving was Idle)" --
        // Idle, not Saving, so the coroutine never started.
        assertTrue(
            "refresh did not leave the screen Ready, so save() will do nothing: " +
                "${model.state.value}",
            model.state.value is CarryState.Ready,
        )
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
        // Naming the state it actually saw, because this wait is the one
        // that has timed out in CI and "the save never finished" says
        // nothing a later reader can act on -- Saving means the coroutine
        // never resumed, Idle means `save()` returned before launching,
        // Failed means it got an answer nobody expected.
        assertTrue("$why (saving was ${model.saving.value})", reached)
    }

    @Test
    fun `a stale answer cannot overwrite a newer one`() = runTest(dispatcher) {
        // Two resumes in quick succession (unlock then rotate). Cancelling
        // does not stop the blocking read underneath -- it only discards the
        // result -- and discarding it is the point: an older answer landing
        // after a newer one would put an older sheet on screen.
        val model = viewModel()
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

    /**
     * A save driven all the way through the write.
     *
     * Everything below `folder.save(...)` -- the three outcomes, the
     * "nothing newer arrived" guard, the draft clear and the re-seed from
     * what was written -- had never executed in any test: the two existing
     * `save()` calls here reach a refusal and an "already recorded", both
     * of which return before the write. Found by reading Kover's line
     * report for CarryViewModel.kt, 2026-10-16.
     */
    /**
     * Waits for a refresh to be completely finished, seed included.
     *
     * `advanceUntilIdle` is not enough: `reconcile` seeds through
     * `Dispatchers.Default`, which the test dispatcher does not drive, so
     * the seed can land AFTER a test's first `edit()` and overwrite it --
     * which is what made the four save tests below reach "already
     * recorded" with nothing to write (2026-10-16).
     */
    private suspend fun settled(model: CarryViewModel) {
        val scope = model.viewModelScope.coroutineContext.job
        val done = runCatching {
            withContext(Dispatchers.Default) {
                withTimeout(20_000) {
                    // Waits only. `runTest` drains the scheduler while the
                    // body is suspended here; advancing it from this thread
                    // as well ran two "Main" tasks at once, which made a
                    // save test fail now and then.
                    while (scope.children.any { it.isActive }) delay(5)
                }
            }
        }.isSuccess
        // Named, because a bare TimeoutCancellationException here also
        // trips tearDown's own check and the pair reads like two unrelated
        // failures.
        assertTrue("a coroutine never finished; the save or the refresh is stuck", done)
    }

    private suspend fun saved(
        model: CarryViewModel, outcome: Saved = Saved.YES,
        onWrite: (String) -> Unit = {},
    ): String? {
        var written: String? = null
        // The disk holds what the page read, as it does unless something
        // else wrote it; the tests of that case set their own.
        model.readNow = { (model.state.value as? CarryState.Ready)?.raw }
        model.write = { _, text -> written = text; onWrite(text); outcome }
        model.save()
        // Settled, not "no longer Saving": a refresh landing during the
        // write flips the state to Idle on its way past, and a wait that
        // watched only for that would read the answer before the write's
        // own result had been applied.
        settled(model)
        // `save()` returns without launching on several paths and
        // `settled()` then comes straight back, so a test can assert
        // happily against a save that never happened -- which is exactly
        // what these four did on their first run.
        assertTrue("the write never ran; save() returned before it", written != null)
        return written
    }

    @Test
    fun `what was written is what the form then shows`() = runTest(dispatcher) {
        // The re-seed. Without it the next refresh reads a state captured
        // before the save and blanks the form the owner just filled in.
        val handle = SavedStateHandle()
        val model = viewModel(handle)
        model.read = { ready() }
        model.refresh(); settled(model)
        model.edit(0, SessionPatch.Entry(weight = 95.0, reps = 8))
        assertTrue("a draft is expected before the save", handle.keys().isNotEmpty())

        val written = saved(model)
        advanceUntilIdle()

        assertEquals(SaveState.Saved, model.saving.value)
        assertTrue("the numbers must be in what went to the folder", written!!.contains("95"))
        assertEquals(
            "the screen must hold the bytes that were written, not the ones read before",
            written, (model.state.value as CarryState.Ready).raw,
        )
        assertEquals(SessionPatch.Entry(weight = 95.0, reps = 8), model.edits.value[0])
        assertTrue("the draft is spent once it is on disk", handle.keys().isEmpty())
        // The re-seed one line further on is pinned separately, by
        // `a save does not re-seed the rows the screen never showed`:
        // nothing HERE can tell it from its absence, because what was
        // typed and what comes back out of the written file are the same
        // map. A file with a row that does not decode makes them differ.
    }

    @Test
    fun `a refused write says the file is unchanged, and keeps the draft`() = runTest(dispatcher) {
        // "Try again" is only honest while the numbers are still somewhere,
        // and the draft is the only place they are.
        val handle = SavedStateHandle()
        val model = viewModel(handle)
        model.read = { ready() }
        model.refresh(); settled(model)
        model.edit(0, SessionPatch.Entry(weight = 95.0, reps = 8))

        saved(model, Saved.NO_FILE_INTACT)
        advanceUntilIdle()

        val state = model.saving.value
        assertTrue("expected a failure, got $state", state is SaveState.Failed)
        assertFalse("an intact file is not a damaged one", (state as SaveState.Failed).damaged)
        assertTrue(state.why, state.why.contains("unchanged"))
        assertTrue("the numbers must survive a refusal", handle.keys().isNotEmpty())
        assertEquals(SessionPatch.Entry(weight = 95.0, reps = 8), model.edits.value[0])
    }

    @Test
    fun `a damaged file is reported as damaged, not as a retry`() = runTest(dispatcher) {
        // Different words for different things to do: one of these means
        // "press it again", the other means "go and look at the file
        // before it syncs".
        val handle = SavedStateHandle()
        val model = viewModel(handle)
        model.read = { ready() }
        model.refresh(); settled(model)
        model.edit(0, SessionPatch.Entry(weight = 95.0, reps = 8))

        saved(model, Saved.NO_FILE_DAMAGED)
        advanceUntilIdle()

        val state = model.saving.value as SaveState.Failed
        assertTrue("the owner has to be told to look at the file", state.damaged)
        assertTrue(state.why, state.why.contains("may be damaged"))
        // This is where the draft matters most: the file on disk may need
        // rescuing before a retry is possible, and what the owner typed is
        // now the only copy of something nothing can re-derive.
        assertTrue("the numbers must survive a damaged file", handle.keys().isNotEmpty())
    }

    @Test
    fun `a row with a weight and no name is refused, and the typing kept`() = runTest(dispatcher) {
        // The branch CLAUDE.md names: this row used to be filtered out at
        // the save, so a weight typed into a row the owner had not named
        // yet was dropped silently under a "Saved" message with no way to
        // tell afterwards. The patch refuses instead -- and a refusal must
        // not take the numbers with it, because the row IS on screen and
        // naming it is one tap away.
        //
        // Reached without the write seam at all: this returns before the
        // folder is touched, which is why it stayed at 0% while the rest
        // of the path was being covered.
        val handle = SavedStateHandle()
        val model = viewModel(handle)
        model.read = { ready() }
        model.refresh(); settled(model)
        model.addRow()
        model.editAdded(0, model.added.value[0].copy(weight = 20.0))

        var wrote = 0
        model.write = { _, _ -> wrote++; Saved.YES }
        model.save()
        settled(model)

        assertEquals("nothing may reach the folder", 0, wrote)
        val state = model.saving.value
        assertTrue("expected a refusal, got $state", state is SaveState.Failed)
        assertFalse("a refusal is not a damaged file", (state as SaveState.Failed).damaged)
        assertTrue(state.why, state.why.contains("an added row needs a name"))
        assertEquals("the row stays, so it can be named", 20.0, model.added.value[0].weight)
        assertTrue("and the typing is still recoverable", handle.keys().isNotEmpty())
    }

    @Test
    fun `Add on a still-empty last row adds nothing, and an empty row is never written`() = runTest(dispatcher) {
        // The screen sends the owner to the empty row instead; a second
        // blank row would be one more thing to find and leave behind.
        val model = viewModel()
        val disk = Disk(sheet)
        model.on(disk)
        model.refresh(); settled(model)
        model.addRow()
        model.addRow()
        assertEquals(1, model.added.value.size)
        model.editAdded(0, model.added.value[0].copy(name = "pop squat"))
        model.addRow()
        assertEquals(2, model.added.value.size)

        // The second row is left empty: the save goes through, and the file
        // holds the named row only.
        model.save(); settled(model)
        assertEquals(SaveState.Saved, model.saving.value)
        // Read raw: `addedIn` drops blank rows itself, so it would hide one.
        val written = Session.json.parseToJsonElement(disk.text!!).jsonObject["added"]!!.jsonArray
        assertEquals(listOf("pop squat"), written.map { it.jsonObject["name"]!!.jsonPrimitive.content })
    }

    @Test
    fun `a save does not write over a file replaced by one without our label`() = runTest(dispatcher) {
        // The patch is built from the bytes last read. A file replaced in
        // the meantime by an unlabelled one, or one from a newer coach,
        // must not be overwritten with this session.
        val model = viewModel()
        model.read = { ready() }
        model.refresh(); settled(model)
        model.addRow()
        model.editAdded(0, model.added.value[0].copy(name = "pop squat", weight = 20.0))

        var wrote = 0
        model.write = { _, _ -> wrote++; Saved.YES }
        model.readNow = { """{"sheet_id":"x","date":"2026-10-17","sets":[]}""" }
        model.save(); settled(model)
        assertEquals("nothing may reach the folder", 0, wrote)
        val state = model.saving.value
        assertTrue("expected a refusal, got $state", state is SaveState.Failed)
        assertTrue((state as SaveState.Failed).why, "\"schema\" label" in state.why)

        // The control: the same save over the file it was typed against goes through.
        model.readNow = { (model.state.value as CarryState.Ready).raw }
        model.save(); settled(model)
        assertEquals(1, wrote)
    }

    /** A folder of one file: what the page reads, what a save checks and what it writes. */
    private class Disk(var text: String?) { var writes = 0 }

    private fun CarryViewModel.on(disk: Disk) {
        read = { ready(disk.text!!) }
        readNow = { disk.text }
        write = { _, text -> disk.writes++; disk.text = text; Saved.YES }
    }

    @Test
    fun `a save over a file that changed, emptied or went is refused, with the typing kept and listed`() = runTest(dispatcher) {
        // madCode, 2026-10-17: refuse, keep the typing, offer the new file.
        for (onDisk in listOf(sheet.replace("10 x bench press", "12 x bench press"), "", null)) {
            val handle = SavedStateHandle()
            val model = viewModel(handle)
            val disk = Disk(sheet)
            model.on(disk)
            model.refresh(); settled(model)
            model.edit(0, SessionPatch.Entry(weight = 95.0, reps = 8))

            disk.text = onDisk
            model.save(); settled(model)

            assertEquals("nothing may reach the folder for $onDisk", 0, disk.writes)
            val state = model.saving.value as SaveState.Failed
            assertEquals("This session file changed since you opened it, so nothing was saved.", state.why)
            assertEquals(listOf("deadlift: 95 lb, 8 reps"), state.typed)
            assertEquals("a file to load only when one reads", onDisk == sheet.replace("10 x bench press", "12 x bench press"), state.newFile)
            assertEquals(SessionPatch.Entry(weight = 95.0, reps = 8), model.edits.value[0])
            assertTrue("the typing is still recoverable", handle.keys().isNotEmpty())
        }
    }

    @Test
    fun `Carry's own save is the file the next save checks against`() = runTest(dispatcher) {
        val model = viewModel()
        val disk = Disk(sheet)
        model.on(disk)
        model.refresh(); settled(model)

        model.edit(0, SessionPatch.Entry(weight = 95.0, reps = 8))
        model.save(); settled(model)
        assertEquals(SaveState.Saved, model.saving.value)

        model.edit(1, SessionPatch.Entry(weight = 45.0, reps = 10))
        model.save(); settled(model)
        assertEquals("the second save must not see the first as a change", SaveState.Saved, model.saving.value)
        assertEquals(2, disk.writes)
    }

    @Test
    fun `a file that only gained a BOM, CRLF line endings or a reformat saves`() = runTest(dispatcher) {
        // Through the real SessionFolder.read, so what is compared is what
        // the provider's bytes decode to.
        val tree = android.net.Uri.parse("content://com.android.externalstorage.documents/tree/primary%3Agym")
        val doc = android.provider.DocumentsContract.buildDocumentUriUsingTree(tree, "doc")
        // Reordered keys, and compact rather than indented: the JSON branch.
        val obj = Session.json.parseToJsonElement(sheet) as kotlinx.serialization.json.JsonObject
        val reordered = kotlinx.serialization.json.JsonObject(obj.entries.reversed().associate { it.key to it.value }).toString()
        assertTrue("the fixture must differ as text", reordered != sheet && !reordered.contains("\n"))
        for (onDisk in listOf("\uFEFF" + sheet, sheet.replace("\n", "\r\n"), reordered)) {
            val model = viewModel()
            model.folder.remember(tree)
            org.robolectric.Shadows.shadowOf(app.contentResolver).registerInputStreamSupplier(doc) {
                java.io.ByteArrayInputStream(onDisk.toByteArray(Charsets.UTF_8))
            }
            assertEquals("the harness must reach the real read", onDisk, model.folder.read("doc"))
            model.read = { ready() }
            model.refresh(); settled(model)
            model.edit(0, SessionPatch.Entry(weight = 95.0, reps = 8))

            var wrote = 0
            model.write = { _, _ -> wrote++; Saved.YES }
            model.save(); settled(model)
            assertEquals("${model.saving.value}", SaveState.Saved, model.saving.value)
            assertEquals(1, wrote)
        }
    }

    @Test
    fun `after Carry's own damaged write, Try again repairs the file`() = runTest(dispatcher) {
        // The write truncated the file and couldn't put it back. That file
        // is Carry's own doing, so it is the baseline for Try again; taking
        // it for someone else's change blocked saving for good.
        val handle = SavedStateHandle()
        val model = viewModel(handle)
        val disk = Disk(sheet)
        model.on(disk)
        model.refresh(); settled(model)
        model.edit(0, SessionPatch.Entry(weight = 95.0, reps = 8))

        model.write = { _, text -> disk.writes++; disk.text = text.take(30); Saved.NO_FILE_DAMAGED }
        model.save(); settled(model)
        assertTrue((model.saving.value as SaveState.Failed).damaged)

        // Typing on doesn't hide the warning; the file is still broken.
        model.edit(1, SessionPatch.Entry(weight = 40.0, reps = 10))
        assertTrue("the damage warning stays", (model.saving.value as SaveState.Failed).damaged)

        model.write = { _, text -> disk.writes++; disk.text = text; Saved.YES }
        model.save(); settled(model)
        assertEquals(SaveState.Saved, model.saving.value)
        assertEquals(2, disk.writes)
        assertTrue(disk.text!!, "95" in disk.text!! && "40" in disk.text!!)
    }

    @Test
    fun `a damaged file changed again by someone else is refused, still saying it may be damaged`() = runTest(dispatcher) {
        val model = viewModel()
        val disk = Disk(sheet)
        model.on(disk)
        model.refresh(); settled(model)
        model.edit(0, SessionPatch.Entry(weight = 95.0, reps = 8))
        model.write = { _, text -> disk.writes++; disk.text = text.take(30); Saved.NO_FILE_DAMAGED }
        model.save(); settled(model)

        disk.text = sheet.replace("10 x bench press", "12 x bench press")
        model.save(); settled(model)
        assertEquals(1, disk.writes)
        val state = model.saving.value as SaveState.Failed
        assertTrue(state.damaged)
        assertTrue(state.why, "may be damaged" in state.why && "changed since you opened it" in state.why)
    }

    @Test
    fun `dropping the numbers after a refusal loads the new file`() = runTest(dispatcher) {
        val handle = SavedStateHandle()
        val model = viewModel(handle)
        val disk = Disk(sheet)
        model.on(disk)
        model.refresh(); settled(model)
        model.edit(0, SessionPatch.Entry(weight = 95.0, reps = 8))
        val rewritten = sheet.replace("10 x bench press", "12 x bench press")
        disk.text = rewritten
        model.save(); settled(model)

        model.discardEdits(); settled(model)
        assertEquals(rewritten, (model.state.value as CarryState.Ready).raw)
        assertEquals(emptyMap<Int, SessionPatch.Entry>(), model.edits.value)
        assertEquals(SaveState.Idle, model.saving.value)
        assertTrue(handle.keys().isEmpty())
        assertEquals(0, disk.writes)
    }

    @Test
    fun `two taps in one frame write once`() = runTest(dispatcher) {
        // The composable captures `enabled` before `save()` ever runs, so
        // the second tap used to start a second write against the same
        // document and the two could undo each other.
        val model = viewModel()
        model.read = { ready() }
        model.refresh(); settled(model)
        model.edit(0, SessionPatch.Entry(weight = 95.0, reps = 8))

        var wrote = 0
        model.readNow = { (model.state.value as CarryState.Ready).raw }
        model.write = { _, _ -> wrote++; model.save(); Saved.YES }
        model.save()
        settled(model)

        assertEquals("the second tap must find the first still in flight", 1, wrote)
    }

    @Test
    fun `a newer sheet that arrived during the write is not overwritten`() = runTest(dispatcher) {
        // The folder is shared with a syncing machine, so the runner can
        // replace the sheet while the owner is mid-save. Putting the text
        // we wrote on screen regardless would put the older session back
        // in front of them.
        val newer = sheet.replace("2026-10-17", "2026-10-18")
        val model = viewModel()
        model.read = { ready() }
        model.refresh(); settled(model)
        model.edit(0, SessionPatch.Entry(weight = 95.0, reps = 8))

        val written = saved(model) {
            // Arrives while the write is in flight.
            model.read = { ready(newer, id = "doc2") }
            model.refresh()
        }
        advanceUntilIdle()

        assertTrue("the write itself must still have happened", written!!.contains("95"))
        assertEquals(
            "the newer sheet must still be the one on screen",
            newer, (model.state.value as CarryState.Ready).raw,
        )
        // And the form must not still be holding the old session's numbers.
        // Everything after the write used to run whether or not the sheet
        // had changed under it, so `_edits` was re-seeded from the bytes we
        // had just written -- Saturday's 95 sitting against Tuesday's
        // deadlift, on a screen the owner reads in a lobby, with "Saved"
        // still tappable above it.
        //
        // NOT asserted: `saving`. Which of Idle and Saved lands depends on
        // whether the save's resumption or `reconcile`'s comes back from
        // its Dispatchers.Default hop last, and pinning one arm of that
        // made this test fail 4 times in 20 (review, 2026-10-16).
        assertEquals(
            "the old sheet's numbers must not be sitting on the new sheet's rows",
            SessionPatch.entriesIn(newer), model.edits.value,
        )
    }

    @Test
    fun `a half-filled form survives the process dying`() = runTest(dispatcher) {
        // The likeliest real loss in the app: ten rows filled in the car,
        // switch to Spotify, Android reclaims the process.
        val handle = SavedStateHandle()
        val first = viewModel(handle)
        first.read = { ready() }
        first.refresh()
        advanceUntilIdle()
        first.edit(0, SessionPatch.Entry(weight = 95.0, reps = 8))

        // Same SavedStateHandle, brand new ViewModel: that is what process
        // death looks like from in here.
        val reborn = viewModel(handle)
        reborn.read = { ready() }
        reborn.refresh()
        advanceUntilIdle()
        assertEquals(SessionPatch.Entry(weight = 95.0, reps = 8), reborn.edits.value[0])
    }

    @Test
    fun `a number the importer would refuse stops the save, naming every row`() = runTest(dispatcher) {
        // The importer refuses the whole file over one weight past 1000 or
        // one fractional rep count, so a save that let 2500 through lost
        // every other number typed that day, and the phone said "Saved".
        val handle = SavedStateHandle()
        val model = viewModel(handle)
        model.read = { ready() }
        model.refresh(); settled(model)
        model.edit(0, SessionPatch.Entry(weight = 2500.0, reps = 8))
        model.edit(1, SessionPatch.Entry(weight = 45.0, fractionalReps = 8.5))

        var wrote = 0
        model.write = { _, _ -> wrote++; Saved.YES }
        model.save()
        settled(model)

        assertEquals("nothing may reach the folder", 0, wrote)
        val state = model.saving.value as SaveState.Failed
        assertEquals(
            "2 rows need fixing: deadlift weight 2500 is over 1000; " +
                "bench press reps 8.5 isn't a whole number. Nothing was saved.",
            state.why,
        )
        assertEquals("not clamped, not dropped", 2500.0, model.edits.value[0]!!.weight)
        assertTrue("the typing is still recoverable", handle.keys().isNotEmpty())

        // Fixed, it goes.
        model.edit(0, SessionPatch.Entry(weight = 250.0, reps = 8))
        model.edit(1, SessionPatch.Entry(weight = 45.0, reps = 8))
        val written = saved(model)
        assertEquals(SaveState.Saved, model.saving.value)
        assertTrue(written!!, written.contains("250"))
    }

    @Test
    fun `a fractional rep count survives the process dying`() = runTest(dispatcher) {
        // Held as its own field so the box and the draft agree. Restored
        // as null, the row would come back blank and the save would go.
        val handle = SavedStateHandle()
        val first = viewModel(handle)
        first.read = { ready() }
        first.refresh(); settled(first)
        first.edit(0, SessionPatch.Entry(fractionalReps = 8.5))

        val reborn = viewModel(handle)
        reborn.read = { ready() }
        reborn.refresh(); settled(reborn)
        assertEquals(SessionPatch.Entry(fractionalReps = 8.5), reborn.edits.value[0])
    }

    @Test
    fun `the same session rewritten keeps what was typed`() = runTest(dispatcher) {
        // The coach re-runs and the file is replaced underneath. The form
        // used to go blank with no prompt. (A file for a DIFFERENT session
        // is a separate case entirely -- see the test above.)
        val model = viewModel()
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
        val saturday = viewModel(handle)
        saturday.read = { ready() }
        saturday.refresh(); advanceUntilIdle()
        saturday.edit(0, SessionPatch.Entry(weight = 95.0, reps = 8))

        val tuesday = viewModel(handle)
        tuesday.read = { ready(sheet.replace("2026-10-17:2G", "2026-10-20:2G"), id = "tue") }
        tuesday.refresh(); advanceUntilIdle()
        assertEquals("a draft belongs to one session", emptyMap<Int, SessionPatch.Entry>(), tuesday.edits.value)
        assertFalse(tuesday.fileMovedUnderEdits.value)
    }

    @Test
    fun `there is a way out of a session that was rewritten`() = runTest(dispatcher) {
        // Otherwise the warning is permanent: the save refuses, every
        // refresh re-raises it, and the only exits destroy the numbers.
        val model = viewModel()
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
        val model = viewModel()
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
        val model = viewModel(handle)
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
        val model = viewModel()
        model.folder.remember(android.net.Uri.parse("content://x/tree/primary%3ASync%2Fgym"))
        model.forgetFolder()
        assertEquals(null, model.folder.uri)
        assertEquals(emptyMap<Int, SessionPatch.Entry>(), model.edits.value)
        assertEquals(CarryState.NeedsFolder(), model.state.value)
    }

    @Test
    fun `picking the whole phone is reported rather than held`() {
        val model = viewModel()
        model.rejectedTooWide()
        val state = model.state.value
        assertTrue(state is CarryState.NeedsFolder && state.tooWide)
    }

    @Test
    fun `the whole-phone refusal outlasts the read right after the pick, and only that one`() = runTest(dispatcher) {
        // The Activity resumes after every pick, and that resume re-reads a
        // folder that was never taken.
        val model = viewModel()
        model.read = { CarryState.NeedsFolder() }
        model.rejectedTooWide()
        model.refresh()
        advanceUntilIdle()
        assertTrue("the refusal was replaced: ${model.state.value}", (model.state.value as? CarryState.NeedsFolder)?.tooWide == true)
        // The next return to the app says what is true now.
        model.refresh()
        advanceUntilIdle()
        assertEquals(CarryState.NeedsFolder(), model.state.value)
    }

    @Test
    fun `the folder outlives the activity that asked for it`() {
        // Held against the Application, so an abandoned read cannot keep a
        // destroyed Activity alive while a blocked provider finishes.
        val model = viewModel()
        assertEquals(false, model.folder.chosen)
    }
    @Test
    fun `a save does not re-seed the rows the screen never showed`() = runTest(dispatcher) {
        // The worst of the three seeds, and the one with no fixture until
        // now: after a successful save the form is re-seeded from what was
        // WRITTEN -- which is the whole file, dropped rows included. The
        // header then counts a row nobody can see as filled, so a sheet of
        // one visible row reads "2 of 1 filled" the moment the owner saves
        // it. The note in `what was written is what the form then shows`
        // says no fixture made the two differ; this is that fixture.
        val raw = """{"schema":"gym-session/1","sheet_id":"s","date":"2026-10-17","sets":[
            {"ref":0,"line":"10 x squat","canonical":"squat"},
            {"ref":1,"canonical":"row","key":"row","weight":60,"reps":8}
        ]}"""
        val session = SessionFiles.parse(raw)!!
        assertEquals("the premise: row 1 does not decode", 1, session.sets.size)

        val model = viewModel()
        model.read = { CarryState.Ready(session, raw, "doc", unreadable = 1) }
        model.refresh(); settled(model)
        model.edit(0, SessionPatch.Entry(weight = 45.0, reps = 10))

        val written = saved(model)
        advanceUntilIdle(); settled(model)
        assertTrue("row 1 must still be in the file", written!!.contains("\"weight\": 60"))
        assertEquals("the invisible row came back in the re-seed", setOf(0), model.edits.value.keys)
    }

    @Test
    fun `typing that is not saved yet holds back an update, and saving or discarding releases it`() = runTest(dispatcher) {
        // Installing replaces the running app, so an update started over typed numbers loses them.
        val model = viewModel()
        model.read = { ready() }
        model.refresh(); settled(model)
        assertFalse(model.unsaved)
        model.edit(0, SessionPatch.Entry(weight = 95.0, reps = 8))
        assertTrue(model.unsaved)
        model.discardEdits()
        assertFalse(model.unsaved)
    }

    private suspend fun kotlinx.coroutines.test.TestScope.until(what: () -> Boolean) =
        withContext(Dispatchers.Default) {
            withTimeout(5_000) { while (!what()) delay(5) }
        }

    /** Asks whether there's unsaved typing only once [typed] lets it, as the real one does after
     *  the download; the debug build's own tests cover the rest (LiveUpdatesTest). */
    private class GatedUpdater(val typed: kotlinx.coroutines.CompletableDeferred<Unit>) : SelfUpdater by SelfUpdater.NONE {
        val asked = kotlinx.coroutines.CompletableDeferred<Unit>()
        var handedOver = false
        override suspend fun install(build: Int, unsaved: () -> Boolean): UpdateProblem? {
            asked.complete(Unit)
            withContext(Dispatchers.Default) { typed.await() }
            if (unsaved()) return UpdateProblem("save first", offerLink = false)
            handedOver = true
            return null
        }
    }

    @Test
    fun `typing during the download stops the install, and the typing stays`() = runTest(dispatcher) {
        // The tap found nothing typed; the owner then typed while the build downloaded. Handing
        // it to Android now would replace the app under those numbers.
        val model = viewModel()
        model.read = { ready() }
        model.refresh(); settled(model)
        val typed = kotlinx.coroutines.CompletableDeferred<Unit>()
        val updater = GatedUpdater(typed)
        model.updater = updater
        model.installUpdate(140)
        until { updater.asked.isCompleted }
        model.edit(0, SessionPatch.Entry(weight = 95.0, reps = 8))
        typed.complete(Unit)
        until { model.updateProblem.value != null }
        assertFalse("nothing may be installed over typing", updater.handedOver)
        assertTrue("the typing must still be there", model.unsaved)
    }

    @Test
    fun `with nothing typed the build is handed over and nothing is said`() = runTest(dispatcher) {
        val model = viewModel()
        model.read = { ready() }
        model.refresh(); settled(model)
        val updater = GatedUpdater(kotlinx.coroutines.CompletableDeferred(Unit))
        model.updater = updater
        model.installUpdate(140)
        until { updater.handedOver }
        assertEquals(null, model.updateProblem.value)
    }

    @Test
    fun `a draft left from another session does not hold back an update`() = runTest(dispatcher) {
        val handle = SavedStateHandle()
        val model = viewModel(handle)
        model.read = { ready() }
        model.refresh(); settled(model)
        model.edit(0, SessionPatch.Entry(weight = 95.0, reps = 8))
        val draft = handle.get<String>(CarryViewModel.DRAFT)!!
        handle[CarryViewModel.DRAFT] = draft.replace(Regex("\"sheetId\":\\s*\"[^\"]*\""), "\"sheetId\": \"another-day\"")
        assertTrue("the draft must now name another session", handle.get<String>(CarryViewModel.DRAFT)!!.contains("another-day"))
        assertFalse(model.unsaved)
    }

    private fun sheetOn(date: String) = """
        {"schema":"gym-session/1","sheet_id":"$date:2G","date":"$date","class":"2G",
         "sets":[{"ref":0,"line":"10 x deadlift","canonical":"deadlift"}]}
    """.trimIndent()

    /** Two sheets, the newest listed first, whose writes land back in [files]. */
    private class TwoSheets : Folder {
        val files = mutableMapOf("new" to "", "old" to "")
        override val label: String? = "Sync/gym"
        override val chosen = true
        override val grantLost = false
        private val names = mapOf("new" to "session-2026-10-17-2G.json", "old" to "session-2026-10-10-2G.json")
        override fun list() = names.filterKeys { it in files }.toList()
        override fun read(documentId: String) = files[documentId]
        override fun write(documentId: String, text: String) = true.also { files[documentId] = text }
    }

    private fun finishingModel(folder: TwoSheets, handle: SavedStateHandle = SavedStateHandle()) =
        viewModel(handle).also { model ->
            folder.files["new"] = sheetOn("2026-10-17")
            folder.files["old"] = sheetOn("2026-10-10")
            model.read = { carryState(folder, model.finishing) }
            model.readNow = { folder.read(it) }
            model.write = { id, text -> folder.write(id, text); Saved.YES }
        }

    @Test
    fun `an older session can be finished, and the save lands in its own file`() = runTest(dispatcher) {
        val folder = TwoSheets()
        val model = finishingModel(folder)
        model.refresh(); settled(model)
        assertEquals("2026-10-17:2G", (model.state.value as CarryState.Ready).session.sheetId)

        assertTrue(model.finishOlder("2026-10-10:2G"))
        settled(model)
        val ready = model.state.value as CarryState.Ready
        assertEquals("2026-10-10:2G", ready.session.sheetId)
        assertTrue("every screen has to say it isn't the newest", ready.older)

        model.edit(0, SessionPatch.Entry(weight = 95.0, reps = 8))
        model.save(); settled(model)
        assertEquals(SaveState.Saved, model.saving.value)
        assertEquals(95.0, SessionPatch.entriesIn(folder.files["old"]!!)[0]?.weight)
        assertTrue("the newest sheet is untouched", SessionPatch.entriesIn(folder.files["new"]!!).isEmpty())

        assertTrue(model.backToNewest())
        settled(model)
        assertEquals("2026-10-17:2G", (model.state.value as CarryState.Ready).session.sheetId)
        assertFalse((model.state.value as CarryState.Ready).older)
    }

    @Test
    fun `unsaved typing stops another session being opened over it`() = runTest(dispatcher) {
        // There is one draft. Typing on the older sheet would replace the
        // newest sheet's unsaved numbers, so the switch is refused instead.
        val folder = TwoSheets()
        val model = finishingModel(folder)
        model.refresh(); settled(model)
        model.edit(0, SessionPatch.Entry(weight = 60.0))

        assertFalse(model.finishOlder("2026-10-10:2G"))
        settled(model)
        assertEquals("2026-10-17:2G", (model.state.value as CarryState.Ready).session.sheetId)
        assertEquals(60.0, model.edits.value[0]?.weight)
    }

    @Test
    fun `the session being finished survives the process dying, and goes when its file does`() = runTest(dispatcher) {
        val folder = TwoSheets()
        val handle = SavedStateHandle()
        finishingModel(folder, handle).apply { refresh(); settled(this); finishOlder("2026-10-10:2G"); settled(this) }

        val reborn = finishingModel(folder, handle)
        reborn.refresh(); settled(reborn)
        assertEquals("2026-10-10:2G", (reborn.state.value as CarryState.Ready).session.sheetId)

        // Part way through a sync's replace: still listed, not readable. The
        // sheet on screen and the pin both hold.
        folder.files["old"] = "{\"sche"
        reborn.refresh(); settled(reborn)
        assertEquals("2026-10-10:2G", (reborn.state.value as CarryState.Ready).session.sheetId)
        assertEquals("2026-10-10:2G", reborn.finishing)

        // Gone from the listing. The newest comes back, and the pin goes, so
        // the old day doesn't spring back open if the file returns.
        folder.files.remove("old")
        reborn.refresh(); settled(reborn)
        assertEquals("2026-10-17:2G", (reborn.state.value as CarryState.Ready).session.sheetId)
        assertEquals(null, reborn.finishing)
    }
}
