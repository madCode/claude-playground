package spotter.coach

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

// Two classes logged by an invented lifter through the real logging code, then exported and read
// back as plain JSON, the way the scorecard reads a session file.
class ExportTest {
    private val vocabulary = Vocabulary.starting()
    private val identity = Identity(vocabulary)
    private val monday = LocalDate.of(2026, 3, 2)
    private val thursday = LocalDate.of(2026, 3, 5)
    private val board = listOf("* 8 x goblet squat", "* 8 x chest press", "* 30 sec of goblet squat hold", "* 8 / 6 / 4 x deadlift")

    private val written = mutableListOf<JournalEntry>()
    private fun sheet(day: LocalDate) = Sheet.make(board, Journal.history(written.filter { it.date < day }, vocabulary), identity)
    private fun save(day: LocalDate, className: String, draft: (Sheet) -> LogDraft) {
        val sheet = sheet(day)
        val logged = Logging.logged(written, day, className)
        Logging.events(draft(sheet).copy(seen = logged.keys), sheet, logged, day, className, at = 100L + written.size)
            .forEach { written += it.copy(id = written.size + 1L) }
    }

    private fun logTwoClasses() {
        save(monday, "2G") { s ->
            LogDraft()
                .with(s.rows[0].id, LogDraft.picked(s.rows[0], 40))
                .with(s.rows[1].id, LogDraft.picked(s.rows[1], 25).copy(count = 2))
                .with(s.rows[2].id, LogDraft.picked(s.rows[2], 35))
        }
        // A correction, and a row removed again.
        save(monday, "2G") { s ->
            LogDraft().with(s.rows[0].id, LogDraft.picked(s.rows[0], 40)).with(s.rows[1].id, LogDraft.picked(s.rows[1], 30).copy(count = 2, hurt = true))
        }
        save(thursday, "Orange 60 / Tornado!") { s ->
            LogDraft().with(s.rows[0].id, LogDraft.asListed(s.rows[0])).with(s.rows[3].id, LogDraft.picked(s.rows[3], 45).copy(count = 2))
        }
    }

    private fun sets(text: String) = Json.parseToJsonElement(text).jsonObject["sets"]!!.jsonArray.map { it.jsonObject }

    @Test
    fun eachClassIsASessionTheScorecardReads() {
        logTwoClasses()
        val files = Export.files(written)
        assertEquals(setOf("session-2026-03-02-2G.json", "session-2026-03-05-Orange_60_Tornado.json", Export.LOG_FILE), files.keys)

        val first = Json.parseToJsonElement(files.getValue("session-2026-03-02-2G.json")).jsonObject
        assertEquals("gym-session/1", first["schema"]!!.jsonPrimitive.content)
        assertEquals("2026-03-02", first["date"]!!.jsonPrimitive.content)
        assertEquals("2G", first["class"]!!.jsonPrimitive.content)
        // The rows as last corrected; the removed hold isn't there.
        val monday = sets(files.getValue("session-2026-03-02-2G.json"))
        assertEquals(listOf("8 x goblet squat", "8 x chest press"), monday.map { it["line"]!!.jsonPrimitive.content })
        // Per dumbbell, the count apart; a new row's sheet showed no number: null, not zero.
        assertEquals(40.0, monday[0]["weight"]!!.jsonPrimitive.double, 0.0)
        assertEquals(1, monday[0]["count"]!!.jsonPrimitive.int)
        assertEquals(JsonNull, monday[0]["suggested_weight"])
        assertEquals(30.0, monday[1]["weight"]!!.jsonPrimitive.double, 0.0)
        assertTrue(monday[1]["hurt"]!!.jsonPrimitive.boolean)
        assertEquals("one dumbbell, both hands", monday[0]["variant"]!!.jsonObject["implement"]!!.jsonPrimitive.content)

        // Thursday's sheet had a number for the squat, and it is the one the person saw.
        val thursday = sets(files.getValue("session-2026-03-05-Orange_60_Tornado.json"))
        assertEquals(45.0, thursday[0]["suggested_weight"]!!.jsonPrimitive.double, 0.0)
        assertEquals(1, thursday[0]["suggested_count"]!!.jsonPrimitive.int)
        assertEquals(45.0, thursday[0]["weight"]!!.jsonPrimitive.double, 0.0)
        // A ladder keeps its rounds, a side each.
        assertEquals(listOf(8, 6, 4), thursday[1]["rounds"]!!.jsonArray.map { it.jsonPrimitive.int })
    }

    // The same fields, with the same names, as the session made from a sheet: one shape to grade.
    @Test
    fun theShapeIsTheSheetSessions() {
        logTwoClasses()
        val fromLog = sets(Export.files(written).getValue("session-2026-03-02-2G.json")).first()
        val squat = identity.read(BoardLine.read("8 x goblet squat").words) as Reading.Known
        val fromSheet = sets(ScorecardSession.from(monday, "2G", sheet(monday), mapOf(0 to Logged(monday, squat, Load(40, 1), 8..8, listOf(8)))).toJson()).first()
        assertEquals(fromSheet.keys, fromLog.keys)
        // And the same values, but for what the sheet said in words.
        assertEquals(fromSheet - "suggested", fromLog - "suggested")
    }

    // The whole log, a line an event, with the correction and the removal as events of their own.
    @Test
    fun theFullLogKeepsEveryEvent() {
        logTwoClasses()
        val lines = Export.files(written).getValue(Export.LOG_FILE).trimEnd().lines().map { Json.parseToJsonElement(it).jsonObject }
        assertEquals(written.size, lines.size)
        assertEquals(written.map { it.kind.name }, lines.map { it["kind"]!!.jsonPrimitive.content })
        assertTrue(lines.any { it["kind"]!!.jsonPrimitive.content == "REMOVED" } && lines.any { it["kind"]!!.jsonPrimitive.content == "CORRECTED" })
        val ladder = lines.last()
        assertEquals(Export.LOG_SCHEMA, ladder["schema"]!!.jsonPrimitive.content)
        assertEquals("Orange 60 / Tornado!", ladder["class"]!!.jsonPrimitive.content)
        assertEquals(listOf(8, 6, 4), ladder["ladder"]!!.jsonArray.map { it.jsonPrimitive.int })
        assertEquals(true, ladder["heaviest_only"]!!.jsonPrimitive.boolean)
        assertEquals("deadlift", ladder["parts"]!!.jsonArray.single().jsonObject["movement"]!!.jsonPrimitive.content)
        // Nothing logged: only the empty log, so the folder shows the export ran.
        assertEquals(mapOf(Export.LOG_FILE to ""), Export.files(emptyList()))
    }
}
