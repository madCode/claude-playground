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
        // "Lately" is four weeks; before that it is history, not news.
        assertTrue(Progress.of(log, today.plusWeeks(4)).wentUp.isEmpty())
        assertEquals(1, Progress.of(log, day(20).plusWeeks(4)).wentUp.size)
        // The same exercise twice in one class is one session, not a step.
        assertTrue(single(logged(20, press, 25), logged(20, press, 30)).isEmpty())
    }

    // What is next is what the next sheet will say: up a dumbbell, or one clean session from it.
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
}
