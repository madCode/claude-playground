package spotter.coach

import java.io.File
import java.time.LocalDate
import kotlin.system.exitProcess

/**
 * Replays a history through the coach, for grading it (DESIGN.md, "The scorecard decides"): for
 * each class in the file, the sheet the coach would have made from the days before it, beside
 * what was lifted, as a session file the scorecard reads.
 *
 * The class's board is the history's own lines for it, a line a row. Every row of the file is in
 * its session, read by the coach or not: a row it couldn't read has no suggestion, and that
 * counts against it. The history is someone's own, so nothing of it is printed but counts.
 */
object Replay {
    /** One class replayed: its file name and the session. */
    data class Replayed(val file: String, val session: ScorecardSession)

    fun run(history: HistoryImport.Result, identity: Identity): List<Replayed> {
        val classes = history.all.groupBy { it.date to (it.className ?: NO_CLASS) }
        return classes.entries.sortedBy { it.key.first }.map { (cls, rows) ->
            val (date, name) = cls
            // Only the days before: the coach never sees the class it is being graded on, nor
            // another class of the same day, whose order in the day the file doesn't say.
            val before = history.rows.map { it.logged }.filter { it.date < date }
            val sameDay = classes.keys.filter { it.first == date }.map { it.second }.toSet()
            val session = ScorecardSession(
                sheetId = "$date:$name",
                date = date.toString(),
                className = name,
                sets = rows.mapIndexed { i, row -> set(i, row, before, identity) },
            )
            Replayed(Export.sessionFile(date, name, sameDay), session)
        }
    }

    private fun set(ref: Int, row: HistoryImport.Row, before: List<Logged>, identity: Identity): ScorecardRow {
        // A sheet of the one line: the number depends on the history, not on the line's neighbours.
        val sheetRow = row.line?.let { Sheet.make(listOf(it), before, identity).rows.singleOrNull() }
        val made = if (sheetRow != null) {
            ScorecardRow.from(ref, sheetRow, row.logged?.takeIf { it.key == (sheetRow.reading as? Reading.Known)?.key }, row.skipped)
        } else {
            ScorecardRow(ref = ref, line = row.line.orEmpty(), suggested = "not sure what this is", skipped = row.skipped)
        }
        // What was lifted is the file's word, whatever the coach made of the row: the weight as
        // written (22.5 stays 22.5), so the grade is against the lift and not the coach's reading.
        return made.copy(
            weight = row.weight.takeUnless { row.skipped },
            reps = made.reps ?: row.reps.takeIf { row.weight != null && !row.skipped },
            hurt = row.hurt, easy = row.easy, hard = row.hard, easier = row.easier,
        )
    }

    /** Writes each class's session into [out] and says what it did in counts. */
    fun write(historyFile: File, out: File, today: LocalDate = LocalDate.now()): String {
        // Sessions are someone's training data: never into a folder git would offer to commit.
        generateSequence(out.absoluteFile) { it.parentFile }.firstOrNull { File(it, ".git").exists() }?.let {
            throw IllegalArgumentException("$out is inside a git checkout ($it). Choose a folder outside it: the sessions are private.")
        }
        val identity = Identity(Vocabulary.starting())
        val history = HistoryImport.read(historyFile.readText(), identity, today)
        val replayed = run(history, identity)
        out.mkdirs()
        replayed.forEach { File(out, it.file).writeText(it.session.toJson() + "\n") }
        val sets = replayed.sumOf { it.session.sets.size }
        val numbered = replayed.sumOf { r -> r.session.sets.count { it.suggestedWeight != null && it.weight != null } }
        val weighted = replayed.sumOf { r -> r.session.sets.count { it.weight != null } }
        return buildString {
            appendLine("${replayed.size} classes, $sets rows, written to $out")
            appendLine("$weighted rows with a weight lifted; $numbered of them had a number on the sheet")
            appendLine("rows read into history: ${history.rows.size}; left out: " + history.left.entries.joinToString { "${it.key.name.lowercase().replace('_', ' ')} ${it.value}" }.ifEmpty { "none" })
        }
    }

    const val NO_CLASS = "class"
}

/** `replay <history.jsonl> <out-folder>`: see docs/REPLAY.md. */
fun main(args: Array<String>) {
    if (args.size != 2) {
        System.err.println("usage: replay <history.jsonl> <out-folder>")
        exitProcess(2)
    }
    try {
        print(Replay.write(File(args[0]), File(args[1])))
    } catch (e: Exception) {
        // The message only: a stack trace could carry a line of the file.
        System.err.println("replay failed: ${e.message}")
        exitProcess(1)
    }
}
