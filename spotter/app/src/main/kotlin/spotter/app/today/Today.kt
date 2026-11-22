package spotter.app.today

import spotter.coach.Identity
import spotter.coach.Journal
import spotter.coach.JournalEntry
import spotter.coach.Logged
import spotter.coach.Logging
import spotter.coach.Sheet
import spotter.coach.TodaysBoard
import spotter.coach.Vocabulary
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** What the home screen shows in its middle. */
sealed interface Today {
    data object Loading : Today

    /** Spotter may not read the calendar, so it doesn't know which class is next. */
    data object NeedsCalendar : Today

    /** Nothing to show, and why, in a sentence. */
    data class Nothing(val reason: String) : Today

    /**
     * The sheet for the next class. [title] names the class and when ("2G · Sat 22 Nov · 9:50");
     * [notes] say what the person must know about the board before trusting it: borrowed from
     * the other format, or an earlier day's because today's couldn't be fetched.
     */
    data class Showing(
        val title: String,
        val notes: List<String>,
        val sheet: Sheet,
        /** The class, as its log is kept. */
        val date: LocalDate,
        val className: String,
        /** What is logged for this class already, by sheet row. */
        val logged: Map<String, JournalEntry> = emptyMap(),
    ) : Today

    companion object {
        private val day = DateTimeFormatter.ofPattern("EEE d MMM", Locale.UK)
        private val time = DateTimeFormatter.ofPattern("H:mm", Locale.UK)

        /**
         * The screen's state from the whole event log. The sheet's numbers come from before this
         * class, so logging it shows what was done, not next time's number in its place.
         */
        fun of(chosen: TodaysBoard, entries: List<JournalEntry>, identity: Identity, vocabulary: Vocabulary): Today {
            val lines = chosen as? TodaysBoard.Lines ?: return from(chosen, emptyList(), identity)
            val date = lines.booked.date
            val name = className(lines)
            val before = entries.filter { it.date < date || (it.date == date && it.className != name) }
            return from(chosen, Journal.history(before, vocabulary), identity, Logging.logged(entries, date, name))
        }

        private fun className(lines: TodaysBoard.Lines) = lines.booked.logName

        /** The classes with anything logged, as [spotter.coach.Booking.next] takes them. */
        fun loggedClasses(entries: List<JournalEntry>): Set<Pair<LocalDate, String>> =
            Journal.live(entries).map { it.date to it.className }.toSet()

        /** The screen's state for the board [chosen], with the sheet made from [log]. */
        fun from(chosen: TodaysBoard, log: List<Logged>, identity: Identity, logged: Map<String, JournalEntry> = emptyMap()): Today = when (chosen) {
            is TodaysBoard.Nothing -> Nothing(chosen.reason.replaceFirstChar { it.uppercase() } + ".")
            is TodaysBoard.Lines -> {
                val booked = chosen.booked
                val title = listOfNotNull(booked.tag, booked.date.format(day), booked.start?.format(time)).joinToString(" · ")
                val notes = listOfNotNull(
                    chosen.staleFrom?.let { "This is the board from ${it.format(day)}: the one for ${booked.date.format(day)} couldn't be fetched." },
                    chosen.borrowedFrom?.let { "No ${booked.tag} board yet, so this is the $it board. Leave rows that aren't in your class." },
                )
                Showing(title, notes, Sheet.make(chosen.lines, log, identity, chosen.borrowedFrom), booked.date, className(chosen), logged)
            }
        }
    }
}
