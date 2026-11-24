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

    fun run(history: HistoryImport.Result, identity: Identity, rules: Rules = Rules()): List<Replayed> {
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
                sets = rows.mapIndexed { i, row -> set(i, row, before, identity, rules) },
            )
            Replayed(Export.sessionFile(date, name, sameDay), session)
        }
    }

    private fun set(ref: Int, row: HistoryImport.Row, before: List<Logged>, identity: Identity, rules: Rules): ScorecardRow {
        // A sheet of the one line: the number depends on the history, not on the line's neighbours.
        val sheetRow = row.line?.let { Sheet.make(listOf(it), before, identity, rules = rules).rows.singleOrNull() }
        val made = if (sheetRow != null) {
            ScorecardRow.from(ref, sheetRow, row.logged?.takeIf { it.key == (sheetRow.reading as? Reading.Known)?.key }, row.skipped)
        } else {
            ScorecardRow(ref = ref, line = row.line.orEmpty(), suggested = "not sure what this is", skipped = row.skipped)
        }
        // What was lifted is the file's word, whatever the coach made of the row: the weight as
        // written (22.5 stays 22.5), so the grade is against the lift and not the coach's reading.
        // Reps only where the coach read the row: the file's own number may count both sides,
        // and beside the line's reps a side a failed set could look like more than was asked.
        return made.copy(
            weight = row.weight.takeUnless { row.skipped },
            done = row.done, hurt = row.hurt, easy = row.easy, hard = row.hard, easier = row.easier,
        )
    }

    /** Writes each class's session into [out] and says what it did in counts. */
    fun write(historyFile: File, out: File, today: LocalDate = LocalDate.now(), rules: Rules = Rules(), countsFile: File? = null): String {
        // Sessions are someone's training data: never into a folder git would offer to commit.
        // The folder as it really is: a link into a checkout is in it, "../.." out of one is not.
        val folder = out.canonicalFile
        generateSequence(folder) { it.parentFile }.firstOrNull { File(it, ".git").exists() }?.let {
            throw IllegalArgumentException("$out is inside a git checkout ($it). Choose a folder outside it: the sessions are private.")
        }
        // A session left from another run would be graded as this one's; a folder that can't be
        // listed may hold one.
        require(!folder.exists() || (folder.isDirectory && folder.list()?.isEmpty() == true)) { "$out isn't an empty folder. Choose a new or empty one." }
        val identity = Identity(Vocabulary.starting())
        // The owner's rulings on how many dumbbells, when given: without them the run is as it was.
        val counts = countsFile?.let {
            // Before reading it: a huge file would fill memory ahead of the parser's own limit.
            require(it.length() <= 4L * DumbbellCounts.MAX_CHARS) { "That file is too big to be a list of dumbbell counts." }
            DumbbellCounts.parse(it.readText())
        } ?: DumbbellCounts.NONE
        val history = HistoryImport.read(historyFile.readText(), identity, today, counts = counts)
        val replayed = run(history, identity, rules)
        try {
            folder.mkdirs()
            replayed.forEach { File(folder, it.file).writeText(it.session.toJson() + "\n") }
        } catch (e: java.io.IOException) {
            // Without the file's name, which holds a date and a class.
            throw IllegalStateException("couldn't write the sessions to $out")
        }
        val sets = replayed.sumOf { it.session.sets.size }
        val numbered = replayed.sumOf { r -> r.session.sets.count { it.suggestedWeight != null && it.weight != null } }
        val weighted = replayed.sumOf { r -> r.session.sets.count { it.weight != null } }
        return buildString {
            appendLine("${replayed.size} classes, $sets rows, written to $out" + if (!rules.shared) " (shared weights off)" else "")
            if (countsFile != null) appendLine("dumbbell counts: ${counts.size} movements ruled on; ${history.rows.count { it.countRuled }} rows took their count from them")
            appendLine("$weighted rows with a weight lifted; $numbered of them had a number on the sheet")
            appendLine("rows read into history: ${history.rows.size}; left out: " + history.left.entries.joinToString { "${it.key.name.lowercase().replace('_', ' ')} ${it.value}" }.ifEmpty { "none" })
        }
    }

    const val NO_CLASS = "class"
}

/** `replay <history.jsonl> <out-folder> [no-shared] [counts=<dumbbell_counts.yaml>]`: see docs/REPLAY.md. */
fun main(args: Array<String>) {
    val flags = args.drop(2)
    val counts = flags.filter { it.startsWith("counts=") }
    if (args.size < 2 || counts.size > 1 || counts.any { it == "counts=" } || args.take(2).any { it == "no-shared" || it.startsWith("counts=") } || (flags - counts.toSet()).any { it != "no-shared" } || flags.count { it == "no-shared" } > 1) {
        System.err.println("usage: replay <history.jsonl> <out-folder> [no-shared] [counts=<dumbbell_counts.yaml>]")
        exitProcess(2)
    }
    try {
        val countsFile = counts.singleOrNull()?.removePrefix("counts=")?.let(::File)
        print(Replay.write(File(args[0]), File(args[1]), rules = Rules(shared = "no-shared" !in flags), countsFile = countsFile))
    } catch (e: Exception) {
        // The message only: a stack trace could carry a line of the file.
        System.err.println("replay failed: ${e.message}")
        exitProcess(1)
    }
}
