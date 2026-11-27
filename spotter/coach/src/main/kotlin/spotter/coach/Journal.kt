package spotter.coach

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate

/**
 * One event in the append-only log (DESIGN.md, Rules): a row logged, or a correction or removal
 * of one logged before. Nothing is edited in place, so what was logged never changes meaning,
 * and a correction is its own event, kept beside what it corrects.
 *
 * An exercise's identity is fixed when it is logged: [parts] holds the movements and variations
 * as they were read then, so a later change to the word lists never relabels a logged row.
 */
data class JournalEntry(
    /** Order of writing; assigned by the store. 0 for one not yet written. */
    val id: Long = 0,
    /** When it was written, in epoch milliseconds. */
    val at: Long,
    val kind: Kind,
    /** For a correction, the entry it replaces; for a removal, the entry it removes. */
    val target: Long? = null,
    val date: LocalDate,
    val className: String,
    /** The sheet row it was logged from ([SheetRow.id]). */
    val row: String,
    /** The board's own words. */
    val line: String,
    val key: String,
    val parts: List<LoggedPart>,
    val lb: Int,
    val count: Int?,
    val targetLow: Int?,
    val targetHigh: Int?,
    /** A ladder's rounds in reps a side ([Scheme.Ladder.perSide]), as [reps] are. */
    val ladder: List<Int>?,
    val reps: List<Int>,
    val hurt: Boolean = false,
    val formBroke: Boolean = false,
    val stopped: Boolean = false,
    // How it felt, for the scorecard and later rules; the coach doesn't read them yet.
    val easy: Boolean = false,
    val hard: Boolean = false,
    /** An easier version was done than the one listed. */
    val easier: Boolean = false,
    /** A ladder logged with one weight, its heaviest round's ([Logged.heaviestOnly]). */
    val heaviestOnly: Boolean = false,
    /**
     * What the sheet showed for the row when it was logged, for the scorecard: the board may have
     * changed by the time of an export. Both null when the sheet gave no number.
     */
    val shownLb: Int? = null,
    val shownCount: Int? = null,
    /**
     * A ladder's dumbbell for each round, when the person said them. [lb] is then round one's,
     * the round the sheet's number is for, and [heaviestOnly] is false.
     */
    val roundLb: List<Int>? = null,
) {
    /** [roundLb] when it can be set against the ladder, round for round; null when not said, or damaged. */
    val rounds: List<Int>? get() = roundLb?.takeIf { ladder != null && it.isNotEmpty() && it.size == ladder.size && it.all { lb -> lb > 0 } }

    enum class Kind { LOGGED, CORRECTED, REMOVED }
}

/** A part as logged: the movement's name and its variations. */
@Serializable
data class LoggedPart(val movement: String, val variations: Map<String, String>)

object Journal {
    private val json = Json

    fun partsJson(parts: List<LoggedPart>): String = json.encodeToString(parts)
    fun partsFrom(text: String): List<LoggedPart> = json.decodeFromString(text)
    fun intsJson(ints: List<Int>): String = json.encodeToString(ints)
    fun intsFrom(text: String): List<Int> = json.decodeFromString(text)

    /** An entry for a row logged now. [ladder] is in reps a side ([Scheme.Ladder.perSide]). */
    fun logged(
        at: Long, date: LocalDate, className: String, row: String, line: String, exercise: Reading.Known,
        load: Load, target: IntRange?, ladder: List<Int>?, reps: List<Int>,
        hurt: Boolean = false, formBroke: Boolean = false, stopped: Boolean = false,
        easy: Boolean = false, hard: Boolean = false, easier: Boolean = false,
        heaviestOnly: Boolean = false, shown: Load? = null, roundLb: List<Int>? = null,
    ) = JournalEntry(
        at = at, kind = JournalEntry.Kind.LOGGED, date = date, className = className, row = row, line = line,
        key = exercise.key, parts = exercise.parts.map { LoggedPart(it.movement.name, it.variations) },
        lb = load.lb, count = load.count, targetLow = target?.first, targetHigh = target?.last, ladder = ladder,
        reps = reps, hurt = hurt, formBroke = formBroke, stopped = stopped, easy = easy, hard = hard, easier = easier,
        heaviestOnly = heaviestOnly, shownLb = shown?.lb, shownCount = shown?.count, roundLb = roundLb,
    )

    /**
     * The history the coach reads, from every entry ever written, in the order written: each row
     * as last corrected, removed rows left out. A correction or removal of an entry that isn't
     * there is ignored, so a damaged log loses that one change, not the rest.
     */
    fun history(entries: List<JournalEntry>, vocabulary: Vocabulary): List<Logged> = read(entries, vocabulary).map { it.second }

    /** Each live row beside what the coach reads it as; a damaged row is left out. */
    fun read(entries: List<JournalEntry>, vocabulary: Vocabulary): List<Pair<JournalEntry, Logged>> =
        live(entries).mapNotNull { e -> toLogged(e, vocabulary)?.let { e to it } }

    /** Each row as last corrected, removed rows left out, in the order first logged. */
    fun live(entries: List<JournalEntry>): List<JournalEntry> {
        // Each live row, by the id of the entry that first logged it.
        val rows = linkedMapOf<Long, JournalEntry>()
        // Any entry's id, to the row it belongs to: corrections can target a correction.
        val rowOf = mutableMapOf<Long, Long>()
        // Only written entries: an id is what corrections point at, so unwritten ones (id 0)
        // would collapse into one row.
        for (e in entries.filter { it.id > 0 }.sortedBy { it.id }) {
            when (e.kind) {
                JournalEntry.Kind.LOGGED -> {
                    rows[e.id] = e
                    rowOf[e.id] = e.id
                }
                JournalEntry.Kind.CORRECTED -> {
                    val row = e.target?.let { rowOf[it] } ?: continue
                    if (row !in rows) continue
                    rows[row] = e
                    rowOf[e.id] = row
                }
                JournalEntry.Kind.REMOVED -> {
                    val row = e.target?.let { rowOf[it] } ?: continue
                    rows.remove(row)
                }
            }
        }
        return rows.values.toList()
    }

    /**
     * The live rows done with nothing held ([RowLog.NOTHING]), each with its exercise: no part
     * of the history the coach reads, but part of the class, for looking back at it.
     */
    fun bare(entries: List<JournalEntry>, vocabulary: Vocabulary): List<Pair<JournalEntry, Reading.Known>> =
        live(entries).filter { it.lb == RowLog.NOTHING }.mapNotNull { e -> exercise(e, vocabulary)?.let { e to it } }

    internal fun exercise(e: JournalEntry, vocabulary: Vocabulary): Reading.Known? {
        if (e.parts.isEmpty()) return null
        // The movement as the lists know it now, for what isn't in the key (one-leggedness); a
        // movement since renamed keeps its logged name, so the key is unchanged either way.
        val parts = e.parts.map { p ->
            Part(vocabulary.movements.firstOrNull { it.name == p.movement } ?: Movement(p.movement), p.variations)
        }
        // The parts are the identity. Should the key's format ever change, keys rebuilt from them
        // change with the sheet's, so history still matches rather than vanishing.
        return Reading.Known(parts, emptySet())
    }

    private fun toLogged(e: JournalEntry, vocabulary: Vocabulary): Logged? {
        val exercise = exercise(e, vocabulary) ?: return null
        val read = read(e)
        // A damaged row loses itself: a weight or count no load can have, an empty ladder.
        val load = runCatching { Load(read.lb, e.count) }.getOrNull() ?: return null
        val target = if (e.targetLow != null && e.targetHigh != null) e.targetLow..e.targetHigh else null
        return Logged(
            e.date, exercise, load, target, e.reps,
            hurt = e.hurt, formBroke = e.formBroke, stopped = e.stopped, ladder = e.ladder?.takeIf { it.isNotEmpty() },
            hard = e.hard, easy = e.easy, heaviestOnly = read.heaviestOnly, faded = read.faded,
        )
    }

    private class Read(val lb: Int, val heaviestOnly: Boolean, val faded: Boolean = false)

    /**
     * The one weight the coach reads a ladder at. With a weight for each round there are two
     * sound readings, and the entry gets whichever says more without saying too much:
     *
     * - Round one's weight, done from round one: it can raise the number, like a listed ladder.
     * - The heaviest round's, held for the ladder's fewest reps or more, as a one-weight ladder is read: it only
     *   holds a number. Taken when the ladder climbed to more than round one's weight is worth
     *   (started light, so round one says too little), and when something went wrong (a twinge,
     *   a stop), since the heaviest round is where that is assumed to have happened.
     */
    private fun read(e: JournalEntry): Read {
        val rounds = e.rounds
        val ladder = e.ladder
        if (rounds == null || ladder == null) return Read(e.lb, e.heaviestOnly)
        val first = rounds.first()
        val top = rounds.max()
        if (top > first) {
            // Whichever round the heaviest fell in, it was held for at least the ladder's fewest
            // reps, which is what a one-weight ladder's number stands for.
            val worth = Progression.converted(top, ladder.min(), ladder.first())
            if (e.hurt || e.stopped || e.formBroke || (worth != null && worth > first)) return Read(top, heaviestOnly = true)
        }
        return Read(first, heaviestOnly = false, faded = rounds.indices.any { rounds[it] < first && ladder[it] <= ladder.first() })
    }
}
