package com.app.bartwidget

import com.app.bartwidget.testutil.FakeBart
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class BartApiTest {
    private val bart = FakeBart()
    private val api = BartApi(bart.base)

    @After fun close() = bart.close()

    @Test
    fun asksForTheStationAndParsesItsBoard() = runTest {
        val trains = api.departures("DUBL", 1_000L)
        assertEquals(listOf("DUBL"), bart.requests)
        assertEquals(listOf("Daly City"), trains.map { it.destination }.distinct())
        assertEquals(listOf(1_000L, 1_000L + 19 * 60_000, 1_000L + 39 * 60_000), trains.map { it.departsAt })
    }

    @Test
    fun anErrorFromBartFails() = runTest {
        bart.failing += "MONT"
        val result = runCatching { api.departures("MONT", 0) }
        assertEquals("BART error 500", whyFailed(result.exceptionOrNull()!!))
    }

    @Test
    fun aTimeoutOrAnUnreadableReplySaySo() {
        assertEquals("BART didn't answer", whyFailed(java.net.SocketTimeoutException("Read timed out")))
        assertEquals("Couldn't read BART's times", whyFailed(runCatching { parseEtd("<html>Down for maintenance</html>", 0) }.exceptionOrNull()!!))
    }

    @Test
    fun anUnreachableServerFails() = runTest {
        val dead = BartApi("http://127.0.0.1:1")
        assertEquals("No connection", whyFailed(runCatching { dead.departures("MONT", 0) }.exceptionOrNull()!!))
    }
}
