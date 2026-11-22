package spotter.coach

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

// Invented calendar entries, and boards in the feed's shape made of public board lines.
class TodayTest {
    private val day = LocalDate.of(2026, 11, 21)
    private val nine = day.atTime(9, 50)

    private fun board(date: LocalDate, vararg classes: Pair<String, List<String>>) =
        DayBoard(date, classes.toMap())

    @Test
    fun aFeedFileIsReadOrRefused() {
        val text = """{"schema": "carry-board/1", "date": "2026-11-21", "thread": "x",
            "classes": {"2G": {"comment": "c", "lines": ["**Floor Block 1**", "* 8 x deadlift"]}, "3G": {"lines": []}}}"""
        val b = DayBoard.parse(text)!!
        assertEquals(day, b.date)
        assertEquals(listOf("**Floor Block 1**", "* 8 x deadlift"), b.classes["2G"])
        // A class with no lines isn't a board.
        assertNull(b.classes["3G"])
        assertNull(DayBoard.parse(text.replace("carry-board/1", "carry-board/2")))
        assertNull(DayBoard.parse(text.replace("2026-11-21", "tomorrow")))
        assertNull(DayBoard.parse("<html>rate limited</html>"))
        // A file with no class read is no board, so it can't hide an older good one.
        assertNull(DayBoard.parse("""{"schema": "carry-board/1", "date": "2026-11-21", "classes": {}}"""))
        assertNull(DayBoard.parse("""{"schema": "carry-board/1", "date": "2026-11-21", "classes": {"2G": {"lines": [1, 2]}}}"""))
        assertNull(DayBoard.parse("x".repeat(DayBoard.MAX_CHARS + 1)))
    }

    @Test
    fun theClassIsTheNextOneBooked() {
        val events = listOf(
            CalendarEvent("Dentist", day, nine),
            CalendarEvent("Orange 60 - Tornado", day, nine),
            CalendarEvent("Orange 60 Min 3G", day, day.atTime(12, 0)),
            CalendarEvent("Orange 60 Min 2G", day.plusDays(1), day.plusDays(1).atTime(9, 0)),
        )
        assertEquals(BookedClass("3G", day, day.atTime(12, 0), "Orange 60 Min 3G"), Booking.next(events, nine))
        // After today's class, tomorrow's.
        assertEquals("2G", Booking.next(events, day.atTime(16, 0))?.tag)
        // Lower case and an all-day entry.
        assertEquals(BookedClass("2G", day, null, "orange 60 min 2g"), Booking.next(listOf(CalendarEvent("orange 60 min 2g", day, null)), nine))
        assertNull(Booking.next(listOf(CalendarEvent("Lunch with an orange", day, nine)), nine))
        // Three hours after it starts, today's class is still the one, so it can be logged in the car.
        assertEquals(day, Booking.next(events, day.atTime(14, 59))?.date)
        assertEquals(day.plusDays(1), Booking.next(events, day.atTime(15, 1))?.date)
    }

    // Review: a class today that names no format stops tomorrow's showing in its place.
    @Test
    fun aClassWithNoFormatIsStillTodaysClass() {
        val events = listOf(
            CalendarEvent("Orange 60 Min Tread 50", day, nine),
            CalendarEvent("Orange 60 Min 2G", day.plusDays(1), nine.plusDays(1)),
        )
        val booked = Booking.next(events, day.atTime(7, 0))!!
        assertNull(booked.tag)
        val chosen = TodaysBoard.choose(booked, null, board(day, "2G" to listOf("x"))) as TodaysBoard.Nothing
        assertTrue(chosen.reason.contains("Tread 50"))
    }

    // Review: in the evening, tomorrow's board isn't posted yet; that isn't an outage, and
    // today's board must not show as tomorrow's.
    @Test
    fun notPostedYetIsNotAnOutage() {
        val tomorrow = BookedClass("2G", day.plusDays(1), nine.plusDays(1))
        val today = board(day, "2G" to listOf("* 8 x squat"))
        assertTrue(TodaysBoard.choose(tomorrow, null, today, notPosted = true) is TodaysBoard.Nothing)
        assertEquals(day, (TodaysBoard.choose(tomorrow, null, today, notPosted = false) as TodaysBoard.Lines).staleFrom)
    }

    @Test
    fun theDaysOwnBoardFirst() {
        val booked = BookedClass("2G", day, nine)
        val today = board(day, "2G" to listOf("* 8 x deadlift"), "3G" to listOf("* 8 x squat"))
        assertEquals(TodaysBoard.Lines(booked, listOf("* 8 x deadlift")), TodaysBoard.choose(booked, today, null))
    }

    // Trap 9: a class with no board of its own borrows the other format's, labelled.
    @Test
    fun aClassWithNoBoardBorrowsTheOther() {
        val booked = BookedClass("2G", day, nine)
        val today = board(day, "3G" to listOf("* 8 x squat"))
        val chosen = TodaysBoard.choose(booked, today, null) as TodaysBoard.Lines
        assertEquals("3G", chosen.borrowedFrom)
        assertNull(chosen.staleFrom)
    }

    // An outage shows the last good board, clearly marked with its date, never a blank or a
    // later day's.
    @Test
    fun anOutageShowsTheLastGoodBoardMarked() {
        val booked = BookedClass("2G", day, nine)
        val yesterday = board(day.minusDays(1), "2G" to listOf("* 8 x squat"))
        val chosen = TodaysBoard.choose(booked, null, yesterday) as TodaysBoard.Lines
        assertEquals(day.minusDays(1), chosen.staleFrom)
        // A fetched board for another day is no help, and a kept board from after the class is none.
        assertEquals(day.minusDays(1), (TodaysBoard.choose(booked, board(day.plusDays(1), "2G" to listOf("x")), yesterday) as TodaysBoard.Lines).staleFrom)
        assertTrue(TodaysBoard.choose(booked, null, board(day.plusDays(1), "2G" to listOf("x"))) is TodaysBoard.Nothing)
        assertTrue(TodaysBoard.choose(booked, null, null) is TodaysBoard.Nothing)
        assertTrue(TodaysBoard.choose(null, yesterday, yesterday) is TodaysBoard.Nothing)
    }

    // A real day from the feed (a public r/orangetheory board), read the way the phone reads it.
    @Test
    fun aRealFeedDayReads() {
        val text = javaClass.getResource("/boards/2026-11-21.json")!!.readText()
        val board = DayBoard.parse(text)!!
        val chosen = TodaysBoard.choose(BookedClass("2G", day, nine), board, null) as TodaysBoard.Lines
        assertTrue(Sheet.make(chosen.lines, emptyList(), Identity(Vocabulary.starting())).text().contains("8 x deadlift  new: go light"))
    }
}
