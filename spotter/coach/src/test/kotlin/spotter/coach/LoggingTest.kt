package spotter.coach

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

// An invented lifter logging a board made of public board lines.
class LoggingTest {
    private val vocabulary = Vocabulary.starting()
    private val identity = Identity(vocabulary)
    private val monday = LocalDate.of(2026, 3, 2)
    private val thursday = LocalDate.of(2026, 3, 5)
    private val board = listOf(
        "**Floor Block 1 - 12 minutes**",
        "* 8 x chest press",
        "* 8-10 x deadlift",
        "* 10 / 8 / 6 x squat",
        "* 8 x push-up",
        "* 8 x zorbing",
        "* 30 sec of goblet squat hold",
    )

    // A store's ids, as the log assigns them.
    private val written = mutableListOf<JournalEntry>()
    private fun write(events: List<JournalEntry>) = events.forEach { written += it.copy(id = written.size + 1L) }

    private fun sheet(day: LocalDate) =
        Sheet.make(board, Journal.history(written.filter { it.date < day }, vocabulary), identity)

    private fun save(day: LocalDate, draft: LogDraft): List<JournalEntry> {
        val sheet = sheet(day)
        val events = Logging.events(draft, sheet, Logging.logged(written, day, "2G"), day, "2G", at = written.size + 100L)
        write(events)
        return events
    }

    private fun row(sheet: Sheet, words: String) = sheet.rows.first { it.shown == words }

    // The whole loop: logged as listed on Monday, the sheet goes up a dumbbell on Thursday.
    @Test
    fun aClassLoggedAsListedRaisesTheNextSheet() {
        val first = sheet(monday)
        val press = row(first, "8 x chest press")
        assertEquals(Suggestion.NoNumber.NEW, press.suggestion)
        // Nothing to have done "as listed" on a new row: it says what was held.
        assertNull(LogDraft.asListed(press))
        save(monday, LogDraft().with(press.id, LogDraft.picked(press, 25).copy(count = 2)))

        // 8 reps at 25 was clean: up a dumbbell.
        val next = row(sheet(thursday), "8 x chest press")
        assertEquals("2×30 ▲", sheet(thursday).glance(next))
        val saved = save(thursday, LogDraft().with(next.id, LogDraft.asListed(next))).single()
        assertEquals(listOf(30, 2), listOf(saved.shownLb, saved.shownCount))
        val friday = thursday.plusDays(1)
        assertEquals("2×35 ▲", sheet(friday).glance(row(sheet(friday), "8 x chest press")))
    }

    // Saving again writes nothing; a change is a correction; clearing a row removes it.
    @Test
    fun savingWritesOnlyWhatChanged() {
        val sheet = sheet(monday)
        val press = row(sheet, "8 x chest press")
        val draft = LogDraft().with(press.id, RowLog(25, 2, 8))
        assertEquals(listOf(JournalEntry.Kind.LOGGED), save(monday, draft).map { it.kind })
        // The screen reopened: it starts from what was logged, and saving it again changes nothing.
        val reopened = LogDraft.from(sheet, Logging.logged(written, monday, "2G"))
        assertEquals(draft.rows, reopened.rows)
        assertTrue(save(monday, reopened).isEmpty())

        val corrected = save(monday, reopened.with(press.id, RowLog(25, 2, 7, hurt = true)))
        assertEquals(JournalEntry.Kind.CORRECTED, corrected.single().kind)
        assertEquals(1L, corrected.single().target)
        // Opened from a log written elsewhere since, a row the screen never showed stays logged.
        assertTrue(save(monday, LogDraft()).isEmpty())
        val removed = save(monday, LogDraft.from(sheet, Logging.logged(written, monday, "2G")).with(press.id, null))
        assertEquals(JournalEntry.Kind.REMOVED, removed.single().kind)
        // The removal names the correction, and the row is gone from history.
        assertEquals(2L, removed.single().target)
        assertTrue(Journal.history(written, vocabulary).isEmpty())
    }

    // A row with nothing to log (bodyweight, not read) writes nothing, whatever the draft says.
    @Test
    fun onlyWeightedRowsSpotterReadAreLogged() {
        val sheet = sheet(monday)
        val pushUp = row(sheet, "8 x push-up")
        val unknown = row(sheet, "8 x zorbing")
        assertEquals(listOf(false, false), listOf(pushUp, unknown).map(LogDraft::loggable))
        val draft = LogDraft().with(pushUp.id, RowLog(10, 2, 8)).with(unknown.id, RowLog(10, 2, 8))
        assertTrue(save(monday, draft).isEmpty())
    }

    // A range is logged at its low end; a ladder keeps its rounds, a side each; a timed row no reps.
    @Test
    fun eachKindOfRowIsLoggedInItsOwnTerms() {
        val sheet = sheet(monday)
        val deadlift = row(sheet, "8-10 x deadlift")
        val squat = row(sheet, "10 / 8 / 6 x squat")
        val hold = row(sheet, "30 sec of goblet squat hold")
        // A bare line on a new row: the count isn't known until the person says.
        assertEquals(RowLog(40, null, 8), LogDraft.picked(deadlift, 40))
        // A goblet hold is one dumbbell in both hands: the row's own count.
        assertEquals(RowLog(35, 1, null), LogDraft.picked(hold, 35))
        val events = save(
            monday,
            LogDraft()
                .with(deadlift.id, LogDraft.picked(deadlift, 40).copy(count = 2))
                .with(squat.id, LogDraft.picked(squat, 30).copy(count = 2))
                .with(hold.id, LogDraft.picked(hold, 35)),
        )
        val bySquat = events.associateBy { it.row }
        assertEquals(listOf(8), bySquat.getValue(deadlift.id).reps)
        assertEquals(listOf(10, 8, 6), bySquat.getValue(squat.id).ladder)
        assertEquals(listOf(10, 8, 6), bySquat.getValue(squat.id).reps)
        assertEquals(emptyList<Int>(), bySquat.getValue(hold.id).reps)
        // Each comes back as the coach reads it, and the squat as a ladder done clean.
        val history = Journal.history(written, vocabulary)
        assertEquals(3, history.size)
        // One weight for a ladder is its heaviest round's, held for the 6: round one's 10 gets
        // what that converts to, rounded down, and never a step up.
        assertTrue(bySquat.getValue(squat.id).heaviestOnly)
        val next = row(sheet(thursday), "10 / 8 / 6 x squat")
        assertEquals("2×25 =", sheet(thursday).glance(next))
        // Ticked as listed, the ladder was done at the sheet's number from round one.
        val ticked = save(thursday, LogDraft().with(next.id, LogDraft.asListed(next))).single()
        assertEquals(false, ticked.heaviestOnly)
        // What the sheet showed is kept with the event, for the scorecard: nothing, on a new row.
        assertNull(bySquat.getValue(squat.id).shownLb)
    }

    // "Last time" is the last logged, also when two fall on one day (one exercise in two blocks).
    @Test
    fun lastHeldIsTheLastLogged() {
        val deadlift = identity.read(BoardLine.read("8 x deadlift").words) as Reading.Known
        val log = listOf(Logged(monday, deadlift, Load(40, 2), 8..8, listOf(8)), Logged(monday, deadlift, Load(45, 1), 8..8, listOf(8)))
        assertEquals(Load(45, 1), Sheet.make(listOf("8 x deadlift"), log, identity).rows.single().lastHeld)
    }

    // A ladder done at the sheet's number is a listed one, whatever else is said about it; only
    // another weight makes it the heaviest round's.
    @Test
    fun onlyAnotherWeightMakesALadderHeaviestOnly() {
        val squat = identity.read(BoardLine.read("8 x squat").words) as Reading.Known
        val history = listOf(Logged(monday, squat, Load(30, 2), 8..8, listOf(8, 6, 4), ladder = listOf(8, 6, 4)))
        val sheet = Sheet.make(listOf("8 / 6 / 4 x squat"), history, identity)
        val row = sheet.rows.single()
        val listed = LogDraft.asListed(row)!!
        fun saved(log: RowLog) = Logging.events(LogDraft().with(row.id, log), sheet, emptyMap(), thursday, "2G", 1L).single()
        assertEquals(false, saved(listed.copy(count = 1, hard = true, hurt = true)).heaviestOnly)
        assertEquals(true, saved(listed.copy(lb = 50)).heaviestOnly)
    }

    // A count nobody has said stays unknown, for the screen to ask; it is never guessed.
    @Test
    fun anUnknownCountStaysUnknown() {
        val imported = Logged(monday, identity.read(BoardLine.read("8 x deadlift").words) as Reading.Known, Load(40, null), 8..8, listOf(8))
        val deadlift = Sheet.make(listOf("8 x deadlift"), listOf(imported), identity).rows.single()
        assertEquals(null, LogDraft.asListed(deadlift)!!.count)
        assertEquals(RowLog(45, null, 8), LogDraft.picked(deadlift, 45))
        // A row with no number takes its count from the last time it was logged.
        val counted = Logged(monday, identity.read(BoardLine.read("8 x deadlift").words) as Reading.Known, Load(40, 2), 8..8, listOf(8))
        val far = Sheet.make(listOf("20 x deadlift"), listOf(counted), identity).rows.single()
        assertTrue(far.suggestion is Suggestion.NoNumber)
        assertEquals(RowLog(30, 2, 20), LogDraft.picked(far, 30))
    }

    // Another day's or class's log never shows on this one's screen.
    @Test
    fun theScreenStartsFromThisClassOnly() {
        val press = row(sheet(monday), "8 x chest press")
        save(monday, LogDraft().with(press.id, RowLog(25, 2, 8)))
        assertTrue(Logging.logged(written, thursday, "2G").isEmpty())
        assertTrue(Logging.logged(written, monday, "3G").isEmpty())
    }

    // The draft survives the app being killed as text.
    @Test
    fun aDraftRoundTrips() {
        val draft = LogDraft().with("a", RowLog(25, 2, 8, easy = true, stopped = true)).with("b", RowLog(40, null, null, easier = true))
        assertEquals(draft, Json.decodeFromString<LogDraft>(Json.encodeToString(draft)))
    }
}
