package com.app.carry

import com.app.carry.brain.ClassCalendar
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Phase 5 of the brain move, in its side-by-side form (docs/designs/brain-move.md): for today
 * and tomorrow, the class the phone's own calendar says against the sheet the laptop wrote,
 * in `brain/calendar-compare.json`. The laptop still decides; nothing here is shown.
 */
object CalendarCheck {
    const val REPORT = "calendar-compare.json"

    private val SHEET = Regex("""^session-(\d{4}-\d{2}-\d{2})-([A-Za-z0-9]+)\.json$""")

    fun runOnce(sessions: Folder, brain: BrainFolder, calendar: CalendarSource, now: LocalDateTime, build: String): Boolean {
        val sheets = sessions.list()
        val days = listOf(now.toLocalDate(), now.toLocalDate().plusDays(1)).map { date ->
            val events = calendar.eventsOn(date)
            ClassCalendar.day(date, events?.let(ClassCalendar::classOn), events != null, laptopSheet(sessions, sheets, date))
        }
        val text = ClassCalendar.report(days, build, now).toString() + "\n"
        val existing = brain.list().firstOrNull { it.second == REPORT }?.first
        return existing?.let { brain.write(it, text) } ?: brain.create(REPORT, text)
    }

    /** The laptop's sheet for [date]: the first by name, as a day has one class. */
    private fun laptopSheet(sessions: Folder, sheets: List<Pair<String, String>>, date: LocalDate): JsonObject? =
        sheets.filter { (_, name) -> SHEET.matchEntire(name)?.groupValues?.get(1) == date.toString() }
            .minByOrNull { it.second }
            ?.let { (id, _) -> sessions.read(id) }
            ?.let { runCatching { Json.parseToJsonElement(it) as JsonObject }.getOrNull() }
}
