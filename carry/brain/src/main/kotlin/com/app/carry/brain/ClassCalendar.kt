package com.app.carry.brain

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Which class is on a day, from the phone's own calendar (phase 5 of
 * docs/designs/brain-move.md), decided as gym-coach's `gym_calendar` decides
 * it from the laptop's: the first event whose title reads like
 * "Orange 60 Min 2G", and its start. Only the title is matched and only the
 * tag and start leave this object; a calendar is a log of someone's life,
 * and nothing else of it is kept or written anywhere.
 */
object ClassCalendar {
    const val REPORT_SCHEMA = "brain-calendar/1"

    /** One event as the phone's calendar gives it: its title, and its start for a timed one. */
    data class Event(val title: String, val start: LocalDateTime?)

    /** The class on a day: its tag ("2G") and its start, null for an all-day entry. */
    data class Found(val classTag: String, val start: LocalDateTime?)

    /** `gym_calendar.ORANGE_CLASS_RE`, Python's meaning: case-insensitive, Unicode `\d` and `\b`. */
    private val ORANGE_CLASS = pyRegex("orange.*?(\\dG)\\b", ignoreCase = true)

    /** `gym_calendar.class_for_date` over [events] in their order: the first that matches. */
    fun classOn(events: List<Event>): Found? = events.firstNotNullOfOrNull { e ->
        ORANGE_CLASS.find(e.title)?.let { Found(it.groupValues[1].uppercase(Locale.ROOT), e.start) }
    }

    /** As the laptop writes a sheet's `starts_at`: `isoformat(timespec="minutes")`. */
    fun startsAt(start: LocalDateTime?): String? = start?.format(MINUTES)

    private val MINUTES = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")

    /**
     * One day's comparison: what the phone's calendar says against the sheet the laptop wrote
     * for that day, if it has. [phone] null is "no class"; [phoneProblem], when set, is why the
     * calendar couldn't be read (`no_calendar_access`, `calendar_unreadable`), which is not
     * the same as no class and is said so. A laptop
     * sheet only exists once the day's board is out, so a class only the phone has seen is
     * normal for tomorrow, and reported as that rather than as a difference.
     */
    fun day(date: LocalDate, phone: Found?, phoneProblem: String?, laptopSheet: JsonObject?): JsonObject {
        fun side(tag: String?, startsAt: String?): JsonElement =
            if (tag == null) JsonNull else JsonObject(mapOf("class" to JsonPrimitive(tag), "starts_at" to (startsAt?.let(::JsonPrimitive) ?: JsonNull)))
        val laptopTag = (laptopSheet?.get("class") as? JsonPrimitive)?.takeIf { it.isString }?.content
        val laptopStart = (laptopSheet?.get("starts_at") as? JsonPrimitive)?.takeIf { it.isString }?.content
        val phoneStart = startsAt(phone?.start)
        val status = when {
            phoneProblem != null -> phoneProblem
            phone == null && laptopTag == null -> "no_class"
            phone == null -> "laptop_only"
            laptopTag == null -> "phone_only"
            phone.classTag == laptopTag && phoneStart == laptopStart -> "same"
            else -> "differ"
        }
        return JsonObject(mapOf(
            "date" to JsonPrimitive(date.toString()),
            "status" to JsonPrimitive(status),
            "phone" to side(phone?.classTag, phoneStart),
            "laptop" to side(laptopTag, laptopStart),
        ))
    }

    fun report(days: List<JsonObject>, build: String, checked: LocalDateTime): JsonObject = JsonObject(mapOf(
        "schema" to JsonPrimitive(REPORT_SCHEMA),
        "brain" to JsonPrimitive(build),
        "checked" to JsonPrimitive(startsAt(checked)),
        "days" to JsonArray(days),
    ))
}
