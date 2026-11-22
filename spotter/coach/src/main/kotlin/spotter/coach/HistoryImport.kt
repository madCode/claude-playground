package spotter.coach

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import java.time.LocalDate

/** One history row as the coach reads it, with the class it was logged in. */
data class Imported(val logged: Logged, val className: String?, val line: String?)

/**
 * Reads gym-coach's `history.jsonl` (docs/HISTORY-FORMAT.md) into the coach's own history.
 *
 * A row becomes history only when all of it can be read: the exercise by Spotter's own lists,
 * the weight, and what the reps were against. Anything less is left out and counted by reason,
 * because a half-read row would put a number on the sheet that nothing later could tell from a
 * sound one. The file is someone's training data: nothing from it is logged or kept here.
 */
object HistoryImport {
    /** Why a row isn't in the history. */
    enum class Left {
        /** Not a JSON object, or no date it could have been logged on. */
        UNREADABLE,
        /** Beyond [MAX_LINES]: the oldest lines of a file too long to be a history. */
        TOO_MANY,
        /** Marked skipped: it wasn't done. */
        SKIPPED,
        /** Bodyweight, timed with nothing held, or the weight wasn't said. */
        NO_WEIGHT,
        /** Lighter or heavier than any dumbbell on the rack: something else was held. */
        OFF_RACK,
        /** An easier version was done than the one the line names, so it isn't that exercise's set. */
        EASIER,
        /** Form broke down, so the reps aren't to be trusted: gym-coach never builds on such a row either. */
        BROKE_DOWN,
        /** Spotter's lists don't name the exercise in full. */
        NOT_NAMED,
        /** The reps can't be set against what was asked, so the set can't be called clean or short. */
        REPS_UNCLEAR,
    }

    data class Result(val rows: List<Imported>, val left: Map<Left, Int>) {
        val logged: List<Logged> get() = rows.map { it.logged }
    }

    /** More than any history holds; a file built to exhaust memory stops here. */
    const val MAX_LINES = 200_000

    private val json = Json { ignoreUnknownKeys = true }

    /** [today] bounds the dates: a row dated after it was never logged, and would outrank every real one. */
    fun read(text: String, identity: Identity, today: LocalDate): Result {
        val rows = mutableListOf<Imported>()
        val left = sortedMapOf<Left, Int>()
        // A byte-order mark some editors put first would cost the first row.
        val lines = text.removePrefix("\uFEFF").lineSequence().filter { it.isNotBlank() }.toList()
        // The file is appended to, so the newest rows are last: those are the ones to keep.
        if (lines.size > MAX_LINES) left[Left.TOO_MANY] = lines.size - MAX_LINES
        lines.takeLast(MAX_LINES).forEach { line ->
            when (val row = row(line, identity, today)) {
                is Left -> left.merge(row, 1, Int::plus)
                is Imported -> rows += row
            }
        }
        // Appended roughly by date, not strictly: the coach reads history in date order.
        return Result(rows.sortedBy { it.logged.date }, left)
    }

    // An Imported, or the reason there isn't one.
    private fun row(text: String, identity: Identity, today: LocalDate): Any {
        if (text.length > MAX_ROW) return Left.UNREADABLE
        val o = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: return Left.UNREADABLE
        val date = o.string("date")?.let { runCatching { LocalDate.parse(it) }.getOrNull() }?.takeIf { it <= today && it.year >= 2000 }
            ?: return Left.UNREADABLE
        if (o.flag("skipped")) return Left.SKIPPED
        val weight = o.number("weight")?.takeIf { it > 0 } ?: return Left.NO_WEIGHT
        // Down to a dumbbell that exists, never up: 22.5 is read as 20. The sheet can then say
        // "2×20", which is on the rack, where "2×22" is not.
        val lb = Rack.atMost(weight)?.takeIf { weight <= Rack.dumbbells.last() } ?: return Left.OFF_RACK
        if (o.string("breakdown") != null) return Left.BROKE_DOWN
        if (o.flag("easier")) return Left.EASIER

        val written = o.string("line")
        val line = written?.let(BoardLine::read)
        // The line as written says most; an old row has only the name it was filed under.
        val words = line?.words ?: o.string("exercise") ?: (o["components"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content }?.joinToString(" to ")
        val exercise = words?.let { identity.read(it) } as? Reading.Known ?: return Left.NOT_NAMED

        val variant = o["variant"] as? JsonObject
        // A name that fixes the count wins (a goblet squat is one dumbbell, a single-arm press
        // one): the row's own hint is filled in by default and says "two" on single-arm lines.
        // Where the name says nothing the hint is the only word, and without it the count
        // stays unknown.
        val count = Sheet.rowCount(exercise) ?: when (variant?.string("implement")) {
            "double_dumbbell" -> 2
            "single_dumbbell", "ybell" -> 1
            else -> null
        }
        val reps = o.int("reps")
        val asked = o.int("prescribed_reps")
        // Not finished, though the reps don't show it: never a clean set to step up from.
        val flags = Flags(o.flag("hurt"), o.flag("hard") || o.flag("incomplete"))
        val load = Load(lb, count)
        val logged = when (val scheme = line?.scheme) {
            is Scheme.Reps -> {
                val target = scheme.perSide()
                val done = if (reps == null) emptyList() else listOf(perSide(reps, asked, scheme, target) ?: return Left.REPS_UNCLEAR)
                Logged(date, exercise, load, target, done, hurt = flags.hurt, hard = flags.hard)
            }
            is Scheme.Ladder -> {
                val rounds = scheme.perSide()
                val off = if (reps != null && asked != null) reps - asked else 0
                // One weight for the whole ladder, its heaviest. Which round the row's numbers
                // are for isn't said, so a shortfall is put on the last; a ladder read this way
                // never raises a number, so the choice only tells a near miss from a miss.
                val done = if (reps == null) emptyList() else rounds.dropLast(1) + maxOf(0, rounds.last() + minOf(0, off))
                Logged(date, exercise, load, rounds.first().let { it..it }, done, hurt = flags.hurt, hard = flags.hard, ladder = rounds, heaviestOnly = true)
            }
            is Scheme.Timed, Scheme.AsManyAsPossible -> Logged(date, exercise, load, null, emptyList(), hurt = flags.hurt, hard = flags.hard)
            null -> {
                // No line to read the reps from: only what the row itself says was asked.
                if (reps == null || asked == null || asked < 1) return Left.REPS_UNCLEAR
                Logged(date, exercise, load, asked..asked, listOf(reps), hurt = flags.hurt, hard = flags.hard)
            }
        }
        return Imported(logged, o.string("class"), written)
    }

    /**
     * The reps done, a side, from the row's two numbers. The row doesn't say whether they count
     * a side or both sides, so the asked number is matched to the line: against "12 total" an
     * asked 6 is a side and an asked 12 is both. Where that can't be told, null: 10 reported
     * against "12 total" may be five a side, one short, or ten a side.
     */
    private fun perSide(reps: Int, asked: Int?, scheme: Scheme.Reps, target: IntRange): Int? {
        val halves = scheme.count == Count.TOTAL
        val sided = scheme.count != Count.PLAIN
        return when {
            // Both sides counted: 8 of 12 total is 4 a side.
            halves && asked == scheme.low && asked != target.first -> reps / 2
            // Nothing asked on the row: only a number the line itself could have asked for.
            asked == null -> reps.takeIf { !halves && it in target }
            asked != target.first -> null
            // A side. More than a side was asked may be both sides counted: twice what was
            // asked is the set done as asked, and anything else can't be told.
            reps <= target.last || !sided -> reps
            reps == 2 * target.first -> target.first
            else -> null
        }
    }

    private class Flags(val hurt: Boolean, val hard: Boolean)

    private const val MAX_ROW = 20_000

    private fun JsonObject.prim(key: String): JsonPrimitive? = (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }
    private fun JsonObject.string(key: String): String? = prim(key)?.takeIf { it.isString }?.content
    private fun JsonObject.flag(key: String): Boolean = prim(key)?.booleanOrNull == true
    private fun JsonObject.number(key: String): Double? = prim(key)?.takeIf { !it.isString }?.doubleOrNull?.takeIf { it.isFinite() }
    private fun JsonObject.int(key: String): Int? = prim(key)?.takeIf { !it.isString }?.intOrNull?.takeIf { it in 0..9999 }
}
