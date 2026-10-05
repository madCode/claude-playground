package com.app.bartwidget

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Serializable
data class Train(
    val destination: String,
    /** When it leaves, worked out once from BART's "minutes" so the time stays right as it ages. */
    val departsAt: Long,
    val platform: String,
    val hexColor: String,
    val cars: Int,
    val delayed: Boolean,
    /** BART's code for the destination ("ANTC"); with the station, it names a line to star. */
    val destAbbr: String = "",
)

@Serializable
data class Board(
    val abbr: String,
    val fetchedAt: Long,
    val trains: List<Train>,
    /** Set when the last refresh failed; the trains are then from [fetchedAt], not now. */
    val error: String? = null,
)

@Serializable
data class Snapshot(val boards: Map<String, Board> = emptyMap())

data class DestinationRow(val destination: String, val hexColor: String, val trains: List<Train>) {
    val key: String get() = trains.first().destAbbr.ifEmpty { destination }
}

/** A line is a destination from a station: Antioch from Montgomery isn't Antioch from 12th St. */
fun lineKey(station: String, row: DestinationRow) = "$station:${row.key}"

/**
 * Parses an etd.aspx JSON response. BART's JSON is converted from XML, so a list with one entry
 * comes back as a bare object and an empty list as a missing key; both are handled.
 */
fun parseEtd(json: String, fetchedAt: Long): List<Train> {
    val root = Json.parseToJsonElement(json).jsonObject["root"]?.jsonObject ?: return emptyList()
    return listOf(root["station"]).flatMap(::items).flatMap { station ->
        items(station.jsonObject["etd"]).flatMap { etd ->
            val dest = etd.jsonObject.string("destination") ?: "?"
            val destAbbr = etd.jsonObject.string("abbreviation") ?: ""
            items(etd.jsonObject["estimate"]).mapNotNull { e ->
                val est = e.jsonObject
                val minutes = est.string("minutes")?.let { if (it.equals("Leaving", true)) 0 else it.toIntOrNull() }
                    ?: return@mapNotNull null
                if (est.string("cancelflag") == "1") return@mapNotNull null
                Train(
                    destination = dest,
                    departsAt = fetchedAt + minutes * 60_000L,
                    platform = est.string("platform") ?: "",
                    hexColor = est.string("hexcolor") ?: "#888888",
                    cars = est.string("length")?.toIntOrNull() ?: 0,
                    delayed = (est.string("delay")?.toIntOrNull() ?: 0) > 0,
                    destAbbr = destAbbr,
                )
            }
        }
    }.sortedBy { it.departsAt }
}

private fun items(e: JsonElement?): List<JsonElement> = when (e) {
    null -> emptyList()
    is JsonArray -> e.jsonArray
    is JsonObject -> listOf(e)
    else -> emptyList()
}

private fun JsonObject.string(key: String): String? = (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.jsonPrimitive?.content

/** Trains still to leave, one row per destination, soonest first. */
fun destinationRows(trains: List<Train>, now: Long): List<DestinationRow> =
    trains.filter { it.departsAt >= now - 30_000 }
        .groupBy { it.destination }
        .map { (dest, ts) -> DestinationRow(dest, ts.first().hexColor, ts) }
        .sortedBy { it.trains.first().departsAt }

/** Starred lines first, then the rest; each group soonest first. */
fun starredFirst(rows: List<DestinationRow>, station: String, lines: Set<String>): List<DestinationRow> =
    rows.sortedBy { lineKey(station, it) !in lines }

data class WidgetRows(val shown: List<DestinationRow>, val more: Int, val canCollapse: Boolean)

/**
 * What a station shows on the widget: its starred lines (none, if none has a train now), or with
 * no lines starred its next [UNSTARRED_ROWS]; the rest behind "+N more". Expanded, every line.
 */
fun widgetRows(rows: List<DestinationRow>, station: String, lines: Set<String>, expanded: Boolean): WidgetRows {
    val ordered = starredFirst(rows, station, lines)
    val starredHere = lines.any { it.startsWith("$station:") }
    val preferred = if (starredHere) ordered.filter { lineKey(station, it) in lines } else ordered.take(UNSTARRED_ROWS)
    return when {
        preferred.size == rows.size -> WidgetRows(ordered, 0, false)
        expanded -> WidgetRows(ordered.take(MAX_ROWS), 0, true)
        else -> WidgetRows(preferred, rows.size - preferred.size, false)
    }
}

const val UNSTARRED_ROWS = 3
// Glance allows ten children in a column; BART has at most eight destinations at a station.
const val MAX_ROWS = 10

private val clock = DateTimeFormatter.ofPattern("h:mm")

/** A wall-clock time, which unlike "13 min" doesn't go wrong when a widget hasn't redrawn. */
fun formatClock(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    clock.format(Instant.ofEpochMilli(epochMs).atZone(zone))

fun minutesUntil(epochMs: Long, now: Long): Int = ((epochMs - now + 30_000) / 60_000).toInt().coerceAtLeast(0)

fun toggleStar(starred: List<String>, abbr: String): List<String> =
    if (abbr in starred) starred - abbr else starred + abbr
