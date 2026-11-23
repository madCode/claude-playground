package spotter.coach

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

// A board in the feed's shape, made of public board lines, and an invented lifter's log.
class SheetTest {
    private val identity = Identity(Vocabulary.starting())
    private fun exercise(line: String) = identity.read(BoardLine.read(line).words) as Reading.Known
    private fun logged(line: String, lb: Int, reps: List<Int>, count: Int? = 2, target: IntRange? = 8..8, easy: Boolean = false) =
        Logged(LocalDate.of(2026, 3, 1), exercise(line), Load(lb, count), target, reps, easy = easy)

    private val board = listOf(
        "**Tread / Row Block 1 - 12.5 minutes**",
        "* 3 min push (PW @ 4%+)",
        "**Floor Block 1 - 12.5 minutes circuit**",
        "* 8 x close grip chest press (explosive)",
        "* 8 x deadlift",
        "* 8 each x swing lunge with rotation",
        "* Repeat until time is called",
        "**Floor Block 2 - 9.5 minutes circuit**",
        "* 8 x tricep push-up",
        "* 8 x deadlift to bicep curl",
    )

    // The glance as the person sees it: board words, then the number, ▲ ▼ =, warnings on top.
    @Test
    fun theSheetReadsInBoardOrderWithWarningsFirst() {
        val log = listOf(
            logged("8 x close grip chest press (explosive)", 25, listOf(8, 8, 8), easy = true),
            logged("8 x deadlift", 40, listOf(8, 8, 8), easy = true),
            logged("8 x bicep curl", 20, listOf(8, 8, 7)),
        )
        val text = Sheet.make(board, log, identity).text()
        assertEquals(
            """
            ! 8 each x swing lunge with rotation: not sure what this is (unknown words)
            == floor block 1 - 12.5 minutes circuit
            8 x close grip chest press (explosive)  2×30 ▲
            8 x deadlift  2×45 ▲
            8 each x swing lunge with rotation  ?
            == floor block 2 - 9.5 minutes circuit
            8 x tricep push-up  no weight
            8 x deadlift to bicep curl  2×20 new
            """.trimIndent(),
            text,
        )
    }

    // Trap 2: a combo's part takes a number only from that part done the same way.
    @Test
    fun aComboPartCountsOnlyOnTheSameImplement() {
        val log = listOf(logged("8 x bicep curl", 20, listOf(8, 8, 8)))
        val ybell = Sheet.make(listOf("8 x y-bell high row to bicep curl"), log, identity).rows.single()
        assertEquals(Suggestion.NoNumber.NEW, ybell.suggestion)
        // A part with no number leaves the combo without one, rather than the other part's guess.
        val plain = Sheet.make(listOf("8 x squat to bicep curl"), log, identity).rows.single()
        assertEquals(Suggestion.NoNumber.NEW, plain.suggestion)
        assertTrue(plain.warnings.single().contains("squat"))
        // Parts held differently share no number.
        val mixed = listOf(logged("8 x goblet squat", 30, listOf(8, 8, 8), count = 1), logged("8 x dumbbells shoulder press", 15, listOf(8, 8, 8)))
        assertTrue(Sheet.make(listOf("8 x goblet squat to dumbbells shoulder press"), mixed, identity).rows.single().suggestion is Suggestion.NoNumber)
    }

    // A part that twinged holds the combo at what twinged, with the warning on the row (trap 3:
    // it never raises a number), and a bodyweight part neither blocks nor gets a warning.
    @Test
    fun aComboWithAPartThatTwingedIsHeldThere() {
        val log = listOf(
            logged("8 x deadlift", 30, listOf(8, 8, 8), easy = true),
            Logged(LocalDate.of(2026, 3, 2), exercise("8 x high pull"), Load(15, 2), 8..8, listOf(8, 8, 8), hurt = true),
        )
        val twinged = Sheet.make(listOf("8 x deadlift to high pull"), log, identity).rows.single()
        assertEquals(Load(15, 2), (twinged.suggestion as Suggestion.Weight).load)
        assertEquals(listOf("it twinged last time at 2×15"), twinged.warnings)
        val pushUp = Sheet.make(listOf("8 x deadlift to push-up"), log, identity).rows.single()
        assertEquals(Load(35, 2), (pushUp.suggestion as Suggestion.Weight).load)
        assertTrue(pushUp.warnings.isEmpty())
    }

    // A combo that hurt at the lightest dumbbell has no number, and its other wording doesn't
    // get one from its parts instead. The last weight held is the shared history's too.
    @Test
    fun aCombosNoNumberStandsInItsOtherWording() {
        val log = listOf(
            logged("8 x lateral lunge", 20, listOf(8, 8)),
            logged("8 x upright row", 15, listOf(8, 8)),
            Logged(LocalDate.of(2026, 3, 2), exercise("8 x alt lateral lunge to upright row"), Load(3, 2), 8..8, listOf(4), hurt = true, stopped = true),
        )
        for (line in listOf("8 x alt lateral lunge to upright row", "8 x lateral lunge to upright row")) {
            val row = Sheet.make(listOf(line), log, identity).rows.single()
            assertTrue(line, row.suggestion is Suggestion.NoNumber && !(row.suggestion as Suggestion.NoNumber).new)
        }
        // Nor when it was held another way, which the coach doesn't count: still not the parts'.
        val oneDumbbell = log.dropLast(1) + Logged(LocalDate.of(2026, 3, 2), exercise("8 x alt lateral lunge to upright row"), Load(10, 1), 8..8, listOf(8, 8))
        assertEquals(Suggestion.NoNumber.NEW, Sheet.make(listOf("8 x lateral lunge to upright row"), oneDumbbell, identity).rows.single().suggestion)
        val floor = Sheet.make(listOf("8 x floor chest press"), listOf(logged("8 x chest press", 25, listOf(8, 8))), identity).rows.single()
        assertEquals(Load(25, 2), floor.lastHeld)
    }

    // Trap 6: bodyweight and banded-with-nothing-held rows want no weight; a bare line does.
    @Test
    fun bodyweightAndBandedRowsWantNoWeight() {
        val none = listOf("8 x bodyweight squat", "8 x banded squat", "8 x push-up", "8 x TRX low row", "30 sec of squat hold", "8 x squat pulses", "8 x v-up")
        Sheet.make(none, emptyList(), identity).rows.forEach { assertNull(it.shown, it.suggestion) }
        val some = listOf("5 x squat", "8 x dumbbell burpee", "8 each x low band single leg hold with single arm shoulder press", "30 sec of goblet squat hold")
        Sheet.make(some, emptyList(), identity).rows.forEach { assertEquals(it.shown, Suggestion.NoNumber.NEW, it.suggestion) }
    }

    // Trap 8 and trap 7: a ladder's number is for its first round; "12 total" is 6 a side.
    @Test
    fun theTargetIsRepsASideForTheFirstRound() {
        val rows = Sheet.make(listOf("10 / 8 / 6 x squat", "12 total x alt reverse lunge", "6 / 8 / 10 total x goblet alt lateral lunge"), emptyList(), identity).rows
        assertEquals(10..10, rows[0].target)
        assertEquals(6..6, rows[1].target)
        // A ladder in totals is a side's half too (from the corpus).
        assertEquals(3..3, rows[2].target)
    }

    // An imported load with no count takes the row's count when the row's words fix it.
    @Test
    fun anUnknownCountTakesTheRowsWhenItsWordsFixIt() {
        val log = listOf(logged("8 x goblet squat", 40, listOf(8, 8, 8), count = null, easy = true))
        val row = Sheet.make(listOf("8 x goblet squat"), log, identity).rows.single()
        assertEquals(Load(45, 1), (row.suggestion as Suggestion.Weight).load)
        val bare = Sheet.make(listOf("8 x squat"), listOf(logged("8 x squat", 40, listOf(8, 8, 8), count = null, easy = true)), identity)
        assertEquals("45 lb each (count?)", (bare.rows.single().suggestion as Suggestion.Weight).load.label)
    }

    // Trap 9: a borrowed board says so, so leaving its rows blank is normal.
    @Test
    fun aBorrowedBoardSaysSo() {
        val text = Sheet.make(listOf("8 x squat"), emptyList(), identity, borrowedFrom = "3G").text()
        assertTrue(text.startsWith("Borrowed from the 3G board"))
    }

    @Test
    fun aBigJumpShowsItsTest() {
        val log = listOf(logged("8 x bicep curl", 12, listOf(8, 8, 8), easy = true))
        assertTrue(Sheet.make(listOf("8 x bicep curl"), log, identity).text().endsWith("2×12 = · try 15 if set 1 felt easy"))
    }

    // From the 2026-11-20 board: a complex inside a floor block stays under it, and a row block
    // is a block of its own.
    @Test
    fun aComplexStaysInsideItsBlock() {
        val lines = listOf("**Floor Block 1 - 10 minutes**", "Back-to-back complex:", "* 8 x deadlift", "**Floor Block 2 - 10 minutes**", "Back-to-back complex:", "* 8 x squat", "**Row Block - 2.5 minutes**")
        val rows = Sheet.make(lines, emptyList(), identity).rows
        assertEquals(listOf("floor block 1 - 10 minutes · back-to-back complex", "floor block 2 - 10 minutes · back-to-back complex"), rows.map { it.block })
    }

    // Reps changed but the rules held: the glance says =, not ▼.
    @Test
    fun aChangeOfRepsIsNotADrop() {
        val log = listOf(logged("8 x squat", 20, listOf(8, 8, 7)))
        val row = Sheet.make(listOf("12 x squat"), log, identity).rows.single()
        assertEquals(Change.SAME, (row.suggestion as Suggestion.Weight).change)
    }

    // One leg is a factor for what the legs move; a curl on one leg borrows nothing.
    @Test
    fun aBalanceCurlBorrowsNothing() {
        val log = listOf(logged("8 x bicep curl", 15, listOf(8, 8, 8)))
        assertEquals(Suggestion.NoNumber.NEW, Sheet.make(listOf("8 each x balance bicep curl"), log, identity).rows.single().suggestion)
    }

    // Logging finds its row by id: a row keeps it when the board gains or loses rows around it,
    // and a line repeated in a block gets one per repeat.
    @Test
    fun aRowsIdSurvivesTheBoardChangingAroundIt() {
        val kept = Sheet.make(board, emptyList(), identity).rows
        val fetched = Sheet.make(board.take(3) + "* 8 x squat" + board.drop(3), emptyList(), identity).rows
        val deadlift = kept.first { it.shown == "8 x deadlift" }
        assertEquals(deadlift.id, fetched.first { it.shown == "8 x deadlift" }.id)
        assertTrue(kept.indexOf(deadlift) != fetched.indexOfFirst { it.id == deadlift.id })
        // A board fixed after posting: a capital, a double space, the block's minutes.
        val edited = Sheet.make(listOf("**Floor Block 1 - 13 minutes circuit**", "* 8 x close grip chest press (explosive)", "* 8 x  Deadlift"), emptyList(), identity).rows
        assertEquals(deadlift.id, edited.first { it.shown.endsWith("Deadlift") }.id)
        // The count is part of the row.
        assertTrue(Sheet.make(listOf("**Floor Block 1 - 12.5 minutes circuit**", "* 10 x deadlift"), emptyList(), identity).rows.single().id != deadlift.id)
        val twice = Sheet.make(listOf("**Floor Block 1**", "* 8 x squat", "* 8 x deadlift", "* 8 x squat", "**Floor Block 2**", "* 8 x squat"), emptyList(), identity).rows
        assertEquals(4, twice.map { it.id }.toSet().size)
        // Ids are stored in the log: their format is fixed here, apart from the code's names.
        assertEquals("floor block 1 / reps 8-8 plain deadlift / 1", deadlift.id)
        assertEquals(" / ladder 10/8/6 plain squat / 1", Sheet.make(listOf("10 / 8 / 6 x squat"), emptyList(), identity).rows.single().id)
        assertEquals(" / timed 30 goblet squat hold / 1", Sheet.make(listOf("30 sec of goblet squat hold"), emptyList(), identity).rows.single().id)
    }

    // What logging shows back: what Spotter read, and why the number, for each kind of row.
    @Test
    fun eachRowSaysWhatWasReadAndWhy() {
        val log = listOf(logged("8 x deadlift", 40, listOf(8, 8, 8), easy = true))
        val rows = Sheet.make(listOf("8 x deadlift", "8 x close grip chest press", "20 x deadlift", "8 x push-up", "8 x zorbing"), log, identity).rows
        val (deadlift, press, far, pushUp, unknown) = rows
        assertEquals("deadlift", deadlift.understood)
        assertEquals(listOf("clean at 2×40: up a dumbbell"), deadlift.why)
        assertEquals("chest press [grip=close]", press.understood)
        assertEquals(listOf("Nothing logged for this exercise yet, and no sound link to one that has: start light."), press.why)
        // A gap with its own reason says that reason, not the one for a new exercise.
        assertEquals(listOf("last time was 8 reps and this is 20 reps: no sound conversion"), far.why)
        assertEquals(listOf("Done without a weight."), pushUp.why)
        assertEquals("not sure: unknown words (zorbing)", unknown.understood)
        assertEquals(listOf("Not sure what this is, so no number."), unknown.why)
    }

    // The feed writes "&" as "&amp;": the row shows the board's words, not its markup.
    @Test
    fun aRowShowsAnAmpersandAsOne() {
        val row = Sheet.make(listOf("* 8 x squat &amp; press"), emptyList(), identity).rows.single()
        assertEquals("8 x squat & press", row.shown)
    }
}
