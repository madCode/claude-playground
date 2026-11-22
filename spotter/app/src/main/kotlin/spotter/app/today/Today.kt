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
        /** Logged for this class from rows no longer on the board. */
        val strays: List<JournalEntry> = emptyList(),
    ) : Today

    companion object {
        private val day = DateTimeFormatter.ofPattern("EEE d MMM", Locale.UK)
        private val time = DateTimeFormatter.ofPattern("H:mm", Locale.UK)

        /**
         * The screen's state from the whole event log. The sheet's numbers come from before this
         * class, so logging it shows what was done, not next time's number in its place.
         */
        fun of(
            chosen: TodaysBoard, entries: List<JournalEntry>, identity: Identity, vocabulary: Vocabulary,
            /** History from elsewhere ([spotter.app.history.HistoryPick.beside]), read with the log's. */
            imported: List<Logged> = emptyList(),
        ): Today {
            val lines = chosen as? TodaysBoard.Lines ?: return from(chosen, emptyList(), identity)
            val date = lines.booked.date
            val name = className(lines)
            val before = entries.filter { it.date < date || (it.date == date && it.className != name) }
            // By date, the imported rows first within a day: what was logged here is the later word.
            val history = (imported.filter { it.date < date } + Journal.history(before, vocabulary)).sortedBy { it.date }
            return from(chosen, history, identity, entries)
        }

        private fun className(lines: TodaysBoard.Lines) = lines.booked.logName

        /**
         * What to show once the [kept] board has been looked for and before the fetch returns:
         * the kept one when there is one. Without one, what is [showing] stays, unless it is a
         * class since logged: the sheet has moved on from it, so it would sit there with its
         * log missing for as long as the fetch takes, looking as though the save was lost.
         */
        fun afterKept(showing: Today, kept: Today?, logged: Set<Pair<LocalDate, String>>): Today = when {
            kept != null -> kept
            showing is Showing && (showing.date to showing.className) in logged -> Loading
            else -> showing
        }

        /** The classes with anything logged, as [spotter.coach.Booking.next] takes them. */
        fun loggedClasses(entries: List<JournalEntry>): Set<Pair<LocalDate, String>> =
            Journal.live(entries).map { it.date to it.className }.toSet()

        /**
         * The screen's state for the board [chosen], with the sheet made from [log]; the class's
         * own events are picked from [entries] and set against the sheet's rows.
         */
        fun from(chosen: TodaysBoard, log: List<Logged>, identity: Identity, entries: List<JournalEntry> = emptyList()): Today = when (chosen) {
            is TodaysBoard.Nothing -> Nothing(chosen.reason.replaceFirstChar { it.uppercase() } + ".")
            is TodaysBoard.Lines -> {
                val booked = chosen.booked
                val title = listOfNotNull(booked.tag, booked.date.format(day), booked.start?.format(time)).joinToString(" · ")
                val notes = listOfNotNull(
                    chosen.staleFrom?.let { "This is the board from ${it.format(day)}: the one for ${booked.date.format(day)} couldn't be fetched." },
                    chosen.borrowedFrom?.let { "No ${booked.tag} board yet, so this is the $it board. Leave rows that aren't in your class." },
                )
                val sheet = Sheet.make(chosen.lines, log, identity, chosen.borrowedFrom)
                val name = className(chosen)
                val logged = Logging.logged(sheet, entries, booked.date, name)
                Showing(title, notes, sheet, booked.date, name, logged.byRow, logged.strays)
            }
        }
    }
}
