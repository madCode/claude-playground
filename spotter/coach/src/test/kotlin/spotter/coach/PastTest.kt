package spotter.coach

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

// An invented lifter's classes.
class PastTest {
    private val vocabulary = Vocabulary.starting()
    private val identity = Identity(vocabulary)
    private fun known(line: String) = identity.read(BoardLine.read(line).words) as Reading.Known
    private fun day(n: Int) = LocalDate.of(2026, 11, n)

    private var ids = 0L
    private fun entry(day: Int, cls: String, line: String, lb: Int, reps: List<Int>, count: Int? = 2, hurt: Boolean = false, easy: Boolean = false) =
        Journal.logged(1L, day(day), cls, "r${ids + 1}", line, known(line), Load(lb, count), 8..8, null, reps, hurt = hurt, easy = easy).copy(id = ++ids)

    private fun file(day: Int, cls: String?, line: String, lb: Int) =
        Imported(Logged(day(day), known(line), Load(lb, 2), 8..8, listOf(8)), cls, line)

    private val own = Journal.read(
        listOf(
            entry(16, "2G", "8 x deadlift", 40, listOf(8, 8)),
            entry(21, "2G", "8 x deadlift", 45, listOf(7), hurt = true),
            entry(21, "2G", "8 x close grip chest press (explosive)", 30, listOf(8), easy = true),
            entry(21, "3G", "8 total x alt dumbbell deadlift", 40, listOf(8)),
        ),
        vocabulary,
    )
    private val imported = listOf(
        file(10, "2G", "8 x deadlift", 40), file(10, "2G", "8 x squat", 30), file(12, null, "8 x low row", 25),
        // The same day as a class logged here: the file's copy isn't shown beside it.
        file(16, "2G", "8 x deadlift", 40),
    )

    // Newest first, the log's classes and the file's together, a day logged here once.
    @Test
    fun classesAreListedNewestFirst() {
        val classes = Past.classes(own, imported)
        assertEquals(listOf("2026-11-21 2G", "2026-11-21 3G", "2026-11-16 2G", "2026-11-12 class", "2026-11-10 2G"), classes.map { it.id })
        assertEquals(listOf(false, false, false, true, true), classes.map { it.imported })
        assertEquals(listOf(2, 1, 1, 1, 2), classes.map { it.rows.size })
        assertTrue(Past.classes(emptyList(), emptyList()).isEmpty())
    }

    // A row says what was held, the reps and what was said, in plain words.
    @Test
    fun aRowSaysWhatWasDone() {
        val saturday = Past.classes(own, imported).first().rows
        assertEquals(listOf("deadlift", "close grip explosive chest press"), saturday.map { it.name })
        assertEquals(listOf("7 reps · twinge", "8 reps · felt easy"), saturday.map { it.detail })
        assertEquals(Load(45, 2), saturday.first().logged.load)
        val known = known("8 x squat")
        fun row(logged: Logged) = PastRow("squat", logged)
        assertEquals("8, 8, 6 reps", row(Logged(day(1), known, Load(30, 2), 8..8, listOf(8, 8, 6))).reps)
        assertEquals("10 / 8 / 6", row(Logged(day(1), known, Load(30, 2), 10..10, listOf(10, 8, 6), ladder = listOf(10, 8, 6))).reps)
        assertEquals("timed", row(Logged(day(1), known, Load(30, 2), null, emptyList())).detail)
        assertEquals("", row(Logged(day(1), known, Load(30, 2), 8..8, emptyList())).detail)
    }

    // One lift over time is the sessions its number is read from: its other wordings that
    // take the same dumbbells too, and nothing of another lift.
    @Test
    fun aLiftOverTimeIsWhatItsNumberRestsOn() {
        val classes = Past.classes(own, imported)
        val deadlift = Past.lift(known("8 x deadlift").key, classes)
        assertEquals(listOf(day(21), day(21), day(16), day(10)), deadlift.map { it.date })
        assertEquals(listOf(45, 40, 40, 40), deadlift.map { it.row.logged.load.lb })
        assertEquals(listOf(false, false, false, true), deadlift.map { it.imported })
        assertEquals(listOf(day(10)), Past.lift(known("8 x squat").key, classes).map { it.date })
        assertTrue(Past.lift("no such lift", classes).isEmpty())
    }

    // A row on the sheet never done in its own words opens the sessions its number is read
    // from, judged from its side, under its own name; a lift never done opens nothing.
    @Test
    fun aSheetRowFindsTheSessionsItsNumberIsReadFrom() {
        fun one(day: Int, line: String, count: Int) = Imported(Logged(day(day), known(line), Load(40, count), 8..8, listOf(8)), "2G", line)
        val classes = Past.classes(emptyList(), listOf(one(10, "8 x dumbbell deadlift", 1), one(12, "8 total x alt dumbbell deadlift", 1), one(16, "8 total x alt dumbbell deadlift", 2)))
        val row = known("8 x deadlift")
        val all = classes.flatMap { c -> c.rows.map { it.logged } }
        assertEquals(Progression.own(row, all).map { it.date }.reversed(), Past.lift(row.key, classes, listOf(row)).map { it.date })
        assertEquals(listOf(day(16), day(10)), Past.lift(row.key, classes, listOf(row)).map { it.date })
        assertEquals("deadlift", Past.name(row.key, classes, listOf(row)))
        assertTrue(Past.lift(known("8 x squat").key, classes, listOf(known("8 x squat"))).isEmpty())
    }

    // A day the log has only an unreadable row for is still the log's day: the sheet leaves
    // the file out for it, so looking back does too.
    @Test
    fun aDayTheLogHasIsNotShownFromTheFile() {
        val classes = Past.classes(own, imported, ownDays = setOf(day(16), day(21), day(12)))
        assertEquals(listOf("2026-11-21 2G", "2026-11-21 3G", "2026-11-16 2G", "2026-11-10 2G"), classes.map { it.id })
    }
}
