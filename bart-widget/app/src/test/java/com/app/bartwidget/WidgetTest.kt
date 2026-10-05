package com.app.bartwidget

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.appwidget.testing.unit.assertHasRunCallbackClickAction
import androidx.glance.appwidget.testing.unit.assertHasStartActivityClickAction
import androidx.glance.testing.unit.assertHasStartActivityClickAction as assertOpens
import androidx.glance.testing.unit.hasStartActivityClickAction
import androidx.glance.testing.unit.hasText
import androidx.glance.testing.unit.hasTextEqualTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.app.bartwidget.testutil.FakeBart
import com.app.bartwidget.testutil.MORNING
import com.app.bartwidget.testutil.TestApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The widget's content, drawn by Glance's test host from recorded boards. */
@RunWith(AndroidJUnit4::class)
@Config(application = TestApp::class)
class WidgetTest {
    private val app = ApplicationProvider.getApplicationContext<TestApp>()
    private val parking = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.bart.gov/guide/parking"))

    @After fun close() = app.bart.close()

    private fun board(abbr: String, fetchedAt: Long = MORNING, error: String? = null) =
        Board(abbr, fetchedAt, parseEtd(FakeBart.fixture("etd-${abbr.lowercase()}.json")!!, fetchedAt), error)

    private fun snapshot(vararg boards: Board) = Snapshot(boards.associateBy { it.abbr })

    @Test
    fun eachStarredStationWithClockTimesInStarOrder() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(DpSize(320.dp, 600.dp))
        setContext(app)
        provideComposable { WidgetContent(listOf("DUBL", "MONT"), snapshot(board("MONT"), board("DUBL")), MORNING, parking) }

        onNode(hasText("Updated 7:20")).assertExists()
        // Once as a station, once as where Montgomery's trains are going.
        onAllNodes(hasTextEqualTo("Dublin/Pleasanton")).assertCountEquals(2)
        onNode(hasTextEqualTo("Montgomery St.")).assertExists()
        // Dublin's one destination, three trains; never "in N min", which would go stale.
        onNode(hasText("7:20  7:39  7:59")).assertExists()
        onNode(hasText("SF Airport")).assertExists()
        onAllNodes(hasText("min")).assertCountEquals(0)
    }

    @Test
    fun tappingAStationOpensThatStation() = runGlanceAppWidgetUnitTest {
        setContext(app)
        provideComposable { WidgetContent(listOf("MONT", "DUBL"), snapshot(board("MONT"), board("DUBL")), MORNING, parking) }
        for (abbr in listOf("MONT", "DUBL")) {
            onNode(hasStartActivityClickAction<MainActivity>(actionParametersOf(StationParam to abbr))).assertExists()
        }
    }

    @Test
    fun refreshAndParkingButtons() = runGlanceAppWidgetUnitTest {
        setContext(app)
        provideComposable { WidgetContent(listOf("MONT"), snapshot(board("MONT")), MORNING, parking) }
        onNode(hasTextEqualTo("Refresh")).assertHasRunCallbackClickAction<RefreshAction>()
        onNode(hasTextEqualTo("Parking")).assertHasStartActivityClickAction(parking)
    }

    @Test
    fun trainsThatHaveLeftAreHidden() = runGlanceAppWidgetUnitTest {
        setContext(app)
        provideComposable { WidgetContent(listOf("DUBL"), snapshot(board("DUBL")), MORNING + 25 * 60_000, parking) }
        onNode(hasTextEqualTo("7:59")).assertExists()
    }

    @Test
    fun aStaleBoardSaysWhenItsTimesAreFrom() = runGlanceAppWidgetUnitTest {
        setContext(app)
        provideComposable {
            WidgetContent(listOf("DUBL"), snapshot(board("DUBL", error = Refresher.STALE)), MORNING + 60_000, parking)
        }
        onNode(hasText("Couldn't refresh: times from 7:20")).assertExists()
        onNode(hasText("7:39  7:59")).assertExists()
    }

    @Test
    fun aStationWithNothingToShow() = runGlanceAppWidgetUnitTest {
        setContext(app)
        val never = Board("MONT", 0, emptyList(), Refresher.STALE)
        val empty = Board("WARM", MORNING, emptyList())
        provideComposable { WidgetContent(listOf("MONT", "WARM", "12TH"), snapshot(never, empty), MORNING, parking) }
        onNode(hasTextEqualTo("Couldn't refresh")).assertExists()
        onNode(hasTextEqualTo("No trains")).assertExists()
        onNode(hasText("Loading…")).assertExists()
        // Only Warm Springs was ever fetched, so it sets "Updated".
        onNode(hasText("Updated 7:20")).assertExists()
    }

    @Test
    fun withNoStarsItAsksForSome() = runGlanceAppWidgetUnitTest {
        setContext(app)
        provideComposable { WidgetContent(emptyList(), Snapshot(), MORNING, parking) }
        onNode(hasTextEqualTo("BART")).assertExists()
        onNode(hasText("Tap to star your stations")).assertOpens<MainActivity>()
    }

    @Test
    fun theRefreshButtonFetchesTheStarredStations() = runBlocking {
        app.container.store.toggle("DUBL")
        RefreshAction().onAction(app, object : GlanceId {}, actionParametersOf())
        assertEquals(listOf("DUBL"), app.bart.requests)
        assertEquals(3, app.container.store.snapshot.first().boards.getValue("DUBL").trains.size)
    }

    @Test
    fun placingTheWidgetStartsBackgroundRefreshAndRemovingItStopsIt() {
        val receiver = BartWidgetReceiver()
        receiver.onEnabled(app)
        assertEquals(WorkInfo.State.ENQUEUED, refreshWork())
        receiver.onDisabled(app)
        assertEquals(WorkInfo.State.CANCELLED, refreshWork())
    }

    @Test
    fun theBackgroundRefreshFetches() = runBlocking {
        app.container.store.toggle("MONT")
        val worker = androidx.work.testing.TestListenableWorkerBuilder<RefreshWorker>(app).build()
        assertEquals(androidx.work.ListenableWorker.Result.success(), worker.doWork())
        assertEquals(listOf("MONT"), app.bart.requests)
    }

    private fun refreshWork() = WorkManager.getInstance(app).getWorkInfosForUniqueWork("refresh").get().single().state
}
