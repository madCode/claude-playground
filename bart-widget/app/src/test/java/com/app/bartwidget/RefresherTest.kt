package com.app.bartwidget

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.app.bartwidget.testutil.FakeBart
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class RefresherTest {
    private val bart = FakeBart()
    private val store = Store(PreferenceDataStoreFactory.create {
        File.createTempFile("refresh", ".preferences_pb").also { it.delete(); it.deleteOnExit() }
    })
    private var now = 1_000_000L
    private var widgetUpdates = 0
    private val refresher = Refresher(store, BartApi(bart.base), { now }) { widgetUpdates++ }

    @After fun close() = bart.close()

    @Test
    fun fetchesEveryStarredStationAndUpdatesTheWidget() = runTest {
        store.toggle("MONT")
        store.toggle("DUBL")
        refresher.refresh()
        assertEquals(setOf("MONT", "DUBL"), bart.requests.toSet())
        val boards = store.snapshot.first().boards
        assertEquals(setOf("MONT", "DUBL"), boards.keys)
        assertEquals(now, boards.getValue("DUBL").fetchedAt)
        assertEquals(3, boards.getValue("DUBL").trains.size)
        assertEquals(1, widgetUpdates)
    }

    @Test
    fun aStationThatFailsKeepsItsLastBoardMarkedStale() = runTest {
        store.toggle("MONT")
        store.toggle("DUBL")
        refresher.refresh()
        val earlier = now
        now += 5 * 60_000
        bart.failing += "DUBL"
        refresher.refresh()
        val boards = store.snapshot.first().boards
        assertNull(boards.getValue("MONT").error)
        assertEquals(now, boards.getValue("MONT").fetchedAt)
        assertEquals(Refresher.STALE, boards.getValue("DUBL").error)
        assertEquals(earlier, boards.getValue("DUBL").fetchedAt)
        assertEquals(3, boards.getValue("DUBL").trains.size)

        bart.failing.clear()
        refresher.refresh()
        assertNull(store.snapshot.first().boards.getValue("DUBL").error)
    }

    @Test
    fun aStationThatHasNeverLoadedSaysSo() = runTest {
        store.toggle("MONT")
        bart.failing += "MONT"
        refresher.refresh()
        assertEquals(Board("MONT", 0, emptyList(), Refresher.STALE), store.snapshot.first().boards["MONT"])
    }

    @Test
    fun anUnstarredStationIsDroppedAndNoLongerFetched() = runTest {
        store.toggle("MONT")
        store.toggle("DUBL")
        refresher.refresh()
        store.toggle("MONT")
        bart.requests.clear()
        refresher.refresh()
        assertEquals(listOf("DUBL"), bart.requests)
        assertEquals(setOf("DUBL"), store.snapshot.first().boards.keys)
    }
}
