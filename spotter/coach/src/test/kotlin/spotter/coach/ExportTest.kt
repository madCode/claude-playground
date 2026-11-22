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
    private val board = listOf("* 8 x goblet squat", "* 8 x chest press", "* 30 sec of goblet squat hold", "* 8 / 6 / 4 x deadlift", "* 6 / 8 / 10 total x reverse lunge")
    private val tornado = "session-2026-03-05-Orange_60_Tornado-%08x.json".format("Orange 60 / Tornado!".hashCode())

    private val written = mutableListOf<JournalEntry>()
    private fun sheet(day: LocalDate) = Sheet.make(board, Journal.history(written.filter { it.date < day }, vocabulary), identity)
    private fun save(day: LocalDate, className: String, draft: (Sheet) -> LogDraft) {
        val sheet = sheet(day)
        val logged = Logging.logged(sheet, written, day, className)
        Logging.events(draft(sheet).copy(seen = logged.byRow.keys), sheet, logged, day, className, at = 100L + written.size)
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
            LogDraft()
                .with(s.rows[0].id, LogDraft.asListed(s.rows[0]))
                .with(s.rows[3].id, LogDraft.picked(s.rows[3], 45).copy(count = 2))
                .with(s.rows[4].id, LogDraft.picked(s.rows[4], 20).copy(count = 2))
                // Lighter than the sheet said, two dumbbells against its one: the two never mix.
                .with(s.rows[1].id, LogDraft.picked(s.rows[1], 25))
                .with(s.rows[2].id, LogDraft.picked(s.rows[2], 35))
        }
    }

    private fun sets(text: String) = Json.parseToJsonElement(text).jsonObject["sets"]!!.jsonArray.map { it.jsonObject }

    @Test
    fun eachClassIsASessionTheScorecardReads() {
        logTwoClasses()
        val files = Export.files(written)
        assertEquals(setOf("session-2026-03-02-2G.json", tornado, Export.LOG_FILE), files.keys)

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
        val thursday = sets(files.getValue(tornado)).associateBy { it["line"]!!.jsonPrimitive.content }
        val squat = thursday.getValue("8 x goblet squat")
        assertEquals(45.0, squat["suggested_weight"]!!.jsonPrimitive.double, 0.0)
        assertEquals(1, squat["suggested_count"]!!.jsonPrimitive.int)
        assertEquals(45.0, squat["weight"]!!.jsonPrimitive.double, 0.0)
        // What the sheet showed and what was lifted, each per dumbbell and neither a sum: the
        // sheet said 2×30 after Monday's twinge, and 2×25 was done.
        val press = thursday.getValue("8 x chest press")
        assertEquals(30.0, press["suggested_weight"]!!.jsonPrimitive.double, 0.0)
        assertEquals(25.0, press["weight"]!!.jsonPrimitive.double, 0.0)
        assertEquals(2, press["count"]!!.jsonPrimitive.int)
        // A ladder keeps the board's rounds, also when the board counts both sides.
        assertEquals(listOf(8, 6, 4), thursday.getValue("8 / 6 / 4 x deadlift")["rounds"]!!.jsonArray.map { it.jsonPrimitive.int })
        assertEquals(listOf(6, 8, 10), thursday.getValue("6 / 8 / 10 total x reverse lunge")["rounds"]!!.jsonArray.map { it.jsonPrimitive.int })
        // A timed row is done, with its seconds and no reps.
        val hold = thursday.getValue("30 sec of goblet squat hold")
        assertEquals(true, hold["done"]!!.jsonPrimitive.boolean)
        assertEquals(30, hold["prescribed_duration_sec"]!!.jsonPrimitive.int)
        assertEquals(JsonNull, hold["reps"])
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

    // A correction keeps the number the sheet showed when the row was first logged.
    @Test
    fun aCorrectionKeepsWhatTheSheetShowed() {
        logTwoClasses()
        val first = written.last { it.line == "8 x goblet squat" }
        assertEquals(45, first.shownLb)
        // More history arrives for the same exercise (another day's class logged late), so the
        // sheet rebuilt for Thursday would now show another number; then Thursday's row is corrected.
        save(thursday.minusDays(1), "3G") { s -> LogDraft().with(s.rows[0].id, LogDraft.picked(s.rows[0], 30)) }
        save(thursday, "Orange 60 / Tornado!") { s ->
            LogDraft.from(s, Logging.logged(s, written, thursday, "Orange 60 / Tornado!").byRow).let { d -> d.with(s.rows[0].id, d.rows.getValue(s.rows[0].id).copy(hurt = true)) }
        }
        val corrected = written.last()
        assertEquals(JournalEntry.Kind.CORRECTED, corrected.kind)
        assertEquals(listOf(45, 1), listOf(corrected.shownLb, corrected.shownCount))
    }

    // Two classes on a day whose names cut down alike keep a file each, and so do names in
    // other scripts; a plain name stays plain.
    @Test
    fun noClassOverwritesAnothersFile() {
        val names = setOf("Orange 60 / Tornado", "Orange 60 Tornado", "2G", "2g", "オレンジ 60", "橙色 60", "3G")
        val files = names.map { Export.sessionFile(monday, it, names) }
        assertEquals(names.size, files.map { it.lowercase() }.toSet().size)
        assertEquals("session-2026-03-02-3G.json", Export.sessionFile(monday, "3G", names))
        // Nothing in a name leaves the folder.
        assertTrue(files.plus(Export.sessionFile(monday, "../../etc/x")).all { Regex("""session-[A-Za-z0-9._-]+\.json""").matches(it) && ".." !in it })
        // And through the whole export: both classes are there.
        save(monday, "Orange 60 / Tornado") { s -> LogDraft().with(s.rows[0].id, LogDraft.picked(s.rows[0], 40)) }
        save(monday, "Orange 60 Tornado") { s -> LogDraft().with(s.rows[0].id, LogDraft.picked(s.rows[0], 35)) }
        assertEquals(3, Export.files(written).size)
    }

    // A row no load can have isn't graded; unreadable events are owned up to; a line stays a line.
    @Test
    fun damageIsKeptOutOfTheSessionsAndSaid() {
        logTwoClasses()
        val bad = written.first().copy(id = 99, row = "x", lb = 0)
        val odd = written.first().copy(id = 100, row = "y", line = "8 x \"goblet\" squat\u2028two\nlines")
        val files = Export.files(written + bad + odd, unreadable = 2)
        assertTrue(sets(files.getValue("session-2026-03-02-2G.json")).none { it["weight"]!!.jsonPrimitive.double == 0.0 })
        val lines = files.getValue(Export.LOG_FILE).trimEnd().split(Regex("""\n|\u2028|\u2029|\u0085"""))
        assertEquals(written.size + 3, lines.size)
        assertEquals(2, Json.parseToJsonElement(lines.first()).jsonObject["unreadable"]!!.jsonPrimitive.int)
        assertEquals("8 x \"goblet\" squat\u2028two\nlines", Json.parseToJsonElement(lines.last()).jsonObject["line"]!!.jsonPrimitive.content)
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
        // The log itself keeps a ladder a side each, as the coach reads it.
        assertEquals(listOf(3, 4, 5), lines.single { it["line"]!!.jsonPrimitive.content.contains("total") }["ladder"]!!.jsonArray.map { it.jsonPrimitive.int })
        val deadlift = lines.single { it["line"]!!.jsonPrimitive.content == "8 / 6 / 4 x deadlift" }
        assertEquals(true, deadlift["heaviest_only"]!!.jsonPrimitive.boolean)
        assertEquals("deadlift", deadlift["parts"]!!.jsonArray.single().jsonObject["movement"]!!.jsonPrimitive.content)
        // Nothing logged: only the empty log, so the folder shows the export ran.
        assertEquals(mapOf(Export.LOG_FILE to ""), Export.files(emptyList()))
    }
}
