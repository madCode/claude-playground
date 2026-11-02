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
        val allowed = calendar.allowed()
        val days = listOf(now.toLocalDate(), now.toLocalDate().plusDays(1)).map { date ->
            val events = if (allowed) calendar.eventsOn(date) else null
            val found = events?.let(ClassCalendar::classOn)
            val problem = when {
                !allowed -> "no_calendar_access"
                events == null -> "calendar_unreadable"
                else -> null
            }
            ClassCalendar.day(date, found, problem, laptopSheet(sessions, sheets, date, found?.classTag))
        }
        val text = ClassCalendar.report(days, build, now).toString() + "\n"
        val existing = brain.list().firstOrNull { it.second == REPORT }?.first
        return existing?.let { brain.write(it, text) } ?: brain.create(REPORT, text)
    }

    /**
     * The laptop's sheet for [date]. A day can hold two (two classes, or a rebooking): the one
     * for the phone's class when there is one, else the first by name, so a second sheet never
     * makes a false difference or hides a real one.
     */
    private fun laptopSheet(sessions: Folder, sheets: List<Pair<String, String>>, date: LocalDate, tag: String?): JsonObject? =
        sheets.mapNotNull { (id, name) -> SHEET.matchEntire(name)?.takeIf { it.groupValues[1] == date.toString() }?.let { Triple(id, name, it.groupValues[2]) } }
            .sortedBy { it.second }
            .let { day -> day.firstOrNull { it.third == tag } ?: day.firstOrNull() }
            ?.let { (id, _, _) -> sessions.read(id) }
            ?.let { runCatching { Json.parseToJsonElement(it) as JsonObject }.getOrNull() }
}
