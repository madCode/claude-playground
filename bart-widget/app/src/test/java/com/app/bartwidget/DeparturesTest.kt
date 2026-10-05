package com.app.bartwidget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.app.bartwidget.testutil.FakeBart
import java.time.ZoneId

class DeparturesTest {
    private val t0 = 1_760_000_000_000L

    @Test
    fun parsesARealResponse() {
        val trains = parseEtd(FakeBart.fixture("etd-mont.json")!!, t0)
        assertEquals(24, trains.size)
        // "Leaving" is now.
        assertEquals(t0, trains.first().departsAt)
        assertEquals("Daly City", trains.first().destination)
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
    fun aResponseWithoutAStationIsNoTrains() {
        assertEquals(emptyList<Train>(), parseEtd("""{"root":{"message":{"error":"Invalid orig"}}}""", t0))
        assertEquals(emptyList<Train>(), parseEtd("""{"other":1}""", t0))
    }

    @Test
    fun missingFieldsGetDefaults() {
        val json = """{"root":{"station":{"etd":{"estimate":[{"minutes":"3"},{"minutes":"soon"}]}}}}"""
        val train = parseEtd(json, t0).single()
        assertEquals("?", train.destination)
        assertEquals("#888888", train.hexColor)
        assertEquals(0, train.cars)
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

    private val mont get() = destinationRows(parseEtd(FakeBart.fixture("etd-mont.json")!!, t0), t0)

    @Test
    fun linesAreNamedByStationAndDestinationCode() {
        assertEquals(listOf("DALY", "SFIA", "BERY", "ANTC", "DUBL", "RICH", "PITT", "MLBR"), mont.map { it.key })
        assertEquals("MONT:ANTC", lineKey("MONT", mont[3]))
        // Boards saved before destination codes were kept fall back to the name.
        assertEquals("Antioch", DestinationRow("Antioch", "#fff", listOf(Train("Antioch", 0, "1", "#fff", 8, false))).key)
    }

    @Test
    fun withNoLinesStarredAStationShowsItsNextThree() {
        val r = widgetRows(mont, "MONT", setOf("12TH:ANTC"), expanded = false)
        assertEquals(listOf("DALY", "SFIA", "BERY"), r.shown.map { it.key })
        assertEquals(5, r.more)
    }

    @Test
    fun withLinesStarredOnlyThoseShow() {
        val r = widgetRows(mont, "MONT", setOf("MONT:MLBR", "MONT:ANTC"), expanded = false)
        assertEquals(listOf("ANTC", "MLBR"), r.shown.map { it.key })
        assertEquals(6, r.more)
    }

    @Test
    fun starredLinesWithoutTrainsShowNothingRatherThanOtherLines() {
        val r = widgetRows(mont, "MONT", setOf("MONT:WARM"), expanded = false)
        assertEquals(emptyList<DestinationRow>(), r.shown)
        assertEquals(8, r.more)
    }

    @Test
    fun expandedShowsEveryLineStarredFirstAndCanCollapse() {
        val r = widgetRows(mont, "MONT", setOf("MONT:MLBR"), expanded = true)
        assertEquals("MLBR", r.shown.first().key)
        assertEquals(8, r.shown.size)
        assertEquals(WidgetRows(r.shown, 0, true), r)
    }

    @Test
    fun nothingToExpandWhenEveryLineAlreadyShows() {
        val few = mont.take(3)
        assertEquals(WidgetRows(few, 0, false), widgetRows(few, "MONT", emptySet(), expanded = true))
        val all = mont.map { lineKey("MONT", it) }.toSet()
        assertEquals(WidgetRows(mont, 0, false), widgetRows(mont, "MONT", all, expanded = false))
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
