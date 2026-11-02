package com.app.carry

import com.app.carry.brain.ClassCalendar
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/** Invented calendar entries and sheets. */
class CalendarCheckTest {
    private val day = LocalDate.of(2026, 1, 10)
    // Far from UTC, so a day kept by the wrong clock is off by one.
    private val zone = ZoneId.of("Pacific/Auckland")

    @Test
    fun `a timed class belongs to its local day, an all-day one to its UTC date`() {
        val tenTo10 = LocalDateTime.of(2026, 1, 10, 9, 50).atZone(zone).toInstant().toEpochMilli()
        // In UTC that is still the 9th: kept by the local date it is.
        assertEquals(ClassCalendar.Event("Orange 60 Min 2G", LocalDateTime.of(2026, 1, 10, 9, 50)),
            CalendarReader.keep("Orange 60 Min 2G", tenTo10, allDay = false, date = day, zone = zone))
        assertNull(CalendarReader.keep("Orange 60 Min 2G", tenTo10, allDay = false, date = day.minusDays(1), zone = zone))
        // An all-day entry is stored at UTC midnight of its date, which here is midday local.
        val allDay = day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        assertEquals(ClassCalendar.Event("Orange 60 Min 3G", null),
            CalendarReader.keep("Orange 60 Min 3G", allDay, allDay = true, date = day, zone = zone))
        assertNull(CalendarReader.keep("Orange 60 Min 3G", allDay, allDay = true, date = day.plusDays(1), zone = zone))
    }

    private class Sessions(val files: Map<String, String>) : Folder {
        override val label = "Sync/gym"
        override val chosen = true
        override val grantLost = false
        override fun list() = files.keys.map { it to it }
        override fun read(documentId: String) = files[documentId]
        override fun write(documentId: String, text: String) = false
    }

    private class Brain(val files: MutableMap<String, String> = mutableMapOf()) : BrainFolder {
        override fun list() = files.keys.map { it to it }
        override fun read(documentId: String) = files[documentId]
        override fun create(name: String, text: String): Boolean { files[name] = text; return true }
        override fun write(documentId: String, text: String): Boolean { files[documentId] = text; return true }
    }

    private fun sheet(tag: String, startsAt: String?) =
        """{"schema": "gym-session/1", "sheet_id": "x", "class": "$tag", "starts_at": ${startsAt?.let { "\"$it\"" } ?: "null"}, "sets": []}"""

    private fun days(brain: Brain) = Json.parseToJsonElement(brain.files.getValue(CalendarCheck.REPORT)).jsonObject["days"]!!.jsonArray
        .map { it.jsonObject }

    private fun status(d: JsonObject) = d["status"]!!.jsonPrimitive.content

    @Test
    fun `today and tomorrow are each set against the laptop's sheet`() {
        val sessions = Sessions(mapOf(
            "session-2026-01-10-2G.json" to sheet("2G", "2026-01-10T09:50"),
            "session-2026-01-09-3G.json" to sheet("3G", null),
        ))
        val calendar = object : CalendarSource {
            override fun allowed() = true
            override fun eventsOn(date: LocalDate) = when (date) {
                day -> listOf(ClassCalendar.Event("Orange 60 Min 2G", LocalDateTime.of(2026, 1, 10, 9, 50)))
                else -> listOf(ClassCalendar.Event("Orange 60 Min 3G", LocalDateTime.of(2026, 1, 11, 7, 0)))
            }
        }
        val brain = Brain()
        CalendarCheck.runOnce(sessions, brain, calendar, LocalDateTime.of(2026, 1, 10, 8, 0), "test")
        val (today, tomorrow) = days(brain)
        assertEquals("same", status(today))
        // No sheet for tomorrow until its board is out: the phone's alone, said as that.
        assertEquals("phone_only", status(tomorrow))
        assertEquals("2026-01-11T07:00", tomorrow["phone"]!!.jsonObject["starts_at"]!!.jsonPrimitive.content)
    }

    @Test
    fun `without calendar access the report says so, not that there is no class`() {
        val sessions = Sessions(mapOf("session-2026-01-10-2G.json" to sheet("2G", null)))
        val calendar = object : CalendarSource {
            override fun allowed() = false
            override fun eventsOn(date: LocalDate): List<ClassCalendar.Event>? = null
        }
        val brain = Brain()
        CalendarCheck.runOnce(sessions, brain, calendar, LocalDateTime.of(2026, 1, 10, 8, 0), "test")
        assertEquals(listOf("no_calendar_access", "no_calendar_access"), days(brain).map(::status))
    }

    @Test
    fun `a calendar that is allowed but can't be read is said so, never read as an empty day`() {
        val sessions = Sessions(mapOf("session-2026-01-10-2G.json" to sheet("2G", null)))
        val calendar = object : CalendarSource {
            override fun allowed() = true
            override fun eventsOn(date: LocalDate): List<ClassCalendar.Event>? = null
        }
        val brain = Brain()
        CalendarCheck.runOnce(sessions, brain, calendar, LocalDateTime.of(2026, 1, 10, 8, 0), "test")
        assertEquals(listOf("calendar_unreadable", "calendar_unreadable"), days(brain).map(::status))
    }

    @Test
    fun `with two sheets on one day, the one for the phone's class is compared`() {
        // 2G sorts first; the class is 3G. Compared with the first by name, this reads "differ".
        val sessions = Sessions(mapOf(
            "session-2026-01-10-2G.json" to sheet("2G", "2026-01-10T07:00"),
            "session-2026-01-10-3G.json" to sheet("3G", "2026-01-10T09:50"),
        ))
        val calendar = object : CalendarSource {
            override fun allowed() = true
            override fun eventsOn(date: LocalDate) =
                if (date == day) listOf(ClassCalendar.Event("Orange 60 Min 3G", LocalDateTime.of(2026, 1, 10, 9, 50))) else emptyList()
        }
        val brain = Brain()
        CalendarCheck.runOnce(sessions, brain, calendar, LocalDateTime.of(2026, 1, 10, 6, 0), "test")
        assertEquals("same", status(days(brain)[0]))
    }

    @Test
    fun `nothing from the calendar but the tag and the start reaches the report`() {
        val sessions = Sessions(emptyMap())
        val calendar = object : CalendarSource {
            override fun allowed() = true
            override fun eventsOn(date: LocalDate) = listOf(
                ClassCalendar.Event("Dr Smith, private", LocalDateTime.of(2026, 1, 10, 8, 0)),
                ClassCalendar.Event("Orange 60 Min 2G at the studio on Main St", LocalDateTime.of(2026, 1, 10, 9, 50)),
            )
        }
        val brain = Brain()
        CalendarCheck.runOnce(sessions, brain, calendar, LocalDateTime.of(2026, 1, 10, 8, 0), "test")
        val text = brain.files.getValue(CalendarCheck.REPORT)
        for (word in listOf("Smith", "private", "studio", "Main", "Orange")) {
            assertFalse("$word reached the report: $text", word in text)
        }
    }
}
