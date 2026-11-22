package spotter.app.today

import spotter.coach.Booking
import spotter.coach.CalendarEvent
import spotter.coach.TodaysBoard
import java.time.LocalDate
import java.time.LocalDateTime

/** Which board the sheet is made from right now: the calendar's next class, then its day's board. */
class TodayLoader(private val boards: Boards, private val events: (LocalDate) -> List<CalendarEvent>?) {
    /**
     * The board now. With [fetch] false nothing goes over the network: the kept board shows at
     * once, before a fetch on weak gym signal can take the best part of a minute. Null then when
     * nothing is kept for the class, so the screen waits for the fetch rather than saying so.
     */
    fun load(now: LocalDateTime, fetch: Boolean = true, logged: Set<Pair<LocalDate, String>> = emptySet()): TodaysBoard? {
        val events = events(now.toLocalDate())
            ?: return TodaysBoard.Nothing("Spotter can't read the calendar, so it doesn't know which class is next")
        val booked = Booking.next(events, now, logged) ?: return TodaysBoard.choose(null, null, null)
        if (booked.tag == null) return TodaysBoard.choose(booked, null, null)
        if (!fetch) return boards.lastGood(booked.date)?.takeIf { it.date == booked.date }?.let { TodaysBoard.choose(booked, it, null) }
        val fetched = boards.fetch(booked.date)
        return TodaysBoard.choose(booked, (fetched as? Fetched.Board)?.board, boards.lastGood(booked.date), notPosted = fetched == Fetched.NotPosted)
    }

    /**
     * The latest class already started, other than the one the sheet shows, with its own day's
     * board as kept on the phone: still loggable after the sheet has moved on. Null when there is
     * none in the last two days, or its board wasn't kept. Nothing goes over the network.
     */
    fun earlier(now: LocalDateTime, logged: Set<Pair<LocalDate, String>> = emptySet()): TodaysBoard.Lines? {
        val today = now.toLocalDate()
        val recent = events(today) ?: return null
        val past = events(today.minusDays(Booking.EARLIER_DAYS)) ?: return null
        val booked = Booking.earlier((past + recent).distinct(), now, Booking.next(recent, now, logged), logged) ?: return null
        val board = boards.lastGood(booked.date)?.takeIf { it.date == booked.date } ?: return null
        return TodaysBoard.choose(booked, board, null) as? TodaysBoard.Lines
    }
}
