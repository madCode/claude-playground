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
    data class Apart(
        /** One dumbbell-or-other's weight as the file has it, in words for the screen ("55 lb"); null when none was held or said. */
        val held: String?,
        val said: List<String>,
        val why: String,
        /** The reps, where they are known a side as the other rows' are: a row ticked on the phone. */
        val reps: String = "",
    )

    /** The reps done, as written: "8 reps", "8, 8, 6 reps", "10 / 8 / 6", "timed", or "" when nobody said. */
    val reps: String
        get() = when {
            // The file's own count may be of both sides, where the rows beside it are per side: left unsaid.
            logged == null -> apart?.reps.orEmpty()
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
        /** Rows ticked on the phone as done with nothing held ([Journal.bare]). */
        bare: List<Pair<JournalEntry, Reading.Known>> = emptyList(),
    ): List<PastClass> {
        // In the order first logged, the weighted rows and the others together.
        val mine = (own.map { it.first to row(it.second) } + bare.map { it.first to ticked(it.first, it.second) })
            .sortedBy { it.first.id }.groupBy { it.first.date to it.first.className }
            .map { (at, rows) -> PastClass(at.first, at.second, imported = false, rows = rows.map { it.second }) }
        fun named(name: String?) = name?.let(::plain)?.ifEmpty { null } ?: NO_CLASS
        val read = imported.filter { it.logged.date !in ownDays }.map { Triple(it.logged.date to named(it.className), it.at, row(it.logged)) }
        val apart = others.filter { it.date !in ownDays }.map { Triple(it.date to named(it.className), it.at, apart(it, identity)) }
        // As the file has them, which is the order they were done in. A class of nothing but
        // skipped rows wasn't taken.
        val theirs = (read + apart).groupBy { it.first }
            .filter { (_, rows) -> rows.any { it.third.apart?.why != SKIPPED } }
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

    private fun ticked(e: JournalEntry, exercise: Reading.Known): PastRow {
        val reps = when {
            e.ladder != null -> e.ladder.joinToString(" / ")
            e.reps.isEmpty() -> ""
            e.reps.distinct().size == 1 -> "${e.reps.first()} reps"
            else -> e.reps.joinToString(", ") + " reps"
        }
        val said = listOfNotNull(
            "felt easy".takeIf { e.easy }, "felt hard".takeIf { e.hard }, "twinge".takeIf { e.hurt },
            "had to stop".takeIf { e.stopped }, "easier version".takeIf { e.easier },
        )
        return PastRow(Estimated.name(exercise), null, PastRow.Apart(null, said, "done", reps))
    }

    private fun apart(row: HistoryImport.Row, identity: Identity?): PastRow {
        val words = BoardLine.read(row.line.orEmpty().take(MAX_LINE)).words
        val name = (identity?.read(words) as? Reading.Known)?.let(Estimated::name) ?: plain(words).ifEmpty { "an exercise" }
        val why = when (row.left) {
            HistoryImport.Left.SKIPPED -> SKIPPED
            HistoryImport.Left.NO_WEIGHT -> if (row.done) "done" else ""
            HistoryImport.Left.OFF_RACK -> "not a dumbbell"
            HistoryImport.Left.EASIER -> "easier version"
            HistoryImport.Left.BROKE_DOWN -> "form broke down"
            HistoryImport.Left.NOT_NAMED -> "not read by Spotter"
            HistoryImport.Left.REPS_UNCLEAR -> "reps unclear"
            else -> ""
        }
        val said = listOfNotNull("felt easy".takeIf { row.easy }, "felt hard".takeIf { row.hard }, "twinge".takeIf { row.hurt })
        // A skipped row held nothing, whatever the file carries; nor is a weight no gym has one.
        val held = row.weight?.takeIf { !row.skipped && it >= 1 && it <= MAX_LB }?.let { w ->
            val tenths = Math.round(w * 10)
            (if (tenths % 10 == 0L) "${tenths / 10}" else "${tenths / 10}.${tenths % 10}") + " lb"
        }
        return PastRow(name, null, PastRow.Apart(held, said, why))
    }

    // The file is someone's typing, or anyone's: one line of plain text, of a sane length,
    // with nothing that turns the screen's text around and no half of a character at the cut.
    private fun plain(text: String): String {
        val clean = text.map { if (it.isISOControl() || Character.getType(it) == Character.FORMAT.toInt()) ' ' else it }.joinToString("")
            .trim().replace(Regex(" +"), " ")
        val cut = clean.take(MAX_NAME)
        return if (cut.lastOrNull()?.isHighSurrogate() == true) cut.dropLast(1) else cut
    }

    private const val SKIPPED = "skipped"
    private const val MAX_LINE = 400
    private const val MAX_NAME = 80
    private const val MAX_LB = 500.0

    const val NO_CLASS = "class"
}
