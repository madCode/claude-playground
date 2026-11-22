package spotter.app

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import spotter.app.today.Boards
import spotter.app.today.Calendar
import spotter.app.today.Fetched
import spotter.app.update.Download
import spotter.app.today.TodayLoader
import spotter.coach.CalendarEvent
import spotter.coach.TodaysBoard
import java.io.File
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneId

// Feed files in the shape the carry-boards branch publishes, made of public board lines; an
// invented calendar.
class BoardsTest {
    private val dir = kotlin.io.path.createTempDirectory("boards").toFile()
    @After fun clean() { dir.deleteRecursively() }

    private val day = LocalDate.of(2026, 11, 21)
    private fun file(date: LocalDate, tag: String = "2G", line: String = "* 8 x deadlift") =
        """{"schema": "carry-board/1", "date": "$date", "classes": {"$tag": {"lines": ["$line"]}}}"""

    private fun ok(text: String) = Download.Answer(200, text)

    @Test
    fun aFetchedBoardIsKeptAndAFailedOneLeavesTheKeptOne() {
        val urls = mutableListOf<String>()
        var answer: () -> Download.Answer = { ok(file(day.minusDays(1))) }
        val boards = Boards(dir) { url -> urls += url; answer() }
        assertTrue(boards.fetch(day.minusDays(1)) is Fetched.Board)
        assertEquals("https://raw.githubusercontent.com/madCode/claude-playground/carry-boards/boards/2026-11-20.json", urls.single())
        // Offline, a server error, a page that isn't a board, or another day's file: unreachable,
        // and the kept board stays.
        answer = { throw IOException("offline") }
        assertEquals(Fetched.Unreachable, boards.fetch(day))
        answer = { Download.Answer(503, null) }
        assertEquals(Fetched.Unreachable, boards.fetch(day))
        answer = { ok("<html>rate limited</html>") }
        assertEquals(Fetched.Unreachable, boards.fetch(day))
        answer = { ok(file(day.minusDays(1))) }
        assertEquals(Fetched.Unreachable, boards.fetch(day))
        // The feed reached, with no file for the day: not posted yet.
        answer = { Download.Answer(404, null) }
        assertEquals(Fetched.NotPosted, boards.fetch(day))
        assertEquals(day.minusDays(1), boards.lastGood(day)?.date)
        assertEquals(listOf("${day.minusDays(1)}.json"), dir.list()!!.toList())
    }

    @Test
    fun aWeekIsKept() {
        val boards = Boards(dir) { url -> ok(file(LocalDate.parse(url.substringAfterLast('/').removeSuffix(".json")))) }
        (0L..9L).forEach { boards.fetch(day.minusDays(it)) }
        assertEquals(Boards.KEEP, dir.list()!!.size)
        assertEquals(day, boards.lastGood(day)?.date)
        // Nothing kept from after the day asked for.
        assertNull(boards.lastGood(day.minusDays(10)))
    }

    @Test
    fun aDamagedKeptFileIsSkipped() {
        val boards = Boards(dir) { ok(file(day.minusDays(2))) }
        boards.fetch(day.minusDays(2))
        File(dir, "${day.minusDays(1)}.json").writeText("{ half a fi")
        assertEquals(day.minusDays(2), boards.lastGood(day)?.date)
    }

    // An all-day entry is stored at UTC midnight, a timed one in real time: each is kept by the day
    // it belongs to. Carry's laptop once matched only one kind, and was wrong for weeks.
    @Test
    fun allDayAndTimedEntriesKeepTheirDay() {
        val la = ZoneId.of("America/Los_Angeles")
        val allDay = Calendar.event("Orange 60 Min 2G", day.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli(), true, la)
        assertEquals(day, allDay.date)
        assertNull(allDay.start)
        val evening = day.atTime(20, 0).atZone(la)
        val timed = Calendar.event("Orange 60 Min 2G", evening.toInstant().toEpochMilli(), false, la)
        assertEquals(day, timed.date)
        assertEquals(day.atTime(20, 0), timed.start)
    }

    // The calendar names the class; its day's board is fetched; an outage shows the kept one.
    @Test
    fun theLoaderGoesFromCalendarToBoard() {
        val events = listOf(CalendarEvent("Orange 60 Min 2G", day, day.atTime(9, 50)))
        var online = true
        var notPosted = false
        val boards = Boards(dir) { url ->
            when {
                notPosted -> Download.Answer(404, null)
                online -> ok(file(LocalDate.parse(url.substringAfterLast('/').removeSuffix(".json"))))
                else -> throw IOException("offline")
            }
        }
        val loader = TodayLoader(boards) { events }
        val now = day.atTime(8, 0)
        assertEquals(listOf("* 8 x deadlift"), (loader.load(now)!! as TodaysBoard.Lines).lines)
        online = false
        // Today's board was kept when it was fetched, so it isn't stale.
        assertNull((loader.load(now)!! as TodaysBoard.Lines).staleFrom)
        // The evening before tomorrow's class: today's board is kept, tomorrow's isn't posted.
        notPosted = true
        val evening = TodayLoader(boards) { listOf(CalendarEvent("Orange 60 Min 2G", day.plusDays(1), day.plusDays(1).atTime(9, 0))) }
        assertTrue(evening.load(day.atTime(20, 0)) is TodaysBoard.Nothing)
        // Without fetching, the board kept for the class shows at once; nothing kept, nothing yet.
        assertEquals(listOf("* 8 x deadlift"), (loader.load(now, fetch = false) as TodaysBoard.Lines).lines)
        assertEquals(null, evening.load(day.atTime(20, 0), fetch = false))
        // No calendar access: say so, rather than "no class booked".
        val noCalendar = TodayLoader(boards) { null }.load(now)!! as TodaysBoard.Nothing
        assertTrue(noCalendar.reason.contains("calendar"))
    }
}
