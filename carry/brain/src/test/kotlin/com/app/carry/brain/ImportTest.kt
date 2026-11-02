package com.app.carry.brain

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Two rules the Python keeps on purpose for now, pinned here because the
 * fixture run reaches the second only together with a change of numbers:
 * a row whose numbers were cleared with a marker kept is still answered, so
 * the import takes the file's word and erases the logged numbers; and a
 * rename alone of an added row changes nothing compared, so it is not logged.
 * Invented rows.
 */
class ImportTest {
    private val kb = Kb.fromJson(File(System.getProperty("brainFixtures") ?: "../brain-fixtures", "exercises.json").readText())
    private val name = "session-2026-01-10-2G.json"

    private fun file(set: String, added: String = "") = ImportSource.Text(
        """{"schema": "gym-session/1", "sheet_id": "2026-01-10:2G", "date": "2026-01-10", "class": "2G",
           "sets": [{"ref": 0, "line": "10 x goblet squat", "canonical": "goblet squat", $set}], "added": [$added]}""",
    )

    @Test
    fun `numbers cleared with a marker kept erase the logged numbers`() {
        val first = planImport(listOf(name to file(""""weight": 20, "reps": 10""")), emptyList(), kb, write = true)
        assertEquals(listOf("$name: logged 1 set"), first.report)
        val cleared = planImport(listOf(name to file(""""weight": null, "reps": null, "easy": true""")), first.appended, kb, write = true)
        assertEquals(listOf("$name: updated 1"), cleared.report)
        val row = cleared.rewritten!!.single()
        assertEquals(JsonNull, row["weight"])
        assertEquals(JsonNull, row["reps"])
        assertEquals(JsonPrimitive(true), row["easy"])
    }

    @Test
    fun `a logged 0 cleared to blank is a change, not the same answer`() {
        val first = planImport(listOf(name to file(""""weight": 0, "reps": 10""")), emptyList(), kb, write = true)
        val cleared = planImport(listOf(name to file(""""weight": null, "reps": 10""")), first.appended, kb, write = true)
        assertEquals(listOf("$name: updated 1"), cleared.report)
        assertEquals(JsonNull, cleared.rewritten!!.single()["weight"])
    }

    @Test
    fun `a read that failed unexpectedly is reported as that, and the next file still imports`() {
        val plan = planImport(
            listOf(name to ImportSource.Failed("RuntimeError", unreadable = false), name to file(""""weight": 20""")),
            emptyList(), kb, write = true,
        )
        assertEquals(listOf("$name: not imported (RuntimeError)", "$name: logged 1 set"), plan.report)
    }

    @Test
    fun `an added row's id that is not text becomes the row id Python's str() makes`() {
        val plan = planImport(listOf(name to file(""""weight": 20""", """{"id": true, "name": "squat", "reps": 10.0}""")), emptyList(), kb, write = true)
        assertEquals(JsonPrimitive("2026-01-10:2G#added#True"), plan.appended.last()["row_id"])
    }

    @Test
    fun `a rename alone of an added row is not logged`() {
        val set = """"weight": 20"""
        val row = """{"id": "a1", "name": "%s", "weight": 15, "reps": 10}"""
        val first = planImport(listOf(name to file(set, row.format("goblet squat"))), emptyList(), kb, write = true)
        assertEquals(listOf("$name: logged 2 sets"), first.report)
        val renamed = planImport(listOf(name to file(set, row.format("reverse lunge"))), first.appended, kb, write = true)
        assertEquals(listOf(null), renamed.report)
        assertNull(renamed.rewritten)
        assertTrue(renamed.appended.isEmpty())
    }
}
