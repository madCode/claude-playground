package com.app.bartwidget

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.net.URL

/**
 * Against the real api.bart.gov: the format the app parses and the stations it bundles still
 * match BART's. Runs only with -PliveCheck (see CLAUDE.md).
 */
class LiveCheckTest {
    private val api = BartApi("https://api.bart.gov")

    @Before fun onlyWhenAsked() = assumeTrue(System.getProperty("liveCheck") == "true")

    @Test
    fun liveBoardsParse() = runBlocking {
        val now = System.currentTimeMillis()
        for (abbr in listOf("MONT", "12TH", "DUBL")) {
            val trains = api.departures(abbr, now)
            println("$abbr: ${trains.size} trains, " + destinationRows(trains, now).joinToString { "${it.destination} ${it.trains.size}" })
            trains.forEach { assert(it.departsAt >= now && it.destination != "?") { it } }
        }
    }

    @Test
    fun theBundledStationsAreBartsStations() {
        val json = URL("https://api.bart.gov/api/stn.aspx?cmd=stns&key=${BartApi.KEY}&json=y").readText()
        val live = Json.parseToJsonElement(json).jsonObject["root"]!!.jsonObject["stations"]!!.jsonObject["station"]!!.jsonArray
            .associate { it.jsonObject["abbr"]!!.jsonPrimitive.content to it.jsonObject["name"]!!.jsonPrimitive.content }
        assertEquals(live, STATIONS.associate { it.abbr to it.name })
    }
}
