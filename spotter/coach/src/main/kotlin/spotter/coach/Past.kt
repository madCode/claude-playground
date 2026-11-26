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
    val logged: Logged,
) {
    /** The reps done, as written: "8 reps", "8, 8, 6 reps", "10 / 8 / 6", "timed", or "" when nobody said. */
    val reps: String
        get() = when {
            logged.ladder != null -> logged.ladder.joinToString(" / ")
            logged.reps.isEmpty() -> if (logged.target == null) "timed" else ""
            logged.reps.distinct().size == 1 -> "${logged.reps.first()} reps"
            else -> logged.reps.joinToString(", ") + " reps"
        }

    /** What was said about it, in the Log screen's own words. */
    val said: List<String>
        get() = listOfNotNull(
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
    fun classes(own: List<Pair<JournalEntry, Logged>>, imported: List<Imported>, ownDays: Set<LocalDate> = own.map { it.first.date }.toSet()): List<PastClass> {
        val mine = own.groupBy { it.first.date to it.first.className }
            .map { (at, rows) -> PastClass(at.first, at.second, imported = false, rows = rows.map { row(it.second) }) }
        val theirs = imported.filter { it.logged.date !in ownDays }.groupBy { it.logged.date to (it.className ?: NO_CLASS) }
            .map { (at, rows) -> PastClass(at.first, at.second, imported = true, rows = rows.map { row(it.logged) }) }
        return (mine + theirs).sortedWith(compareByDescending<PastClass> { it.date }.thenBy { it.className })
    }

    /**
     * Each time the lift with [key] was done, the newest first: the sessions the sheet reads its
     * number from, its other wordings that take the same dumbbells included
     * ([Progression.own]). Empty when no class has it.
     */
    fun lift(key: String, classes: List<PastClass>): List<PastSession> {
        val all = sessions(classes)
        val exercise = all.firstOrNull { it.row.logged.key == key }?.row?.logged?.exercise ?: return emptyList()
        return of(exercise, all)
    }

    /**
     * The key to open [lift] with for a row on the sheet: the row's own when it has been done
     * in those words, else its newest other wording's, which is what its number is read
     * from. Null when there is nothing to show.
     */
    fun keyFor(exercise: Reading.Known, classes: List<PastClass>): String? {
        val all = sessions(classes)
        return (all.firstOrNull { it.row.logged.key == exercise.key } ?: of(exercise, all).firstOrNull())?.row?.logged?.key
    }

    /** The name [lift]'s sessions go under: the wording that was opened. */
    fun name(key: String, classes: List<PastClass>): String? = sessions(classes).firstOrNull { it.row.logged.key == key }?.row?.name

    private fun sessions(classes: List<PastClass>) = classes.flatMap { c -> c.rows.map { PastSession(c.date, it, c.imported) } }

    private fun of(exercise: Reading.Known, all: List<PastSession>): List<PastSession> {
        val counted = Progression.own(exercise, all.map { it.row.logged }).toSet()
        // Also a session nothing can be judged from (no reps said): it was done all the same.
        return all.filter { it.row.logged in counted || it.row.logged.key == exercise.key }.sortedByDescending { it.date }
    }

    private fun row(logged: Logged) = PastRow(Estimated.name(logged.exercise), logged)

    const val NO_CLASS = "class"
}
