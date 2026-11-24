package spotter.coach

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

// An invented lifter's log of public board lines.
class ProgressTest {
    private val identity = Identity(Vocabulary.starting())
    private fun exercise(line: String) = identity.read(BoardLine.read(line).words) as Reading.Known
    private val press = exercise("8 x chest press")
    private val curl = exercise("8 x bicep curl")
    private val deadlift = exercise("8 x deadlift")
    private val today = LocalDate.of(2026, 3, 30)
    private fun day(n: Int) = LocalDate.of(2026, 3, n)
    private fun logged(
        day: Int, ex: Reading.Known, lb: Int, reps: Int = 8, done: Int = reps, count: Int = 2, easy: Boolean = false, hurt: Boolean = false,
    ) = Logged(day(day), ex, Load(lb, count), reps..reps, listOf(done, done), hurt = hurt, easy = easy)

    // What went up: heavier than the clean session before it at the same reps, and clean again.
    @Test
    fun whatWentUpIsWhatWasLiftedHeavierAndClean() {
        val log = listOf(
            logged(10, press, 25), logged(14, press, 25), logged(20, press, 30),
            // Heavier but short of the reps: not up yet. Lighter: not up.
            logged(10, curl, 15), logged(20, curl, 20, done = 6),
            logged(10, deadlift, 40), logged(20, deadlift, 35),
        )
        val up = Progress.of(log, today).wentUp
        assertEquals(listOf(Progress.WentUp("chest press", Load(25, 2), Load(30, 2), 8, day(20))), up)
        // Other reps, or one dumbbell against two, aren't the same lift; nor is a twinge progress.
        fun single(vararg l: Logged) = Progress.of(l.toList(), today).wentUp
        assertTrue(single(logged(10, press, 25, reps = 12), logged(20, press, 30)).isEmpty())
        assertTrue(single(logged(10, press, 25, count = 1), logged(20, press, 30)).isEmpty())
        assertTrue(single(logged(10, press, 25), logged(20, press, 30, hurt = true)).isEmpty())
        // "Lately" is four weeks to the day; before that it is history, not news.
        assertEquals(listOf(1, 0), listOf(28L, 29L).map { Progress.of(log, day(20).plusDays(it)).wentUp.size })
        // In range isn't at the top: 30 done for 8 of "8-10" didn't go up "for 10".
        fun ranged(day: Int, lb: Int, done: Int) = Logged(day(day), press, Load(lb, 2), 8..10, listOf(done, done))
        assertTrue(single(ranged(10, 25, 10), ranged(20, 30, 8)).isEmpty())
        assertEquals(10, single(ranged(10, 25, 10), ranged(20, 30, 10)).single().reps)
        // Nor is the earlier session a base unless it was clean, and back to a weight held
        // before isn't up: only a new best is.
        assertTrue(single(logged(10, press, 25, done = 6), logged(20, press, 30)).isEmpty())
        assertTrue(single(logged(2, press, 30), logged(10, press, 25), logged(20, press, 30)).isEmpty())
        assertEquals(Load(30, 2), single(logged(2, press, 30), logged(10, press, 25), logged(20, press, 35)).single().from)
        // A ladder that went lighter in a later round didn't keep its weight; a timed set has
        // no reps to have gone up "for"; an exercise the sheet gives no weight has no number.
        fun ladder(day: Int, rounds: List<Int>) = Journal.history(
            listOf(Journal.logged(1, day(day), "2G", "r", "8 / 6 / 4 x chest press", press, Load(rounds[0], 2), 8..8, listOf(8, 6, 4), listOf(8, 6, 4), roundLb = rounds).copy(id = day.toLong())),
            Vocabulary.starting(),
        ).single()
        assertTrue(single(ladder(10, listOf(25, 25, 25)), ladder(20, listOf(30, 25, 20))).isEmpty())
        assertEquals(1, single(ladder(10, listOf(25, 25, 25)), ladder(20, listOf(30, 30, 30))).size)
        fun timed(day: Int, lb: Int) = Logged(day(day), press, Load(lb, 2), null, emptyList())
        assertTrue(single(timed(10, 25), timed(20, 30)).isEmpty())
        val pushUp = exercise("8 x push-up")
        assertTrue(single(logged(10, pushUp, 25), logged(20, pushUp, 30)).isEmpty())
        assertTrue(Progress.of(listOf(logged(10, pushUp, 25), logged(14, pushUp, 25)), today).next.isEmpty())
        // A count nobody said could be one dumbbell then or two now: not known to be up.
        fun uncounted(day: Int, lb: Int) = Logged(day(day), press, Load(lb, null), 8..8, listOf(8, 8))
        assertTrue(single(uncounted(10, 25), uncounted(20, 30)).isEmpty())
        // The most recent first, and no more than a glance holds.
        val several = Vocabulary.starting().movements.filter { !it.bodyweight }.take(10).flatMapIndexed { i, m ->
            val ex = Reading.Known(listOf(Part(m, emptyMap())), emptySet())
            listOf(logged(1, ex, 25), logged(10 + i, ex, 30))
        }
        val ups = Progress.of(several, today).wentUp
        assertEquals(listOf(Progress.MOST, day(19), day(12)), listOf(ups.size, ups.first().on, ups.last().on))
        // The same exercise twice in one class is one session, not a step.
        assertTrue(single(logged(20, press, 25), logged(20, press, 30)).isEmpty())
    }

    // What is next is what the next sheet will say: up a dumbbell, or one clean session from it.
    // Wordings that share a weight are one exercise on the card, as they are to the coach.
    @Test
    fun sharedWordingsAreListedOnce() {
        val floor = exercise("8 x floor chest press")
        val two = Progress.of(listOf(logged(20, press, 25), logged(22, floor, 25)), today)
        assertEquals(listOf(Load(30, 2) to true), two.next.map { it.load to it.ready })
        // Up on the floor from what was pressed on the bench is a step up, once.
        val up = Progress.of(listOf(logged(20, press, 25), logged(22, floor, 25), logged(24, floor, 30)), today)
        assertEquals(listOf(Load(25, 2) to Load(30, 2)), up.wentUp.map { it.from to it.to })
        // One dumbbell a side at a time and one in both hands aren't the same lift to the coach,
        // so 25 then 35 is no step up, and each stands on the card for itself.
        val alternating = exercise("8 x alt chest press")
        val apart = Progress.of(listOf(logged(18, alternating, 25, count = 1), logged(20, alternating, 25, count = 1), logged(22, press, 35, count = 1)), today)
        assertTrue(apart.wentUp.isEmpty())
        // Named as a person would say it, not by the key it is filed under.
        assertEquals(listOf("alternating chest press" to true, "chest press" to false), apart.next.map { it.exercise to it.ready }.sortedBy { it.first })
        val said = Progress.of(listOf(logged(20, exercise("8 x floor chest fly"), 25), logged(22, exercise("8 x floor chest fly"), 25), logged(22, exercise("8 x goblet lateral shift"), 30, count = 1)), today)
        assertEquals(listOf("floor chest fly", "goblet lateral shift"), said.next.map { it.exercise }.sorted())
    }

    @Test
    fun whatIsNextIsWhatTheNextSheetWillSay() {
        val log = listOf(
            // Two clean sessions at 25: up next time.
            logged(10, press, 25), logged(14, press, 25),
            // One clean session at 30: one more.
            logged(14, curl, 30),
            // Short of the reps: neither.
            logged(14, deadlift, 40, done = 6),
        )
        val next = Progress.of(log, today).next
        assertEquals(
            listOf(Progress.Next("chest press", Load(30, 2), 8, ready = true), Progress.Next("bicep curl", Load(30, 2), 8, ready = false)),
            next,
        )
        // Each agrees with the sheet made from the same log.
        val sheet = Sheet.make(listOf("* 8 x chest press", "* 8 x bicep curl", "* 8 x deadlift"), log, identity)
        assertEquals(listOf("2×30 ▲", "2×30 =", "2×35 ▼"), sheet.rows.map(sheet::glance))
        assertTrue(sheet.rows[1].why.single().contains("up after one more clean session"))
        // A twinge since holds the weight, so nothing is promised; a big jump is a test, not a step.
        assertTrue(Progress.of(listOf(logged(10, press, 25), logged(14, press, 25, hurt = true)), today).next.isEmpty())
        assertTrue(Progress.of(listOf(logged(14, curl, 12)), today).next.isEmpty())
        // The promise needs the count said: a session logged next with its count starts afresh.
        assertTrue(Progress.of(listOf(Logged(day(14), curl, Load(30, null), 8..8, listOf(8, 8))), today).next.isEmpty())
        // A twinge and a clean session in one class, the twinge logged first: the sheet holds
        // with its warning, and nothing is promised.
        val sameDay = listOf(logged(14, curl, 30, hurt = true), logged(14, curl, 30))
        assertEquals(false, (Progression.suggest(curl, 8..8, sameDay) as Suggestion.Weight).oneMore)
        assertTrue(Progress.of(sameDay, today).next.isEmpty())
        // Ready before "one more", whichever was lifted later.
        assertEquals(listOf(true, false), Progress.of(listOf(logged(10, press, 25), logged(14, press, 25), logged(20, curl, 30)), today).next.map { it.ready })
        // Only what was lifted lately, the most recent first within each kind.
        assertTrue(Progress.of(log, today.plusWeeks(5)).next.isEmpty())
        val recent = log + listOf(logged(16, deadlift, 35), logged(18, deadlift, 35))
        assertEquals(listOf("deadlift", "chest press", "bicep curl"), Progress.of(recent, today).next.map { it.exercise })
        // No more than a glance holds, the ready ones first.
        val many = Vocabulary.starting().movements.filter { !it.bodyweight }.take(12).flatMap { m ->
            val ex = Reading.Known(listOf(Part(m, emptyMap())), emptySet())
            listOf(logged(10, ex, 25), logged(14, ex, 25))
        } + logged(14, curl, 30)
        val capped = Progress.of(many, today)
        assertEquals(listOf(Progress.MOST, true), listOf(capped.next.size, capped.next.all { it.ready }))
    }

    // Whether the sheet's numbers are working: what became of each one, counted from the log.
    @Test
    fun adviceCountsWhatBecameOfTheSheetsNumbers() {
        val vocabulary = Vocabulary.starting()
        var id = 0L
        fun entry(
            day: Int, ex: Reading.Known, lb: Int, shown: Int?, done: Int = 8, target: IntRange? = 8..8, count: Int = 2, shownCount: Int = 2,
            hurt: Boolean = false, stopped: Boolean = false, easier: Boolean = false, row: String = "row",
            ladder: List<Int>? = null, heaviestOnly: Boolean = false, rounds: List<Int>? = null,
        ) = Journal.logged(
            1, day(day), "2G", row, "line", ex, Load(lb, count), target, ladder, ladder ?: if (target == null) emptyList() else listOf(done, done),
            hurt = hurt, stopped = stopped, easier = easier, heaviestOnly = heaviestOnly, shown = shown?.let { Load(it, shownCount) }, roundLb = rounds,
        ).copy(id = ++id)
        fun advice(vararg e: JournalEntry, on: LocalDate = today) = Progress.advice(e.toList(), vocabulary, on)
        val counted = arrayOf(
            // As suggested: at the number and every set done; the low end of a range was asked
            // for; a timed row done.
            entry(20, press, 25, shown = 25),
            entry(20, curl, 15, shown = 15, done = 8, target = 8..10),
            entry(20, deadlift, 40, shown = 40, target = null),
            // Heavier than the sheet said, whatever came of it.
            entry(21, deadlift, 45, shown = 40),
            // Lighter, or at the number and short, stopped, with a twinge, or a ladder that faded.
            entry(22, press, 20, shown = 25),
            entry(22, curl, 15, shown = 15, done = 6),
            entry(22, deadlift, 40, shown = 40, hurt = true),
            entry(23, deadlift, 40, shown = 40, target = null, stopped = true),
            entry(23, press, 25, shown = 25, ladder = listOf(8, 6, 4), rounds = listOf(25, 20, 20)),
        )
        assertEquals(Progress.Advice(asSuggested = 3, heavier = 1, lighterOrShort = 5), advice(*counted))
        assertEquals(9, advice(*counted).numbered)
        // Not counted: a row the sheet had no number for (an entry from before the log kept
        // the sheet's number looks the same), a row the person added, an easier version, a
        // ladder by its heaviest round, another number of dumbbells than the sheet said, an
        // exercise the sheet gives no weight, a class after today or more than four weeks back.
        val apart = arrayOf(
            entry(20, press, 25, shown = null),
            entry(20, press, 25, shown = 25, row = Sheet.addedId(1)),
            entry(20, press, 25, shown = 25, easier = true),
            entry(20, press, 35, shown = 25, ladder = listOf(8, 6, 4), heaviestOnly = true),
            entry(20, press, 40, shown = 25, count = 1),
            entry(20, exercise("8 x push-up"), 25, shown = 25),
            entry(31, press, 25, shown = 25),
            entry(1, press, 25, shown = 25),
        )
        assertEquals(Progress.Advice(), advice(*apart))
        assertEquals(1, advice(*apart, on = day(1).plusDays(28)).asSuggested)
        // A row counts as last corrected, and not at all once removed.
        val first = entry(20, press, 20, shown = 25)
        val corrected = first.copy(id = ++id, kind = JournalEntry.Kind.CORRECTED, target = first.id, lb = 25)
        assertEquals(Progress.Advice(asSuggested = 1), advice(first, corrected))
        val removed = first.copy(id = ++id, kind = JournalEntry.Kind.REMOVED, target = first.id)
        assertEquals(Progress.Advice(), advice(first, corrected, removed))
    }

    // Logged as listed from a borrowed board, then ticked as listed again on the class's own
    // board, which asks other reps and shows another number: the person did what each sheet
    // said, so it is never "lighter or short" against the first sheet's number.
    @Test
    fun aRowCorrectedOntoOtherRepsIsNotHeldToItsFirstNumber() {
        val vocabulary = Vocabulary.starting()
        val history = listOf(logged(1, press, 30, easy = true), logged(1, press, 25, reps = 12, easy = true))
        val written = mutableListOf<JournalEntry>()
        fun save(lines: List<String>, tick: Boolean) {
            val sheet = Sheet.make(lines, history, identity)
            val log = Logging.logged(sheet, written, day(20), "2G")
            val draft = LogDraft.from(sheet, log.byRow).let { if (tick) it.with(sheet.rows[0].id, LogDraft.asListed(sheet.rows[0])!!) else it }
            Logging.events(draft, sheet, log, day(20), "2G", 1L).forEach { written += it.copy(id = written.size + 1L) }
        }
        // A ladder first: carried to another board's ladder and ticked there, it keeps its own
        // words and rounds with the new sheet's weight, and isn't held to the first number.
        save(listOf("* 8 / 6 / 4 x chest press"), tick = true)
        assertEquals(Progress.Advice(asSuggested = 1), Progress.advice(written, vocabulary, today))
        save(listOf("* 12 / 10 / 8 x chest press"), tick = true)
        assertTrue(written.last().let { it.kind == JournalEntry.Kind.CORRECTED && it.ladder == listOf(8, 6, 4) && it.lb < it.shownLb!! })
        assertEquals(Progress.Advice(), Progress.advice(written, vocabulary, today))
        written.clear()
        // A poster's change of a capital isn't another row: corrected to fewer reps there, the
        // set still counts, as short.
        save(listOf("* 8 x chest press"), tick = true)
        val revised = Sheet.make(listOf("* 8 x Chest Press"), history, identity)
        val log = Logging.logged(revised, written, day(20), "2G")
        val row = revised.rows[0]
        Logging.events(LogDraft.from(revised, log.byRow).with(row.id, LogDraft.asListed(row)!!.copy(reps = 6)), revised, log, day(20), "2G", 1L)
            .forEach { written += it.copy(id = written.size + 1L) }
        assertEquals(Progress.Advice(lighterOrShort = 1), Progress.advice(written, vocabulary, today))
        written.clear()
        save(listOf("* 8 x chest press"), tick = true)
        assertEquals(Progress.Advice(asSuggested = 1), Progress.advice(written, vocabulary, today))
        // The class's own board: carried over untouched, it still stands as it was logged.
        save(listOf("* 12 x chest press"), tick = false)
        assertEquals(Progress.Advice(asSuggested = 1), Progress.advice(written, vocabulary, today))
        // Said on the new row to have been the same weight for its 12: other reps than the
        // number was shown for, so not "as suggested" on the strength of the weight alone.
        val own = Sheet.make(listOf("* 12 x chest press"), history, identity)
        val carried = Logging.logged(own, written, day(20), "2G")
        val same = Logging.events(LogDraft.from(own, carried.byRow).with(own.rows[0].id, RowLog(written.last().lb, 2, 12)), own, carried, day(20), "2G", 1L)
            .map { it.copy(id = written.size + 1L) }
        assertEquals(listOf(12, written.last().shownLb), same.single().let { listOf(it.targetLow, it.lb) })
        assertEquals(Progress.Advice(), Progress.advice(written + same, vocabulary, today))
        // Ticked as listed on the new row: a lighter weight for more reps, as that sheet said.
        save(listOf("* 12 x chest press"), tick = true)
        assertTrue(written.last().let { it.kind == JournalEntry.Kind.CORRECTED && it.lb < it.shownLb!! })
        assertEquals(Progress.Advice(), Progress.advice(written, vocabulary, today))
    }
}
