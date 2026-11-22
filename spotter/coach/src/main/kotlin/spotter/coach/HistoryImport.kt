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
        /** Not a JSON object, or no date. */
        UNREADABLE,
        /** Marked skipped: it wasn't done. */
        SKIPPED,
        /** Bodyweight, timed with nothing held, or the weight wasn't said. */
        NO_WEIGHT,
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

    fun read(text: String, identity: Identity): Result {
        val rows = mutableListOf<Imported>()
        val left = sortedMapOf<Left, Int>()
        text.lineSequence().filter { it.isNotBlank() }.take(MAX_LINES).forEach { line ->
            when (val row = row(line, identity)) {
                is Left -> left.merge(row, 1, Int::plus)
                is Imported -> rows += row
            }
        }
        // Appended roughly by date, not strictly: the coach reads history in date order.
        return Result(rows.sortedBy { it.logged.date }, left)
    }

    // An Imported, or the reason there isn't one.
    private fun row(text: String, identity: Identity): Any {
        if (text.length > MAX_ROW) return Left.UNREADABLE
        val o = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: return Left.UNREADABLE
        val date = o.string("date")?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return Left.UNREADABLE
        if (o.flag("skipped")) return Left.SKIPPED
        // Decimals round down, never up: 22.5 is read as 22, which no rule can step past 25 from.
        val lb = o.number("weight")?.takeIf { it >= 1 && it <= MAX_LB }?.toInt() ?: return Left.NO_WEIGHT
        if (o.string("breakdown") != null) return Left.BROKE_DOWN

        val written = o.string("line")
        val line = written?.let(BoardLine::read)
        // The line as written says most; an old row has only the name it was filed under.
        val words = line?.words ?: o.string("exercise") ?: (o["components"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content }?.joinToString(" to ")
        val exercise = words?.let { identity.read(it) } as? Reading.Known ?: return Left.NOT_NAMED

        val variant = o["variant"] as? JsonObject
        // The only word on how many dumbbells; where it says nothing, the name may fix it (a
        // goblet squat is one), and otherwise the count stays unknown.
        val count = when (variant?.string("implement")) {
            "double_dumbbell" -> 2
            "single_dumbbell", "ybell" -> 1
            else -> Sheet.rowCount(exercise)
        }
        val reps = o.int("reps")
        val asked = o.int("prescribed_reps")
        // `reps` and `prescribed_reps` are in the same terms, whichever those are (a side, or
        // both sides of "12 total"), so only their difference is carried over, onto the line's
        // own reps a side (trap 7).
        val off = if (reps != null && asked != null) reps - asked else null
        val flags = Flags(o.flag("hurt"), o.flag("hard"))
        val load = Load(lb, count)
        val logged = when (val scheme = line?.scheme) {
            is Scheme.Reps -> {
                val target = scheme.perSide()
                val done = when {
                    reps == null -> emptyList()
                    off != null -> listOf(maxOf(0, target.first + off))
                    // Nothing says what was asked in the reported number's terms.
                    scheme.count == Count.TOTAL -> return Left.REPS_UNCLEAR
                    else -> listOf(reps)
                }
                Logged(date, exercise, load, target, done, hurt = flags.hurt, hard = flags.hard)
            }
            is Scheme.Ladder -> {
                val rounds = scheme.perSide()
                // One weight for the whole ladder, its heaviest; a shortfall is the last round's.
                val done = if (reps == null) emptyList() else rounds.dropLast(1) + maxOf(0, rounds.last() + minOf(0, off ?: 0))
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

    private class Flags(val hurt: Boolean, val hard: Boolean)

    private const val MAX_ROW = 20_000
    private const val MAX_LB = 500.0

    private fun JsonObject.prim(key: String): JsonPrimitive? = (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }
    private fun JsonObject.string(key: String): String? = prim(key)?.takeIf { it.isString }?.content
    private fun JsonObject.flag(key: String): Boolean = prim(key)?.booleanOrNull == true
    private fun JsonObject.number(key: String): Double? = prim(key)?.takeIf { !it.isString }?.doubleOrNull?.takeIf { it.isFinite() }
    private fun JsonObject.int(key: String): Int? = prim(key)?.takeIf { !it.isString }?.intOrNull?.takeIf { it in 0..9999 }
}
