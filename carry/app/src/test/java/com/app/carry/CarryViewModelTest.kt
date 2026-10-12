package com.app.carry

import android.app.Application
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
        val model = CarryViewModel(app)
        model.read = { awaitCancellation() }
        model.refresh()
        advanceUntilIdle()
        assertEquals(CarryState.Stalled, model.state.value)
    }

    @Test
    fun `a refresh that comes back empty leaves the sheet alone`() = runTest(dispatcher) {
        val model = CarryViewModel(app)
        model.read = { CarryState.Ready(SampleSession.today) }
        model.refresh()
        advanceUntilIdle()
        assertEquals(CarryState.Ready(SampleSession.today), model.state.value)

        // Syncthing replaces the file; the next read lands mid-write.
        model.read = { CarryState.Unreadable("session-2026-10-17-2G.json", wrongSchema = false) }
        model.refresh()
        advanceUntilIdle()
        assertEquals(
            "a sheet must not vanish while it is being looked at",
            CarryState.Ready(SampleSession.today), model.state.value,
        )
    }

    @Test
    fun `a stale answer cannot overwrite a newer one`() = runTest(dispatcher) {
        // Two resumes in quick succession (unlock then rotate). Cancelling
        // does not stop the blocking read underneath -- it only discards the
        // result -- and discarding it is the point: an older answer landing
        // after a newer one would put an older sheet on screen.
        val model = CarryViewModel(app)
        model.read = { awaitCancellation() }
        model.refresh()
        model.read = { CarryState.Ready(SampleSession.today) }
        model.refresh()
        advanceUntilIdle()
        assertEquals(CarryState.Ready(SampleSession.today), model.state.value)
    }

    @Test
    fun `the folder outlives the activity that asked for it`() {
        // Held against the Application, so an abandoned read cannot keep a
        // destroyed Activity alive while a blocked provider finishes.
        val model = CarryViewModel(app)
        assertEquals(false, model.folder.chosen)
    }
}
