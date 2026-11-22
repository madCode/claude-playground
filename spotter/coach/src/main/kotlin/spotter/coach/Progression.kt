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
    val hard: Boolean = false,
    /**
     * A ladder logged with one weight, the heaviest of its rounds, with nobody having said what
     * each round held: it can hold the number but never raise it, since the number is round one's.
     */
    val heaviestOnly: Boolean = false,
) {
    val key: String get() = exercise.key

    /** Reps a side asked of the first set, for converting between rep counts; null when timed. */
    val firstTarget: Int? get() = ladder?.first() ?: target?.last

    /**
     * The reps the logged weight stands for, when telling one rep count's history from another's:
     * what was asked, or the fewest-rep round of a ladder logged with its heaviest weight.
     */
    val anchor: Int? get() = if (ladder != null && heaviestOnly) ladder.min() else firstTarget

    /**
     * The reps the weight is known to have been done for, for converting between rep counts: the
     * fewest of the sets logged, or what a ladder's first round asked. Null when timed.
     */
    val repsDone: Int?
        get() = when {
            // The heaviest round is the one with the fewest reps: that is what the weight was held for.
            ladder != null -> if (heaviestOnly) ladder.min() else ladder.first()
            // More reps than asked are no sounder a basis than what was asked.
            else -> reps.minOrNull()?.takeIf { target != null }?.let { minOf(it, target!!.last) }
        }

    /** Whether there is anything to judge: a rep target with no reps logged says nothing. */
    val judged: Boolean get() = reps.isNotEmpty() || (target == null && ladder == null)

    // The first-set rule needs sets to compare. One set logged is the heaviest, often from a
    // later, tired round (the Log screen's one number), so it holds unless three or more short:
    // madCode's call, 2026-11-22.
    private val missed: Boolean
        get() = stopped || formBroke || shortBy().let { short ->
            (short.size > 1 && short.first() > 0) || short.any { it >= 3 }
        }

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

/** Against the logged session the number rests on. */
enum class Change { UP, SAME, DOWN, NEW }

/** What the sheet shows for a row. */
sealed interface Suggestion {
    /**
     * A weight, why, and how it compares with last time. [test] is a heavier dumbbell to try
     * when the next step is a big jump ("12 · try 15 if set 1 felt easy").
     */
    data class Weight(
        val load: Load,
        val change: Change,
        val reasons: List<String>,
        val test: Load? = null,
        /** Said on the row before the number: pain last time. */
        val caution: String? = null,
    ) : Suggestion

    /** No sound basis: [NEW], or why not. A gap beats a guess. */
    data class NoNumber(val reason: String, val new: Boolean = false) : Suggestion {
        companion object {
            /** Nothing logged for the exercise, and no sound link to one that has. */
            val NEW = NoNumber("new: go light", new = true)
        }
    }
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
        // madCode: a twinge holds the weight and notes it. With nothing but twinges logged, the
        // latest one is the basis, and the cap below holds it there or a step under.
        val sound = own.filter { it.outcome != Outcome.HURT }
        val to = target?.last
        // Each rep count keeps its own history (TRAINING.md, 5): the last time these reps were
        // asked is a better basis than a conversion from other reps, which is rounded down each
        // time and would walk the number down on a board that alternates 8s and 10s. But only
        // while the latest session bears it out: a set missed since, at any reps, puts the
        // latest session in charge (trap 4), and so does a latest weight lighter than this one
        // predicts for its reps, which is how a number from long ago would come back too heavy.
        val latest = sound.lastOrNull()
        val sameReps = sound.lastOrNull { it.anchor == to }
        val since = sound.dropWhile { it !== sameReps }.drop(1)
        // Null when the two can't be compared (timed, or reps Epley doesn't cover).
        val borneOut: Boolean? = if (sameReps == null || latest == null || latest === sameReps) true else {
            val a = sameReps.anchor
            val b = latest.anchor
            if (a == null || b == null || a !in epleyReps || b !in epleyReps) null
            else latest.load.lb >= (convert(sameReps.load.lb, a, b) ?: 0)
        }
        val basis = when {
            latest == null -> last
            sameReps != null && borneOut != false && since.none { it.outcome == Outcome.MISS } -> sameReps
            else -> latest
        }
        // No step up from an older session on the strength of one that can't be compared with
        // it, or that came up short.
        val noStep = basis !== latest && (borneOut == null || since.any { it.outcome == Outcome.NEAR_MISS })
        val reasons = mutableListOf<String>()
        var test: Load? = null
        val held = basis.load.lb
        var lb = when {
            basis.outcome == Outcome.HURT -> held
            basis.outcome == Outcome.CLEAN && noStep ->
                held.also { reasons += "clean at ${basis.load.label} the last time these reps came up: same again" }
            basis.outcome == Outcome.CLEAN && basis.heaviestOnly ->
                held.also { reasons += "a ladder logged at ${basis.load.label}, its heaviest round: same again" }
            // Weak evidence against a step, so it delays one rather than cancels it (TRAINING.md, 3).
            basis.outcome == Outcome.CLEAN && basis.hard && basis.firstTarget != null ->
                held.also { reasons += "done at ${basis.load.label} but it felt hard: same again" }
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

        // ▲ ▼ = say what the rules decided, not what a change of reps converted to.
        var decided = lb
        // Different reps from last time: Epley, only where it holds, from the reps the weight
        // was done for. A timed set and a counted one don't convert either way. The estimate is
        // off by as much as a dumbbell step (TRAINING.md, 5), so it never rides on a step up:
        // it starts from the weight held, or the step down after a miss.
        // After a miss the step down is expected to reach what was asked, so it converts from that.
        val from = if (basis.outcome == Outcome.MISS) basis.anchor else basis.repsDone ?: basis.anchor
        if (basis.anchor != to) {
            if (lb > held) {
                lb = held
                decided = held
                reasons.clear()
                reasons += "done at ${basis.load.label}"
            }
            if (from == null || to == null || from !in epleyReps || to !in epleyReps) {
                return Suggestion.NoNumber("last time was ${from?.let { "$it reps" } ?: "timed"} and this is ${to?.let { "$it reps" } ?: "timed"}: no sound conversion")
            }
            val converted = convert(lb, from, to)
                ?: return Suggestion.NoNumber("$to reps is lighter than the lightest dumbbell from last time's weight")
            if (converted != lb) reasons += "$from reps last time, $to now: $converted"
            lb = converted
            test = null
        }

        // The latest set that hurt, since the last sound one, caps the number at what hurt, or
        // under it if it was also a miss (trap 3). Last, so no step above can lift the number
        // past it. The latest, not the lowest: a twinge at 20 followed by a twinge at 25 done in
        // full holds 25. A sound session after a twinge clears it.
        var caution: String? = null
        own.lastOrNull { it.outcome == Outcome.HURT && (latest == null || !it.date.isBefore(latest.date)) }?.let { hurt ->
            var cap = if (hurt.hurtAndMissed) {
                Rack.down(hurt.load.lb) ?: return Suggestion.NoNumber("it hurt at the lightest dumbbell: no number")
            } else {
                hurt.load.lb
            }
            // What twinged at 8 reps is too much for 12: the cap converts down, never up.
            val hurtReps = hurt.anchor
            if (hurtReps != null && to != null && to > hurtReps && hurtReps in epleyReps && to in epleyReps) {
                cap = convert(cap, hurtReps, to) ?: return Suggestion.NoNumber("it hurt at the lightest dumbbell: no number")
            }
            decided = minOf(decided, cap)
            lb = minOf(lb, cap)
            // Said whether or not the cap bit: the person should know before picking it up.
            reasons += "it twinged at ${hurt.load.label}, so ${if (hurt.hurtAndMissed) "under that" else "no higher"}"
            caution = "it twinged last time at ${hurt.load.label}"
            // Never offer to try what hurt, or more.
            if ((test?.lb ?: 0) >= cap) test = null
        }

        val load = basis.load.copy(lb = lb)
        // Against the session the number rests on, so the arrow agrees with the reason.
        val change = when {
            decided > held -> Change.UP
            decided < held -> Change.DOWN
            else -> Change.SAME
        }
        return Suggestion.Weight(load, change, reasons, test, caution)
    }

    private fun convert(lb: Int, from: Int, to: Int): Int? = Rack.atMost(lb * (1 + from / 30.0) / (1 + to / 30.0))

    /**
     * A new exercise borrows only through a stated factor: a one-legged version of a two-legged
     * lift you've done, at 0.6 (trap 10). A loose relative is no basis (trap 11): no number.
     */
    private fun borrow(exercise: Reading.Known, target: IntRange?, log: List<Logged>): Suggestion {
        val part = exercise.parts.singleOrNull() ?: return Suggestion.NoNumber.NEW
        // One leg is a factor on what the legs move; a curl on one leg is a balance, not a 0.6.
        if (!part.oneLeg || part.movement.pattern !in setOf("squat", "lunge", "hinge")) return Suggestion.NoNumber.NEW
        val twoLegged = log.filter { logged ->
            val p = logged.exercise.parts.singleOrNull() ?: return@filter false
            // The plain two-legged lift: a sumo or wide stance is a variation of its own.
            p.movement == part.movement && !p.oneLeg && p.variations["legs"] in setOf(null, "standing") &&
                p.variations - "legs" == part.variations - "legs"
        }
        // The plain lift before a "standing" one, then the most recent.
        val source = twoLegged.filter { it.judged }.sortedWith(compareBy({ it.exercise.parts.single().variations["legs"] == null }, { it.date })).lastOrNull()
            ?: return Suggestion.NoNumber.NEW
        val from = suggest(source.exercise, target, log) as? Suggestion.Weight ?: return Suggestion.NoNumber.NEW
        // A two-legged lift that twinged is no basis for a first go on one leg.
        if (from.caution != null) return Suggestion.NoNumber.NEW
        val lb = Rack.atMost(from.load.lb * ONE_LEG) ?: return Suggestion.NoNumber.NEW
        return Suggestion.Weight(
            from.load.copy(lb = lb),
            Change.NEW,
            listOf("new on one leg: about 0.6 of the ${from.load.label} your ${part.movement.name} would be next"),
        )
    }
}
