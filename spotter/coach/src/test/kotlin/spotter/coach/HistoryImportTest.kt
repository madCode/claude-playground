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

    private fun read(vararg rows: String) = HistoryImport.read(rows.joinToString("\n"), identity)

    // The count comes from the row's one hint, then from a name that fixes it, and is otherwise
    // unknown; a decimal weight rounds down.
    @Test
    fun weightAndCountAreReadWithoutGuessing() {
        val result = read(
            row("8 x chest press", "25.0", 8, 8, "double_dumbbell"),
            row("8 x goblet squat", "40", 8, 8),
            row("8 x deadlift", "22.5", 8, 8),
            row("8 x deadlift", "30", 8, 8, "ybell"),
        )
        assertEquals(listOf(Load(25, 2), Load(40, 1), Load(22, null), Load(30, 1)), result.logged.map { it.load })
        assertEquals(exercise("8 x goblet squat").key, result.logged[1].key)
        assertEquals(listOf("2G", "8 x chest press"), listOf(result.rows[0].className, result.rows[0].line))
        assertTrue(result.left.isEmpty())
    }

    // Reported and asked reps are in the same terms, so the shortfall is what carries over, onto
    // the line's own reps a side: 12 of "12 total" is 6 a side done, not 12 (trap 7).
    @Test
    fun repsAreSetAgainstTheLine() {
        val result = read(
            row("12 total x reverse lunge", "20", 12, 12, "double_dumbbell"),
            row("12 total x reverse lunge", "20", 6, 6, "double_dumbbell"),
            row("8 x chest press", "25", 6, 8, "double_dumbbell"),
            row("6 - 10 x chest press", "25", 9, 6, "double_dumbbell"),
            row("8 x chest press", "25", null, 8, "double_dumbbell"),
        ).logged
        assertEquals(listOf(6..6, 6..6, 8..8, 6..10, 8..8), result.map { it.target })
        assertEquals(listOf(listOf(6), listOf(6), listOf(6), listOf(9), emptyList()), result.map { it.reps })
        assertEquals(listOf(Outcome.CLEAN, Outcome.CLEAN, Outcome.NEAR_MISS, Outcome.IN_RANGE), result.take(4).map { it.outcome })
        // Nothing said about the reps: nothing to judge, so it can't raise a number.
        assertEquals(false, result[4].judged)
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
            row("8 x chest press", "25", 4, 8, extra = """, "breakdown": "fail""""),
            row("8 x zorbing", "25", 8, 8),
            row("12 total x reverse lunge", "20", 12, null),
            "",
            row("8 x chest press", "25", 8, 8, "double_dumbbell", date = "2026-03-01", extra = """, "hurt": true, "hard": true, "note": "said 30 lb"}""".dropLast(1)),
        )
        assertEquals(
            mapOf(
                HistoryImport.Left.UNREADABLE to 3, HistoryImport.Left.SKIPPED to 1, HistoryImport.Left.NO_WEIGHT to 3,
                HistoryImport.Left.BROKE_DOWN to 1, HistoryImport.Left.NOT_NAMED to 1, HistoryImport.Left.REPS_UNCLEAR to 1,
            ),
            result.left,
        )
        val kept = result.logged.single()
        assertEquals(listOf(LocalDate.of(2026, 3, 1), Load(25, 2), true, true), listOf(kept.date, kept.load, kept.hurt, kept.hard))
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
        val history = read(row("8 x chest press", "25", 8, 8, "double_dumbbell")).logged
        val sheet = Sheet.make(listOf("* 8 x chest press"), history, identity)
        assertEquals("2×30 ▲", sheet.glance(sheet.rows.single()))
    }

    // Carry's fixtures hold an invented lifter's history in the real format, 1,300 rows of public
    // board lines: every row is either read or left out with a reason, and none throws.
    @Test
    fun theInventedLiftersHistoryReads() {
        val rows = File("../../carry/brain-fixtures/sheets.jsonl").readLines().filter { it.isNotBlank() }
            .flatMap { Json.parseToJsonElement(it).jsonObject.getValue("logged").jsonArray }
        val result = HistoryImport.read(rows.joinToString("\n") { it.toString() }, identity)
        assertEquals(rows.size, result.rows.size + result.left.values.sum())
        assertEquals(null, result.left[HistoryImport.Left.UNREADABLE])
        // 702 when written. A change to the word lists may raise it; a drop means rows that
        // were read no longer are.
        assertTrue("${result.rows.size} read, left ${result.left}", result.rows.size >= 700)
    }
}
