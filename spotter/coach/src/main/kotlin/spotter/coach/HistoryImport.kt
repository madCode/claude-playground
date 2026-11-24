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
data class Imported(
    val logged: Logged, val className: String?, val line: String?,
    /** Neither the line nor the row said how many dumbbells: the owner's counts file did. */
    val countRuled: Boolean = false,
)

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

    data class Result(val rows: List<Imported>, val left: Map<Left, Int>, val all: List<Row> = emptyList()) {
        val logged: List<Logged> get() = rows.map { it.logged }
    }

    /**
     * A row of the file as it stands, read or not, for [Replay]: grading the coach needs what
     * was lifted on the rows it couldn't read too, since a blank row counts against it.
     */
    data class Row(
        val date: LocalDate,
        val className: String?,
        /** The line as written, or the name an old row was filed under. */
        val line: String?,
        /** One dumbbell's weight as the file has it, not brought down to the rack. */
        val weight: Double?,
        val reps: Int?,
        val skipped: Boolean,
        /** A timed or bodyweight row finished. */
        val done: Boolean,
        val hurt: Boolean,
        val easy: Boolean,
        val hard: Boolean,
        val easier: Boolean,
        /** What the coach made of it; null when [left] says why it made nothing. */
        val logged: Logged?,
        val left: Left?,
    )

    /** More than any history holds; a file built to exhaust memory stops here. */
    const val MAX_LINES = 200_000

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * [today] bounds the dates: a row dated after it was never logged, and would outrank every
     * real one. [everyRow] also keeps each row as the file has it ([Result.all]), which only the
     * replay wants: on a phone it would hold a second copy of a history in memory for nothing.
     * [counts] is the owner's own rulings on how many dumbbells, for the rows that don't say.
     */
    fun read(text: String, identity: Identity, today: LocalDate, everyRow: Boolean = true, counts: DumbbellCounts = DumbbellCounts.NONE): Result {
        val rows = mutableListOf<Imported>()
        val all = mutableListOf<Row>()
        val left = sortedMapOf<Left, Int>()
        // A byte-order mark some editors put first would cost the first row.
        val lines = text.removePrefix("\uFEFF").lineSequence().filter { it.isNotBlank() }.toList()
        // The file is appended to, so the newest rows are last: those are the ones to keep.
        if (lines.size > MAX_LINES) left[Left.TOO_MANY] = lines.size - MAX_LINES
        lines.takeLast(MAX_LINES).forEach { line ->
            val o = line.takeIf { it.length <= MAX_ROW }?.let { runCatching { json.parseToJsonElement(it) as? JsonObject }.getOrNull() }
            val date = o?.let { date(it, today) }
            val row = if (o == null || date == null) Left.UNREADABLE else row(o, date, identity, counts)
            when (row) {
                is Left -> left.merge(row, 1, Int::plus)
                is Imported -> rows += row
            }
            if (everyRow && o != null && date != null) {
                all += Row(
                    date, o.string("class"), o.string("line") ?: filedAs(o), o.number("weight")?.takeIf { it > 0 }, o.int("reps"),
                    o.flag("skipped"), o.flag("done"), o.flag("hurt"), o.flag("easy"), o.flag("hard"), o.flag("easier"),
                    (row as? Imported)?.logged, row as? Left,
                )
            }
        }
        // Appended roughly by date, not strictly: the coach reads history in date order.
        return Result(rows.sortedBy { it.logged.date }, left, all.sortedBy { it.date })
    }

    private fun date(o: JsonObject, today: LocalDate): LocalDate? =
        o.string("date")?.let { runCatching { LocalDate.parse(it) }.getOrNull() }?.takeIf { it <= today && it.year >= 2000 }

    // The name an old row was filed under: its exercise, or a combo's parts.
    private fun filedAs(o: JsonObject): String? = filed(o).takeIf { it.isNotEmpty() }?.joinToString(" to ")

    private fun filed(o: JsonObject): List<String> =
        o.string("exercise")?.let(::listOf) ?: (o["components"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content }.orEmpty()

    // An Imported, or the reason there isn't one.
    private fun row(o: JsonObject, date: LocalDate, identity: Identity, counts: DumbbellCounts): Any {
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
        val words = line?.words ?: filedAs(o)
        val exercise = words?.let { identity.read(it) } as? Reading.Known ?: return Left.NOT_NAMED

        val variant = o["variant"] as? JsonObject
        // A name that fixes the count wins (a goblet squat is one dumbbell, a single-arm press
        // one): the row's own hint is filled in by default and says "two" on single-arm lines.
        // Where the name says nothing the hint is the next word. Where neither says, the
        // owner's counts file, by the name the row is filed under; else the count stays unknown.
        val said = Sheet.rowCount(exercise) ?: when (variant?.string("implement")) {
            "double_dumbbell" -> 2
            "single_dumbbell", "ybell" -> 1
            else -> null
        }
        val count = said ?: counts.count(filed(o), written)
        val reps = o.int("reps")
        val asked = o.int("prescribed_reps")
        // Not finished, though the reps may not show it: read as "felt hard", which delays a step.
        val hard = o.flag("hard") || o.flag("incomplete")
        val flags = Flags(o.flag("hurt"), hard, o.flag("easy") && !hard)
        val load = Load(lb, count)
        val logged = when (val scheme = line?.scheme) {
            is Scheme.Reps -> {
                val target = scheme.perSide()
                val done = if (reps == null) emptyList() else listOf(perSide(reps, asked, scheme, target) ?: return Left.REPS_UNCLEAR)
                Logged(date, exercise, load, target, done, hurt = flags.hurt, hard = flags.hard, easy = flags.easy)
            }
            is Scheme.Ladder -> {
                val rounds = scheme.perSide()
                if (rounds.any { it < 1 }) return Left.REPS_UNCLEAR
                val short = if (reps != null && asked != null) maxOf(0, asked - reps) else 0
                // Asked as a round's own total ("10 / 8 / 6 total", asked 10): both sides were
                // counted, so the shortfall is halved, upwards.
                val bothSides = scheme.count == Count.TOTAL && asked in scheme.rounds && asked !in rounds
                val off = -(if (bothSides) (short + 1) / 2 else short)
                // One weight for the whole ladder, its heaviest. Which round the row's numbers
                // are for isn't said, so a shortfall is put on the last; a ladder read this way
                // never raises a number, so the choice only tells a near miss from a miss.
                val done = if (reps == null) emptyList() else rounds.dropLast(1) + maxOf(0, rounds.last() + minOf(0, off))
                Logged(date, exercise, load, rounds.first().let { it..it }, done, hurt = flags.hurt, hard = flags.hard, easy = flags.easy, ladder = rounds, heaviestOnly = true)
            }
            is Scheme.Timed, Scheme.AsManyAsPossible -> Logged(date, exercise, load, null, emptyList(), hurt = flags.hurt, hard = flags.hard, easy = flags.easy)
            null -> {
                // No line to read the reps from: only what the row itself says was asked.
                if (reps == null || asked == null || asked < 1) return Left.REPS_UNCLEAR
                Logged(date, exercise, load, asked..asked, listOf(reps), hurt = flags.hurt, hard = flags.hard, easy = flags.easy)
            }
        }
        return Imported(logged, o.string("class"), written, countRuled = said == null && count != null)
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
            // "1 total" is no reps a side: nothing to hold a set against.
            target.first < 1 -> null
            // Both sides counted: 8 of 12 total is 4 a side. The shortfall is halved upwards,
            // so one short of an odd total ("5 total", 4 done) is still one short.
            halves && asked == scheme.low && asked != target.first ->
                if (reps < asked) maxOf(0, target.first - (asked - reps + 1) / 2) else target.first + (reps - asked) / 2
            // Nothing asked on the row: only a number the line itself could have asked for.
            asked == null -> reps.takeIf { !halves && it in target }
            asked != target.first -> null
            // A side. More than a side was asked may be both sides counted: twice what was
            // asked is the set done as asked, and anything else can't be told.
            !sided || reps <= target.first -> reps
            // Twice what a side was asked: the set as asked with both sides counted, unless a
            // wide range ("4 - 8 each") could also have asked for that many a side.
            reps == 2 * target.first -> target.first.takeIf { reps > target.last }
            reps <= target.last -> reps
            else -> null
        }
    }

    private class Flags(val hurt: Boolean, val hard: Boolean, val easy: Boolean)

    private const val MAX_ROW = 20_000

    private fun JsonObject.prim(key: String): JsonPrimitive? = (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }
    private fun JsonObject.string(key: String): String? = prim(key)?.takeIf { it.isString }?.content
    private fun JsonObject.flag(key: String): Boolean = prim(key)?.booleanOrNull == true
    private fun JsonObject.number(key: String): Double? = prim(key)?.takeIf { !it.isString }?.doubleOrNull?.takeIf { it.isFinite() }
    private fun JsonObject.int(key: String): Int? = prim(key)?.takeIf { !it.isString }?.intOrNull?.takeIf { it in 0..9999 }
}
