package spotter.app.today

import spotter.coach.Booking
import spotter.coach.CalendarEvent
import spotter.coach.TodaysBoard
import java.time.LocalDateTime

/** Which board the sheet is made from right now: the calendar's next class, then its day's board. */
class TodayLoader(private val boards: Boards, private val events: (java.time.LocalDate) -> List<CalendarEvent>?) {
    /**
     * The board now. With [fetch] false nothing goes over the network: the kept board shows at
     * once, before a fetch on weak gym signal can take the best part of a minute. Null then when
     * nothing is kept for the class, so the screen waits for the fetch rather than saying so.
     */
    fun load(now: LocalDateTime, fetch: Boolean = true): TodaysBoard? {
        val events = events(now.toLocalDate())
            ?: return TodaysBoard.Nothing("Spotter can't read the calendar, so it doesn't know which class is next")
        val booked = Booking.next(events, now) ?: return TodaysBoard.choose(null, null, null)
        if (booked.tag == null) return TodaysBoard.choose(booked, null, null)
        if (!fetch) return boards.lastGood(booked.date)?.takeIf { it.date == booked.date }?.let { TodaysBoard.choose(booked, it, null) }
        val fetched = boards.fetch(booked.date)
        return TodaysBoard.choose(booked, (fetched as? Fetched.Board)?.board, boards.lastGood(booked.date), notPosted = fetched == Fetched.NotPosted)
    }
}
