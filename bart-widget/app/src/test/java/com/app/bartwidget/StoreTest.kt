package com.app.bartwidget

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class StoreTest {
    private val data = PreferenceDataStoreFactory.create {
        File.createTempFile("store", ".preferences_pb").also { it.delete(); it.deleteOnExit() }
    }
    private val store = Store(data)

    @Test
    fun starsAreKeptInTheOrderTheyWereStarred() = runTest {
        assertEquals(emptyList<String>(), store.starred.first())
        store.toggle("MONT")
        store.toggle("DUBL")
        store.toggle("12TH")
        store.toggle("DUBL")
        assertEquals(listOf("MONT", "12TH"), store.starred.first())
    }

    @Test
    fun boardsSurviveARoundTrip() = runTest {
        val board = Board("MONT", 42, listOf(Train("Antioch", 99, "2", "#ffff33", 10, true)), error = "Couldn't refresh")
        store.saveSnapshot(Snapshot(mapOf("MONT" to board)))
        assertEquals(board, store.snapshot.first().boards["MONT"])
    }

    @Test
    fun anUnreadableSnapshotIsTreatedAsNone() = runTest {
        data.edit { it[stringPreferencesKey("snapshot")] = "{not json" }
        assertEquals(Snapshot(), store.snapshot.first())
    }
}
