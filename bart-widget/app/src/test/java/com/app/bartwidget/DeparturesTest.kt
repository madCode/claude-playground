package com.app.bartwidget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class DeparturesTest {
    private val t0 = 1_760_000_000_000L

    private fun fixture(name: String) = javaClass.classLoader!!.getResource(name)!!.readText()

    @Test
    fun parsesARealResponse() {
        val trains = parseEtd(fixture("etd-mont.json"), t0)
        assertTrue(trains.isNotEmpty())
        assertEquals(trains.sortedBy { it.departsAt }, trains)
        assertTrue(trains.all { it.departsAt >= t0 && it.hexColor.startsWith("#") })
    }

    @Test
    fun aSingleTrainComesAsAnObjectNotAList() {
        val json = """{"root":{"station":{"abbr":"DUBL","etd":{"destination":"Daly City",
            "estimate":{"minutes":"7","platform":"2","length":"10","hexcolor":"#0099cc","delay":"0","cancelflag":"0"}}}}}"""
        val trains = parseEtd(json, t0)
        assertEquals(1, trains.size)
        assertEquals("Daly City", trains[0].destination)
        assertEquals(t0 + 7 * 60_000, trains[0].departsAt)
        assertEquals(10, trains[0].cars)
    }

    @Test
    fun leavingIsNowAndCancelledTrainsAreDropped() {
        val json = """{"root":{"station":[{"abbr":"MONT","etd":[{"destination":"Antioch","estimate":[
            {"minutes":"Leaving","platform":"2","hexcolor":"#ffff33","delay":"0","cancelflag":"0"},
            {"minutes":"12","platform":"2","hexcolor":"#ffff33","delay":"120","cancelflag":"1"},
            {"minutes":"20","platform":"2","hexcolor":"#ffff33","delay":"120","cancelflag":"0"}]}]}]}}"""
        val trains = parseEtd(json, t0)
        assertEquals(listOf(t0, t0 + 20 * 60_000), trains.map { it.departsAt })
        assertEquals(listOf(false, true), trains.map { it.delayed })
    }

    @Test
    fun noTrainsAtNight() {
        assertEquals(emptyList<Train>(), parseEtd("""{"root":{"station":[{"abbr":"MONT","message":""}]}}""", t0))
    }

    @Test
    fun rowsHideTrainsThatHaveLeftAndPutTheSoonestFirst() {
        fun train(dest: String, min: Int) = Train(dest, t0 + min * 60_000L, "1", "#fff", 8, false)
        val trains = listOf(train("Antioch", -5), train("Millbrae", 4), train("Antioch", 9), train("Millbrae", 24))
        val rows = destinationRows(trains, t0)
        assertEquals(listOf("Millbrae", "Antioch"), rows.map { it.destination })
        assertEquals(1, rows[1].trains.size)
    }

    @Test
    fun clockTimesAreLocalWallClock() {
        // 2026-10-05 07:33 in the Bay Area is 14:33 UTC.
        val ms = java.time.ZonedDateTime.of(2026, 10, 5, 14, 33, 0, 0, ZoneId.of("UTC")).toInstant().toEpochMilli()
        assertEquals("7:33", formatClock(ms, ZoneId.of("America/Los_Angeles")))
        assertEquals(13, minutesUntil(ms, ms - 13 * 60_000))
        assertEquals(0, minutesUntil(ms, ms + 60_000))
    }

    @Test
    fun starsKeepTheOrderTheyWereAddedIn() {
        val s = toggleStar(toggleStar(toggleStar(emptyList(), "MONT"), "DUBL"), "12TH")
        assertEquals(listOf("MONT", "DUBL", "12TH"), s)
        assertEquals(listOf("MONT", "12TH"), toggleStar(s, "DUBL"))
    }

    @Test
    fun everyStationHasAName() {
        assertEquals(50, STATIONS.size)
        assertEquals("Montgomery St.", stationName("MONT"))
    }
}
