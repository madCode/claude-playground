package spotter.coach

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.LocalDate

// An invented lifter's history in gym-coach's format, replayed: nobody's training data.
class ReplayTest {
    @get:Rule val tmp = TemporaryFolder()
    private val identity = Identity(Vocabulary.starting())
    private val today = LocalDate.of(2026, 4, 1)

    private fun row(date: String, cls: String?, line: String?, weight: String, reps: Int?, extra: String = "") =
        """{"date": "$date", "class": ${cls?.let { "\"$it\"" }}, "line": ${line?.let { "\"$it\"" }}, "variant": {"implement": "double_dumbbell"}, "weight": $weight, "reps": $reps, "prescribed_reps": $reps$extra}"""

    private val history = listOf(
        row("2026-03-02", "2G", "8 x chest press", "25", 8),
        row("2026-03-02", "2G", "8 x zorbing", "20", 8, """, "hurt": true, "done": true"""),
        row("2026-03-02", "3G", "8 x chest press", "30", 8),
        // Appended late, out of date order.
        row("2026-03-09", "2G", "8 x deadlift", "22.5", 8, """, "hurt": true"""),
        row("2026-03-05", "2G", "8 x chest press", "30", 8, """, "hard": true"""),
        row("2026-03-05", "2G", "8 x deadlift", "40", 8, """, "skipped": true"""),
        row("2026-03-09", "2G", "8 x chest press", "30", 8),
        row("2026-03-09", null, null, "15", 10, """, "exercise": "shoulder press""""),
        "not a row",
    ).joinToString("\n")

    private fun replay() = Replay.run(HistoryImport.read(history, identity, today), identity)

    // Each class is graded on the sheet made from the days before it, and nothing later.
    @Test
    fun eachClassSeesOnlyTheDaysBefore() {
        val sessions = replay()
        assertEquals(
            listOf("session-2026-03-02-2G.json", "session-2026-03-02-3G.json", "session-2026-03-05-2G.json", "session-2026-03-09-2G.json", "session-2026-03-09-class.json"),
            sessions.map { it.file },
        )
        fun press(i: Int) = sessions[i].session.sets.first { it.line == "8 x chest press" }
        // Day one: no history, so no number, in either class; the 3G class doesn't see the 2G one.
        assertEquals(listOf(null, 25.0), listOf(press(0).suggestedWeight, press(0).weight))
        assertEquals(listOf(null, 30.0), listOf(press(1).suggestedWeight, press(1).weight))
        // Thursday: Monday's two classes are history. The later row in the file is the latest, so
        // the 30 is the number, clean once; the lift was 30, felt hard.
        assertEquals(listOf(30.0, 30.0, true), listOf(press(2).suggestedWeight, press(2).weight, press(2).hard))
        // The Monday after: felt hard at 30 holds it there.
        assertEquals(listOf(30.0, 30.0), listOf(press(3).suggestedWeight, press(3).weight))
    }

    // Shared weights can be switched off, to grade the coach both ways: a reworded repeat then
    // has no number, and the report says which run it was.
    @Test
    fun sharedWeightsCanBeSwitchedOffInAReplay() {
        val curls = listOf(row("2026-03-02", "2G", "8 x bicep curl", "15", 8), row("2026-03-05", "2G", "8 x alternating bicep curl", "15", 8)).joinToString("\n")
        val read = HistoryImport.read(curls, identity, today)
        fun second(rules: Rules) = Replay.run(read, identity, rules)[1].session.sets.single().suggestedWeight
        assertEquals(listOf(15.0, null), listOf(second(Rules()), second(Rules(shared = false))))
        val file = tmp.newFile("curls.jsonl").apply { writeText(curls) }
        val report = Replay.write(file, File(tmp.root, "off"), today, Rules(shared = false))
        assertEquals("2 classes, 2 rows, written to ${File(tmp.root, "off")} (shared weights off)", report.lines().first())
        assertTrue("0 of them had a number" in report)
        assertTrue("1 of them had a number" in Replay.write(file, File(tmp.root, "on"), today))
    }

    // The owner's counts file can be given to a replay; without it the run is as it was, and
    // with it the report says how many rows it filled.
    @Test
    fun aReplayCanTakeTheCountsFile() {
        // Rows that don't say how many dumbbells, filed under the name the counts file rules on.
        fun lift(date: String, line: String, weight: Int) =
            """{"date": "$date", "class": "2G", "exercise": "squat", "line": "$line", "variant": {"implement": null}, "weight": $weight, "reps": 8, "prescribed_reps": 8}"""
        val rows = listOf(lift("2026-03-02", "8 x squat", 40), lift("2026-03-05", "8 x single arm squat", 20)).joinToString("\n")
        val file = tmp.newFile("lifts.jsonl").apply { writeText(rows) }
        val counts = tmp.newFile("counts.yaml").apply { writeText("schema: dumbbell-counts/1\nmovements:\n  squat: 2\n") }
        val plain = Replay.write(file, File(tmp.root, "plain"), today)
        assertTrue("0 of them had a number" in plain && "dumbbell counts" !in plain)
        val ruled = Replay.write(file, File(tmp.root, "ruled"), today, countsFile = counts)
        assertTrue(ruled, "1 of them had a number" in ruled)
        assertTrue("dumbbell counts: 1 movements ruled on; 1 rows took their count from them" in ruled)
    }

    // Rows the coach couldn't read are in the session with what was lifted and no suggestion: a
    // blank row counts against the coach. A skipped row has no weight; a weight is as written.
    @Test
    fun everyRowIsInItsSession() {
        val sessions = replay()
        val zorbing = sessions[0].session.sets[1]
        // No reps on a row the coach didn't read: the file's number may count both sides.
        assertEquals(listOf("8 x zorbing", null, 20.0, null, null), listOf(zorbing.line, zorbing.suggestedWeight, zorbing.weight, zorbing.reps, zorbing.key))
        assertEquals(listOf(true, true), listOf(zorbing.hurt, zorbing.done))
        assertEquals(listOf(8, false), sessions[0].session.sets[0].let { listOf(it.reps, it.done) })
        val skipped = sessions[2].session.sets[1]
        assertEquals(listOf("8 x deadlift", null, null, true), listOf(skipped.line, skipped.weight, skipped.reps, skipped.skipped))
        val decimal = sessions[3].session.sets[0]
        assertEquals(listOf(22.5, true), listOf(decimal.weight, decimal.hurt))
        // An old row with no line and no class goes by the name it was filed under.
        val old = sessions[4].session.sets.single()
        assertEquals(listOf("shoulder press", 15.0, "class"), listOf(old.line, old.weight, sessions[4].session.className))
        assertEquals(listOf(0, 1), sessions[0].session.sets.map { it.ref })
    }

    // The command: a file per class in the scorecard's format, and a report in counts only.
    @Test
    fun theFilesAreWrittenAndTheReportHasOnlyCounts() {
        val file = tmp.newFile("history.jsonl").apply { writeText(history) }
        val out = File(tmp.root, "sessions")
        val report = Replay.write(file, out, today)
        assertEquals(5, out.list()!!.size)
        val session = Json.parseToJsonElement(File(out, "session-2026-03-05-2G.json").readText()).jsonObject
        assertEquals(listOf("gym-session/1", "2026-03-05", "2G"), listOf("schema", "date", "class").map { session.getValue(it).jsonPrimitive.content })
        assertEquals(30.0, session.getValue("sets").jsonArray[0].jsonObject.getValue("suggested_weight").jsonPrimitive.content.toDouble(), 0.0)
        assertEquals(
            listOf(
                "5 classes, 8 rows, written to $out",
                "7 rows with a weight lifted; 2 of them had a number on the sheet",
                "rows read into history: 6; left out: unreadable 1, skipped 1, not named 1",
            ),
            report.trim().lines(),
        )
        assertTrue(listOf("chest press", "zorbing", "22.5").none { it in report })
    }

    // Sessions are private: the tool won't write them where git would offer to commit them.
    @Test
    fun itRefusesAFolderInsideAGitCheckout() {
        val file = tmp.newFile("history.jsonl").apply { writeText(history) }
        val repo = tmp.newFolder("repo").also { File(it, ".git").mkdir() }
        val out = File(repo, "replay/out")
        try {
            Replay.write(file, out, today)
            fail("wrote into a checkout")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("private"))
        }
        assertTrue(!out.exists())
        // Nor through a link into one; and a path that climbs out of the checkout is fine.
        val link = File(tmp.root, "link")
        java.nio.file.Files.createSymbolicLink(link.toPath(), repo.toPath())
        try {
            Replay.write(file, File(link, "out"), today)
            fail("wrote into a checkout through a link")
        } catch (e: IllegalArgumentException) {
            assertTrue(!File(repo, "out").exists())
        }
        Replay.write(file, File(repo, "../outside"), today)
        assertEquals(5, File(tmp.root, "outside").list()!!.size)
    }

    // A folder with files in it may hold another run's sessions, which would be graded with these.
    @Test
    fun itRefusesAFolderThatIsntEmpty() {
        val file = tmp.newFile("history.jsonl").apply { writeText(history) }
        val out = tmp.newFolder("used").also { File(it, "session-2026-01-01-2G.json").writeText("{}") }
        try {
            Replay.write(file, out, today)
            fail("wrote beside an earlier run")
        } catch (e: IllegalArgumentException) {
            assertEquals(1, out.list()!!.size)
        }
        try {
            Replay.write(file, file, today)
            fail("took a file for a folder")
        } catch (e: IllegalArgumentException) {
            assertEquals(history, file.readText())
        }
    }

    // Carry's invented lifter, 163 classes of public board lines: every row of every class comes
    // out, and a class never sees its own day.
    @Test
    fun theInventedLiftersHistoryReplays() {
        val rows = File("../../carry/brain-fixtures/sheets.jsonl").readLines().filter { it.isNotBlank() }
            .flatMap { Json.parseToJsonElement(it).jsonObject.getValue("logged").jsonArray }
        val result = HistoryImport.read(rows.joinToString("\n") { it.toString() }, identity, LocalDate.of(2030, 1, 1))
        val sessions = Replay.run(result, identity)
        assertEquals(rows.size, sessions.sumOf { it.session.sets.size })
        assertEquals(sessions.size, sessions.map { it.file }.toSet().size)
        val first = sessions.first().session
        assertNull(first.sets.firstNotNullOfOrNull { it.suggestedWeight })
        // No class's sheet uses its own day or a later one: cut the history off half way, and the
        // sessions before the cut come out the same.
        val cut = sessions[sessions.size / 2].session.date
        val early = HistoryImport.read(rows.filter { it.jsonObject.getValue("date").jsonPrimitive.content < cut }.joinToString("\n") { it.toString() }, identity, LocalDate.of(2030, 1, 1))
        assertEquals(sessions.filter { it.session.date < cut }, Replay.run(early, identity))
        // Some rows get a number once there is history to make it from.
        assertTrue(sessions.sumOf { s -> s.session.sets.count { it.suggestedWeight != null && it.weight != null } } > 100)
    }
}
