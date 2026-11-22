package spotter.coach

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File
import java.time.LocalDate

// The export read back as plain JSON, the way the scorecard reads a session file. Public board
// lines and an invented lifter.
class ScorecardTest {
    private val identity = Identity(Vocabulary.starting())
    private fun exercise(line: String) = identity.read(BoardLine.read(line).words) as Reading.Known
    private val earlier = LocalDate.of(2026, 3, 1)
    private val today = LocalDate.of(2026, 3, 3)

    private val board = listOf(
        "**Floor Block 1 - 10 minutes**",
        "* 8 x goblet squat",
        "* 8 x chest press",
        "* 8 x push-up",
        "* 8 x deadlift",
        "* 8 x swing lunge with rotation",
    )
    private val log = listOf(
        Logged(earlier, exercise("8 x goblet squat"), Load(35, 1), 8..8, listOf(8, 8, 8), easy = true),
        Logged(earlier, exercise("8 x chest press"), Load(25, 2), 8..8, listOf(8, 8, 7)),
    )

    private fun session(logged: Map<Int, Logged>, skipped: Set<Int> = emptySet()) =
        Json.parseToJsonElement(ScorecardSession.from(today, "2G", Sheet.make(board, log, identity), logged, skipped).toJson()).jsonObject

    @Test
    fun weightsArePerDumbbellWithTheCountApart() {
        val logged = mapOf(
            0 to Logged(today, exercise("8 x goblet squat"), Load(40, 1), 8..8, listOf(8, 8, 8)),
            1 to Logged(today, exercise("8 x chest press"), Load(25, 2), 8..8, listOf(8, 8, 8)),
        )
        val s = session(logged)
        assertEquals("2026-03-03", s["date"]!!.jsonPrimitive.content)
        val sets = s["sets"]!!.jsonArray.map { it.jsonObject }
        // A goblet 1×40 is 40, never a sum; 2×25 is 25, with the count in its own field.
        assertEquals(40.0, sets[0]["suggested_weight"]!!.jsonPrimitive.double, 0.0)
        assertEquals(40.0, sets[0]["weight"]!!.jsonPrimitive.double, 0.0)
        assertEquals(1, sets[0]["count"]!!.jsonPrimitive.int)
        assertEquals(25.0, sets[1]["suggested_weight"]!!.jsonPrimitive.double, 0.0)
        assertEquals(25.0, sets[1]["weight"]!!.jsonPrimitive.double, 0.0)
        assertEquals(2, sets[1]["count"]!!.jsonPrimitive.int)
    }

    // No number on the sheet is a null suggestion, so the scorecard counts it as unnumbered.
    @Test
    fun noNumberIsNullNotZero() {
        val sets = session(mapOf(3 to Logged(today, exercise("8 x deadlift"), Load(30, 2), 8..8, listOf(8, 8, 8))))["sets"]!!.jsonArray.map { it.jsonObject }
        assertEquals(JsonNull, sets[3]["suggested_weight"])
        assertEquals("new: go light", sets[3]["suggested"]!!.jsonPrimitive.content)
        assertEquals(30.0, sets[3]["weight"]!!.jsonPrimitive.double, 0.0)
        // Rows with no weight to grade: bodyweight and "not sure".
        assertEquals(JsonNull, sets[2]["suggested_weight"])
        assertEquals(JsonNull, sets[4]["suggested_weight"])
        assertEquals(JsonNull, sets[4]["key"])
    }

    // Trap 9: a row left blank is not skipped; only a row marked skipped is.
    @Test
    fun aBlankRowIsNotSkipped() {
        val sets = session(emptyMap(), skipped = setOf(1))["sets"]!!.jsonArray.map { it.jsonObject }
        assertEquals(JsonNull, sets[0]["weight"])
        assertEquals("false", sets[0]["skipped"]!!.jsonPrimitive.content)
        assertEquals("true", sets[1]["skipped"]!!.jsonPrimitive.content)
        assertEquals(5, sets.size)
    }

    // Carry's reader decides a file is a session from its schema and needs its sheet id; reps
    // are one number, the reps every set reached, with each set's apart.
    @Test
    fun itIsACarrySessionFile() {
        val logged = mapOf(1 to Logged(today, exercise("8 x chest press"), Load(25, 2), 8..8, listOf(8, 8, 7)))
        val s = session(logged)
        assertEquals("gym-session/1", s["schema"]!!.jsonPrimitive.content)
        assertEquals("2026-03-03:2G", s["sheet_id"]!!.jsonPrimitive.content)
        val row = s["sets"]!!.jsonArray[1].jsonObject
        assertEquals(7, row["reps"]!!.jsonPrimitive.int)
        assertEquals(8, row["prescribed_reps"]!!.jsonPrimitive.int)
        assertEquals(3, row["set_reps"]!!.jsonArray.size)
    }

    // A set logged against another row would be graded as that row's.
    @Test
    fun aSetMustBeLoggedAgainstItsOwnRow() {
        val wrong = mapOf(0 to Logged(today, exercise("8 x chest press"), Load(25, 2), 8..8, listOf(8, 8, 8)))
        assertThrows(IllegalArgumentException::class.java) { session(wrong) }
    }

    // docs/scorecard-example.json is what this code writes for an invented class, so the example
    // madCode checks against scorecard.py can't drift from the export.
    @Test
    fun theExampleIsWhatTheCodeWrites() {
        val board = listOf("**Floor Block 1 - 10 minutes**", "* 8 x goblet squat", "* 8 x chest press", "* 8 x push-up", "* 8 x deadlift", "* 10 / 8 / 6 x single arm low row")
        val logged = mapOf(
            0 to Logged(today, exercise("8 x goblet squat"), Load(40, 1), 8..8, listOf(8, 8, 8)),
            1 to Logged(today, exercise("8 x chest press"), Load(25, 2), 8..8, listOf(8, 8, 8)),
            3 to Logged(today, exercise("8 x deadlift"), Load(30, 2), 8..8, listOf(8, 8, 8)),
        )
        val written = ScorecardSession.from(today, "2G", Sheet.make(board, log, identity), logged).toJson() + "\n"
        val example = File("../docs/scorecard-example.json")
        if (System.getenv("WRITE_EXAMPLE") != null) example.writeText(written)
        assertEquals(written, example.readText())
    }
}
