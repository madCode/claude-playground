package com.app.bartwidget

import android.content.ComponentName
import android.content.ContextWrapper
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
import com.app.bartwidget.testutil.idleUntil
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
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
        provideComposable { WidgetContent(listOf("DUBL", "MONT"), emptySet(), emptySet(), snapshot(board("MONT"), board("DUBL")), MORNING, parking) }

        onNode(hasText("Updated 7:20")).assertExists()
        // The station; Montgomery's trains to Dublin are behind "+5 more".
        onAllNodes(hasTextEqualTo("Dublin/Pleasanton")).assertCountEquals(1)
        onNode(hasTextEqualTo("Montgomery St.")).assertExists()
        // Dublin's one destination, three trains; never "in N min", which would go stale.
        onNode(hasText("7:20  7:39  7:59")).assertExists()
        onNode(hasText("SF Airport")).assertExists()
        onAllNodes(hasText("min")).assertCountEquals(0)
        // With no lines starred, Montgomery shows its next three; Dublin has only one.
        onNode(hasTextEqualTo("+5 more")).assertExists()
        onAllNodes(hasText("more")).assertCountEquals(1)
    }

    @Test
    fun aStationShowsOnlyItsStarredLines() = runGlanceAppWidgetUnitTest {
        setContext(app)
        provideComposable { WidgetContent(listOf("MONT"), setOf("MONT:ANTC", "MONT:MLBR", "12TH:SFIA"), emptySet(), snapshot(board("MONT")), MORNING, parking) }
        onNode(hasTextEqualTo("Antioch")).assertExists()
        onNode(hasTextEqualTo("Millbrae")).assertExists()
        onNode(hasTextEqualTo("SF Airport")).assertDoesNotExist()
        onNode(hasTextEqualTo("+6 more")).assertHasRunCallbackClickAction<ToggleExpandAction>(actionParametersOf(StationParam to "MONT"))
    }

    @Test
    fun expandedAStationShowsEveryLineStarredFirst() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(DpSize(320.dp, 600.dp))
        setContext(app)
        provideComposable { WidgetContent(listOf("MONT"), setOf("MONT:MLBR"), setOf("MONT"), snapshot(board("MONT")), MORNING, parking) }
        for (dest in listOf("Millbrae", "Daly City", "SF Airport", "Berryessa", "Antioch", "Dublin/Pleasanton", "Richmond", "Pittsburg/Bay Point")) {
            onNode(hasTextEqualTo(dest)).assertExists()
        }
        onNode(hasTextEqualTo("Show less")).assertHasRunCallbackClickAction<ToggleExpandAction>(actionParametersOf(StationParam to "MONT"))
    }

    @Test
    fun starredLinesWithNoTrainsSaySoRatherThanShowOthers() = runGlanceAppWidgetUnitTest {
        setContext(app)
        provideComposable { WidgetContent(listOf("DUBL"), setOf("DUBL:WARM"), emptySet(), snapshot(board("DUBL")), MORNING, parking) }
        onNode(hasText("No trains on your lines right now")).assertExists()
        onNode(hasTextEqualTo("Daly City")).assertDoesNotExist()
        onNode(hasTextEqualTo("+1 more")).assertExists()
    }

    @Test
    fun moreExpandsThatStationOnThatWidgetAndLessCollapsesIt() = runBlocking {
        val manager = android.appwidget.AppWidgetManager.getInstance(app)
        shadowOf(manager).addInstalledProvider(
            android.appwidget.AppWidgetProviderInfo().apply { provider = android.content.ComponentName(app, BartWidgetReceiver::class.java) },
        )
        // Bound without the update broadcast createWidget sends, whose goAsync Robolectric can't finish.
        val id = android.appwidget.AppWidgetHost(app, 1).allocateAppWidgetId()
        manager.bindAppWidgetIdIfAllowed(id, android.content.ComponentName(app, BartWidgetReceiver::class.java))
        val glanceId = GlanceAppWidgetManager(app).getGlanceIdBy(id)
        suspend fun expanded() = getAppWidgetState(app, PreferencesGlanceStateDefinition, glanceId)[ExpandedKey] ?: emptySet()

        ToggleExpandAction().onAction(app, glanceId, actionParametersOf(StationParam to "MONT"))
        assertEquals(setOf("MONT"), expanded())
        ToggleExpandAction().onAction(app, glanceId, actionParametersOf(StationParam to "DUBL"))
        ToggleExpandAction().onAction(app, glanceId, actionParametersOf(StationParam to "MONT"))
        assertEquals(setOf("DUBL"), expanded())
    }

    @Test
    fun tappingAStationOpensThatStation() = runGlanceAppWidgetUnitTest {
        setContext(app)
        provideComposable { WidgetContent(listOf("MONT", "DUBL"), emptySet(), emptySet(), snapshot(board("MONT"), board("DUBL")), MORNING, parking) }
        for (abbr in listOf("MONT", "DUBL")) {
            onNode(hasStartActivityClickAction<MainActivity>(actionParametersOf(StationParam to abbr))).assertExists()
        }
    }

    @Test
    fun refreshAndParkingButtons() = runGlanceAppWidgetUnitTest {
        setContext(app)
        provideComposable { WidgetContent(listOf("MONT"), emptySet(), emptySet(), snapshot(board("MONT")), MORNING, parking) }
        onNode(hasTextEqualTo("Refresh")).assertHasRunCallbackClickAction<RefreshAction>()
        onNode(hasTextEqualTo("Parking")).assertHasStartActivityClickAction(parking)
    }

    @Test
    fun trainsThatHaveLeftAreHidden() = runGlanceAppWidgetUnitTest {
        setContext(app)
        provideComposable { WidgetContent(listOf("DUBL"), emptySet(), emptySet(), snapshot(board("DUBL")), MORNING + 25 * 60_000, parking) }
        onNode(hasTextEqualTo("7:59")).assertExists()
    }

    @Test
    fun aStaleBoardSaysWhenItsTimesAreFrom() = runGlanceAppWidgetUnitTest {
        setContext(app)
        provideComposable {
            WidgetContent(listOf("DUBL"), emptySet(), emptySet(), snapshot(board("DUBL", error = "No connection")), MORNING + 60_000, parking)
        }
        onNode(hasText("No connection: times from 7:20")).assertExists()
        onNode(hasText("7:39  7:59")).assertExists()
    }

    @Test
    fun aStationWithNothingToShow() = runGlanceAppWidgetUnitTest {
        setContext(app)
        val never = Board("MONT", 0, emptyList(), "BART didn't answer")
        val empty = Board("WARM", MORNING, emptyList())
        provideComposable { WidgetContent(listOf("MONT", "WARM", "12TH"), emptySet(), emptySet(), snapshot(never, empty), MORNING, parking) }
        onNode(hasTextEqualTo("BART didn't answer")).assertExists()
        onNode(hasTextEqualTo("No trains")).assertExists()
        onNode(hasText("Loading…")).assertExists()
        // Only Warm Springs was ever fetched, so it sets "Updated".
        onNode(hasText("Updated 7:20")).assertExists()
    }

    @Test
    fun withNoStarsItAsksForSome() = runGlanceAppWidgetUnitTest {
        setContext(app)
        provideComposable { WidgetContent(emptyList(), emptySet(), emptySet(), Snapshot(), MORNING, parking) }
        onNode(hasTextEqualTo("BART")).assertExists()
        onNode(hasText("Tap to star your stations")).assertOpens<MainActivity>()
    }

    @Test
    fun theRefreshButtonFetchesInAForegroundService() = runBlocking {
        app.container.store.toggle("DUBL")
        RefreshAction().onAction(app, object : GlanceId {}, actionParametersOf())
        val started = shadowOf(app).nextStartedService
        assertEquals(RefreshService::class.java.name, started.component?.className)

        val service = Robolectric.buildService(RefreshService::class.java, started).create().startCommand(0, 1).get()
        assertNotNull(shadowOf(service).lastForegroundNotification)
        idleUntil { shadowOf(service).isStoppedBySelf }
        assertEquals(listOf("DUBL"), app.bart.requests)
        assertEquals(3, app.container.store.snapshot.first().boards.getValue("DUBL").trains.size)
    }

    @Test
    fun ifAndroidRefusesTheServiceRefreshStillFetches() = runBlocking {
        app.container.store.toggle("DUBL")
        val refused = object : ContextWrapper(app) {
            override fun startForegroundService(service: Intent): ComponentName = throw IllegalStateException("not allowed")
        }
        RefreshAction().onAction(refused, object : GlanceId {}, actionParametersOf())
        assertEquals(listOf("DUBL"), app.bart.requests)
    }

    @Test
    fun ifAndroidSilentlyDropsTheServiceRefreshStillFetches() = runBlocking {
        app.container.store.toggle("DUBL")
        val dropped = object : ContextWrapper(app) {
            override fun startForegroundService(service: Intent): ComponentName? = null
        }
        RefreshAction().onAction(dropped, object : GlanceId {}, actionParametersOf())
        assertEquals(listOf("DUBL"), app.bart.requests)
    }

    // Android 14+ crashes the app at startForeground without these; Robolectric doesn't check.
    @Test
    fun theServiceIsDeclaredAsDataSyncWithItsPermissions() {
        val info = app.packageManager.getServiceInfo(ComponentName(app, RefreshService::class.java), 0)
        assertEquals(android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC, info.foregroundServiceType)
        val requested = app.packageManager.getPackageInfo(app.packageName, android.content.pm.PackageManager.GET_PERMISSIONS).requestedPermissions!!.toSet()
        assertTrue(requested.containsAll(setOf("android.permission.FOREGROUND_SERVICE", "android.permission.FOREGROUND_SERVICE_DATA_SYNC")))
    }

    @Test
    fun placingTheWidgetStartsBackgroundRefreshAndRemovingItStopsIt() {
        val receiver = BartWidgetReceiver()
        receiver.onEnabled(app)
        // Scheduled: waiting, or already on its first run, which test WorkManager may start at once.
        assertTrue(refreshWork().toString(), !refreshWork().isFinished)
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

    @Test
    fun aFailedBackgroundRefreshRetriesAFewTimesThenWaitsForTheNext() = runBlocking {
        app.container.store.toggle("MONT")
        app.bart.failing += "MONT"
        fun worker(attempt: Int) = androidx.work.testing.TestListenableWorkerBuilder<RefreshWorker>(app).setRunAttemptCount(attempt).build()
        assertEquals(androidx.work.ListenableWorker.Result.retry(), worker(0).doWork())
        assertEquals(androidx.work.ListenableWorker.Result.success(), worker(RefreshWorker.MAX_RETRIES).doWork())
    }

    private fun refreshWork() = WorkManager.getInstance(app).getWorkInfosForUniqueWork("refresh").get().single().state
}
