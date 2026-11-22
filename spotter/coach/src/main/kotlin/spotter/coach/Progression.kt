package spotter.coach

import java.time.LocalDate

/**
 * One exercise as logged in one class: what was held, what was asked, and how each set went.
 * [reps] are reps a side, one entry per set; "✓ as listed" logs the target for every set.
 */
data class Logged(
    val date: LocalDate,
    val exercise: Reading.Known,
    val load: Load,
    /** Reps a side the line asked for (a range, or one number); null for a timed set. */
    val target: IntRange?,
    val reps: List<Int>,
    /** A twinge or pain. Such a set never raises a number (trap 3). */
    val hurt: Boolean = false,
    val formBroke: Boolean = false,
    val stopped: Boolean = false,
    /** A ladder's rounds ("8 / 6 / 4"), each set judged against its own; [target] is ignored. */
    val ladder: List<Int>? = null,
) {
    val key: String get() = exercise.key

    /** Reps a side asked of the first set, for converting between rep counts; null when timed. */
    val firstTarget: Int? get() = ladder?.first() ?: target?.last

    /** Whether there is anything to judge: a rep target with no reps logged says nothing. */
    val judged: Boolean get() = reps.isNotEmpty() || (target == null && ladder == null)

    private val missed: Boolean
        get() = stopped || formBroke || shortBy().let { it.firstOrNull()?.let { first -> first > 0 } == true || it.any { s -> s >= 3 } }

    // How many reps short each set was of its own target's low end.
    private fun shortBy(): List<Int> = reps.mapIndexed { i, r ->
        val asked = ladder?.let { it.getOrElse(i) { _ -> it.last() } } ?: target?.first ?: return emptyList()
        maxOf(0, asked - r)
    }

    /** How the session went, by the rules in DESIGN.md ("Progression"). */
    val outcome: Outcome
        get() = when {
            hurt -> Outcome.HURT
            missed -> Outcome.MISS
            shortBy().any { it > 0 } -> Outcome.NEAR_MISS
            ladder != null || target == null -> Outcome.CLEAN
            reps.all { it >= target.last } -> Outcome.CLEAN
            // Every set in the range but not at its top: hold until the top is reached.
            else -> Outcome.IN_RANGE
        }

    /** A hurt set that was also short or stopped: what hurt was already too heavy. */
    val hurtAndMissed: Boolean get() = hurt && missed
}

enum class Outcome { CLEAN, IN_RANGE, NEAR_MISS, MISS, HURT }

/** Against last time this exercise was logged. */
enum class Change { UP, SAME, DOWN, NEW }

/** What the sheet shows for a row. */
sealed interface Suggestion {
    /**
     * A weight, why, and how it compares with last time. [test] is a heavier dumbbell to try
     * when the next step is a big jump ("12 · try 15 if set 1 felt easy").
     */
    data class Weight(val load: Load, val change: Change, val reasons: List<String>, val test: Load? = null) : Suggestion

    /** No sound basis: "new: go light", or why not. A gap beats a guess. */
    data class NoNumber(val reason: String) : Suggestion
}

/**
 * Suggests a weight for one row from the log: straight sets with 1–2 reps in reserve, up a
 * dumbbell after a clean session, hold after a near miss or a twinge, down a step after a miss.
 * See DESIGN.md, "Progression".
 */
object Progression {
    /** A step more than this much heavier is a big jump: the sheet holds and offers a test. */
    private const val BIG_JUMP = 1.2

    /** One leg from two (trap 10): well under the two-legged weight. */
    private const val ONE_LEG = 0.6

    // Epley's estimate is only sound for sets of 3 to 12 reps.
    private val epleyReps = 3..12

    /**
     * The weight for a row of [exercise] asking [target] reps a side (the first round, for a
     * ladder; null when timed), from [log] in the order it was logged.
     */
    fun suggest(exercise: Reading.Known, target: IntRange?, log: List<Logged>): Suggestion {
        // Sessions on one date keep the order they were logged in.
        val own = log.filter { it.key == exercise.key && it.judged }.sortedBy { it.date }
        if (own.isEmpty()) return borrow(exercise, target, log)

        val last = own.last()
        val basis = own.lastOrNull { it.outcome != Outcome.HURT }
            ?: return Suggestion.NoNumber("it hurt last time: no number until a set goes well")
        val reasons = mutableListOf<String>()
        var test: Load? = null
        val held = basis.load.lb
        var lb = when {
            // A timed set says nothing about reps in reserve.
            basis.outcome == Outcome.CLEAN && basis.firstTarget == null -> held.also { reasons += "a timed set at ${basis.load.label}: same again" }
            basis.outcome == Outcome.CLEAN -> {
                val up = Rack.up(held)
                when {
                    up == null -> held.also { reasons += "clean at ${basis.load.label}, the heaviest there is" }
                    up > held * BIG_JUMP -> held.also {
                        test = basis.load.copy(lb = up)
                        reasons += "clean at ${basis.load.label}; $up is a big jump, so try it only if set 1 feels easy"
                    }
                    else -> up.also { reasons += "clean at ${basis.load.label}: up a dumbbell" }
                }
            }
            basis.outcome == Outcome.IN_RANGE -> held.also { reasons += "${basis.load.label} in range, not yet at the top: same again" }
            basis.outcome == Outcome.NEAR_MISS -> held.also { reasons += "a near miss at ${basis.load.label}: same again" }
            else -> Rack.down(held)?.also { reasons += "missed at ${basis.load.label}: down a step" }
                ?: held.also { reasons += "missed at ${basis.load.label}, the lightest there is: same again" }
        }

        // Different reps from last time: Epley, only where it holds. A timed set and a counted one
        // don't convert either way.
        val from = basis.firstTarget
        val to = target?.last
        if (from != to) {
            if (from == null || to == null || from !in epleyReps || to !in epleyReps) {
                return Suggestion.NoNumber("last time was ${from?.let { "$it reps" } ?: "timed"} and this is ${to?.let { "$it reps" } ?: "timed"}: no sound conversion")
            }
            val converted = Rack.atMost(lb * (1 + from / 30.0) / (1 + to / 30.0))
                ?: return Suggestion.NoNumber("$to reps is lighter than the lightest dumbbell from last time's weight")
            if (converted != lb) reasons += "$from reps last time, $to now: $converted"
            lb = converted
            test = null
        }

        // A set that hurt since then caps the number at what hurt, or under it if it was also a
        // miss (trap 3). Last, so no step above can lift the number past it.
        for (hurt in own.filter { it.outcome == Outcome.HURT && !it.date.isBefore(basis.date) }) {
            val cap = if (hurt.hurtAndMissed) {
                Rack.down(hurt.load.lb) ?: return Suggestion.NoNumber("it hurt at the lightest dumbbell: no number")
            } else {
                hurt.load.lb
            }
            if (lb > cap) {
                lb = cap
                reasons += "it hurt at ${hurt.load.label}, so ${if (hurt.hurtAndMissed) "under that" else "no higher"}"
            }
            // Never offer to try what hurt, or more.
            if ((test?.lb ?: 0) >= cap) test = null
        }

        val load = basis.load.copy(lb = lb)
        val change = when {
            lb > last.load.lb -> Change.UP
            lb < last.load.lb -> Change.DOWN
            else -> Change.SAME
        }
        return Suggestion.Weight(load, change, reasons, test)
    }

    /**
     * A new exercise borrows only through a stated factor: a one-legged version of a two-legged
     * lift you've done, at 0.6 (trap 10). A loose relative is no basis (trap 11): no number.
     */
    private fun borrow(exercise: Reading.Known, target: IntRange?, log: List<Logged>): Suggestion {
        val part = exercise.parts.singleOrNull() ?: return Suggestion.NoNumber("new: go light")
        if (!part.oneLeg) return Suggestion.NoNumber("new: go light")
        val twoLegged = log.filter { logged ->
            val p = logged.exercise.parts.singleOrNull() ?: return@filter false
            // The plain two-legged lift: a sumo or wide stance is a variation of its own.
            p.movement == part.movement && !p.oneLeg && p.variations["legs"] in setOf(null, "standing") &&
                p.variations - "legs" == part.variations - "legs"
        }
        // The plain lift before a "standing" one, then the most recent.
        val source = twoLegged.filter { it.judged }.sortedWith(compareBy({ it.exercise.parts.single().variations["legs"] == null }, { it.date })).lastOrNull()
            ?: return Suggestion.NoNumber("new: go light")
        val from = suggest(source.exercise, target, log) as? Suggestion.Weight ?: return Suggestion.NoNumber("new: go light")
        val lb = Rack.atMost(from.load.lb * ONE_LEG) ?: return Suggestion.NoNumber("new: go light")
        return Suggestion.Weight(
            from.load.copy(lb = lb),
            Change.NEW,
            listOf("new on one leg: about 0.6 of the ${from.load.label} your ${part.movement.name} would be next"),
        )
    }
}
