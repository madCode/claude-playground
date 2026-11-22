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
) {
    companion object {
        /** A logged entry in the screen's terms. */
        fun of(e: JournalEntry) = RowLog(e.lb, e.count, e.reps.singleOrNull()?.takeIf { e.ladder == null }, e.easy, e.hard, e.hurt, e.stopped, e.easier)
    }
}

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
    /** Logged rows no longer on the board ([ClassLog.strays]) the person has cleared, by entry id. */
    val cleared: Set<Long> = emptySet(),
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
         * "Changed", once a weight is picked from the rack: that weight at the board's reps.
         * Nothing is logged before the pick, so no made-up set enters history. The count is only
         * what is known: the row's words, the sheet's number, or the last time it was logged;
         * otherwise unknown until the person says.
         */
        fun picked(row: SheetRow, lb: Int): RowLog = RowLog(lb, count(row), reps(row))

        private fun count(row: SheetRow): Int? = (row.reading as? Reading.Known)?.let(Sheet::rowCount)
            ?: (row.suggestion as? Suggestion.Weight)?.load?.count
            ?: row.lastHeld?.count

        private fun reps(row: SheetRow): Int? = if (row.line.scheme is Scheme.Ladder) null else row.target?.first

        /** The draft for a class already logged, or partly: what is in the log for its rows. */
        fun from(sheet: Sheet, logged: Map<String, JournalEntry>): LogDraft {
            val rows = sheet.rows.mapNotNull { row ->
                val e = logged[row.id] ?: return@mapNotNull null
                row.id to RowLog.of(e)
            }.toMap()
            return LogDraft(rows, rows.keys)
        }
    }
}

/**
 * What is logged for one class, set against the board as it is now. The board can change under a
 * log: the class's own board replaces a borrowed one, or its poster revises a line.
 */
data class ClassLog(
    /** By [SheetRow.id]: the entry logged from that row, or carried over to it from a row now gone. */
    val byRow: Map<String, JournalEntry> = emptyMap(),
    /** Logged from rows no longer on the board, with no row to carry over to: shown, to keep or clear. */
    val strays: List<JournalEntry> = emptyList(),
)

object Logging {
    /**
     * The live entries for one class, matched to [sheet]'s rows: what the Log screen starts from.
     *
     * An entry whose row is gone carries over to the first free row of the same exercise and the
     * same kind of count (madCode, 2026-11-22): what was done was done, whichever board named it.
     * It keeps its own weight and reps. With no such row it is a stray.
     */
    fun logged(sheet: Sheet, entries: List<JournalEntry>, date: LocalDate, className: String): ClassLog {
        val live = Journal.live(entries).filter { it.date == date && it.className == className }
        val ids = sheet.rows.map { it.id }.toSet()
        val byRow = linkedMapOf<String, JournalEntry>()
        // Two entries can't share a row id unless the log is damaged; the later one wins, as before.
        live.filter { it.row in ids }.forEach { byRow[it.row] = it }
        val strays = mutableListOf<JournalEntry>()
        for (e in live.filter { it.row !in ids }) {
            val row = sheet.rows.firstOrNull { it.id !in byRow && LogDraft.loggable(it) && (it.reading as Reading.Known).key == e.key && shape(it) == shape(e) }
            if (row == null) strays += e else byRow[row.id] = e
        }
        return ClassLog(byRow, strays)
    }

    // A ladder, a counted row and a timed one are logged in different terms: an entry carried to
    // a row of another kind would be rewritten in the wrong ones when corrected.
    private fun shape(row: SheetRow) = when {
        row.line.scheme is Scheme.Ladder -> "ladder"
        row.target != null -> "counted"
        else -> "other"
    }

    private fun shape(e: JournalEntry) = when {
        e.ladder != null -> "ladder"
        e.reps.size == 1 -> "counted"
        else -> "other"
    }

    /**
     * The events to write for [draft] against what is [logged] already: a new row is logged, a
     * changed one corrected, one cleared removed, and one unchanged left alone, so saving twice
     * writes nothing the second time. Rows no longer on the sheet are left as they are.
     *
     * Only a row the screen opened with ([LogDraft.seen]) and then cleared is removed: a row logged
     * since the screen opened, which it never showed, isn't the person's to have cleared.
     *
     * An entry carried over from a row now gone is moved to the row it was carried to, keeping
     * its own words, target and reps. A stray is removed only when cleared.
     */
    fun events(
        draft: LogDraft, sheet: Sheet, log: ClassLog, date: LocalDate, className: String, at: Long,
    ): List<JournalEntry> = rowEvents(draft, sheet, log.byRow, date, className, at) +
        log.strays.filter { it.id in draft.cleared }.map { it.copy(id = 0, at = at, kind = JournalEntry.Kind.REMOVED, target = it.id) }

    private fun rowEvents(
        draft: LogDraft, sheet: Sheet, logged: Map<String, JournalEntry>, date: LocalDate, className: String, at: Long,
    ): List<JournalEntry> = sheet.rows.filter(LogDraft::loggable).mapNotNull { row ->
        val now = draft.rows[row.id]
        val before = logged[row.id]
        when {
            now == null && before == null -> null
            now == null -> if (row.id !in draft.seen) null else before!!.copy(id = 0, at = at, kind = JournalEntry.Kind.REMOVED, target = before.id)
            // Unchanged. A carried entry settles on its row all the same, so that removing
            // another one later can't shuffle which entry sits on which row.
            before != null && RowLog.of(before) == now ->
                if (before.row == row.id) null else before.copy(id = 0, at = at, kind = JournalEntry.Kind.CORRECTED, target = before.id, row = row.id)
            before == null -> entry(row, now, date, className, at)
            // Logged from other words (a borrowed board, a line since revised), and the reps left
            // as they were: the correction keeps the entry's own line, target and rounds. Set
            // against this row's target, 8 reps done of 8 asked would read as 8 of 12, a miss
            // that never happened. Reps entered anew ("as listed", a fresh pick) speak for this
            // row, and take its terms below.
            before.line != row.shown && now.reps == RowLog.of(before).reps -> before.copy(
                id = 0, at = at, kind = JournalEntry.Kind.CORRECTED, target = before.id, row = row.id,
                lb = now.lb, count = now.count, reps = before.ladder ?: listOfNotNull(now.reps),
                hurt = now.hurt, stopped = now.stopped, easy = now.easy, hard = now.hard, easier = now.easier,
                // The weight as it was keeps what it meant; another is the heaviest round's
                // unless it is the sheet's number, as on a row of its own.
                heaviestOnly = before.ladder != null &&
                    if (now.lb == before.lb) before.heaviestOnly else now.lb != LogDraft.asListed(row)?.lb,
            )
            // A correction keeps what the sheet showed when the row was first logged: the
            // sheet as rebuilt now may show another number, and the scorecard grades the
            // one the person lifted from.
            else -> entry(row, now, date, className, at).copy(
                kind = JournalEntry.Kind.CORRECTED, target = before.id, shownLb = before.shownLb, shownCount = before.shownCount,
            )
        }
    }

    private fun entry(row: SheetRow, log: RowLog, date: LocalDate, className: String, at: Long): JournalEntry {
        val ladder = (row.line.scheme as? Scheme.Ladder)?.perSide()
        return Journal.logged(
            at, date, className, row.id, row.shown, row.reading as Reading.Known, Load(log.lb, log.count),
            target = row.target, ladder = ladder, reps = ladder ?: listOfNotNull(log.reps),
            hurt = log.hurt, stopped = log.stopped, easy = log.easy, hard = log.hard, easier = log.easier,
            // A ladder logged at the sheet's number was done at it from round one; another weight
            // is the heaviest round's. Only the weight tells them apart: saying how many
            // dumbbells, or how it felt, doesn't make a listed ladder a changed one.
            heaviestOnly = ladder != null && log.lb != LogDraft.asListed(row)?.lb, shown = (row.suggestion as? Suggestion.Weight)?.load,
        )
    }
}
