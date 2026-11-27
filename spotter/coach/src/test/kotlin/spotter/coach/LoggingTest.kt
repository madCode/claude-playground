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
        val events = Logging.events(draft, sheet, Logging.logged(sheet, written, day, "2G"), day, "2G", at = written.size + 100L)
        write(events)
        return events
    }

    private fun row(sheet: Sheet, words: String) = sheet.rows.first { it.shown == words }

    // The whole loop: what is logged on Monday is Thursday's number, and two ticks as listed in
    // a row take the sheet up a dumbbell.
    @Test
    fun aClassLoggedAsListedRaisesTheNextSheet() {
        val first = sheet(monday)
        val press = row(first, "8 x chest press")
        assertEquals(Suggestion.NoNumber.NEW, press.suggestion)
        // Nothing to have done "as listed" on a new row: it says what was held.
        assertNull(LogDraft.asListed(press))
        save(monday, LogDraft().with(press.id, LogDraft.picked(press, 25).copy(count = 2)))

        // 8 reps at 25 was clean, once: the same again.
        val next = row(sheet(thursday), "8 x chest press")
        assertEquals("2×25 =", sheet(thursday).glance(next))
        val saved = save(thursday, LogDraft().with(next.id, LogDraft.asListed(next))).single()
        assertEquals(listOf(25, 2), listOf(saved.shownLb, saved.shownCount))
        // Ticked as listed a second time running: up a dumbbell.
        val friday = thursday.plusDays(1)
        assertEquals("2×30 ▲", sheet(friday).glance(row(sheet(friday), "8 x chest press")))
        // And "felt easy" on the new weight takes it up after one.
        val third = row(sheet(friday), "8 x chest press")
        save(friday, LogDraft().with(third.id, LogDraft.asListed(third)!!.copy(easy = true)))
        val saturday = friday.plusDays(1)
        assertEquals("2×35 ▲", sheet(saturday).glance(row(sheet(saturday), "8 x chest press")))
    }

    // Saving again writes nothing; a change is a correction; clearing a row removes it.
    @Test
    fun savingWritesOnlyWhatChanged() {
        val sheet = sheet(monday)
        val press = row(sheet, "8 x chest press")
        val draft = LogDraft().with(press.id, RowLog(25, 2, 8))
        assertEquals(listOf(JournalEntry.Kind.LOGGED), save(monday, draft).map { it.kind })
        // The screen reopened: it starts from what was logged, and saving it again changes nothing.
        val reopened = LogDraft.from(sheet, Logging.logged(sheet, written, monday, "2G").byRow)
        assertEquals(draft.rows, reopened.rows)
        assertTrue(save(monday, reopened).isEmpty())

        val corrected = save(monday, reopened.with(press.id, RowLog(25, 2, 7, hurt = true)))
        assertEquals(JournalEntry.Kind.CORRECTED, corrected.single().kind)
        assertEquals(1L, corrected.single().target)
        // Opened from a log written elsewhere since, a row the screen never showed stays logged.
        assertTrue(save(monday, LogDraft()).isEmpty())
        val removed = save(monday, LogDraft.from(sheet, Logging.logged(sheet, written, monday, "2G").byRow).with(press.id, null))
        assertEquals(JournalEntry.Kind.REMOVED, removed.single().kind)
        // The removal names the correction, and the row is gone from history.
        assertEquals(2L, removed.single().target)
        assertTrue(Journal.history(written, vocabulary).isEmpty())
    }

    // A row Spotter didn't read writes nothing, whatever the draft says.
    @Test
    fun onlyRowsSpotterReadAreLogged() {
        val sheet = sheet(monday)
        val pushUp = row(sheet, "8 x push-up")
        val unknown = row(sheet, "8 x zorbing")
        assertEquals(listOf(true, false), listOf(pushUp, unknown).map(LogDraft::loggable))
        assertEquals(listOf(true, false), listOf(pushUp, unknown).map(LogDraft::bare))
        assertTrue(save(monday, LogDraft().with(unknown.id, RowLog(10, 2, 8))).isEmpty())
    }

    // A push-up ticked as listed is done with nothing held: kept in the log and the backup's
    // full copy, but no part of what the coach reads or the scorecard grades. Saved again,
    // nothing more is written; cleared, it is removed.
    @Test
    fun aRowWithNoWeightIsTickedAsDone() {
        val pushUp = row(sheet(monday), "8 x push-up")
        val ticked = LogDraft.asListed(pushUp)!!
        assertEquals(RowLog(RowLog.NOTHING, null, 8), ticked)
        val entry = save(monday, LogDraft().with(pushUp.id, ticked.copy(hard = true))).single()
        assertEquals(listOf(0, null, listOf(8), true), listOf(entry.lb, entry.count, entry.reps, entry.hard))
        assertTrue(Journal.history(written, vocabulary).isEmpty())
        assertEquals("push-up", Journal.bare(written, vocabulary).single().second.let(Estimated::name))
        val files = Export.files(written)
        assertEquals(setOf(Export.LOG_FILE), files.keys)
        assertTrue(files.getValue(Export.LOG_FILE).contains("\"lb\":0"))
        val log = Logging.logged(sheet(monday), written, monday, "2G")
        val draft = LogDraft.from(sheet(monday), log.byRow)
        assertEquals(ticked.copy(hard = true), draft.rows[pushUp.id])
        assertTrue(Logging.events(draft, sheet(monday), log, monday, "2G", 5L).isEmpty())
        assertEquals(JournalEntry.Kind.REMOVED, Logging.events(draft.with(pushUp.id, null), sheet(monday), log, monday, "2G", 5L).single().kind)
    }

    // A weight added to a push-up is logged like any other, and the next sheet still gives
    // the push-up no number (madCode, 2026-11-27): what was held is said in the row's why.
    // It lends nothing to a weighted lift either.
    @Test
    fun aWeightAddedToABodyweightRowIsNeverSuggested() {
        val pushUp = row(sheet(monday), "8 x push-up")
        assertEquals(listOf("Done without a weight."), pushUp.why)
        val entry = save(monday, LogDraft().with(pushUp.id, LogDraft.picked(pushUp, 10).copy(count = 1))).single()
        assertEquals(listOf(10, 1), listOf(entry.lb, entry.count))
        val next = sheet(monday.plusDays(2))
        val again = row(next, "8 x push-up")
        assertEquals(null, again.suggestion)
        assertEquals("no weight", next.glance(again))
        assertEquals(listOf("Done without a weight.", "Last time you held 1×10."), again.why)
        assertEquals(RowLog(RowLog.NOTHING, null, 8), LogDraft.asListed(again))
        assertEquals(Suggestion.NoNumber.NEW, row(next, "8 x chest press").suggestion)
        // Nor is it graded: the sheet was never going to offer that row a number.
        assertEquals(setOf(Export.LOG_FILE), Export.files(written).keys)
        // A row that was shown a number when logged stays graded, whatever the lists say now.
        assertEquals(2, Export.files(listOf(written.single().copy(shownLb = 10, shownCount = 1))).size)
    }

    // The version of a bodyweight move that names a dumbbell is the same move with the weight
    // said, so a weight added to the bare one is a session of it: "dumbbell crunch" has a
    // number once a crunch was done holding one (madCode: translate between the very similar).
    @Test
    fun aWeightAddedToABodyweightRowCountsForItsWeightedTwin() {
        fun known(line: String) = identity.read(BoardLine.read(line).words) as Reading.Known
        val weighted = known("12 x dumbbell crunch")
        val bare = known("12 x crunch")
        assertEquals(listOf(true, false), listOf(weighted, bare).map(Sheet::wantsWeight))
        val added = Logged(monday, bare, Load(10, 2), 12..12, listOf(12))
        assertEquals(Load(10, 2), (Progression.suggest(weighted, 12..12, listOf(added)) as Suggestion.Weight).load)
    }

    // A range offers each of its counts in place of the tick, and the count tapped is what the
    // coach reads: the top of the range twice running goes up, the low end twice holds.
    @Test
    fun aRangeIsLoggedAtTheCountThatWasDone() {
        val first = row(sheet(monday), "8-10 x deadlift")
        // Nothing to have done on a new row, and one count has its tick.
        assertNull(LogDraft.counts(first))
        save(monday, LogDraft().with(first.id, LogDraft.picked(first, 40).copy(count = 2, reps = 10)))
        assertNull(LogDraft.counts(row(sheet(thursday), "8 x chest press")))

        val next = row(sheet(thursday), "8-10 x deadlift")
        assertEquals(listOf(8, 9, 10), LogDraft.counts(next))
        val friday = thursday.plusDays(1)
        val before = written.toList()
        save(thursday, LogDraft().with(next.id, LogDraft.asListed(next)!!.copy(reps = 10)))
        assertEquals("2×45 ▲", sheet(friday).glance(row(sheet(friday), "8-10 x deadlift")))
        // The same day at the low end instead: within the range, so the weight holds.
        written.clear(); written += before
        save(thursday, LogDraft().with(next.id, LogDraft.asListed(next)!!.copy(reps = 8)))
        assertEquals("2×40 =", sheet(friday).glance(row(sheet(friday), "8-10 x deadlift")))
    }

    // Counts are offered in the board's own numbers, and a range too wide for a button each
    // offers its two ends.
    @Test
    fun aRangesCountsAreTheBoardsNumbers() {
        val lines = listOf("* 12 - 16 total x alt reverse lunge", "* 4 - 14 x deadlift", "* 9 - 15 total x alt lateral lunge")
        fun at(day: LocalDate) = Sheet.make(lines, Journal.history(written.filter { it.date < day }, vocabulary), identity)
        val start = at(monday)
        write(Logging.events(
            LogDraft().with(start.rows[0].id, LogDraft.picked(start.rows[0], 20).copy(count = 2))
                .with(start.rows[1].id, LogDraft.picked(start.rows[1], 40).copy(count = 2))
                .with(start.rows[2].id, LogDraft.picked(start.rows[2], 20).copy(count = 2)),
            start, ClassLog(), monday, "2G", at = 1L,
        ))
        val (lunge, deadlift, odd) = at(thursday).rows
        // An odd total halves to counts the board doesn't say: it keeps the tick.
        assertNull(LogDraft.counts(odd))
        assertTrue(LogDraft.asListed(odd) != null)
        assertEquals(listOf("12", "14", "16"), LogDraft.counts(lunge)!!.map { LogDraft.countLabel(lunge, it) })
        assertEquals(listOf(4, 14), LogDraft.counts(deadlift))
    }

    // A weight picked on a range starts at its low end; a ladder keeps its rounds, a side each; a timed row no reps.
    @Test
    fun eachKindOfRowIsLoggedInItsOwnTerms() {
        val sheet = sheet(monday)
        val deadlift = row(sheet, "8-10 x deadlift")
        val squat = row(sheet, "10 / 8 / 6 x squat")
        val hold = row(sheet, "30 sec of goblet squat hold")
        // A bare line on a new row: the deadlift starts at its usual two, and a squat, which
        // goes either way, at nothing until the person says.
        assertEquals(RowLog(40, 2, 8), LogDraft.picked(deadlift, 40))
        assertEquals(null, LogDraft.picked(squat, 30).count)
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

    // A weight for each round: the entry is filed under round one's, the round the sheet's number
    // is for, so a ladder can move the number where one weight for it only held.
    @Test
    fun aLadderWithEachRoundsWeightIsReadFromRoundOne() {
        val squat = row(sheet(monday), "10 / 8 / 6 x squat")
        val picked = LogDraft.picked(squat, 30).copy(count = 2)
        // Each round starts as a copy of the one before: one tap up on round two takes round
        // three with it, and one more on round three finishes a ladder that climbs.
        val each = picked.eachRound(3)
        assertEquals(listOf(30, 30, 30), each.rounds)
        val climbed = each.round(1, 35).round(2, 40)
        assertEquals(listOf(listOf(30, 35, 40), 40), listOf(climbed.rounds, climbed.lb))
        // A round set apart stays where it is when an earlier one moves.
        assertEquals(listOf(35, 35, 40), climbed.round(0, 35).rounds)
        assertEquals(listOf(30, 30, 40), climbed.round(1, 30).rounds)

        val said = each.round(2, 35).copy(easy = true)
        val saved = save(monday, LogDraft().with(squat.id, said)).single()
        assertEquals(listOf(30, listOf(30, 30, 35), false), listOf(saved.lb, saved.roundLb, saved.heaviestOnly))
        // Round one at 30 felt easy: up a dumbbell, which one weight for the ladder never does.
        assertEquals("2×35 ▲", sheet(thursday).glance(row(sheet(thursday), "10 / 8 / 6 x squat")))
        // The screen reopens with the rounds, and saving it untouched writes nothing.
        val log = Logging.logged(sheet(monday), written, monday, "2G")
        val reopened = LogDraft.from(sheet(monday), log.byRow)
        assertEquals(said, reopened.rows[squat.id])
        assertTrue(save(monday, reopened).isEmpty())
        // Back to one weight: the heaviest, as the row's one number always was.
        val one = save(monday, reopened.with(squat.id, said.at(said.lb))).single()
        assertEquals(listOf(35, null, true), listOf(one.lb, one.roundLb, one.heaviestOnly))
    }

    // Thursday's number for the ladder after Monday's was logged one way, by an invented lifter.
    private fun afterLadder(log: (RowLog) -> RowLog): String {
        written.clear()
        val squat = row(sheet(monday), "10 / 8 / 6 x squat")
        save(monday, LogDraft().with(squat.id, log(LogDraft.picked(squat, 30).copy(count = 2))))
        val next = sheet(thursday)
        val row = row(next, "10 / 8 / 6 x squat")
        return next.glance(row) + " | " + (row.suggestion as Suggestion.Weight).reasons.joinToString("; ")
    }

    // Saying each round's weight never gives a worse number than saying only the heaviest.
    @Test
    fun aLaddersRoundsAreReadByWhatTheySay() {
        val oneWeight = afterLadder { it }
        assertTrue(oneWeight, oneWeight.startsWith("2×25 ="))
        // Started light and climbed to 30 for the 6: round one's 20 would pull the number down,
        // so it is read as the one weight is.
        assertEquals(oneWeight, afterLadder { it.at(20).eachRound(3).round(1, 25).round(2, 30) })
        // The same when the heaviest was reached early and kept, or fell in the middle.
        assertEquals(oneWeight, afterLadder { it.at(20).eachRound(3).round(1, 30) })
        assertEquals(oneWeight, afterLadder { it.at(20).eachRound(3).round(1, 30).round(2, 25) })
        // Started at 30 and came down as the reps did: 30 wasn't kept, so it holds, and "felt
        // easy" doesn't lift it.
        val faded = afterLadder { it.eachRound(3).round(1, 25).round(2, 20).copy(easy = true) }
        assertEquals("2×30 = | started at 2×30 and went lighter in a later round: same again", faded)
        // Had to stop somewhere in a ladder that climbed: taken as the heaviest round, as with
        // one weight, not as a stop at the lightest.
        assertEquals(afterLadder { it.copy(stopped = true) }, afterLadder { it.at(20).eachRound(3).round(1, 25).round(2, 30).copy(stopped = true) })
        // Also when the climb was too small to change the number by itself.
        assertEquals(afterLadder { it.copy(hurt = true) }, afterLadder { it.at(25).eachRound(3).round(2, 30).copy(hurt = true) })
        // 30 throughout is 30 from round one, clean: more than one weight alone could say.
        assertTrue(afterLadder { it.eachRound(3) }.startsWith("2×30 ="))
    }

    // Rounds said against a row with another number of rounds than the entry carried over to
    // it: they are this row's, and the entry takes its terms.
    @Test
    fun roundsSaidOnACarriedLadderAreTheRowsOwn() {
        val history = listOf(Logged(monday.minusDays(7), identity.read(BoardLine.read("8 x squat").words) as Reading.Known, Load(30, 2), 8..8, listOf(8)))
        val borrowed = Sheet.make(listOf("* 12 / 10 / 8 x squat"), history, identity)
        val listed = LogDraft.asListed(borrowed.rows[0])!!
        write(Logging.events(LogDraft().with(borrowed.rows[0].id, listed), borrowed, ClassLog(), monday, "2G", 1L))
        val sheet = Sheet.make(listOf("* 10 / 8 / 6 / 4 x squat"), history, identity)
        val log = Logging.logged(sheet, written, monday, "2G")
        val row = sheet.rows.single()
        val said = RowLog.of(log.byRow.getValue(row.id)).at(20).eachRound(4).round(1, 25).round(2, 30)
        val saved = Logging.events(LogDraft.from(sheet, log.byRow).with(row.id, said), sheet, log, monday, "2G", 2L).single()
        assertEquals(
            listOf(JournalEntry.Kind.CORRECTED, 20, listOf(20, 25, 30, 30), listOf(10, 8, 6, 4), false),
            listOf(saved.kind, saved.lb, saved.roundLb, saved.ladder, saved.heaviestOnly),
        )
        // Rounds that fit no row (three said, four asked) leave only their heaviest, and never
        // as "done as listed", though it is the sheet's number.
        val listedHere = LogDraft.asListed(row)!!
        val misfit = Logging.events(LogDraft().with(row.id, listedHere.copy(rounds = listOf(20, 25, listedHere.lb))), sheet, ClassLog(), monday, "2G", 3L).single()
        assertEquals(listOf(listedHere.lb, null, true), listOf(misfit.lb, misfit.roundLb, misfit.heaviestOnly))
    }

    // A damaged cell of rounds costs the rounds, not the screen: the entry reads as its one weight.
    @Test
    fun damagedRoundsAreNotRead() {
        val squat = row(sheet(monday), "10 / 8 / 6 x squat")
        val saved = save(monday, LogDraft().with(squat.id, LogDraft.picked(squat, 30).copy(count = 2).eachRound(3))).single()
        for (bad in listOf(listOf(0, 25, 30), listOf(30, 30), emptyList())) {
            assertEquals(RowLog(30, 2, null), RowLog.of(saved.copy(roundLb = bad)))
        }
        assertEquals(30, Journal.history(listOf(saved.copy(id = 1, roundLb = listOf(-5, 999, 30))), vocabulary).single().load.lb)
        // An emptied ladder with emptied rounds is a damaged row that loses itself, not a crash.
        assertTrue(Journal.history(listOf(saved.copy(id = 1, ladder = emptyList(), roundLb = emptyList(), reps = emptyList())), vocabulary).all { it.ladder == null })
        // And a straight set has no rounds, whatever the cell says.
        val press = row(sheet(monday), "8 x chest press")
        val straight = save(monday, LogDraft().with(press.id, RowLog(25, 2, 8))).single()
        assertEquals(RowLog(25, 2, 8), RowLog.of(straight.copy(roundLb = listOf(20, 50))))
    }

    // An exercise the board missed, typed in: read as a board line is, logged like any row, and
    // the next sheet's number for it. The scorecard is told the sheet showed nothing for it.
    @Test
    fun anAddedExerciseIsLoggedAndFeedsTheNextSheet() {
        val history = Journal.history(written, vocabulary)
        // Only an exercise line makes a row; what was typed is read by the same reader.
        assertNull(Sheet.added(Sheet.addedId(1), "Great class today!", history, identity))
        assertNull(Sheet.added(Sheet.addedId(1), "Floor block 3:", history, identity))
        assertEquals(false, LogDraft.loggable(Sheet.added(Sheet.addedId(1), "8 x zorbing", history, identity)!!))

        val board = sheet(monday)
        val draft = LogDraft().adding("8 x chest press", board).adding("10 x hammer curl", board)
        assertEquals(listOf(Sheet.addedId(1), Sheet.addedId(2)), draft.added.keys.toList())
        val rows = draft.added.map { (id, typed) -> Sheet.added(id, typed, history, identity)!! }
        assertEquals(listOf(Sheet.ADDED, "10 x hammer curl", 10..10), rows[1].let { listOf(it.block, it.shown, it.target) })
        val sheet = board.copy(rows = board.rows + rows)
        val events = Logging.events(draft.with(rows[1].id, LogDraft.picked(rows[1], 25).copy(count = 2, easy = true)), sheet, ClassLog(), monday, "2G", 1L)
        // The row left alone isn't logged, and the added one is filed under its own id.
        assertEquals(listOf(Sheet.addedId(2) to 25), events.map { it.row to it.lb })
        write(events)
        val next = Sheet.make(listOf("* 10 x hammer curl"), Journal.history(written, vocabulary), identity)
        assertEquals("2×30 ▲", next.glance(next.rows.single()))

        // Logged again where the sheet has a number for it: the scorecard still hears "no number",
        // since an added row was never on the sheet before class.
        val later = Sheet.added(Sheet.addedId(1), "10 x hammer curl", Journal.history(written, vocabulary), identity)!!
        val ticked = Logging.events(LogDraft().with(later.id, LogDraft.asListed(later)!!), Sheet(listOf(later)), ClassLog(), thursday, "2G", 2L).single()
        assertEquals(listOf(30, null), listOf(ticked.lb, ticked.shownLb))
        // The next id is past every one on the sheet and in the draft, so none is given out twice.
        assertEquals(Sheet.addedId(4), LogDraft().adding("8 x squat", Sheet(listOf(later.copy(id = Sheet.addedId(3))))).added.keys.single())
        assertEquals(listOf(true, false), listOf(Sheet.addedId(3), board.rows[0].id).map(Sheet::isAdded))
        // With the clock, an id is also apart from one the sheet doesn't show yet, and two adds
        // within one tick still differ.
        val timed = LogDraft().adding("8 x squat", board, now = 1_000).adding("8 x squat", board, now = 1_000)
        assertEquals(listOf(Sheet.addedId(1_000), Sheet.addedId(1_001)), timed.added.keys.toList())
    }

    // Logged from a board row since gone (a stray), and the same exercise added as one more set:
    // the stray isn't carried onto the added row, so both stand, and a Clear on the stray holds.
    @Test
    fun aStrayIsNotCarriedOntoAnAddedRow() {
        logOn(listOf("* 10 x hammer curl"), 0 to RowLog(15, 2, 10))
        val board = Sheet.make(listOf("* 8 x chest press"), emptyList(), identity)
        val draft = LogDraft().adding("10 x hammer curl", board)
        val added = Sheet.added(draft.added.keys.single(), "10 x hammer curl", emptyList(), identity)!!
        val sheet = board.copy(rows = board.rows + added)
        val log = Logging.logged(sheet, written, monday, "2G")
        assertEquals(listOf(emptySet<String>(), listOf(15)), listOf(log.byRow.keys, log.strays.map { it.lb }))
        val second = Logging.events(draft.with(added.id, RowLog(20, 2, 10)), sheet, log, monday, "2G", 2L).single()
        assertEquals(listOf(JournalEntry.Kind.LOGGED, 20), listOf(second.kind, second.lb))
        val cleared = Logging.events(draft.copy(cleared = setOf(log.strays.single().id)), sheet, log, monday, "2G", 2L).single()
        assertEquals(JournalEntry.Kind.REMOVED, cleared.kind)
    }

    // A draft whose added line took an id the log has since filed another line under leaves
    // that entry alone, rather than correcting one exercise with another's weight.
    @Test
    fun anAddedLineNeverCorrectsAnotherLinesEntry() {
        val board = Sheet.make(listOf("* 8 x chest press"), emptyList(), identity)
        val curl = Sheet.added(Sheet.addedId(1), "10 x hammer curl", emptyList(), identity)!!
        write(Logging.events(LogDraft().with(curl.id, RowLog(15, 2, 10)), board.copy(rows = board.rows + curl), ClassLog(), monday, "2G", 1L))
        // Made from the board before the curl was on it, so it gave out the same id.
        val stale = LogDraft().adding("8 x goblet squat", board).with(Sheet.addedId(1), RowLog(35, 1, 8))
        val now = board.copy(rows = board.rows + curl)
        assertTrue(Logging.events(stale, now, Logging.logged(now, written, monday, "2G"), monday, "2G", 2L).isEmpty())
    }

    // Logged when its line still read, and now it doesn't (the word lists changed): what is
    // logged stays through a save, and can be cleared, though nothing new can be said of it.
    @Test
    fun aLoggedRowThatNoLongerReadsCanStillBeCleared() {
        val press = row(sheet(monday), "8 x chest press")
        save(monday, LogDraft().with(press.id, RowLog(25, 2, 8)))
        val unsure = row(sheet(monday), "8 x zorbing").reading
        val unread = sheet(monday).let { s -> s.copy(rows = s.rows.map { if (it.id == press.id) it.copy(reading = unsure, suggestion = null) else it }) }
        val log = Logging.logged(unread, written, monday, "2G")
        val draft = LogDraft.from(unread, log.byRow)
        assertEquals(RowLog(25, 2, 8), draft.rows[press.id])
        assertTrue(Logging.events(draft, unread, log, monday, "2G", 5L).isEmpty())
        assertTrue(Logging.events(draft.with(press.id, RowLog(30, 2, 8)), unread, log, monday, "2G", 5L).isEmpty())
        val cleared = Logging.events(draft.with(press.id, null), unread, log, monday, "2G", 5L).single()
        assertEquals(listOf(JournalEntry.Kind.REMOVED, written.single().id), listOf(cleared.kind, cleared.target))
    }

    // An added line taken back before it was saved leaves nothing behind, entered or not.
    @Test
    fun anAddedLineCanBeTakenBack() {
        val board = sheet(monday)
        val draft = LogDraft().adding("10 x hammer curl", board).adding("8 x goblet squat", board)
        val (curl, squat) = draft.added.keys.toList()
        val left = draft.with(curl, RowLog(15, 2, 10)).with(squat, RowLog(30, 1, 8)).removing(curl)
        assertEquals(listOf(setOf(squat), setOf(squat)), listOf(left.added.keys, left.rows.keys))
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
        fun saved(log: RowLog) = Logging.events(LogDraft().with(row.id, log), sheet, ClassLog(), thursday, "2G", 1L).single()
        assertEquals(false, saved(listed.copy(count = 1, hard = true, hurt = true)).heaviestOnly)
        assertEquals(true, saved(listed.copy(lb = 50)).heaviestOnly)
    }

    // A count nobody has said stays unknown, for the screen to ask; it is never guessed.
    @Test
    fun anUnknownCountStaysUnknown() {
        val imported = Logged(monday, identity.read(BoardLine.read("8 x squat").words) as Reading.Known, Load(40, null), 8..8, listOf(8))
        val squat = Sheet.make(listOf("8 x squat"), listOf(imported), identity).rows.single()
        assertEquals(null, LogDraft.asListed(squat)!!.count)
        assertEquals(RowLog(45, null, 8), LogDraft.picked(squat, 45))
        assertTrue(LogDraft.asks(squat))
        // A lift with a usual number starts at it, ticked or picked, and the screen still asks:
        // it is a guess to change, not something anyone said. One way of doing a lift can
        // have its own: a sumo squat is one dumbbell.
        val unsaid = Logged(monday, identity.read(BoardLine.read("8 x deadlift").words) as Reading.Known, Load(40, null), 8..8, listOf(8))
        val deadlift = Sheet.make(listOf("8 x deadlift"), listOf(unsaid), identity).rows.single()
        assertEquals(listOf(2, 2), listOf(LogDraft.asListed(deadlift)!!.count, LogDraft.picked(deadlift, 45).count))
        assertTrue(LogDraft.asks(deadlift))
        val sumo = Sheet.make(listOf("8 x sumo squat", "8 x single leg deadlift", "8 x pullover"), emptyList(), identity).rows
        assertEquals(listOf(1, null, 1), sumo.map { LogDraft.picked(it, 30).count })
        // A row with no number takes its count from the last time it was logged.
        val counted = Logged(monday, identity.read(BoardLine.read("8 x deadlift").words) as Reading.Known, Load(40, 2), 8..8, listOf(8))
        val far = Sheet.make(listOf("20 x deadlift"), listOf(counted), identity).rows.single()
        assertTrue(far.suggestion is Suggestion.NoNumber)
        assertEquals(RowLog(30, 2, 20), LogDraft.picked(far, 30))
        assertEquals(false, LogDraft.asks(far))
    }

    // Another day's or class's log never shows on this one's screen.
    @Test
    fun theScreenStartsFromThisClassOnly() {
        val press = row(sheet(monday), "8 x chest press")
        save(monday, LogDraft().with(press.id, RowLog(25, 2, 8)))
        assertEquals(ClassLog(), Logging.logged(sheet(thursday), written, thursday, "2G"))
        assertEquals(ClassLog(), Logging.logged(sheet(monday), written, monday, "3G"))
    }

    private val own = listOf("**Floor Block 1**", "* 8 x chest press", "* 8 x chest press", "* 10 / 8 / 6 x squat", "* 8 x push-up")
    private fun logOn(lines: List<String>, vararg logs: Pair<Int, RowLog>) {
        val sheet = Sheet.make(lines, emptyList(), identity)
        write(Logging.events(logs.fold(LogDraft()) { d, (i, log) -> d.with(sheet.rows[i].id, log) }, sheet, ClassLog(), monday, "2G", 1L))
    }

    // Logged from a borrowed board, then the class's own is posted: what was done carries over
    // to the row with the same exercise, with its own weight and reps, and the rest is shown.
    @Test
    fun aLogCarriesOverToTheSameExerciseOnTheRealBoard() {
        logOn(listOf("**Floor Block 2 - 10 minutes**", "* 10 x chest press", "* 12 x deadlift"), 0 to RowLog(25, 2, 10), 1 to RowLog(40, 2, 12))
        val sheet = Sheet.make(own, emptyList(), identity)
        val log = Logging.logged(sheet, written, monday, "2G")
        // The first free press row takes it; the deadlift has nowhere to go.
        assertEquals(mapOf(sheet.rows[0].id to 25), log.byRow.mapValues { it.value.lb })
        assertEquals(listOf("12 x deadlift"), log.strays.map { it.line })
        val draft = LogDraft.from(sheet, log.byRow)
        assertEquals(RowLog(25, 2, 10), draft.rows[sheet.rows[0].id])

        // Saved untouched, the press settles on its row, as it was logged; the stray stays in
        // history until it is cleared.
        val settled = Logging.events(draft, sheet, log, monday, "2G", 2L).single()
        assertEquals(written[0].copy(id = 0, at = 2L, kind = JournalEntry.Kind.CORRECTED, target = 1L, row = sheet.rows[0].id), settled)
        // Corrected, it keeps its own words and target: 10 asked and 10 done, not 10 done of the
        // 8 this row asks, and a twinge said now is kept.
        val moved = Logging.events(draft.with(sheet.rows[0].id, RowLog(30, 2, 10, hurt = true)), sheet, log, monday, "2G", 2L).single()
        assertEquals(
            written[0].copy(id = 0, at = 2L, kind = JournalEntry.Kind.CORRECTED, target = 1L, row = sheet.rows[0].id, lb = 30, hurt = true),
            moved,
        )
        assertEquals(listOf("10 x chest press", 10, 10, listOf(10)), listOf(moved.line, moved.targetLow, moved.targetHigh, moved.reps))
        // Entered anew from this row (a fresh pick gives its 8 reps), it is this row's set: 8 of
        // the 8 asked here, not 8 of the old board's 10.
        val anew = Logging.events(draft.with(sheet.rows[0].id, LogDraft.picked(sheet.rows[0], 30).copy(count = 2)), sheet, log, monday, "2G", 2L).single()
        assertEquals(listOf(JournalEntry.Kind.CORRECTED, 1L, "8 x chest press", 8, 8, listOf(8)), listOf(anew.kind, anew.target, anew.line, anew.targetLow, anew.targetHigh, anew.reps))
        // Cleared, the stray is removed, and nothing but it.
        val removed = Logging.events(draft.copy(cleared = setOf(log.strays.single().id, 99L)), sheet, log, monday, "2G", 2L).single { it.kind == JournalEntry.Kind.REMOVED }
        assertEquals(listOf(JournalEntry.Kind.REMOVED, 2L), listOf(removed.kind, removed.target))
        write(listOf(removed))
        assertTrue(Logging.logged(sheet, written, monday, "2G").strays.isEmpty())
    }

    // One row takes one entry: a second of the same exercise goes to the next free row, a third
    // has none, and a row logged in its own right is never taken over.
    @Test
    fun eachRowTakesOneCarriedEntry() {
        logOn(listOf("* 10 x chest press", "* 12 x chest press", "* 15 x chest press"), 0 to RowLog(20, 2, 10), 1 to RowLog(25, 2, 12), 2 to RowLog(30, 2, 15))
        val sheet = Sheet.make(own, emptyList(), identity)
        write(Logging.events(LogDraft().with(sheet.rows[0].id, RowLog(35, 2, 8)), sheet, ClassLog(), monday, "2G", 2L))
        val log = Logging.logged(sheet, written, monday, "2G")
        assertEquals(listOf(35, 20), listOf(sheet.rows[0].id, sheet.rows[1].id).map { log.byRow.getValue(it).lb })
        assertEquals(listOf(25, 30), log.strays.map { it.lb })
    }

    // Settled by a save, two carried entries keep their rows when the first is cleared: the
    // second doesn't slide up into the row just cleared.
    @Test
    fun aSettledEntryKeepsItsRowWhenAnotherIsCleared() {
        logOn(listOf("* 10 x chest press", "* 12 x chest press"), 0 to RowLog(20, 2, 10), 1 to RowLog(25, 2, 12))
        val sheet = Sheet.make(own, emptyList(), identity)
        val (a, b) = sheet.rows[0].id to sheet.rows[1].id
        val log = Logging.logged(sheet, written, monday, "2G")
        val draft = LogDraft.from(sheet, log.byRow)
        write(Logging.events(draft.with(a, null), sheet, log, monday, "2G", 2L))
        val after = Logging.logged(sheet, written, monday, "2G")
        assertEquals(mapOf(b to 25), after.byRow.mapValues { it.value.lb })
        assertTrue(after.strays.isEmpty())
        // And a second save has nothing left to settle.
        assertTrue(Logging.events(LogDraft.from(sheet, after.byRow), sheet, after, monday, "2G", 3L).isEmpty())
    }

    // A carried ladder corrected keeps the rounds that were done, and stays a listed one unless
    // the weight changes.
    @Test
    fun aCarriedLadderKeepsItsRounds() {
        val borrowed = Sheet.make(listOf("* 12 / 10 / 8 x squat"), listOf(Logged(monday.minusDays(7), identity.read(BoardLine.read("8 x squat").words) as Reading.Known, Load(30, 2), 8..8, listOf(8))), identity)
        val listed = LogDraft.asListed(borrowed.rows[0])!!
        write(Logging.events(LogDraft().with(borrowed.rows[0].id, listed), borrowed, ClassLog(), monday, "2G", 1L))
        assertEquals(false, written[0].heaviestOnly)
        val sheet = Sheet.make(own, emptyList(), identity)
        val log = Logging.logged(sheet, written, monday, "2G")
        val row = sheet.rows[2]
        val felt = Logging.events(LogDraft.from(sheet, log.byRow).with(row.id, listed.copy(hard = true)), sheet, log, monday, "2G", 2L).single()
        assertEquals(listOf(listOf(12, 10, 8), listOf(12, 10, 8), false, true), listOf(felt.ladder, felt.reps, felt.heaviestOnly, felt.hard))
        val heavier = Logging.events(LogDraft.from(sheet, log.byRow).with(row.id, listed.copy(lb = listed.lb + 5)), sheet, log, monday, "2G", 2L).single()
        assertEquals(listOf(listOf(12, 10, 8), true), listOf(heavier.ladder, heavier.heaviestOnly))
        // Once heaviest-only, the same weight stays so, whatever else is said.
        write(listOf(heavier))
        val again = Logging.logged(sheet, written, monday, "2G")
        val still = Logging.events(LogDraft.from(sheet, again.byRow).with(row.id, RowLog.of(heavier).copy(easy = true)), sheet, again, monday, "2G", 3L).single()
        assertEquals(true, still.heaviestOnly)
        // Ticked as listed on a sheet that has a number, it was done at that number from round one.
        val numbered = Sheet.make(own, listOf(Logged(monday.minusDays(7), identity.read(BoardLine.read("8 x squat").words) as Reading.Known, Load(30, 2), 8..8, listOf(8))), identity)
        val asListed = LogDraft.asListed(numbered.rows[2])!!
        assertTrue(asListed.lb != heavier.lb)
        val carried = Logging.logged(numbered, written, monday, "2G")
        val ticked = Logging.events(LogDraft.from(numbered, carried.byRow).with(numbered.rows[2].id, asListed), numbered, carried, monday, "2G", 4L).single()
        assertEquals(listOf(false, asListed.lb), listOf(ticked.heaviestOnly, ticked.lb))
    }

    // A straight set done isn't a ladder done: it doesn't carry to a row counted another way.
    @Test
    fun anEntryCarriesOnlyToARowOfItsKind() {
        logOn(listOf("* 8 x squat"), 0 to RowLog(30, 2, 8))
        val sheet = Sheet.make(own, emptyList(), identity)
        val log = Logging.logged(sheet, written, monday, "2G")
        assertTrue(log.byRow.isEmpty())
        assertEquals(listOf("8 x squat"), log.strays.map { it.line })
    }

    // The draft survives the app being killed as text.
    @Test
    fun aDraftRoundTrips() {
        val draft = LogDraft().with("a", RowLog(25, 2, 8, easy = true, stopped = true)).with("b", RowLog(40, null, null, easier = true))
        assertEquals(draft, Json.decodeFromString<LogDraft>(Json.encodeToString(draft)))
    }
}
