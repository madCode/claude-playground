package spotter.app

import org.junit.Assert.assertEquals
import org.junit.Test
import spotter.app.today.Today
import spotter.coach.BoardLine
import spotter.coach.BookedClass
import spotter.coach.Identity
import spotter.coach.Journal
import spotter.coach.Load
import spotter.coach.Reading
import spotter.coach.TodaysBoard
import spotter.coach.Vocabulary
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

// An invented lifter taking two classes in one day, on public board lines.
class TodayOfTest {
    private val vocabulary = Vocabulary.starting()
    private val identity = Identity(vocabulary)
    private val day = LocalDate.of(2026, 11, 21)
    private val press = identity.read(BoardLine.read("8 x chest press").words) as Reading.Known

    private fun at(time: LocalDateTime) = time.toInstant(ZoneOffset.UTC).toEpochMilli()
    private fun logged(className: String, lb: Int, at: LocalDateTime, id: Long) =
        Journal.logged(at(at), day, className, "row", "8 x chest press", press, Load(lb, 2), 8..8, null, listOf(8), easy = true).copy(id = id)

    private fun glance(tag: String, start: LocalDateTime?, entries: List<spotter.coach.JournalEntry>): String {
        val board = TodaysBoard.Lines(BookedClass(tag, day, start, "Orange 60 Min $tag"), listOf("* 8 x chest press"))
        val shown = Today.of(board, entries, identity, vocabulary, zone = ZoneOffset.UTC) as Today.Showing
        return shown.sheet.glance(shown.sheet.rows.single())
    }

    // The morning class's sheet is made from what was known before it: the evening's log, on
    // the same date, isn't its history. The evening class does read the morning's.
    @Test
    fun aClassReadsOnlyWhatWasLoggedBeforeItStarted() {
        val morning = day.atTime(9, 50)
        val evening = day.atTime(17, 0)
        val entries = listOf(logged("2G", 25, day.atTime(11, 0), 1), logged("3G", 30, day.atTime(18, 5), 2))
        assertEquals("new: go light", glance("2G", morning, entries))
        assertEquals("2×30 ▲", glance("3G", evening, entries))
        // Corrected in the evening, the morning's log is still the morning's: it began before.
        val corrected = entries + entries[0].copy(id = 3, at = at(day.atTime(20, 0)), kind = spotter.coach.JournalEntry.Kind.CORRECTED, target = 1, lb = 30)
        assertEquals("2×35 ▲", glance("3G", evening, corrected))
        // Logged only after the evening class began, it wasn't on that class's sheet either.
        assertEquals("new: go light", glance("3G", evening, listOf(logged("2G", 25, day.atTime(19, 0), 1))))
        // A booking with no time can't be placed in the day: it reads the day's other logs, as before.
        assertEquals("2×30 ▲", glance("3G", null, listOf(logged("2G", 25, day.atTime(19, 0), 1))))
    }
}
