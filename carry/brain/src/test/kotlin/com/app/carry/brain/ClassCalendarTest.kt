package com.app.carry.brain

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/** Invented calendar entries. */
class ClassCalendarTest {
    private val nine = LocalDateTime.of(2026, 1, 10, 9, 50)

    @Test
    fun `the class is read from the title as the laptop reads it`() {
        // A timed event and an all-day one: the laptop's first calendar query found only all-day
        // events, so both kinds are here and either going missing fails.
        assertEquals(ClassCalendar.Found("2G", nine), ClassCalendar.classOn(listOf(ClassCalendar.Event("Orange 60 Min 2G", nine))))
        assertEquals(ClassCalendar.Found("3G", null), ClassCalendar.classOn(listOf(ClassCalendar.Event("Orange 60 Min 3G", null))))
        // Case-insensitive, as the Python pattern is, and the tag upper-cased.
        assertEquals("2G", ClassCalendar.classOn(listOf(ClassCalendar.Event("ORANGE 60 min 2g", nine)))?.classTag)
    }

    @Test
    fun `only an orange class with a tag counts, and the first of them`() {
        val events = listOf(
            ClassCalendar.Event("Dentist", nine),
            ClassCalendar.Event("Orange 60 - Tornado", nine),
            ClassCalendar.Event("Orange 60 Min 2Gx", nine),
            ClassCalendar.Event("Orange 60 Min 3G", nine.plusHours(2)),
            ClassCalendar.Event("Orange 60 Min 2G", nine.plusHours(4)),
        )
        assertEquals(ClassCalendar.Found("3G", nine.plusHours(2)), ClassCalendar.classOn(events))
        assertNull(ClassCalendar.classOn(listOf(ClassCalendar.Event("Lunch with an orange", nine))))
    }

    @Test
    fun `a day's comparison says what kind of difference it is`() {
        val day = LocalDate.of(2026, 1, 10)
        fun sheet(tag: String, at: String?) = JsonObject(mapOf("class" to JsonPrimitive(tag), "starts_at" to (at?.let(::JsonPrimitive) ?: JsonNull)))
        fun status(phone: ClassCalendar.Found?, laptop: JsonObject?, readable: Boolean = true) =
            (ClassCalendar.day(day, phone, readable, laptop)["status"] as JsonPrimitive).content
        val found = ClassCalendar.Found("2G", nine)
        assertEquals("same", status(found, sheet("2G", "2026-01-10T09:50")))
        assertEquals("differ", status(found, sheet("3G", "2026-01-10T09:50")))
        assertEquals("differ", status(found, sheet("2G", "2026-01-10T11:50")))
        assertEquals("phone_only", status(found, null))
        assertEquals("laptop_only", status(null, sheet("2G", null)))
        assertEquals("no_class", status(null, null))
        assertEquals("no_calendar_access", status(null, sheet("2G", null), readable = false))
        // An all-day class has no start on either side.
        assertEquals("same", status(ClassCalendar.Found("2G", null), sheet("2G", null)))
    }
}
