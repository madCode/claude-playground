package spotter.coach

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

// Invented rows in gym-coach's history format (docs/HISTORY-FORMAT.md): nobody's training data.
class HistoryImportTest {
    private val vocabulary = Vocabulary.starting()
    private val identity = Identity(vocabulary)
    private fun exercise(words: String) = identity.read(BoardLine.read(words).words) as Reading.Known

    private fun row(
        line: String?, weight: String?, reps: Int?, asked: Int?, implement: String? = null, exercise: String? = null,
        date: String = "2026-03-03", extra: String = "",
    ) = buildString {
        append("""{"date": "$date", "exercise": ${exercise?.let { "\"$it\"" }}, "components": null, "line": ${line?.let { "\"$it\"" }}, """)
        append(""""variant": {"implement": ${implement?.let { "\"$it\"" }}, "arms": "both"}, "weight": $weight, "reps": $reps, "prescribed_reps": $asked, "class": "2G", "source": "form"$extra}""")
    }

    private val today = LocalDate.of(2026, 4, 1)
    private fun read(vararg rows: String) = HistoryImport.read(rows.joinToString("\n"), identity, today)

    // The count comes from the row's one hint, then from a name that fixes it, and is otherwise
    // unknown; a decimal weight rounds down.
    @Test
    fun weightAndCountAreReadWithoutGuessing() {
        val result = read(
            row("8 x chest press", "25.0", 8, 8, "double_dumbbell"),
            row("8 x goblet squat", "40", 8, 8),
            // A lunge is done with one dumbbell or two: nobody said, so nobody knows.
            row("8 x reverse lunge", "22.5", 8, 8),
            row("8 x deadlift", "30", 8, 8, "ybell"),
            // The name fixes the count: the hint says "two" by default on single-arm lines.
            row("8 each x single arm shoulder press", "15", 8, 8, "double_dumbbell"),
            row("8 x goblet squat", "40", 8, 8, "double_dumbbell"),
        )
        // 22.5 comes down to a dumbbell on the rack, never up.
        assertEquals(listOf(Load(25, 2), Load(40, 1), Load(20, null), Load(30, 1), Load(15, 1), Load(40, 1)), result.logged.map { it.load })
        assertEquals(exercise("8 x goblet squat").key, result.logged[1].key)
        assertEquals(listOf("2G", "8 x chest press"), listOf(result.rows[0].className, result.rows[0].line))
        assertTrue(result.left.isEmpty())
    }

    // Reported and asked reps are in the same terms, so the shortfall is what carries over, onto
    // the line's own reps a side: 12 of "12 total" is 6 a side done, not 12 (trap 7).
    // Where the file doesn't say how many dumbbells, the lift's usual number is taken, and the
    // row says so; a lift whose number varies stays unknown, and what the file says always wins.
    @Test
    fun aLiftWithAUsualNumberOfDumbbellsIsReadAtIt() {
        val read = read(
            row("8 x deadlift", "40", 8, 8), row("8 x chest fly", "15", 8, 8), row("8 x tricep extension", "20", 8, 8),
            row("8 x reverse lunge", "20", 8, 8), row("8 x deadlift", "40", 8, 8, implement = "single_dumbbell", date = "2026-03-04"),
            row("8 x single arm deadlift", "20", 8, 8, date = "2026-03-05"),
        )
        assertEquals(listOf(2, 2, 1, null, 1, 1), read.rows.map { it.logged.load.count })
        assertEquals(listOf(true, true, true, false, false, false), read.rows.map { it.countAssumed })
        // Which lets the plain lift lend to the one-armed one: the sheet has a number.
        val sheet = Sheet.make(listOf("8 x single arm chest fly"), read.logged, identity)
        assertEquals(Load(15, 1), (sheet.rows.single().suggestion as Suggestion.Weight).load)
    }

    @Test
    fun repsAreSetAgainstTheLine() {
        val result = read(
            // Asked 12 of "12 total": both sides counted, so 12 is 6 a side and 8 is 4.
            row("12 total x reverse lunge", "20", 12, 12, "double_dumbbell"),
            row("12 total x reverse lunge", "20", 8, 12, "double_dumbbell"),
            // Asked 6 of "12 total": a side. 12 reported is the set done as asked.
            row("12 total x reverse lunge", "20", 6, 6, "double_dumbbell"),
            row("12 total x reverse lunge", "20", 5, 6, "double_dumbbell"),
            row("12 total x reverse lunge", "20", 12, 6, "double_dumbbell"),
            row("8 x chest press", "25", 6, 8, "double_dumbbell"),
            row("6 - 10 x chest press", "25", 9, 6, "double_dumbbell"),
            row("8 x chest press", "25", null, 8, "double_dumbbell"),
            row("8 x chest press", "25", 8, null, "double_dumbbell"),
        ).logged
        assertEquals(listOf(6..6, 6..6, 6..6, 6..6, 6..6, 8..8, 6..10, 8..8, 8..8), result.map { it.target })
        assertEquals(listOf(listOf(6), listOf(4), listOf(6), listOf(5), listOf(6), listOf(6), listOf(9), emptyList(), listOf(8)), result.map { it.reps })
        assertEquals(listOf(Outcome.CLEAN, Outcome.NEAR_MISS, Outcome.IN_RANGE), listOf(result[0], result[1], result[6]).map { it.outcome })
        // Nothing said about the reps: nothing to judge, so it can't raise a number.
        assertEquals(false, result[7].judged)
    }

    // Where the reported number may count a side or both, the row is left out: 10 against
    // "12 total" is one short or four over, and "8" against "4 each" is clean or double.
    @Test
    fun repsThatMayBeASideOrBothAreLeftOut() {
        val result = read(
            row("12 total x reverse lunge", "20", 10, 6, "double_dumbbell"),
            row("12 total x reverse lunge", "20", 12, null, "double_dumbbell"),
            row("12 total x reverse lunge", "20", 12, 9, "double_dumbbell"),
            row("6 each x reverse lunge", "20", 10, 6, "double_dumbbell"),
            row("4 each x reverse lunge", "20", 8, null, "double_dumbbell"),
            row("8 x chest press", "25", 12, null, "double_dumbbell"),
            // A wide range could have asked for 8 a side, or 8 may be 4 a side counted twice.
            row("4 - 8 each x reverse lunge", "20", 8, 4, "double_dumbbell"),
            // "1 total" is no reps a side.
            row("1 total x reverse lunge", "20", 1, 1, "double_dumbbell"),
            row("2 / 1 total x reverse lunge", "20", 1, 1, "double_dumbbell"),
            // Twice what a side was asked is the set done as asked, counted on both sides.
            row("6 each x reverse lunge", "20", 12, 6, "double_dumbbell"),
            // One short of an odd total is one short, not rounded away.
            row("5 total x reverse lunge", "20", 4, 5, "double_dumbbell"),
            row("6 - 10 each x reverse lunge", "20", 8, 6, "double_dumbbell"),
            // No sides to confuse: more than asked is more than asked.
            row("8 x chest press", "25", 10, 8, "double_dumbbell"),
        )
        assertEquals(mapOf(HistoryImport.Left.REPS_UNCLEAR to 9), result.left)
        assertEquals(listOf(listOf(6), listOf(1), listOf(8), listOf(10)), result.logged.map { it.reps })
        assertEquals(listOf(2..2, Outcome.NEAR_MISS), result.logged[1].let { listOf(it.target, it.outcome) })
    }

    // A ladder has one weight, its heaviest round's, so it can hold a number but not raise it.
    @Test
    fun aLadderIsItsHeaviestRound() {
        val (clean, short) = read(
            row("10 / 8 / 6 x squat", "35", 6, 6, "double_dumbbell"),
            row("10 / 8 / 6 total x reverse lunge", "20", 2, 3, "double_dumbbell"),
        ).logged
        assertEquals(listOf(listOf(10, 8, 6), listOf(10, 8, 6), true), listOf(clean.ladder, clean.reps, clean.heaviestOnly))
        assertEquals(listOf(listOf(5, 4, 3), listOf(5, 4, 2)), listOf(short.ladder, short.reps))
        // Asked as a round's own total, both sides were counted: 6 of 10 is two a side short.
        val total = read(row("10 / 8 / 6 total x reverse lunge", "20", 6, 10, "double_dumbbell")).logged.single()
        assertEquals(listOf(5, 4, 1), total.reps)
    }

    // An old row has no line: the name it was filed under, and its own word on what was asked.
    @Test
    fun anOldRowIsReadFromItsName() {
        val result = read(
            row(null, "15", 10, 10, exercise = "shoulder press"),
            row(null, "15", 10, null, exercise = "shoulder press"),
            """{"date": "2026-03-05", "exercise": null, "components": ["deadlift", "bent over row"], "line": null, "variant": null, "weight": 20, "reps": 10, "prescribed_reps": 10}""",
        )
        assertEquals(listOf(exercise("10 x shoulder press").key, exercise("10 x deadlift to bent over row").key), result.logged.map { it.key })
        assertEquals(listOf(10..10, listOf(10), null), result.logged[0].let { listOf(it.target, it.reps, it.load.count) })
        // With no word on what was asked, 10 reps can't be called clean.
        assertEquals(mapOf(HistoryImport.Left.REPS_UNCLEAR to 1), result.left)
    }

    // What can't be read in full is left out and counted, never half-read.
    @Test
    fun rowsThatCantBeTrustedAreLeftOutByReason() {
        val result = read(
            "not json",
            "[1, 2]",
            row("8 x chest press", "25", 8, 8, date = "last tuesday"),
            row("8 x chest press", "25", 8, 8, extra = """, "skipped": true"""),
            row("8 x push-up", "null", 8, 8, "bodyweight"),
            row("8 x chest press", "\"25\"", 8, 8),
            row("8 x chest press", "0", 8, 8),
            row("8 x chest press", "100", 8, 8),
            row("8 x chest press", "2", 8, 8),
            row("8 x chest press", "25", 8, 8, extra = """, "easier": true"""),
            row("8 x chest press", "25", 8, 8, date = "2026-04-02"),
            row("8 x chest press", "25", 8, 8, date = "1999-12-31"),
            row("8 x chest press", "25", 4, 8, extra = """, "breakdown": "fail""""),
            row("8 x zorbing", "25", 8, 8),
            row("12 total x reverse lunge", "20", 12, null),
            "",
            row("8 x chest press", "25", 8, 8, "double_dumbbell", date = "2026-03-01", extra = """, "hurt": true, "hard": true, "note": "said 30 lb"}""".dropLast(1)),
        )
        assertEquals(
            mapOf(
                HistoryImport.Left.UNREADABLE to 5, HistoryImport.Left.SKIPPED to 1, HistoryImport.Left.NO_WEIGHT to 3,
                HistoryImport.Left.OFF_RACK to 2, HistoryImport.Left.EASIER to 1,
                HistoryImport.Left.BROKE_DOWN to 1, HistoryImport.Left.NOT_NAMED to 1, HistoryImport.Left.REPS_UNCLEAR to 1,
            ),
            result.left,
        )
        val kept = result.logged.single()
        assertEquals(listOf(LocalDate.of(2026, 3, 1), Load(25, 2), true, true), listOf(kept.date, kept.load, kept.hurt, kept.hard))
    }

    // Not finished, though the reps don't say so: it can't be the clean set a step up starts from.
    @Test
    fun anUnfinishedSetDoesntRaiseTheNumber() {
        val history = read(row("8 x chest press", "25", 8, 8, "double_dumbbell", extra = """, "incomplete": true""")).logged
        assertEquals(true, history.single().hard)
        val sheet = Sheet.make(listOf("* 8 x chest press"), history, identity)
        assertEquals("2×25 =", sheet.glance(sheet.rows.single()))
    }

    // A file with a byte-order mark keeps its first row, and one too long to be a history keeps
    // its newest rows, which are appended last, and says how many it didn't take.
    @Test
    fun theFirstRowAndTheNewestRowsAreKept() {
        assertEquals(1, HistoryImport.read("\uFEFF" + row("8 x chest press", "25", 8, 8), identity, today).rows.size)
        val old = row("8 x chest press", "20", 8, 8, date = "2026-03-01")
        val text = (List(HistoryImport.MAX_LINES) { old } + row("8 x deadlift", "40", 8, 8, date = "2026-03-02")).joinToString("\n")
        val result = HistoryImport.read("$old\n$text", identity, today)
        assertEquals(mapOf(HistoryImport.Left.TOO_MANY to 2), result.left)
        assertEquals(40, result.logged.last().load.lb)
    }

    // Rows come out in date order whatever order they were appended in, a day's rows as written.
    @Test
    fun historyIsInDateOrder() {
        val result = read(
            row("8 x chest press", "30", 8, 8, date = "2026-03-10"),
            row("8 x chest press", "20", 8, 8, date = "2026-03-03"),
            row("8 x deadlift", "40", 8, 8, date = "2026-03-03"),
        )
        assertEquals(listOf(20, 40, 30), result.logged.map { it.load.lb })
    }

    // The whole path: an imported history puts a number on the next sheet.
    @Test
    fun anImportedHistoryFeedsTheSheet() {
        val once = read(row("8 x chest press", "25", 8, 8, "double_dumbbell")).logged
        assertEquals("2×25 =", Sheet.make(listOf("* 8 x chest press"), once, identity).let { it.glance(it.rows.single()) })
        // "Felt easy" comes through, and one such session is enough to go up.
        val history = read(row("8 x chest press", "25", 8, 8, "double_dumbbell", extra = """, "easy": true""")).logged
        val sheet = Sheet.make(listOf("* 8 x chest press"), history, identity)
        assertEquals("2×30 ▲", sheet.glance(sheet.rows.single()))
        // Hard or unfinished outweighs easy.
        assertEquals(false, read(row("8 x chest press", "25", 8, 8, extra = """, "easy": true, "incomplete": true""")).logged.single().easy)
    }

    // Carry's fixtures hold an invented lifter's history in the real format, 1,300 rows of public
    // board lines: every row is either read or left out with a reason, and none throws.
    @Test
    fun theInventedLiftersHistoryReads() {
        val rows = File("../../carry/brain-fixtures/sheets.jsonl").readLines().filter { it.isNotBlank() }
            .flatMap { Json.parseToJsonElement(it).jsonObject.getValue("logged").jsonArray }
        val result = HistoryImport.read(rows.joinToString("\n") { it.toString() }, identity, LocalDate.of(2030, 1, 1))
        assertEquals(rows.size, result.rows.size + result.left.values.sum())
        assertEquals(null, result.left[HistoryImport.Left.UNREADABLE])
        // 698 when written. A change to the word lists may raise it; a drop means rows that
        // were read no longer are.
        assertTrue("${result.rows.size} read, left ${result.left}", result.rows.size >= 698)
    }
}
