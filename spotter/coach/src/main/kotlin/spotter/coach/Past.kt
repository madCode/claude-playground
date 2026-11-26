package spotter.coach

import java.time.LocalDate

/**
 * A class already done, for looking back (DESIGN.md, "Screens": History): what was held for each
 * exercise, the reps and what was said about it. Read from the phone's own log and the imported
 * history, as the coach reads them, so what is shown is what the sheet's numbers rest on.
 */
data class PastClass(
    val date: LocalDate,
    val className: String,
    /** From the imported history, not logged here. */
    val imported: Boolean,
    val rows: List<PastRow>,
) {
    /** Which class this is, across reloads: its day and name. */
    val id: String get() = "$date $className"
}

/** One exercise of a past class. */
data class PastRow(
    /** The exercise as a person says it. */
    val name: String,
    /** What the coach reads of it; null for a row that is no part of a lift's history ([apart]). */
    val logged: Logged?,
    /** A row the coach leaves out, as the imported file has it: bodyweight, skipped, something else held. */
    val apart: Apart? = null,
) {
    /** What an imported row outside the coach's history says. */
    data class Apart(val weight: Double?, val reps: Int?, val said: List<String>, val why: String)

    /** The reps done, as written: "8 reps", "8, 8, 6 reps", "10 / 8 / 6", "timed", or "" when nobody said. */
    val reps: String
        get() = when {
            logged == null -> apart?.reps?.let { "$it reps" }.orEmpty()
            logged.ladder != null -> logged.ladder.joinToString(" / ")
            logged.reps.isEmpty() -> if (logged.target == null) "timed" else ""
            logged.reps.distinct().size == 1 -> "${logged.reps.first()} reps"
            else -> logged.reps.joinToString(", ") + " reps"
        }

    /** What was said about it, in the Log screen's own words. */
    val said: List<String>
        get() = if (logged == null) apart?.said.orEmpty() + listOfNotNull(apart?.why?.takeIf { it.isNotEmpty() }) else listOfNotNull(
            "felt easy".takeIf { logged.easy },
            "felt hard".takeIf { logged.hard },
            "twinge".takeIf { logged.hurt },
            "had to stop".takeIf { logged.stopped },
            "form broke down".takeIf { logged.formBroke },
        )

    /** The reps and what was said, on one line: "7 reps · twinge". */
    val detail: String get() = (listOf(reps).filter { it.isNotEmpty() } + said).joinToString(" · ")
}

/** One time a lift was done, for its own history. */
data class PastSession(val date: LocalDate, val row: PastRow, val imported: Boolean)

object Past {
    /**
     * Every class with something logged, the newest first. [own] is the phone's log as read
     * ([Journal.read]); [imported] the history file's rows. A day logged here is shown as logged
     * here: the file's rows for it would be the same class a second time. [ownDays] are the days
     * the log has anything for, a row that can't be read included, since those are the days
     * the sheet leaves the file out for.
     */
    fun classes(
        own: List<Pair<JournalEntry, Logged>>,
        imported: List<Imported>,
        ownDays: Set<LocalDate> = own.map { it.first.date }.toSet(),
        /** The file's rows the coach leaves out ([HistoryImport.Result.others]): shown in their class all the same. */
        others: List<HistoryImport.Row> = emptyList(),
        /** Names a left-out row as its lift is said, where its line reads; else it keeps its own words. */
        identity: Identity? = null,
    ): List<PastClass> {
        val mine = own.groupBy { it.first.date to it.first.className }
            .map { (at, rows) -> PastClass(at.first, at.second, imported = false, rows = rows.map { row(it.second) }) }
        val read = imported.filter { it.logged.date !in ownDays }.map { Triple(it.logged.date to (it.className ?: NO_CLASS), it.at, row(it.logged)) }
        val apart = others.filter { it.date !in ownDays }.map { Triple(it.date to (it.className ?: NO_CLASS), it.at, apart(it, identity)) }
        // As the file has them, which is the order they were done in.
        val theirs = (read + apart).groupBy { it.first }
            .map { (at, rows) -> PastClass(at.first, at.second, imported = true, rows = rows.sortedBy { it.second }.map { it.third }) }
        return (mine + theirs).sortedWith(compareByDescending<PastClass> { it.date }.thenBy { it.className })
    }

    /**
     * Each time the lift with [key] was done, the newest first: the sessions the sheet reads its
     * number from, its other wordings that take the same dumbbells included
     * ([Progression.own]). [sheet] is today's rows: one of those may never have been done in
     * its own words, and which other wordings count is judged from its side. Empty when
     * nothing was done.
     */
    fun lift(key: String, classes: List<PastClass>, sheet: List<Reading.Known> = emptyList()): List<PastSession> {
        val all = sessions(classes)
        val exercise = exercise(key, all, sheet) ?: return emptyList()
        val counted = Progression.own(exercise, all.mapNotNull { it.row.logged }).toSet()
        // Also a session nothing can be judged from (no reps said): it was done all the same.
        return all.filter { it.row.logged != null && (it.row.logged in counted || it.row.logged.key == key) }.sortedByDescending { it.date }
    }

    /** The name [lift]'s sessions go under: the wording that was opened. */
    fun name(key: String, classes: List<PastClass>, sheet: List<Reading.Known> = emptyList()): String? =
        exercise(key, sessions(classes), sheet)?.let(Estimated::name)

    private fun exercise(key: String, all: List<PastSession>, sheet: List<Reading.Known>) =
        sheet.firstOrNull { it.key == key } ?: all.firstOrNull { it.row.logged?.key == key }?.row?.logged?.exercise

    private fun sessions(classes: List<PastClass>) = classes.flatMap { c -> c.rows.map { PastSession(c.date, it, c.imported) } }

    private fun row(logged: Logged) = PastRow(Estimated.name(logged.exercise), logged)

    private fun apart(row: HistoryImport.Row, identity: Identity?): PastRow {
        val line = row.line.orEmpty()
        val name = (identity?.read(BoardLine.read(line).words) as? Reading.Known)?.let(Estimated::name) ?: line.ifBlank { "an exercise" }
        val why = when (row.left) {
            HistoryImport.Left.SKIPPED -> "skipped"
            HistoryImport.Left.NO_WEIGHT -> if (row.done) "done" else ""
            HistoryImport.Left.OFF_RACK -> "not a dumbbell"
            HistoryImport.Left.EASIER -> "easier version"
            HistoryImport.Left.BROKE_DOWN -> "form broke down"
            HistoryImport.Left.NOT_NAMED -> "not read by Spotter"
            HistoryImport.Left.REPS_UNCLEAR -> "reps unclear"
            else -> ""
        }
        val said = listOfNotNull("felt easy".takeIf { row.easy }, "felt hard".takeIf { row.hard }, "twinge".takeIf { row.hurt })
        return PastRow(name, null, PastRow.Apart(row.weight, row.reps.takeIf { !row.skipped }, said, why))
    }

    const val NO_CLASS = "class"
}
