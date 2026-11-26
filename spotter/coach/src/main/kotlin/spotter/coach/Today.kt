package spotter.coach

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * One day's boards from the feed (`boards/YYYY-MM-DD.json` on the `carry-boards` branch, schema
 * `carry-board/1`): each class's workout lines, in order.
 */
data class DayBoard(val date: LocalDate, val classes: Map<String, List<String>>) {
    companion object {
        const val SCHEMA = "carry-board/1"

        // A day's file is a few kilobytes; anything far larger isn't one.
        const val MAX_CHARS = 256 * 1024
        private const val MAX_LINES = 400

        /**
         * The board in [text], or null when it isn't a day's board this app can trust: not JSON,
         * another schema, a date that isn't one, or a class with no lines. The feed is public and
         * comes over the network, so anything off is refused rather than half read.
         */
        fun parse(text: String): DayBoard? {
            if (text.length > MAX_CHARS) return null
            val root = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return null
            if ((root["schema"] as? JsonPrimitive)?.content != SCHEMA) return null
            val date = (root["date"] as? JsonPrimitive)?.content?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
            val classes = (root["classes"] as? JsonObject) ?: return null
            val read = classes.mapNotNull { (tag, value) ->
                val lines = ((value as? JsonObject)?.get("lines") as? JsonArray)
                    ?.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
                    ?.takeIf { it.isNotEmpty() && it.size <= MAX_LINES }
                    ?: return@mapNotNull null
                tag to lines
            }.toMap()
            // No class read is no board: kept, it would hide an older good one.
            if (read.isEmpty()) return null
            return DayBoard(date, read)
        }
    }
}

/**
 * The class booked in the calendar: its format ("2G") and when it starts, if it's timed. [tag] is
 * null when the booking names no format ("Orange 60 Min Tread 50"): that class is still the next
 * one, so tomorrow's isn't shown in its place.
 */
data class BookedClass(val tag: String?, val date: LocalDate, val start: LocalDateTime?, val title: String = "") {
    /** The name its log is kept under, with [date]. */
    val logName: String get() = tag ?: title
}

/** A calendar entry, as the phone's calendar gives it. */
data class CalendarEvent(val title: String, val date: LocalDate, val start: LocalDateTime?)

object Booking {
    /** A 60-minute class, the drive and the logging, with room to spare. */
    val KEPT_AFTER_START: java.time.Duration = java.time.Duration.ofHours(3)

    // "Orange 60 Min 2G": the format is in the title, as the studio's booking puts it there.
    // A booking's title starts with the studio's name; "Lunch with an orange" isn't one.
    private val orange = Regex("""^\W*orange\b""", RegexOption.IGNORE_CASE)
    private val format = Regex("""orange.*?(\dg)\b""", RegexOption.IGNORE_CASE)

    /**
     * How long after its start a logged class is still the sheet's: saving the first block's rows
     * mid-class mustn't swap the sheet for tomorrow's before the class is over.
     */
    val CLASS_LENGTH: java.time.Duration = java.time.Duration.ofMinutes(50)

    /** How far back a class can still be logged, or its log changed, from the home screen. */
    const val EARLIER_DAYS = 2L

    private fun classes(events: List<CalendarEvent>): List<BookedClass> = events
        .filter { orange.containsMatchIn(it.title) }
        .sortedWith(compareBy({ it.date }, { it.start ?: it.date.atStartOfDay() }))
        .map { e -> BookedClass(format.find(e.title)?.groupValues?.get(1)?.uppercase(), e.date, e.start, e.title) }

    /**
     * The next booked class in [events] from [now]: today's until [KEPT_AFTER_START] after it
     * starts (or all day when it has no time), otherwise tomorrow's. Nobody picks the class by
     * hand. The class stays long enough to be logged in the car afterwards, and once it is
     * [logged] (by date and [BookedClass.logName]) the sheet moves on to the next one, so the
     * evening before a morning class shows that class (madCode, 2026-11-22).
     */
    fun next(events: List<CalendarEvent>, now: LocalDateTime, logged: Set<Pair<LocalDate, String>> = emptySet()): BookedClass? {
        val today = now.toLocalDate()
        fun done(c: BookedClass) = (c.date to c.logName) in logged && (c.start == null || !c.start.plus(CLASS_LENGTH).isAfter(now))
        val days = classes(events).filter { it.date == today || it.date == today.plusDays(1) }
        // A day whose class is logged is over: an entry beside it that names no format ("Orange
        // 60 - Tornado", the day's theme) doesn't keep the day open, at any hour.
        val over = days.filter(::done).map { it.date }.toSet()
        val upcoming = days.filter { it.start == null || !it.start.isBefore(now.minus(KEPT_AFTER_START)) }
        return upcoming.filterNot { done(it) || (it.tag == null && it.date in over) }
            .groupBy { it.date }.entries.firstOrNull()?.value
            // A day's class with a format first ("Orange 60 - Tornado" may be a theme beside it);
            // a day with only one that names none still stops tomorrow's showing in its place.
            ?.let { day -> day.firstOrNull { it.tag != null } ?: day.first() }
    }

    /**
     * The latest class that has started, within [EARLIER_DAYS], other than the one the sheet
     * shows ([current]): still loggable after the sheet has moved on, so coffee after class
     * doesn't cost the log. Only a class that names its format has a board to log against.
     */
    fun earlier(
        events: List<CalendarEvent>, now: LocalDateTime, current: BookedClass?, logged: Set<Pair<LocalDate, String>> = emptySet(),
    ): BookedClass? {
        val today = now.toLocalDate()
        // By day and name, as the log is kept: one class entered twice in the calendar under
        // two titles isn't offered beside itself.
        val shown = current?.let { it.date to it.logName }
        return classes(events).lastOrNull { c ->
            c.tag != null && (c.date to c.logName) != shown && !c.date.isBefore(today.minusDays(EARLIER_DAYS)) &&
                // An all-day entry has started once its day is past, or once it is logged.
                (c.start?.let { !it.isAfter(now) } ?: (c.date.isBefore(today) || (c.date to c.logName) in logged))
        }
    }
}

/** Which board lines the sheet is made from, and what the sheet must say about them. */
sealed interface TodaysBoard {
    /**
     * The lines for [booked]. [borrowedFrom] is the other format whose board this is, when the
     * class had none of its own (trap 9). [staleFrom] is the date of the board shown when the
     * class's own day couldn't be fetched: the last good one, clearly marked, never a blank.
     */
    data class Lines(
        val booked: BookedClass,
        val lines: List<String>,
        val borrowedFrom: String? = null,
        val staleFrom: LocalDate? = null,
    ) : TodaysBoard

    /** Nothing to show, and why: no class booked, or no board posted yet. */
    data class Nothing(val reason: String, /** No class is coming, rather than a class without a board. */ val noClass: Boolean = false) : TodaysBoard

    companion object {
        /**
         * The board for [booked] from the day's [fetched] board when there is one, else the
         * [lastGood] board kept from before, marked with its date. [notPosted] says the feed was
         * reached and has no file for the day yet.
         */
        fun choose(booked: BookedClass?, fetched: DayBoard?, lastGood: DayBoard?, notPosted: Boolean = false): TodaysBoard {
            booked ?: return Nothing("no class booked today or tomorrow", noClass = true)
            if (booked.tag == null) return Nothing("the next class, \"${booked.title}\", names no format (2G or 3G)")
            val day = fetched?.takeIf { it.date == booked.date }
            if (day != null) return fromDay(booked, day, stale = false) ?: Nothing("no board posted for ${booked.date} yet")
            // The feed was reached and has no file for the day: it isn't posted yet, which is not
            // an outage. An earlier day's board would read as this class's.
            if (notPosted) return lastGood?.takeIf { it.date == booked.date }?.let { fromDay(booked, it, stale = false) }
                ?: Nothing("no board posted for ${booked.date} yet")
            val kept = lastGood?.takeIf { !it.date.isAfter(booked.date) }
                ?: return Nothing("no board for ${booked.date}, and none kept from before")
            return fromDay(booked, kept, stale = kept.date != booked.date)
                ?: Nothing("no board for ${booked.date}, and none kept from before")
        }

        private fun fromDay(booked: BookedClass, day: DayBoard, stale: Boolean): Lines? {
            val staleFrom = day.date.takeIf { stale }
            day.classes[booked.tag]?.let { return Lines(booked, it, staleFrom = staleFrom) }
            val (other, lines) = day.classes.entries.sortedBy { it.key }.firstOrNull() ?: return null
            return Lines(booked, lines, borrowedFrom = other, staleFrom = staleFrom)
        }
    }
}
