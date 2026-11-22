package spotter.coach

import kotlinx.serialization.Serializable
import java.time.LocalDate

/**
 * What one row says was done: the heaviest dumbbell held, how many, and reps a side (null for a
 * timed row, and for a ladder, whose rounds stay as listed). The rest is one tap each.
 */
@Serializable
data class RowLog(
    val lb: Int,
    val count: Int?,
    val reps: Int?,
    val easy: Boolean = false,
    val hard: Boolean = false,
    /** A twinge: the weight holds next time (trap 3). */
    val hurt: Boolean = false,
    /** Had to stop: down a step next time. */
    val stopped: Boolean = false,
    val easier: Boolean = false,
)

/**
 * A class being logged: what each sheet row says, by [SheetRow.id]. A row left out stays unlogged,
 * never "skipped" (DESIGN.md, Screens). Kept as one value so the screen can save and restore it
 * whole, across a rotation or the app being killed.
 */
@Serializable
data class LogDraft(
    val rows: Map<String, RowLog> = emptyMap(),
    /** The rows logged when the screen opened: only these can be cleared by leaving them out. */
    val seen: Set<String> = emptySet(),
) {
    fun with(id: String, log: RowLog?) = copy(rows = if (log == null) rows - id else rows + (id to log))

    companion object {
        /** Whether a row can be logged here: a weighted row Spotter read. */
        fun loggable(row: SheetRow): Boolean = row.reading is Reading.Known && row.suggestion != null

        /**
         * "✓ as listed": the sheet's number and the board's reps. Null when the sheet has no number
         * to have done as listed ("new: go light"), so the row has to say what was held.
         *
         * For a range ("8-10") as listed is its low end: done within the range, which holds the
         * weight rather than raising it on reps nobody said were reached.
         */
        fun asListed(row: SheetRow): RowLog? {
            val load = (row.suggestion as? Suggestion.Weight)?.load ?: return null
            return RowLog(load.lb, load.count, reps(row))
        }

        /**
         * Where "changed" starts: as listed when there is a number, else the lightest pair a
         * new exercise would start from, for the person to step up from.
         */
        fun startChanged(row: SheetRow): RowLog {
            val count = (row.reading as? Reading.Known)?.let(Sheet::rowCount) ?: 2
            // An imported number with no count: logging is the chance to say how many.
            return asListed(row)?.let { if (it.count == null) it.copy(count = count) else it } ?: RowLog(START_LB, count, reps(row))
        }

        private fun reps(row: SheetRow): Int? = if (row.line.scheme is Scheme.Ladder) null else row.target?.first

        private const val START_LB = 10

        /** The draft for a class already logged, or partly: what is in the log for its rows. */
        fun from(sheet: Sheet, logged: Map<String, JournalEntry>): LogDraft {
            val rows = sheet.rows.mapNotNull { row ->
                val e = logged[row.id] ?: return@mapNotNull null
                row.id to RowLog(e.lb, e.count, e.reps.singleOrNull()?.takeIf { e.ladder == null }, e.easy, e.hard, e.hurt, e.stopped, e.easier)
            }.toMap()
            return LogDraft(rows, rows.keys)
        }
    }
}

object Logging {
    /** The live entries for one class's rows, by row id: what the Log screen starts from. */
    fun logged(entries: List<JournalEntry>, date: LocalDate, className: String): Map<String, JournalEntry> =
        Journal.live(entries).filter { it.date == date && it.className == className }.associateBy { it.row }

    /**
     * The events to write for [draft] against what is [logged] already: a new row is logged, a
     * changed one corrected, one cleared removed, and one unchanged left alone, so saving twice
     * writes nothing the second time. Rows no longer on the sheet are left as they are.
     *
     * Only a row the screen opened with ([LogDraft.seen]) and then cleared is removed: a row logged
     * since the screen opened, which it never showed, isn't the person's to have cleared.
     */
    fun events(
        draft: LogDraft, sheet: Sheet, logged: Map<String, JournalEntry>, date: LocalDate, className: String, at: Long,
    ): List<JournalEntry> = sheet.rows.filter(LogDraft::loggable).mapNotNull { row ->
        val now = draft.rows[row.id]
        val before = logged[row.id]
        when {
            now == null && before == null -> null
            now == null -> if (row.id !in draft.seen) null else before!!.copy(id = 0, at = at, kind = JournalEntry.Kind.REMOVED, target = before.id)
            before != null && LogDraft.from(sheet, logged).rows[row.id] == now -> null
            else -> {
                val e = entry(row, now, date, className, at)
                if (before == null) e else e.copy(kind = JournalEntry.Kind.CORRECTED, target = before.id)
            }
        }
    }

    private fun entry(row: SheetRow, log: RowLog, date: LocalDate, className: String, at: Long): JournalEntry {
        val ladder = (row.line.scheme as? Scheme.Ladder)?.perSide()
        return Journal.logged(
            at, date, className, row.id, row.shown, row.reading as Reading.Known, Load(log.lb, log.count),
            target = row.target, ladder = ladder, reps = ladder ?: listOfNotNull(log.reps),
            hurt = log.hurt, stopped = log.stopped, easy = log.easy, hard = log.hard, easier = log.easier,
            // The screen takes one weight for the row; a ladder's is its heaviest round's.
            heaviestOnly = ladder != null, shown = (row.suggestion as? Suggestion.Weight)?.load,
        )
    }
}
