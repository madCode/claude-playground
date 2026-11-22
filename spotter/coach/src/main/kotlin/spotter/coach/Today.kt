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
data class BookedClass(val tag: String?, val date: LocalDate, val start: LocalDateTime?, val title: String = "")

/** A calendar entry, as the phone's calendar gives it. */
data class CalendarEvent(val title: String, val date: LocalDate, val start: LocalDateTime?)

object Booking {
    // "Orange 60 Min 2G": the format is in the title, as the studio's booking puts it there.
    // A booking's title starts with the studio's name; "Lunch with an orange" isn't one.
    private val orange = Regex("""^\W*orange\b""", RegexOption.IGNORE_CASE)
    private val format = Regex("""orange.*?(\dg)\b""", RegexOption.IGNORE_CASE)

    /**
     * The next booked class in [events] from [now]: today's if one hasn't started yet (or has no
     * time), otherwise tomorrow's. Nobody picks the class by hand.
     */
    fun next(events: List<CalendarEvent>, now: LocalDateTime): BookedClass? =
        events.asSequence()
            .filter { it.date == now.toLocalDate() || it.date == now.toLocalDate().plusDays(1) }
            .sortedWith(compareBy({ it.date }, { it.start ?: it.date.atStartOfDay() }))
            .filter { it.start == null || !it.start.isBefore(now.minusHours(1)) }
            .filter { orange.containsMatchIn(it.title) }
            .groupBy { it.date }.entries.firstOrNull()?.value
            // A day's class with a format first ("Orange 60 - Tornado" may be a theme beside it);
            // a day with only one that names none still stops tomorrow's showing in its place.
            ?.let { day -> day.firstOrNull { format.containsMatchIn(it.title) } ?: day.first() }
            ?.let { e -> BookedClass(format.find(e.title)?.groupValues?.get(1)?.uppercase(), e.date, e.start, e.title) }
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
    data class Nothing(val reason: String) : TodaysBoard

    companion object {
        /**
         * The board for [booked] from the day's [fetched] board when there is one, else the
         * [lastGood] board kept from before, marked with its date. [notPosted] says the feed was
         * reached and has no file for the day yet.
         */
        fun choose(booked: BookedClass?, fetched: DayBoard?, lastGood: DayBoard?, notPosted: Boolean = false): TodaysBoard {
            booked ?: return Nothing("no class booked today or tomorrow")
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
