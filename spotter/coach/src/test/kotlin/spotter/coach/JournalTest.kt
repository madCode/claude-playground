package spotter.coach

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

// An invented lifter's log of public board lines.
class JournalTest {
    private val vocabulary = Vocabulary.starting()
    private val identity = Identity(vocabulary)
    private fun exercise(line: String) = identity.read(BoardLine.read(line).words) as Reading.Known
    private val day = LocalDate.of(2026, 3, 1)
    private var clock = 0L

    private fun logged(id: Long, line: String, lb: Int, reps: List<Int> = listOf(8, 8, 8)) =
        Journal.logged(++clock, day, "2G", "/ $line / 1", line, exercise(line), Load(lb, 2), 8..8, null, reps).copy(id = id)

    @Test
    fun aCorrectionReplacesAndARemovalRemoves() {
        val press = logged(1, "8 x chest press", 25)
        val squat = logged(2, "8 x squat", 30)
        val fixed = press.copy(id = 3, kind = JournalEntry.Kind.CORRECTED, target = 1, lb = 20)
        val again = press.copy(id = 4, kind = JournalEntry.Kind.CORRECTED, target = 3, lb = 15)
        val gone = squat.copy(id = 5, kind = JournalEntry.Kind.REMOVED, target = 2)
        val history = Journal.history(listOf(press, squat, fixed, again, gone), vocabulary)
        assertEquals(listOf(15), history.map { it.load.lb })
        // The order written, not the order handed over.
        assertEquals(history, Journal.history(listOf(gone, again, fixed, squat, press), vocabulary))
    }

    // What was logged never changes meaning: a row keeps the identity it was logged with.
    @Test
    fun aRowKeepsItsLoggedIdentity() {
        val e = logged(1, "8 x floor close grip chest press", 25)
        val back = Journal.history(listOf(e), vocabulary).single()
        assertEquals("chest press [grip=close, position=floor]", back.key)
        // A movement the lists no longer hold keeps its logged name and key.
        val renamed = e.copy(parts = listOf(LoggedPart("bench thing", mapOf("grip" to "close"))), key = "bench thing [grip=close]")
        assertEquals("bench thing [grip=close]", Journal.history(listOf(renamed), vocabulary).single().key)
    }

    // A damaged entry loses itself, not the log.
    @Test
    fun aBrokenEntryIsSkipped() {
        val good = logged(1, "8 x squat", 30)
        val orphan = good.copy(id = 2, kind = JournalEntry.Kind.CORRECTED, target = 99, lb = 50)
        val noWeight = logged(3, "8 x deadlift", 40).copy(lb = 0)
        val threeDumbbells = logged(4, "8 x deadlift", 40).copy(count = 3)
        val emptyLadder = logged(5, "8 x chest press", 25).copy(ladder = emptyList())
        val unwritten = logged(0, "8 x lunge", 20)
        val history = Journal.history(listOf(good, orphan, noWeight, threeDumbbells, emptyLadder, unwritten), vocabulary)
        assertEquals(listOf(30, 25), history.map { it.load.lb })
        // The empty ladder reads as none, so the coach can use the row.
        Progression.suggest(exercise("8 x chest press"), 8..8, history)
    }

    // The parts are the identity: a stored key that no longer matches doesn't hide the row.
    @Test
    fun theKeyIsRebuiltFromTheParts() {
        val e = logged(1, "8 x deadlift", 40).copy(key = "an older format")
        assertEquals("deadlift", Journal.history(listOf(e), vocabulary).single().key)
    }

    // Removed stays removed, whatever is written against it after.
    @Test
    fun aRemovedRowCantBeCorrectedBack() {
        val press = logged(1, "8 x chest press", 25)
        val gone = press.copy(id = 2, kind = JournalEntry.Kind.REMOVED, target = 1)
        val late = press.copy(id = 3, kind = JournalEntry.Kind.CORRECTED, target = 1, lb = 30)
        assertTrue(Journal.history(listOf(press, gone, late), vocabulary).isEmpty())
    }

    // The history feeds progression as any log does.
    @Test
    fun theHistoryFeedsTheSuggestion() {
        val history = Journal.history(listOf(logged(1, "8 x chest press", 25)), vocabulary)
        val s = Progression.suggest(exercise("8 x chest press"), 8..8, history)
        // One clean session: the weight held is the number.
        assertTrue(s is Suggestion.Weight && s.load == Load(25, 2))
    }

    @Test
    fun partsAndIntsRoundTrip() {
        val parts = exercise("8 x TRX high row to bicep curl").parts.map { LoggedPart(it.movement.name, it.variations) }
        assertEquals(parts, Journal.partsFrom(Journal.partsJson(parts)))
        assertEquals(listOf(8, 6, 4), Journal.intsFrom(Journal.intsJson(listOf(8, 6, 4))))
    }
}
